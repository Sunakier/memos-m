package org.example.memosm.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.example.memosm.api.AuthInterceptor
import org.example.memosm.api.MemosApi
import org.example.memosm.api.MemosApiFactory
import org.example.memosm.api.NominatimApi
import org.example.memosm.api.StreamingAttachmentApi
import org.example.memosm.data.DataStoreManager
import org.example.memosm.data.DraftManager
import org.example.memosm.data.cache.CacheListType
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.model.Account
import org.example.memosm.model.Attachment
import org.example.memosm.model.Draft
import org.example.memosm.model.Location
import org.example.memosm.model.Memo
import org.example.memosm.model.MemoState
import org.example.memosm.model.Reaction
import org.example.memosm.model.Shortcut
import org.example.memosm.model.UserWebhook
import org.example.memosm.model.Visibility
import org.example.memosm.viewmodel.delegates.AppSettingsDelegate
import org.example.memosm.viewmodel.delegates.AppSettingsDelegateImpl
import org.example.memosm.viewmodel.delegates.DraftDelegate
import org.example.memosm.viewmodel.delegates.DraftDelegateImpl
import org.example.memosm.viewmodel.delegates.MemoActionDelegate
import org.example.memosm.viewmodel.delegates.MemoActionDelegateImpl
import org.example.memosm.viewmodel.delegates.MemoListUpdater
import org.example.memosm.viewmodel.delegates.ShortcutDelegate
import org.example.memosm.viewmodel.delegates.ShortcutDelegateImpl
import org.example.memosm.viewmodel.delegates.UserDelegate
import org.example.memosm.viewmodel.delegates.UserDelegateImpl
import org.example.memosm.viewmodel.delegates.WebhookDelegate
import org.example.memosm.viewmodel.delegates.WebhookDelegateImpl
import org.example.memosm.viewmodel.manager.ArchivedMemoListManager
import org.example.memosm.viewmodel.manager.AttachmentManager
import org.example.memosm.viewmodel.manager.CacheCallbacks
import org.example.memosm.viewmodel.manager.CommentListManager
import org.example.memosm.viewmodel.manager.ExploreMemoListManager
import org.example.memosm.viewmodel.manager.SearchMemoListManager
import org.example.memosm.viewmodel.manager.UserMemoListManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MemosViewModel(
    private val dataStoreManager: DataStoreManager,
    private val draftManager: DraftManager,
    private val memoCacheRepository: MemoCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private var api: MemosApi? = null
    private var currentHttpClient: OkHttpClient? = null
    private var currentBaseUrl: String? = null
    private var nominatimApi: NominatimApi? = null

    // Managers
    private var userMemoManager: UserMemoListManager? = null
    private var exploreMemoManager: ExploreMemoListManager? = null
    private var archivedMemoManager: ArchivedMemoListManager? = null
    private var searchMemoManager: SearchMemoListManager? = null
    private var commentManager: CommentListManager? = null
    private var attachmentManager: AttachmentManager? = null

    private var collectionJob: Job? = null

    private val _attachmentAspectRatios =
        MutableStateFlow<Map<Float, Map<String, Float>>>(emptyMap())

    // Delegates
    private val userDelegate: UserDelegate
    private val shortcutDelegate: ShortcutDelegate
    private val webhookDelegate: WebhookDelegate
    private val appSettingsDelegate: AppSettingsDelegate
    private val draftDelegate: DraftDelegate
    private val memoActionDelegate: MemoActionDelegate

    init {
        // Initialize Delegates
        userDelegate = UserDelegateImpl(
            viewModelScope, _uiState, { api }, dataStoreManager
        )
        shortcutDelegate = ShortcutDelegateImpl(
            viewModelScope, _uiState, { api }, { userMemoManager?.fetch(refresh = true) }
        )
        webhookDelegate = WebhookDelegateImpl(
            viewModelScope, _uiState, { api }
        )
        appSettingsDelegate = AppSettingsDelegateImpl(
            viewModelScope, _uiState, dataStoreManager
        ) {
            userMemoManager?.fetch(refresh = true)
            exploreMemoManager?.fetch(refresh = true)
        }
        draftDelegate = DraftDelegateImpl(
            viewModelScope, _uiState, draftManager, { api }
        ) { userMemoManager?.fetch(refresh = true) }

        val memoListUpdater = object : MemoListUpdater {
            override fun updateMemoInLists(memo: Memo) {
                updateMemoInState(memo)
            }

            override fun removeMemoFromLists(memoName: String) {
                val isSame = { m: Memo -> m.name == memoName }
                userMemoManager?.remove(isSame)
                exploreMemoManager?.remove(isSame)
                archivedMemoManager?.remove(isSame)
                searchMemoManager?.remove(isSame)
                commentManager?.remove(isSame)
            }

            override fun refreshUserMemos() {
                userMemoManager?.fetch(refresh = true)
            }
            


            override fun handleMemoStateChange(memo: Memo, updated: Memo) {
                 val oldState = memo.state ?: "NORMAL"
                 val newState = updated.state ?: "NORMAL"
                 val comparator = compareByDescending<Memo> { it.displayTime }

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
            }
        }

        memoActionDelegate = MemoActionDelegateImpl(
            viewModelScope, _uiState, { api }, memoListUpdater, draftDelegate,
            { attachmentManager }, { commentManager }
        )

        userDelegate.updateCurrentAccountInList { account ->
            switchAccount(account)
        }
        appSettingsDelegate.loadPageSize()
        appSettingsDelegate.loadHeaderScale()
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        if (nominatimApi == null) {
            nominatimApi = createNominatimApi()
        }
        return try {
            val response = nominatimApi?.reverseGeocode(lat, lon)
            response?.display_name
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching address", e)
            null
        }
    }

    private suspend fun createApi(
        baseUrl: String, token: String
    ): MemosApi {
        val authInterceptor = AuthInterceptor(token)

        currentHttpClient = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
        currentBaseUrl = baseUrl

        return MemosApiFactory.create(baseUrl, currentHttpClient!!)
    }

    private fun createNominatimApi(): NominatimApi {
        return Retrofit.Builder().baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(NominatimApi::class.java)
    }

    fun updateCurrentAccountInList() {
        userDelegate.updateCurrentAccountInList { account ->
            switchAccount(account)
        }
    }

    fun switchAccount(account: Account) {
        userDelegate.switchAccount(account) { acc ->
            // Re-create API and Managers
             api = createApi(acc.hostUrl, acc.accessToken)
             val currentApi = api!!

             // Initialize Managers with cache callbacks
             val accountId = acc.id

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
                     val hashtag = _uiState.value.userMemoList.selectedHashtag

                     if (shortcut != null && !shortcut.filter.isNullOrBlank()) {
                         "$base && ${shortcut.filter}"
                     } else if (hashtag != null) {
                         val tagName = hashtag.removePrefix("#")
                         "$base && tag in [\"$tagName\"]"
                     } else {
                         base
                     }
                 },
                 pageSizeProvider = { _uiState.value.appSettings.pageSize },
                 cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
                     memoCacheRepository.cacheMemos(
                         accountId, CacheListType.USER, memos
                     )
                 }, getCachedData = {
                     memoCacheRepository.getCachedMemos(
                         accountId, CacheListType.USER
                     )
                 })
             )

             exploreMemoManager = ExploreMemoListManager(
                 scope = viewModelScope,
                 api = currentApi,
                 pageSizeProvider = { _uiState.value.appSettings.pageSize },
                 cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
                     memoCacheRepository.cacheMemos(
                         accountId, CacheListType.EXPLORE, memos
                     )
                 }, getCachedData = {
                     memoCacheRepository.getCachedMemos(
                         accountId, CacheListType.EXPLORE
                     )
                 })
             )

             archivedMemoManager = ArchivedMemoListManager(
                 scope = viewModelScope,
                 api = currentApi,
                 currentUserProvider = { _uiState.value.session.currUser },
                 pageSizeProvider = { _uiState.value.appSettings.pageSize },
                 cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
                     memoCacheRepository.cacheMemos(
                         accountId, CacheListType.ARCHIVED, memos
                     )
                 }, getCachedData = {
                     memoCacheRepository.getCachedMemos(
                         accountId, CacheListType.ARCHIVED
                     )
                 })
             )
             searchMemoManager = SearchMemoListManager(
                 viewModelScope,
                 currentApi,
                 pageSizeProvider = { _uiState.value.appSettings.pageSize })
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
             if (acc.user != null) {
                 userMemoManager?.fetch()
             }
             draftDelegate.loadDraftsForAccount(acc.id)
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
                attachmentManager!!.cellWidth,
                _attachmentAspectRatios
            ) { (userMemos, exploreMemos, archivedMemos), (searchMemos, comments, attachments), cellWidth, aspectRatios ->
                Log.d(
                    "MemosDebug",
                    "ViewModel: StateCollection. aspectRatiosCount=${aspectRatios.values.sumOf { it.size }}"
                )
                _uiState.value.copy(
                    userMemoList = _uiState.value.userMemoList.copy(list = userMemos),
                    exploreMemoList = _uiState.value.exploreMemoList.copy(list = exploreMemos),
                    archivedMemoList = _uiState.value.archivedMemoList.copy(list = archivedMemos),
                    searchMemoList = _uiState.value.searchMemoList.copy(list = searchMemos),
                    detailPane = _uiState.value.detailPane.copy(comments = comments),
                    attachmentList = org.example.memosm.viewmodel.AttachmentListState(
                        list = attachments, cellWidth = cellWidth, aspectRatios = aspectRatios
                    )
                )
            }.collect { newState ->
                _uiState.value = newState

                // Fetch missing users for all visible lists
                val allCreators =
                    (newState.userMemoList.list.items + newState.exploreMemoList.list.items + newState.searchMemoList.list.items + newState.archivedMemoList.list.items).mapNotNull { it.creator }
                        .distinct()
                userDelegate.fetchUsers(allCreators)
            }
        }
    }

    // --- User & Session (Delegated) ---

    // Exposed for delegation only
    private fun fetchCurrentUser() {
        userDelegate.fetchCurrentUser { user ->
             // User fetched, now fetch related data that requires user name
             val name = user.name ?: return@fetchCurrentUser
             viewModelScope.launch { shortcutDelegate.fetchShortcuts(name) }
             viewModelScope.launch { webhookDelegate.fetchWebhooks(name) }
             
             // Refresh user memos now that we have the numeric userId
             userMemoManager?.fetch(refresh = true)
        }
    }

    fun refreshInstanceSettings() = userDelegate.refreshInstanceSettings()
    fun refreshUserStats() = userDelegate.refreshUserStats()
    
    fun updateUserGeneralSetting(locale: String? = null, memoVisibility: Visibility? = null) =
        userDelegate.updateUserGeneralSetting(locale, memoVisibility)

    fun updateUserProfile(
        username: String? = null,
        email: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        description: String? = null,
        password: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) = userDelegate.updateUserProfile(
        username, email, displayName, avatarUrl, description, password, onResult
    )

    // --- App Settings (Delegated) ---
    fun updatePageSize(size: Int) = appSettingsDelegate.updatePageSize(size)
    fun updateHeaderScale(scale: Float) = appSettingsDelegate.updateHeaderScale(scale)

    // --- Shortcuts (Delegated) ---
    fun toggleShortcutFilter(shortcut: Shortcut) = shortcutDelegate.toggleShortcutFilter(shortcut)
    fun toggleHashtagFilter(tag: String) = shortcutDelegate.toggleHashtagFilter(tag)
    fun createShortcut(
        title: String, filter: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) = shortcutDelegate.createShortcut(title, filter, onSuccess, onError)

    fun updateShortcut(
        shortcut: Shortcut,
        title: String,
        filter: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = shortcutDelegate.updateShortcut(shortcut, title, filter, onSuccess, onError)

    fun deleteShortcut(shortcut: Shortcut) = shortcutDelegate.deleteShortcut(shortcut)

    // --- Webhooks (Delegated) ---
    fun createWebhook(
        displayName: String, url: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) = webhookDelegate.createWebhook(displayName, url, onSuccess, onError)

    fun updateWebhook(
        webhook: UserWebhook,
        displayName: String,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = webhookDelegate.updateWebhook(webhook, displayName, url, onSuccess, onError)

    fun deleteWebhook(webhook: UserWebhook) = webhookDelegate.deleteWebhook(webhook)

    // --- Account (Delegated) ---
    fun removeAccount(account: Account) = userDelegate.removeAccount(account)
    fun updateAccountCredentials(
        account: Account, hostUrl: String, token: String
    ) = userDelegate.updateAccountCredentials(account, hostUrl, token)
    
    fun addAccount(hostUrl: String, token: String) = userDelegate.addAccount(hostUrl, token)

    // --- List Fetches ---

    fun fetchUserMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger(RefreshSource.USerMemos)
        userMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreUserMemos() = userMemoManager?.loadMore()

    fun fetchExploreMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger(RefreshSource.ExploreMemos)
        exploreMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreExploreMemos() = exploreMemoManager?.loadMore()

    fun fetchArchivedMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger(RefreshSource.ArchivedMemos)
        archivedMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreArchivedMemos() = archivedMemoManager?.loadMore()

    fun fetchSearchMemos(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger(RefreshSource.SearchMemos)
        searchMemoManager?.fetch(refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreSearchMemos() = searchMemoManager?.loadMore()

    fun searchMemos(isExplore: Boolean, filter: String?, orderBy: String? = null) {
        searchMemoManager?.updateFilter(filter)
        fetchSearchMemos(refresh = true)
    }

    private fun updateRefreshTrigger(source: RefreshSource = RefreshSource.Manual) {
        _uiState.update {
            it.copy(
                isRefreshing = true,
                refreshTrigger = System.currentTimeMillis(),
                refreshSource = source
            )
        }
    }

    private fun clearRefreshingState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun fetchAttachments(refresh: Boolean = false) {
        if (refresh) updateRefreshTrigger(RefreshSource.Attachments)
        attachmentManager?.fetch(refresh = refresh, softRefresh = refresh)
        if (refresh) clearRefreshingState()
    }

    fun loadMoreAttachments() {
        attachmentManager?.loadMore()
    }

    fun updateAttachmentCellWidth(width: Float) {
        attachmentManager?.updateCellWidth(width)
    }
    
    // Kept in VM for now as it's purely UI logic
    fun updateAttachmentAspectRatio(cellWidth: Float, key: String, ratio: Float) {
        // ... Log debug ...
        _attachmentAspectRatios.update { currentRatios ->
            val currentMapForScale = currentRatios[cellWidth] ?: emptyMap()
            if (currentMapForScale[key] == ratio) {
                return@update currentRatios
            }
            val newMapForScale = currentMapForScale + (key to ratio)
            currentRatios + (cellWidth to newMapForScale)
        }
    }

    // --- Detail & CRUD (Delegated) ---
    fun selectMemo(memo: Memo?) = memoActionDelegate.selectMemo(memo)
    fun clearSelectedMemo() = memoActionDelegate.clearSelectedMemo()
    
    fun createMemo(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>? = null,
        location: Location? = null,
        onSuccess: () -> Unit = {}
    ) = memoActionDelegate.createMemo(content, visibility, attachments, location, onSuccess)

    private fun updateMemoInState(updatedMemo: Memo) {
        val isSame = { m: Memo -> m.name == updatedMemo.name }
        userMemoManager?.replace(updatedMemo, isSame)
        exploreMemoManager?.replace(updatedMemo, isSame)
        archivedMemoManager?.replace(updatedMemo, isSame)
        searchMemoManager?.replace(updatedMemo, isSame)
        commentManager?.replace(updatedMemo, isSame)

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
        state: MemoState? = null,
        onSuccess: () -> Unit = {}
    ) = memoActionDelegate.updateMemo(memo, content, visibility, attachments, location, state, onSuccess)

    fun deleteMemo(memo: Memo, onSuccess: () -> Unit = {}) = 
        memoActionDelegate.deleteMemo(memo, onSuccess)

    fun updateMemoPinned(memo: Memo, pinned: Boolean, onSuccess: () -> Unit = {}) =
        memoActionDelegate.updateMemoPinned(memo, pinned, onSuccess)

    fun createComment(parentMemo: Memo, content: String, onSuccess: () -> Unit = {}) =
        memoActionDelegate.createComment(parentMemo, content, onSuccess)

    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? =
        memoActionDelegate.uploadAttachment(uri, context)
        
    fun upsertMemoReaction(memo: Memo, reactionType: String) =
        memoActionDelegate.upsertMemoReaction(memo, reactionType)

    fun deleteMemoReaction(memo: Memo, reaction: Reaction) =
        memoActionDelegate.deleteMemoReaction(memo, reaction)

    // --- Draft Management (Delegated) ---
    
    fun saveDraft(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location? = null,
        draftId: String? = null
    ) = draftDelegate.saveDraft(content, visibility, attachments, location, draftId)

    fun deleteDraft(draftId: String) = draftDelegate.deleteDraft(draftId)
    fun deleteAllDrafts() = draftDelegate.deleteAllDrafts()
    fun publishAllDrafts(onResult: (Int) -> Unit = {}) = draftDelegate.publishAllDrafts(onResult)
    fun setCurrentEditingDraft(draftId: String?) = draftDelegate.setCurrentEditingDraft(draftId)
    fun initializeNewDraftSession() = draftDelegate.initializeNewDraftSession()
    fun getLatestDraft() = draftDelegate.getLatestDraft()
    fun clearCurrentEditingDraft() = draftDelegate.clearCurrentEditingDraft()

    companion object {
        fun provideFactory(
            dataStoreManager: DataStoreManager,
            draftManager: DraftManager,
            memoCacheRepository: MemoCacheRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MemosViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST") return MemosViewModel(
                        dataStoreManager, draftManager, memoCacheRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
