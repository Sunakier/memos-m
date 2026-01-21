package org.example.memosm.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.example.memosm.api.AuthInterceptor
import org.example.memosm.api.MemosApiV0353
import org.example.memosm.data.DataStoreManager
import org.example.memosm.model.*
import org.example.memosm.viewmodel.manager.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MemosViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private var api: MemosApiV0353? = null

    // Managers
    private var userMemoManager: UserMemoListManager? = null
    private var exploreMemoManager: ExploreMemoListManager? = null
    private var archivedMemoManager: ArchivedMemoListManager? = null
    private var searchMemoManager: SearchMemoListManager? = null
    private var commentManager: CommentListManager? = null
    private var attachmentManager: AttachmentManager? = null

    private var collectionJob: Job? = null
    private val pendingUserRequests = mutableSetOf<String>()

    init {
        updateCurrentAccountInList()

        // Restore Draft
        viewModelScope.launch {
            dataStoreManager.memoDraftJson.collect { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        val draft = Gson().fromJson(json, Memo::class.java)
                        _uiState.update {
                            it.copy(draft = it.draft.copy(draftMemo = draft, isDraftLoaded = true))
                        }
                    } catch (e: Exception) {
                        Log.e("MemosViewModel", "Error loading draft", e)
                    }
                } else {
                    _uiState.update {
                        it.copy(draft = it.draft.copy(isDraftLoaded = true))
                    }
                }
            }
        }
    }

    private fun createApi(baseUrl: String, token: String): MemosApiV0353 {
        var normalizedBaseUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedBaseUrl.endsWith("/api/v1/")) {
            normalizedBaseUrl = normalizedBaseUrl.removeSuffix("api/v1/")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(token))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(MemosApiV0353::class.java)
    }

    fun updateCurrentAccountInList() {
        viewModelScope.launch {
            val accounts = dataStoreManager.getAccounts()
            val activeAccount = accounts.find { it.isActive }

            _uiState.update { it.copy(accounts = accounts) }

            if (activeAccount != null) {
                switchAccount(activeAccount)
            } else {
                _uiState.update { it.copy(error = "No active account found") }
            }
        }
    }

    fun switchAccount(account: Account) {
        viewModelScope.launch {
            try {
                dataStoreManager.setActiveAccount(account.id)
                dataStoreManager.updateAccountLastUsed(account.id, System.currentTimeMillis())

                _uiState.update {
                    it.copy(
                        session = SessionState(
                            token = account.accessToken,
                            hostUrl = account.hostUrl,
                            currUser = null
                        ),
                        accounts = it.accounts.map { acc ->
                            acc.copy(isActive = acc.id == account.id)
                        },
                        users = emptyMap()
                    )
                }
                pendingUserRequests.clear()

                api = createApi(account.hostUrl, account.accessToken)
                val currentApi = api!!

                // Initialize Managers
                userMemoManager = UserMemoListManager(viewModelScope, currentApi) {
                    val user = _uiState.value.session.currUser
                    val userId = user?.name?.substringAfterLast("/") ?: ""

                    // Use creator_id and row_status
                    val base = if (userId.isNotEmpty()) {
                        "creator_id == $userId"
                    } else {
                        ""
                    }

                    val shortcut = _uiState.value.userMemoList.selectedShortcut
                    if (shortcut != null && !shortcut.filter.isNullOrBlank()) {
                        "$base && ${shortcut.filter}"
                    } else {
                        base
                    }
                }
                exploreMemoManager = ExploreMemoListManager(viewModelScope, currentApi)
                archivedMemoManager = ArchivedMemoListManager(viewModelScope, currentApi) {
                    _uiState.value.session.currUser
                }
                searchMemoManager = SearchMemoListManager(viewModelScope, currentApi)
                commentManager = CommentListManager(viewModelScope, currentApi)
                attachmentManager = AttachmentManager(
                    viewModelScope,
                    currentApi,
                    _uiState.value.attachmentList.cellWidth
                )

                startStateCollection()
                fetchCurrentUser()
                exploreMemoManager?.fetch()

            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error switching account", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun startStateCollection() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            combine(
                combine(
                    userMemoManager!!.listState,
                    exploreMemoManager!!.listState,
                    archivedMemoManager!!.listState
                ) { u, e, a -> Triple(u, e, a) },
                combine(
                    searchMemoManager!!.listState,
                    commentManager!!.listState,
                    attachmentManager!!.listState
                ) { s, c, at -> Triple(s, c, at) },
                attachmentManager!!.cellWidth
            ) { (userMemos, exploreMemos, archivedMemos), (searchMemos, comments, attachments), cellWidth ->
                _uiState.value.copy(
                    userMemoList = _uiState.value.userMemoList.copy(list = userMemos),
                    exploreMemoList = _uiState.value.exploreMemoList.copy(list = exploreMemos),
                    archivedMemoList = _uiState.value.archivedMemoList.copy(list = archivedMemos),
                    searchMemoList = _uiState.value.searchMemoList.copy(list = searchMemos),
                    detailPane = _uiState.value.detailPane.copy(comments = comments),
                    attachmentList = AttachmentListState(list = attachments, cellWidth = cellWidth)
                )
            }.collect { newState ->
                _uiState.value = newState

                // Fetch missing users for all visible lists
                val allCreators = (newState.userMemoList.list.items +
                        newState.exploreMemoList.list.items +
                        newState.searchMemoList.list.items +
                        newState.archivedMemoList.list.items)
                    .mapNotNull { it.creator }
                    .distinct()
                fetchUsers(allCreators)
            }
        }
    }

    // --- User & Session ---

    private fun fetchUsers(names: List<String>) {
        val toFetch = names.filter { it !in _uiState.value.users && it !in pendingUserRequests }
        if (toFetch.isEmpty()) return

        toFetch.forEach { name ->
            pendingUserRequests.add(name)
            viewModelScope.launch {
                try {
                    val user = api?.getUser(name)
                    if (user != null) {
                        _uiState.update { it.copy(users = it.users + (name to user)) }
                    }
                } catch (e: Exception) {
                    Log.e("MemosViewModel", "Error fetching user $name", e)
                } finally {
                    pendingUserRequests.remove(name)
                }
            }
        }
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            try {
                val user = api?.getCurrentSession()?.user
                Log.d("MemosViewModel", "fetchCurrentUser: user=$user")
                if (user != null) {
                    _uiState.update {
                        Log.d("MemosViewModel", "Updating session with user: ${user.name}")
                        it.copy(session = it.session.copy(currUser = user))
                    }

                    // Force refresh user memos now that we have the numeric userId
                    userMemoManager?.fetch(refresh = true)

                    val resourceName = user.name ?: ""
                    if (resourceName.isNotBlank()) {
                        launch { fetchShortcuts(resourceName) }
                        launch { fetchUserSettings(resourceName) }
                        launch { fetchWebhooks(resourceName) }
                        launch { fetchUserStats(resourceName) }
                    }

                    fetchInstanceProfile()
                }
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching current user", e)
            }
        }
    }

    private suspend fun fetchInstanceProfile() {
        try {
            val profile = api?.getInstanceProfile()
            if (profile != null) {
                _uiState.update { it.copy(session = it.session.copy(instanceProfile = profile)) }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching instance profile", e)
        }
    }

    private suspend fun fetchUserStats(userResourceName: String) {
        try {
            val stats = api?.getUserStats(userResourceName)
            if (stats != null) {
                _uiState.update { it.copy(session = it.session.copy(userStats = stats)) }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching user stats", e)
        }
    }

    private suspend fun fetchUserSettings(userResourceName: String) {
        try {
            val response = api?.listUserSettings(userResourceName)
            val general =
                response?.settings?.find { it.name?.endsWith("general") == true || it.generalSetting != null }?.generalSetting
            if (general != null) {
                _uiState.update { it.copy(session = it.session.copy(userSettings = general)) }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching user settings", e)
        }
    }

    fun updateUserGeneralSetting(locale: String? = null, memoVisibility: String? = null) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val currentSetting = _uiState.value.session.userSettings ?: UserGeneralSetting()
                val newSetting = currentSetting.copy(
                    locale = locale ?: currentSetting.locale,
                    memoVisibility = memoVisibility ?: currentSetting.memoVisibility
                )
                val maskParts = mutableListOf<String>()
                if (locale != null) maskParts.add("locale")
                if (memoVisibility != null) maskParts.add("memoVisibility")
                val updateMask = maskParts.joinToString(",")

                if (updateMask.isNotEmpty()) {
                    api?.updateUserSetting(
                        user.name!!,
                        "GENERAL",
                        UserSetting(generalSetting = newSetting),
                        updateMask
                    )
                    fetchUserSettings(user.name!!)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateUserProfile(
        username: String? = null,
        email: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        description: String? = null,
        password: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val currentUser = _uiState.value.session.currUser ?: return@launch
                val update = User(
                    username = username,
                    email = email,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    description = description,
                    password = password
                )
                val maskParts = mutableListOf<String>()
                if (username != null) maskParts.add("username")
                if (email != null) maskParts.add("email")
                if (displayName != null) maskParts.add("display_name")
                if (avatarUrl != null) maskParts.add("avatar_url")
                if (description != null) maskParts.add("description")
                if (password != null) maskParts.add("password")

                val mask = maskParts.joinToString(",")

                if (mask.isNotEmpty()) {
                    api?.updateUser(currentUser.name!!, update, mask)
                    fetchCurrentUser()
                    onResult(true)
                } else {
                    onResult(true)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                onResult(false)
            }
        }
    }

    // --- Shortcuts ---

    private suspend fun fetchShortcuts(userResourceName: String) {
        try {
            val response = api?.getShortcuts(userResourceName)
            val shortcuts = response?.shortcuts ?: emptyList()
            _uiState.update {
                it.copy(userMemoList = it.userMemoList.copy(shortcuts = shortcuts))
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching shortcuts", e)
        }
    }

    fun toggleShortcutFilter(shortcut: Shortcut) {
        val currShortcut = _uiState.value.userMemoList.selectedShortcut
        val newSelection = if (currShortcut == shortcut) null else shortcut

        _uiState.update {
            it.copy(userMemoList = it.userMemoList.copy(selectedShortcut = newSelection))
        }

        userMemoManager?.fetch(refresh = true)
    }

    fun createShortcut(
        title: String,
        filter: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val shortcut = Shortcut(title = title, filter = filter)
                api?.createShortcut(user.name!!, shortcut)
                fetchShortcuts(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun updateShortcut(
        shortcut: Shortcut,
        title: String,
        filter: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val update = shortcut.copy(title = title, filter = filter)
                api?.updateShortcut(user.name!!, shortcut.name!!, update, "title,filter")
                fetchShortcuts(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteShortcut(shortcut: Shortcut) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                api?.deleteShortcut(user.name!!, shortcut.name!!)
                fetchShortcuts(user.name!!)
            } catch (e: Exception) {
            }
        }
    }

    // --- Webhooks ---

    private suspend fun fetchWebhooks(userResourceName: String) {
        try {
            val response = api?.listUserWebhooks(userResourceName)
            val hooks = response?.webhooks ?: emptyList()
            _uiState.update { it.copy(session = it.session.copy(webhooks = hooks)) }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching webhooks", e)
        }
    }

    fun createWebhook(
        displayName: String,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val webhook = UserWebhook(displayName = displayName, url = url)
                api?.createUserWebhook(user.name!!, webhook)
                fetchWebhooks(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun updateWebhook(
        webhook: UserWebhook,
        displayName: String,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val update = webhook.copy(displayName = displayName, url = url)
                api?.updateUserWebhook(user.name!!, webhook.name!!, update, "display_name,url")
                fetchWebhooks(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteWebhook(webhook: UserWebhook) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                api?.deleteUserWebhook(user.name!!, webhook.name!!)
                fetchWebhooks(user.name!!)
            } catch (e: Exception) {
            }
        }
    }

    // --- Account (Local) ---

    fun removeAccount(account: Account) {
        viewModelScope.launch {
            try {
                dataStoreManager.deleteAccount(account.id)
                updateCurrentAccountInList()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error deleting account", e)
            }
        }
    }

    fun updateAccountCredentials(account: Account, hostUrl: String, token: String) {
        viewModelScope.launch {
            try {
                dataStoreManager.updateAccount(account.id, hostUrl, token)
                updateCurrentAccountInList()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error updating credentials", e)
            }
        }
    }

    // --- List Accessors ---

    fun refreshAll() {
        _uiState.update {
            it.copy(
                isRefreshing = true,
                refreshTrigger = System.currentTimeMillis()
            )
        }
        userMemoManager?.fetch(refresh = true)
        exploreMemoManager?.fetch(refresh = true)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMoreUserMemos() = userMemoManager?.loadMore()
    fun fetchExplore(refresh: Boolean = false) = exploreMemoManager?.fetch(refresh)
    fun loadMoreExplore() = exploreMemoManager?.loadMore()

    fun fetchArchivedMemos(refresh: Boolean = false) = archivedMemoManager?.fetch(refresh)
    fun loadMoreArchived() = archivedMemoManager?.loadMore()

    fun prepareSearch(isExplore: Boolean, filter: String?, orderBy: String? = null) {
        searchMemoManager?.updateFilter(filter)
        searchMemoManager?.fetch(refresh = true)
    }

    fun fetchAttachments(loadMore: Boolean = false) {
        if (loadMore) attachmentManager?.loadMore()
        else attachmentManager?.fetch(refresh = true)
    }

    fun updateAttachmentCellWidth(width: Float) {
        attachmentManager?.updateCellWidth(width)
    }

    // --- Detail & CRUD ---

    fun selectMemo(memo: Memo?) {
        _uiState.update {
            it.copy(detailPane = it.detailPane.copy(selectedMemo = memo))
        }
        if (memo != null) {
            commentManager?.setMemo(memo.name ?: "")
        }
    }

    fun clearSelectedMemo() = selectMemo(null)

    fun createMemo(
        content: String,
        visibility: String,
        attachments: List<Attachment>? = null,
        location: Location? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isPosting = true) }
                val memo = Memo(
                    content = content,
                    visibility = visibility,
                    attachments = attachments,
                    location = location
                )
                val created = api?.createMemo(memo)
                if (created != null) {
                    onSuccess()
                    userMemoManager?.fetch(refresh = true)
                    saveDraft("", "PRIVATE", emptyList(), null)
                    _uiState.update {
                        it.copy(
                            draft = it.draft.copy(
                                composerResetToken = System.currentTimeMillis().toInt()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isPosting = false) }
            }
        }
    }

    private fun updateMemoInState(updatedMemo: Memo) {
        val transform = { state: PaginatedListState<Memo> ->
            state.copy(items = state.items.map { if (it.name == updatedMemo.name) updatedMemo else it })
        }
        userMemoManager?.updateState(transform)
        exploreMemoManager?.updateState(transform)
        archivedMemoManager?.updateState(transform)
        searchMemoManager?.updateState(transform)
        commentManager?.updateState(transform)

        // Keep selectedMemo in sync if it's the one that was updated
        if (_uiState.value.detailPane.selectedMemo?.name == updatedMemo.name) {
            _uiState.update {
                it.copy(detailPane = it.detailPane.copy(selectedMemo = updatedMemo))
            }
        }
    }

    fun updateMemo(
        memo: Memo,
        content: String,
        visibility: String,
        attachments: List<Attachment>,
        location: Location? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val update = memo.copy(
                    content = content,
                    visibility = visibility,
                    attachments = attachments,
                    location = location
                )
                val updated =
                    api?.updateMemo(memo.name!!, update, "content,visibility,attachments,location")
                if (updated != null) {
                    onSuccess()
                    updateMemoInState(updated)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateMemoState(memo: Memo, state: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val update = memo.copy(state = state)
                val updated = api?.updateMemo(memo.name!!, update, "state")
                if (updated != null) {
                    onSuccess()
                    // Remove from current list if it's archived/unarchived and refresh both lists
                    userMemoManager?.fetch(refresh = true)
                    archivedMemoManager?.fetch(refresh = true)
                    exploreMemoManager?.fetch(refresh = true)
                    updateMemoInState(updated)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteMemo(memo: Memo, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                api?.deleteMemo(memo.name!!)
                onSuccess()
                userMemoManager?.fetch(refresh = true)
                exploreMemoManager?.fetch(refresh = true)
                archivedMemoManager?.fetch(refresh = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun createComment(parentMemo: Memo, content: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val comment = Memo(content = content, visibility = parentMemo.visibility)
                api?.createMemoComment(parentMemo.name!!, comment)
                onSuccess()
                commentManager?.fetch(refresh = true)
            } catch (e: Exception) {
            }
        }
    }

    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        return attachmentManager?.uploadAttachment(uri, context)
    }

    fun saveDraft(
        content: String,
        visibility: String,
        attachments: List<Attachment>,
        location: Location? = null
    ) {
        val draftMemo = Memo(
            content = content,
            visibility = visibility,
            attachments = attachments,
            location = location
        )
        _uiState.update {
            it.copy(draft = it.draft.copy(draftMemo = draftMemo))
        }
        viewModelScope.launch {
            dataStoreManager.saveMemoDraft(Gson().toJson(draftMemo))
        }
    }

    fun upsertMemoReaction(memo: Memo, reactionType: String) {
        viewModelScope.launch {
            try {
                val reaction = Reaction(contentId = memo.name!!, reactionType = reactionType)
                val request = UpsertMemoReactionRequest(name = memo.name, reaction = reaction)
                api?.upsertMemoReaction(memo.name, request)

                // Fetch latest memo state to be sure about all reactions and update in-place
                val updated = api?.getMemo(memo.name)
                if (updated != null) {
                    updateMemoInState(updated)
                }
            } catch (e: Exception) {
            }
        }
    }

    fun deleteMemoReaction(memo: Memo, reaction: Reaction) {
        viewModelScope.launch {
            try {
                val reactionName = reaction.name ?: return@launch
                api?.deleteMemoReaction(reactionName)

                // Fetch latest memo state and update in-place
                val updated = api?.getMemo(memo.name!!)
                if (updated != null) {
                    updateMemoInState(updated)
                }
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        fun provideFactory(
            dataStoreManager: DataStoreManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MemosViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MemosViewModel(dataStoreManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    class Factory(
        private val dataStoreManager: DataStoreManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MemosViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MemosViewModel(dataStoreManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
