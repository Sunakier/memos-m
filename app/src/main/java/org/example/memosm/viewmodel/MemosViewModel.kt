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
import org.example.memosm.api.StreamingAttachmentApi
import org.example.memosm.data.DataStoreManager
import org.example.memosm.data.DraftManager
import org.example.memosm.data.cache.CacheListType
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.model.*
import org.example.memosm.viewmodel.manager.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MemosViewModel(
    private val dataStoreManager: DataStoreManager,
    private val draftManager: DraftManager,
    private val memoCacheRepository: MemoCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private var api: MemosApiV0353? = null
    private var currentHttpClient: OkHttpClient? = null
    private var currentBaseUrl: String? = null

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
    }

    private fun createApi(baseUrl: String, token: String): MemosApiV0353 {
        var normalizedBaseUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedBaseUrl.endsWith("/api/v1/")) {
            normalizedBaseUrl = normalizedBaseUrl.removeSuffix("api/v1/")
        }

        currentHttpClient = OkHttpClient.Builder().addInterceptor(AuthInterceptor(token)).build()
        currentBaseUrl = normalizedBaseUrl

        val retrofit = Retrofit.Builder().baseUrl(normalizedBaseUrl).client(currentHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create()).build()

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
                            token = account.accessToken, hostUrl = account.hostUrl, currUser = account.user
                        ), accounts = it.accounts.map { acc ->
                            acc.copy(isActive = acc.id == account.id)
                        }, users = emptyMap()
                    )
                }
                pendingUserRequests.clear()

                api = createApi(account.hostUrl, account.accessToken)
                val currentApi = api!!

                // Initialize Managers with cache callbacks
                val accountId = account.id

                userMemoManager = UserMemoListManager(
                    scope = viewModelScope,
                    api = currentApi,
                    filterProvider = {
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
                    },
                    cacheCallbacks = CacheCallbacks(
                        onFetchSuccess = { memos -> memoCacheRepository.cacheMemos(accountId, CacheListType.USER, memos) },
                        getCachedData = { memoCacheRepository.getCachedMemos(accountId, CacheListType.USER) }
                    )
                )

                exploreMemoManager = ExploreMemoListManager(
                    scope = viewModelScope,
                    api = currentApi,
                    cacheCallbacks = CacheCallbacks(
                        onFetchSuccess = { memos -> memoCacheRepository.cacheMemos(accountId, CacheListType.EXPLORE, memos) },
                        getCachedData = { memoCacheRepository.getCachedMemos(accountId, CacheListType.EXPLORE) }
                    )
                )

                archivedMemoManager = ArchivedMemoListManager(
                    scope = viewModelScope,
                    api = currentApi,
                    currentUserProvider = { _uiState.value.session.currUser },
                    cacheCallbacks = CacheCallbacks(
                        onFetchSuccess = { memos -> memoCacheRepository.cacheMemos(accountId, CacheListType.ARCHIVED, memos) },
                        getCachedData = { memoCacheRepository.getCachedMemos(accountId, CacheListType.ARCHIVED) }
                    )
                )
                searchMemoManager = SearchMemoListManager(viewModelScope, currentApi)
                commentManager = CommentListManager(viewModelScope, currentApi)
                attachmentManager = AttachmentManager(
                    scope = viewModelScope,
                    api = currentApi,
                    streamingApi = currentHttpClient?.let { 
                        StreamingAttachmentApi(it, currentBaseUrl ?: "") 
                    },
                    initialCellWidth = _uiState.value.attachmentList.cellWidth
                )

                startStateCollection()
                fetchCurrentUser()
                exploreMemoManager?.fetch()
                if (account.user != null) {
                    userMemoManager?.fetch()
                }
                loadDraftsForAccount(account.id)

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
                ) { u, e, a -> Triple(u, e, a) }, combine(
                    searchMemoManager!!.listState,
                    commentManager!!.listState,
                    attachmentManager!!.listState
                ) { s, c, at -> Triple(s, c, at) }, attachmentManager!!.cellWidth
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
                val allCreators =
                    (newState.userMemoList.list.items + newState.exploreMemoList.list.items + newState.searchMemoList.list.items + newState.archivedMemoList.list.items).mapNotNull { it.creator }
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

                    // Store user in local account for offline access
                    val activeAccount = _uiState.value.accounts.find { it.isActive }
                    if (activeAccount != null) {
                        dataStoreManager.updateAccountUser(activeAccount.id, user)
                    }

                    // Force refresh user memos now that we have the numeric userId
                    userMemoManager?.fetch(refresh = true)

                    val resourceName = user.name ?: ""
                    if (resourceName.isNotBlank()) {
                        launch { fetchShortcuts(resourceName) }
                        launch { fetchUserSettings(resourceName) }
                        launch { fetchWebhooks(resourceName) }
                        launch { fetchUserStats(resourceName) }
                        launch { fetchActivities() }
                    }

                    fetchInstanceProfile()
                    fetchInstanceSettings()
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

    private suspend fun fetchInstanceSettings() {
        try {
            val settings = api?.getInstanceSetting("settings/MEMO_RELATED")
            if (settings != null) {
                _uiState.update { it.copy(session = it.session.copy(instanceSettings = settings)) }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching instance settings", e)
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

    private suspend fun fetchActivities() {
        try {
            val hostUrl = _uiState.value.session.hostUrl
            Log.d("MemosViewModel", "fetchActivities: Fetching from $hostUrl/api/v1/activities?pageSize=1000")
            Log.d("MemosViewModel", "fetchActivities: Starting to fetch activities")
            val response = api?.listActivities(pageSize = 1000)
            Log.d("MemosViewModel", "fetchActivities: Response received, activities count: ${response?.activities?.size ?: 0}")
            val activities = response?.activities ?: emptyList()
            if (activities.isNotEmpty()) {
                Log.d("MemosViewModel", "fetchActivities: First activity: ${activities.first()}")
                Log.d("MemosViewModel", "fetchActivities: First activity createTime: ${activities.first().createTime}")
                Log.d("MemosViewModel", "fetchActivities: Last activity createTime: ${activities.last().createTime}")
            }
            _uiState.update { it.copy(session = it.session.copy(activities = activities)) }
            Log.d("MemosViewModel", "fetchActivities: Updated UI state with ${activities.size} activities")
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching activities", e)
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

    fun updateUserGeneralSetting(locale: String? = null, memoVisibility: Visibility? = null) {
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
                        user.name!!, "GENERAL", UserSetting(generalSetting = newSetting), updateMask
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
        title: String, filter: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val shortcut = Shortcut(title = title, filter = filter)
                api?.createShortcut(user.name!!, shortcut)
                fetchShortcuts(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
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
                // shortcut.name is in format "users/{uid}/shortcuts/{id}"
                // The API expects just the {id} because the path is defined as "api/v1/{user}/shortcuts/{shortcut}"
                val shortcutId = shortcut.name?.substringAfterLast("/") ?: ""
                
                api?.updateShortcut(user.name!!, shortcutId, update, "title,filter")
                fetchShortcuts(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
            }
        }
    }

    private fun getErrorResponse(e: Exception): String {
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val errorObj = Gson().fromJson(errorBody, com.google.gson.JsonObject::class.java)
                    if (errorObj.has("message")) {
                        return errorObj.get("message").asString
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return e.message ?: "Unknown error"
    }

    fun deleteShortcut(shortcut: Shortcut) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val shortcutId = shortcut.name?.substringAfterLast("/") ?: ""
                api?.deleteShortcut(user.name!!, shortcutId)
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
        displayName: String, url: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val webhook = UserWebhook(displayName = displayName, url = url)
                api?.createUserWebhook(user.name!!, webhook)
                fetchWebhooks(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
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
                val webhookId = webhook.name?.substringAfterLast("/") ?: ""
                
                api?.updateUserWebhook(user.name!!, webhookId, update, "display_name,url")
                fetchWebhooks(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
            }
        }
    }

    fun deleteWebhook(webhook: UserWebhook) {
        viewModelScope.launch {
            try {
                val user = _uiState.value.session.currUser ?: return@launch
                val webhookId = webhook.name?.substringAfterLast("/") ?: ""
                api?.deleteUserWebhook(user.name!!, webhookId)
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

    fun fetchUserMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger()
        userMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreUserMemos() = userMemoManager?.loadMore()

    fun fetchExploreMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger()
        exploreMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreExploreMemos() = exploreMemoManager?.loadMore()

    fun fetchArchivedMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger()
        archivedMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreArchivedMemos() = archivedMemoManager?.loadMore()

    fun fetchSearchMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger()
        searchMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreSearchMemos() = searchMemoManager?.loadMore()

    fun searchMemos(isExplore: Boolean, filter: String?, orderBy: String? = null) {
        searchMemoManager?.updateFilter(filter)
        fetchSearchMemos(refresh = true)
    }

    private fun updateRefreshTrigger() {
        _uiState.update {
            it.copy(
                isRefreshing = true, refreshTrigger = System.currentTimeMillis()
            )
        }
    }

    private fun clearRefreshingState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun fetchAttachments(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger()
        attachmentManager?.fetch(refresh = refresh, softRefresh = refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreAttachments() {
        attachmentManager?.loadMore()
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
        visibility: Visibility,
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
                    // Delete the draft that was just published
                    clearCurrentEditingDraft()
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
        val isSame = { m: Memo -> m.name == updatedMemo.name }

        userMemoManager?.replace(updatedMemo, isSame)
        exploreMemoManager?.replace(updatedMemo, isSame)
        archivedMemoManager?.replace(updatedMemo, isSame)
        searchMemoManager?.replace(updatedMemo, isSame)
        commentManager?.replace(updatedMemo, isSame)

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
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location? = null,
        state: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val update = memo.copy(
                    content = content,
                    visibility = visibility,
                    attachments = attachments,
                    location = location,
                    state = state
                )
                val maskParts = mutableListOf("content", "visibility", "attachments", "location")
                if (state != null) {
                    maskParts.add("state")
                }

                val updated = api?.updateMemo(memo.name!!, update, maskParts.joinToString(","))
                val comparator = Comparator<Memo> { m1, m2 ->
                    (m2.displayTime ?: "").compareTo(m1.displayTime ?: "")
                }

                if (updated != null) {
                    onSuccess()

                    // Handle local list moves if state changed
                    val oldState = memo.state ?: "NORMAL"
                    val newState = updated.state ?: "NORMAL"

                    if (oldState != newState) {
                        if (newState == "ARCHIVED") {
                            // Move from User/Explore -> Archived
                            val isSame = { m: Memo -> m.name == memo.name }
                            userMemoManager?.remove(isSame)
                            exploreMemoManager?.remove(isSame)

                            val isSameUpdated = { m: Memo -> m.name == updated.name }
                            archivedMemoManager?.upsert(updated, isSameUpdated, comparator)
                        } else if (newState == "NORMAL") {
                            // Move from Archived -> User (and maybe Explore if public, but keep simple for now)
                            val isSame = { m: Memo -> m.name == memo.name }
                            archivedMemoManager?.remove(isSame)

                            val isSameUpdated = { m: Memo -> m.name == updated.name }
                            userMemoManager?.upsert(updated, isSameUpdated, comparator)
                        }
                    }

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

                // Local update: Remove from all lists
                val isSame = { m: Memo -> m.name == memo.name }
                userMemoManager?.remove(isSame)
                exploreMemoManager?.remove(isSame)
                archivedMemoManager?.remove(isSame)
                searchMemoManager?.remove(isSame)
                commentManager?.remove(isSame)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateMemoPinned(memo: Memo, pinned: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val update = memo.copy(pinned = pinned)
                val updated = api?.updateMemo(memo.name!!, update, "pinned")
                if (updated != null) {
                    onSuccess()
                    updateMemoInState(updated)
                }
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

    // --- Draft Management ---

    private fun getActiveAccountId(): String? {
        return _uiState.value.accounts.find { it.isActive }?.id
    }

    private fun loadDraftsForAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val drafts = draftManager.getDrafts(accountId)
                _uiState.update {
                    it.copy(draft = it.draft.copy(drafts = drafts, isDraftLoaded = true))
                }
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error loading drafts for account $accountId", e)
                _uiState.update { it.copy(draft = it.draft.copy(isDraftLoaded = true)) }
            }
        }
    }

    fun saveDraft(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location? = null,
        draftId: String? = null
    ) {
        val accountId = getActiveAccountId() ?: return
        val existingDraftId = draftId ?: _uiState.value.draft.currentEditingDraftId

        val draft = Draft(
            id = existingDraftId ?: java.util.UUID.randomUUID().toString(),
            content = content,
            visibility = visibility,
            attachments = attachments,
            location = location,
            createdAt = if (existingDraftId != null) {
                _uiState.value.draft.drafts.find { it.id == existingDraftId }?.createdAt
                    ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            },
            updatedAt = System.currentTimeMillis()
        )

        // Only save if there's actual content
        if (!draft.hasContent()) return

        viewModelScope.launch {
            draftManager.saveDraft(accountId, draft)
            loadDraftsForAccount(accountId)
        }
    }

    fun deleteDraft(draftId: String) {
        val accountId = getActiveAccountId() ?: return
        viewModelScope.launch {
            draftManager.deleteDraft(accountId, draftId)
            loadDraftsForAccount(accountId)
        }
    }

    fun deleteAllDrafts() {
        val accountId = getActiveAccountId() ?: return
        viewModelScope.launch {
            draftManager.clearDrafts(accountId)
            loadDraftsForAccount(accountId)
            // If the current editing draft was one of them, clear it
            setCurrentEditingDraft(null)
        }
    }

    fun setCurrentEditingDraft(draftId: String?) {
        _uiState.update {
            it.copy(draft = it.draft.copy(currentEditingDraftId = draftId))
        }
    }

    /**
     * Initialize a new draft session with a fresh ID.
     * Call this when starting a new memo composition to ensure all saves
     * during this session update the same draft.
     */
    fun initializeNewDraftSession(): String {
        val newDraftId = java.util.UUID.randomUUID().toString()
        setCurrentEditingDraft(newDraftId)
        return newDraftId
    }

    fun getLatestDraft(): Draft? {
        return _uiState.value.draft.drafts.maxByOrNull { it.updatedAt }
    }

    fun clearCurrentEditingDraft() {
        val draftId = _uiState.value.draft.currentEditingDraftId
        if (draftId != null) {
            deleteDraft(draftId)
        }
        setCurrentEditingDraft(null)
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
            dataStoreManager: DataStoreManager,
            draftManager: DraftManager,
            memoCacheRepository: MemoCacheRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MemosViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST") return MemosViewModel(dataStoreManager, draftManager, memoCacheRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    class Factory(
        private val dataStoreManager: DataStoreManager,
        private val draftManager: DraftManager,
        private val memoCacheRepository: MemoCacheRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MemosViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return MemosViewModel(dataStoreManager, draftManager, memoCacheRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
