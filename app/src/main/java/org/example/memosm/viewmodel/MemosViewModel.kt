package org.example.memosm.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.api.MemosApi
import org.example.memosm.data.DataStoreManager
import org.example.memosm.model.*
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream

data class MemosUiState(
    val userMemos: List<Memo> = emptyList(),
    val exploreMemos: List<Memo> = emptyList(),
    val archivedMemos: List<Memo> = emptyList(),
    val searchMemos: List<Memo> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val currUser: User? = null,
    val users: Map<String, User> = emptyMap(), // Cache for users in explore
    val userStats: UserStats? = null,
    val userSettings: UserGeneralSetting? = null,
    val webhooks: List<UserWebhook> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val isLoading: Boolean = false,
    val isExploring: Boolean = false,
    val isFetchingArchived: Boolean = false,
    val isSearching: Boolean = false,
    val isPosting: Boolean = false,
    val isFetchingAttachments: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val exploreNextPageToken: String? = null,
    val archivedNextPageToken: String? = null,
    val nextAttachmentsPageToken: String? = null,
    val isRefreshing: Boolean = false,
    val refreshTrigger: Long = 0L, // Used to trigger scroll to top
    val token: String = "",
    val hostUrl: String = "",
    // Detail pane state
    val selectedMemo: Memo? = null,
    val selectedMemoComments: List<Memo> = emptyList(),
    val isLoadingComments: Boolean = false,
    val attachmentCellWidth: Float = 240f,
    // Draft state
    val draftMemo: Memo? = null,
    val isDraftLoaded: Boolean = false,
    val composerResetToken: Int = 0,
    // Filter state
    val selectedTags: Set<String> = emptySet(),
    // Multi-account state
    val accounts: List<Account> = emptyList()
)

data class MemosErrorResponse(
    val code: Int? = null,
    val message: String? = null
)

class MemosViewModel(
    private var baseUrl: String, 
    private var token: String,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState(token = token, hostUrl = baseUrl))
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private var sanitizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val gson = Gson()
    private var draftSaveJob: Job? = null
    private val DEFAULT_PAGE_SIZE = 20

    private var _api: MemosApi? = null
    private val api: MemosApi 
        get() {
            if (_api == null) {
                _api = createApi(sanitizedBaseUrl, token)
            }
            return _api!!
        }

    private fun createApi(baseUrl: String, token: String): MemosApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
            val request =
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            chain.proceed(request)
        }.build()

        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(MemosApi::class.java)
    }

    init {
        viewModelScope.launch {
            dataStoreManager.attachmentCellWidth.collectLatest { width ->
                if (width != null) {
                    _uiState.value = _uiState.value.copy(attachmentCellWidth = width)
                }
            }
        }

        viewModelScope.launch {
            dataStoreManager.accounts.collectLatest { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
                // If the current account is not in the list (e.g. first login), add it
                if (accounts.none { it.hostUrl == baseUrl && it.accessToken == token }) {
                    updateCurrentAccountInList()
                }
            }
        }
        
        // Load draft
        viewModelScope.launch {
            val draftJson = dataStoreManager.memoDraftJson.first()
            val draft = if (!draftJson.isNullOrBlank()) {
                try {
                    gson.fromJson(draftJson, Memo::class.java)
                } catch (e: Exception) {
                    Log.e("MemosViewModel", "Error parsing draft JSON", e)
                    null
                }
            } else null
            
            _uiState.value = _uiState.value.copy(
                draftMemo = draft,
                isDraftLoaded = true
            )
        }
        
        refreshAll()
    }

    private fun updateCurrentAccountInList() {
        viewModelScope.launch {
            val user = _uiState.value.currUser
            val currentAccounts = dataStoreManager.accounts.first().toMutableList()
            val existingIndex = currentAccounts.indexOfFirst { it.hostUrl == baseUrl && it.accessToken == token }
            
            val newAccount = Account(
                hostUrl = baseUrl,
                accessToken = token,
                name = user?.username,
                displayName = user?.displayName,
                avatarUrl = user?.avatarUrl,
                isActive = true
            )

            if (existingIndex != -1) {
                currentAccounts[existingIndex] = newAccount
            } else {
                currentAccounts.add(newAccount)
            }
            
            // Mark others as inactive
            val finalAccounts = currentAccounts.map { 
                it.copy(isActive = it.hostUrl == baseUrl && it.accessToken == token)
            }
            
            dataStoreManager.saveAccounts(finalAccounts)
        }
    }

    fun switchAccount(account: Account) {
        viewModelScope.launch {
            baseUrl = account.hostUrl
            token = account.accessToken
            sanitizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            _api = null // Force recreation of API
            
            dataStoreManager.saveCredentials(baseUrl, token)
            
            // Clear current UI state for the new account
            _uiState.value = MemosUiState(
                token = token,
                hostUrl = baseUrl,
                accounts = _uiState.value.accounts.map { 
                    it.copy(isActive = it.hostUrl == baseUrl && it.accessToken == token)
                },
                attachmentCellWidth = _uiState.value.attachmentCellWidth
            )
            
            refreshAll()
        }
    }

    fun removeAccount(account: Account) {
        viewModelScope.launch {
            val currentAccounts = _uiState.value.accounts.filter { 
                !(it.hostUrl == account.hostUrl && it.accessToken == account.accessToken)
            }
            dataStoreManager.saveAccounts(currentAccounts)
            
            if (account.hostUrl == baseUrl && account.accessToken == token) {
                if (currentAccounts.isNotEmpty()) {
                    switchAccount(currentAccounts.first())
                } else {
                    dataStoreManager.clearCredentials()
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                isRefreshing = true,
                refreshTrigger = System.currentTimeMillis(),
                error = null
            )
            try {
                // Fetch current user details first to ensure we have the creator filter
                fetchCurrentUser()

                // Fetch memos with creator filter
                val filters = mutableListOf<String>()
                _uiState.value.currUser?.name?.let { creatorName ->
                    val creatorId = creatorName.removePrefix("users/")
                    filters.add("creator_id == $creatorId")
                }
                
                if (_uiState.value.selectedTags.isNotEmpty()) {
                    filters.add(_uiState.value.selectedTags.joinToString(" && ") { "tag in [\"$it\"]" })
                }
                
                val filter = if (filters.isNotEmpty()) filters.joinToString(" && ") else null
                
                val memoResponse = api.listMemos(filter = filter, pageSize = DEFAULT_PAGE_SIZE)
                val newMemos = memoResponse.memos?.map { processMemo(it) } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    userMemos = newMemos,
                    nextPageToken = if (memoResponse.nextPageToken.isNullOrBlank()) null else memoResponse.nextPageToken
                )

                // Fetch attachments
                loadAttachmentsInternal(loadMore = false)

                // Fetch explore memos
                fetchExplore(refresh = true, updateRefreshingState = false)

                // Fetch instance profile
                try {
                    val instance = api.getInstanceProfile()
                    _uiState.value = _uiState.value.copy(instanceProfile = instance)
                } catch (e: Exception) {
                    Log.e("MemosViewModel", "Error fetching instance profile", e)
                }
                
                // After fetching user, update account info in list
                updateCurrentAccountInList()
                
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error during refreshAll", e)
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    private fun processMemo(memo: Memo): Memo {
        val processedAttachments = memo.attachments?.map { processAttachment(it) }
        return memo.copy(attachments = processedAttachments)
    }

    private fun processAttachment(attachment: Attachment): Attachment {
        val downloadUrl = if (attachment.externalLink.isNullOrBlank()) {
            "${sanitizedBaseUrl.removeSuffix("/")}/file/${attachment.name ?: ""}/${attachment.filename}"
        } else if (!attachment.externalLink.startsWith("http")) {
            "${sanitizedBaseUrl.removeSuffix("/")}${if (attachment.externalLink.startsWith("/")) "" else "/"}${attachment.externalLink}"
        } else {
            attachment.externalLink
        }
        return attachment.copy(externalLink = downloadUrl)
    }

    fun fetchAttachments(loadMore: Boolean = false) {
        if (_uiState.value.isFetchingAttachments) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFetchingAttachments = true,
                isRefreshing = if (!loadMore) true else _uiState.value.isRefreshing,
                refreshTrigger = if (!loadMore) System.currentTimeMillis() else _uiState.value.refreshTrigger
            )
            loadAttachmentsInternal(loadMore)
            _uiState.value = _uiState.value.copy(
                isFetchingAttachments = false,
                isRefreshing = if (!loadMore) false else _uiState.value.isRefreshing
            )
        }
    }

    private suspend fun loadAttachmentsInternal(loadMore: Boolean) {
        val currentToken = if (loadMore) _uiState.value.nextAttachmentsPageToken else null
        if (loadMore && currentToken == null) return

        try {
            val response = api.listAttachments(pageToken = currentToken, pageSize = DEFAULT_PAGE_SIZE)
            val rawAttachments = response.attachments ?: emptyList()
            
            val newNextPageToken = if (response.nextPageToken.isNullOrBlank() || response.nextPageToken == currentToken) null else response.nextPageToken
            
            val processedAttachments = rawAttachments.map { processAttachment(it) }

            _uiState.value = _uiState.value.copy(
                attachments = if (loadMore) _uiState.value.attachments + processedAttachments else processedAttachments,
                nextAttachmentsPageToken = newNextPageToken
            )
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching attachments", e)
        }
    }

    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        Log.d("MemosViewModel", "Starting upload for URI: $uri")
        return try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(uri, context) ?: "upload_${System.currentTimeMillis()}"

            // Get MIME type from content resolver, but fall back to extension-based detection
            val resolverMimeType = contentResolver.getType(uri)
            val mimeType = if (resolverMimeType == null || resolverMimeType == "application/octet-stream") {
                getMimeTypeFromExtension(fileName) ?: resolverMimeType ?: "application/octet-stream"
            } else {
                resolverMimeType
            }

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return null
            inputStream.close()

            // Memos API expects bytes to be Base64 encoded in JSON
            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.d("MemosViewModel", "Encoded file to Base64, size: ${base64Content.length}")

            val request = Attachment(
                filename = fileName,
                type = mimeType,
                content = base64Content
            )

            val attachment = api.createAttachment(request)
            Log.d("MemosViewModel", "Upload success: ${attachment.name}")
            val processedAttachment = processAttachment(attachment)
            
            _uiState.value = _uiState.value.copy(
                attachments = listOf(processedAttachment) + _uiState.value.attachments
            )
            processedAttachment
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error uploading attachment", e)
            null
        }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        return name ?: uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return null
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private suspend fun fetchCurrentUser() {
        try {
            val session = api.getCurrentSession()
            session.user?.let { updateUserInfo(it) }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching current session", e)
        }
    }

    private suspend fun updateUserInfo(user: User) {
        val processedUser = processUser(user)
        _uiState.value = _uiState.value.copy(
            currUser = processedUser,
            users = _uiState.value.users + ((user.name ?: "") to processedUser)
        )

        val userId = user.name?.removePrefix("users/") ?: return
        try {
            val stats = api.getUserStats(userId)
            val shortcuts = api.getShortcuts(userId)
            val webhooks = try { api.listUserWebhooks(userId).webhooks ?: emptyList() } catch (e: Exception) { emptyList() }
            
            val generalSetting = try {
                api.getUserSetting(userId, "GENERAL").generalSetting
            } catch (e: Exception) {
                try {
                    api.getUserSetting(userId, "general").generalSetting
                } catch (e2: Exception) {
                    null
                }
            }

            _uiState.value = _uiState.value.copy(
                userStats = stats,
                shortcuts = shortcuts.shortcuts ?: emptyList(),
                webhooks = webhooks,
                userSettings = generalSetting
            )
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching additional user details", e)
        }
    }

    private fun processUser(user: User): User {
        val avatarUrl = user.avatarUrl
        return if (avatarUrl != null && !avatarUrl.startsWith("http")) {
            user.copy(avatarUrl = sanitizedBaseUrl.removeSuffix("/") + avatarUrl)
        } else {
            user
        }
    }

    fun fetchMemos(refresh: Boolean = false) {
        if (refresh) {
            refreshAll()
            return
        }

        if (_uiState.value.isLoading) return
        val currentToken = _uiState.value.nextPageToken ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val filters = mutableListOf<String>()
                _uiState.value.currUser?.name?.let { creatorName ->
                    val creatorId = creatorName.removePrefix("users/")
                    filters.add("creator_id == $creatorId")
                }
                
                if (_uiState.value.selectedTags.isNotEmpty()) {
                    filters.add(_uiState.value.selectedTags.joinToString(" && ") { "tag in [\"$it\"]" })
                }
                
                val filter = if (filters.isNotEmpty()) filters.joinToString(" && ") else null
                
                val response = api.listMemos(pageToken = currentToken, filter = filter, pageSize = DEFAULT_PAGE_SIZE)
                val newMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                
                val newNextPageToken = if (response.nextPageToken.isNullOrBlank() || response.nextPageToken == currentToken) null else response.nextPageToken
                
                _uiState.value = _uiState.value.copy(
                    userMemos = _uiState.value.userMemos + newMemos,
                    nextPageToken = newNextPageToken,
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching more memos", e)
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun fetchExplore(refresh: Boolean = false, updateRefreshingState: Boolean = true) {
        if (_uiState.value.isExploring && !refresh) return
        val currentToken = if (refresh) null else _uiState.value.exploreNextPageToken
        if (!refresh && currentToken == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExploring = true,
                error = if (refresh) null else _uiState.value.error,
                isRefreshing = if (refresh && updateRefreshingState) true else _uiState.value.isRefreshing,
                refreshTrigger = if (refresh && updateRefreshingState) System.currentTimeMillis() else _uiState.value.refreshTrigger
            )
            try {
                val response = api.listMemos(
                    pageToken = currentToken,
                    filter = "visibility in ['PUBLIC', 'PROTECTED']",
                    pageSize = DEFAULT_PAGE_SIZE
                )
                val newMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                
                val newNextPageToken = if (response.nextPageToken.isNullOrBlank() || response.nextPageToken == currentToken) null else response.nextPageToken
                
                // Update the memos immediately so the list displays even if user fetching fails or is slow
                _uiState.value = _uiState.value.copy(
                    exploreMemos = if (refresh) newMemos else _uiState.value.exploreMemos + newMemos,
                    exploreNextPageToken = newNextPageToken,
                    isExploring = false,
                    isRefreshing = if (refresh && updateRefreshingState) false else _uiState.value.isRefreshing
                )

                // Background fetch for user details
                val unknownCreators = newMemos
                    .mapNotNull { it.creator }
                    .filter { it !in _uiState.value.users }
                    .distinct()
                
                launch {
                    unknownCreators.forEach { creatorName ->
                        try {
                            val userId = creatorName.removePrefix("users/")
                            val user = api.getUser(userId)
                            val processedUser = processUser(user)
                            _uiState.value = _uiState.value.copy(
                                users = _uiState.value.users + (creatorName to processedUser)
                            )
                        } catch (e: Exception) {
                            Log.e("MemosViewModel", "Error fetching user $creatorName", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching explore memos", e)
                _uiState.value = _uiState.value.copy(
                    isExploring = false,
                    error = e.localizedMessage,
                    isRefreshing = if (refresh && updateRefreshingState) false else _uiState.value.isRefreshing
                )
            }
        }
    }

    fun fetchArchivedMemos(refresh: Boolean = false) {
        if (_uiState.value.isFetchingArchived && !refresh) return
        val currentToken = if (refresh) null else _uiState.value.archivedNextPageToken
        if (!refresh && currentToken == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFetchingArchived = true,
                error = if (refresh) null else _uiState.value.error,
                isRefreshing = if (refresh) true else _uiState.value.isRefreshing
            )
            try {
                val filters = mutableListOf<String>()
                _uiState.value.currUser?.name?.let { creatorName ->
                    val creatorId = creatorName.removePrefix("users/")
                    filters.add("creator_id == $creatorId")
                }
                val filter = if (filters.isNotEmpty()) filters.joinToString(" && ") else null

                val response = api.listMemos(
                    pageToken = currentToken,
                    state = "ARCHIVED",
                    filter = filter,
                    pageSize = DEFAULT_PAGE_SIZE
                )
                val newMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                
                val newNextPageToken = if (response.nextPageToken.isNullOrBlank() || response.nextPageToken == currentToken) null else response.nextPageToken
                
                _uiState.value = _uiState.value.copy(
                    archivedMemos = if (refresh) newMemos else _uiState.value.archivedMemos + newMemos,
                    archivedNextPageToken = newNextPageToken,
                    isFetchingArchived = false,
                    isRefreshing = if (refresh) false else _uiState.value.isRefreshing
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching archived memos", e)
                _uiState.value = _uiState.value.copy(
                    isFetchingArchived = false,
                    error = e.localizedMessage,
                    isRefreshing = if (refresh) false else _uiState.value.isRefreshing
                )
            }
        }
    }

    fun loadMoreArchived() {
        if (_uiState.value.archivedNextPageToken != null && !_uiState.value.isFetchingArchived) {
            fetchArchivedMemos()
        }
    }

    fun prepareSearch(isExplore: Boolean, filter: String? = null, orderBy: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchMemos = emptyList())
            try {
                val baseFilter = if (isExplore) {
                    "visibility in ['PUBLIC', 'PROTECTED']"
                } else {
                    _uiState.value.currUser?.name?.let { creatorName ->
                        val creatorId = creatorName.removePrefix("users/")
                        "creator_id == $creatorId"
                    }
                }
                
                val finalFilter = if (filter != null) {
                    if (baseFilter != null) "$baseFilter && $filter" else filter
                } else baseFilter

                val response = api.listMemos(filter = finalFilter, orderBy = orderBy, pageSize = 200)
                val searchMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                
                _uiState.value = _uiState.value.copy(
                    searchMemos = searchMemos,
                    isSearching = false
                )
                
                if (isExplore) {
                    val unknownCreators = searchMemos
                        .mapNotNull { it.creator }
                        .filter { it !in _uiState.value.users }
                        .distinct()
                    
                    launch {
                        unknownCreators.forEach { creatorName ->
                            try {
                                val userId = creatorName.removePrefix("users/")
                                val user = api.getUser(userId)
                                val processedUser = processUser(user)
                                _uiState.value = _uiState.value.copy(
                                    users = _uiState.value.users + (creatorName to processedUser)
                                )
                            } catch (e: Exception) {
                                Log.e("MemosViewModel", "Error fetching user $creatorName for search", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error preparing search", e)
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.nextPageToken != null && !_uiState.value.isLoading) {
            fetchMemos()
        }
    }

    fun loadMoreExplore() {
        if (_uiState.value.exploreNextPageToken != null && !_uiState.value.isExploring) {
            fetchExplore()
        }
    }

    fun toggleTagFilter(tag: String) {
        val current = _uiState.value.selectedTags
        val next = if (tag in current) current - tag else current + tag
        _uiState.value = _uiState.value.copy(selectedTags = next)
        refreshAll()
    }

    fun createMemo(
        content: String, 
        visibility: String = "PRIVATE", 
        attachments: List<Attachment>? = null,
        location: Location? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (content.isBlank() && attachments.isNullOrEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            try {
                // Upload any cached attachments that have base64 content but no server-assigned name
                val processedAttachments = attachments?.map { attachment ->
                    if (attachment.name == null && !attachment.content.isNullOrBlank()) {
                        // This is a cached attachment with base64 content - upload it first
                        try {
                            val uploaded = api.createAttachment(attachment)
                            Log.d("MemosViewModel", "Uploaded cached attachment: ${uploaded.name}")
                            uploaded
                        } catch (e: Exception) {
                            Log.e("MemosViewModel", "Failed to upload cached attachment", e)
                            throw e
                        }
                    } else {
                        attachment
                    }
                }

                val memo = api.createMemo(Memo(
                    content = content, 
                    visibility = visibility,
                    attachments = processedAttachments,
                    location = location,
                    state = "NORMAL"
                ))
                _uiState.value = _uiState.value.copy(
                    userMemos = listOf(processMemo(memo)) + _uiState.value.userMemos,
                    isPosting = false,
                    draftMemo = null,
                    composerResetToken = _uiState.value.composerResetToken + 1
                )
                dataStoreManager.clearMemoDraft()
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error creating memo", e)
                _uiState.value = _uiState.value.copy(
                    isPosting = false, error = "Failed to create memo: ${e.localizedMessage}"
                )
            }
        }
    }

    fun saveDraft(content: String, visibility: String, attachments: List<Attachment>, location: Location? = null) {
        val draft = Memo(
            content = content,
            visibility = visibility,
            attachments = attachments,
            location = location
        )
        // Update UI state immediately
        _uiState.value = _uiState.value.copy(draftMemo = draft)
        
        // Debounce saving to DataStore
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(1000) // Wait 1 second before saving to disk
            val json = gson.toJson(draft)
            dataStoreManager.saveMemoDraft(json)
        }
    }

    fun createComment(parentMemo: Memo, content: String, onSuccess: () -> Unit = {}) {
        if (content.isBlank()) return
        val memoId = parentMemo.name?.removePrefix("memos/") ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            try {
                val comment = api.createMemoComment(
                    memo = memoId,
                    comment = Memo(
                        content = content,
                        visibility = parentMemo.visibility,
                        state = "NORMAL"
                    )
                )
                _uiState.value = _uiState.value.copy(
                    selectedMemoComments = _uiState.value.selectedMemoComments + processMemo(comment),
                    isPosting = false
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error creating comment", e)
                _uiState.value = _uiState.value.copy(
                    isPosting = false, error = "Failed to create comment: ${e.localizedMessage}"
                )
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
        val memoName = memo.name ?: return
        val memoId = memoName.removePrefix("memos/")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            try {
                val updatedMemo = api.updateMemo(
                    memo = memoId,
                    memoData = Memo(
                        content = content,
                        visibility = visibility,
                        attachments = attachments,
                        location = location
                    ),
                    updateMask = "content,visibility,attachments,location"
                )
                
                val processed = processMemo(updatedMemo)
                
                // Update in all lists
                val updatedMemos = _uiState.value.userMemos.map {
                    if (it.name == memoName) processed else it
                }
                val updatedExploreMemos = _uiState.value.exploreMemos.map {
                    if (it.name == memoName) processed else it
                }
                val updatedComments = _uiState.value.selectedMemoComments.map {
                    if (it.name == memoName) processed else it
                }
                
                _uiState.value = _uiState.value.copy(
                    userMemos = updatedMemos,
                    exploreMemos = updatedExploreMemos,
                    selectedMemoComments = updatedComments,
                    selectedMemo = if (_uiState.value.selectedMemo?.name == memoName) processed else _uiState.value.selectedMemo,
                    isPosting = false
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error updating memo", e)
                _uiState.value = _uiState.value.copy(
                    isPosting = false, error = "Failed to update memo: ${e.localizedMessage}"
                )
            }
        }
    }

    fun deleteMemo(memo: Memo, onSuccess: () -> Unit = {}) {
        val memoName = memo.name ?: return
        val memoId = memoName.removePrefix("memos/")
        
        viewModelScope.launch {
            try {
                api.deleteMemo(memoId)
                
                // Update in all lists
                val updatedMemos = _uiState.value.userMemos.filter { it.name != memoName }
                val updatedExploreMemos = _uiState.value.exploreMemos.filter { it.name != memoName }
                val updatedComments = _uiState.value.selectedMemoComments.filter { it.name != memoName }
                
                _uiState.value = _uiState.value.copy(
                    userMemos = updatedMemos,
                    exploreMemos = updatedExploreMemos,
                    selectedMemoComments = updatedComments,
                    selectedMemo = if (_uiState.value.selectedMemo?.name == memoName) null else _uiState.value.selectedMemo
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error deleting memo", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete memo: ${e.localizedMessage}"
                )
            }
        }
    }

    fun selectMemo(memo: Memo?) {
        _uiState.value = _uiState.value.copy(
            selectedMemo = memo,
            selectedMemoComments = emptyList(),
            isLoadingComments = memo != null
        )
        
        if (memo != null) {
            fetchMemoComments(memo)
        }
    }

    fun clearSelectedMemo() {
        _uiState.value = _uiState.value.copy(
            selectedMemo = null,
            selectedMemoComments = emptyList(),
            isLoadingComments = false
        )
    }

    private fun fetchMemoComments(memo: Memo) {
        val memoName = memo.name ?: return
        val memoId = memoName.removePrefix("memos/")
        
        viewModelScope.launch {
            try {
                val response = api.listMemoComments(memoId)
                val comments = response.memos?.map { processMemo(it) } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    selectedMemoComments = comments,
                    isLoadingComments = false
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching memo comments", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingComments = false
                )
            }
        }
    }

    fun updateUserGeneralSetting(locale: String? = null, memoVisibility: String? = null) {
        val user = _uiState.value.currUser ?: return
        val userId = user.name?.removePrefix("users/") ?: return
        
        viewModelScope.launch {
            try {
                val currentSettings = _uiState.value.userSettings ?: UserGeneralSetting()
                val updatedGeneral = currentSettings.copy(
                    locale = locale ?: currentSettings.locale,
                    memoVisibility = memoVisibility ?: currentSettings.memoVisibility
                )
                
                val settingKey = "GENERAL"
                val request = UserSetting(
                    name = "users/$userId/settings/$settingKey",
                    generalSetting = updatedGeneral
                )
                
                val updateMask = mutableListOf<String>()
                if (locale != null) updateMask.add("locale")
                if (memoVisibility != null) updateMask.add("memoVisibility")

                val maskString = updateMask.joinToString(",")
                Log.d("MemosViewModel", "Updating settings for $userId with mask: $maskString")

                val updated = try {
                    api.updateUserSetting(userId, settingKey, request, maskString)
                } catch (e: Exception) {
                    Log.w("MemosViewModel", "Update with 'GENERAL' failed, trying 'general'", e)
                    api.updateUserSetting(userId, "general", request.copy(name = "users/$userId/settings/general"), maskString)
                }
                
                _uiState.value = _uiState.value.copy(
                    userSettings = updated.generalSetting,
                    error = null
                )
                Log.d("MemosViewModel", "Settings updated successfully")
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error updating user settings", e)
                _uiState.value = _uiState.value.copy(error = "Failed to update settings: ${e.localizedMessage}")
            }
        }
    }

    fun upsertMemoReaction(memo: Memo, reactionType: String) {
        val memoName = memo.name ?: return
        val memoId = memoName.removePrefix("memos/")
        
        viewModelScope.launch {
            try {
                api.upsertMemoReaction(
                    memo = memoId,
                    request = UpsertMemoReactionRequest(
                        name = memoName,
                        reaction = Reaction(
                            contentId = memoName,
                            reactionType = reactionType
                        )
                    )
                )
                // Refresh the memo to get updated reactions
                refreshMemo(memoName)
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error upserting reaction", e)
            }
        }
    }

    fun deleteMemoReaction(memo: Memo, reaction: Reaction) {
        val memoName = memo.name ?: return
        val reactionName = reaction.name ?: return
//        val memoId = memoName.removePrefix("memos/")
        val reactionId = reactionName.removePrefix("reactions/")
        // Holy shit the documentation was so ass
        viewModelScope.launch {
            try {
                // Use the reaction type (emoji) directly as the identifier in the path
                api.deleteMemoReaction(reactionId)
                refreshMemo(memoName)
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error deleting reaction", e)
            }
        }
    }

    private suspend fun refreshMemo(memoName: String) {
        try {
            val updatedMemo = api.getMemo(memoName.removePrefix("memos/"))
            val processed = processMemo(updatedMemo)
            
            val updatedMemos = _uiState.value.userMemos.map {
                if (it.name == memoName) processed else it
            }
            val updatedExploreMemos = _uiState.value.exploreMemos.map {
                if (it.name == memoName) processed else it
            }
            val updatedComments = _uiState.value.selectedMemoComments.map {
                if (it.name == memoName) processed else it
            }
            
            _uiState.value = _uiState.value.copy(
                userMemos = updatedMemos,
                exploreMemos = updatedExploreMemos,
                selectedMemoComments = updatedComments,
                selectedMemo = if (_uiState.value.selectedMemo?.name == memoName) processed else _uiState.value.selectedMemo
            )
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error refreshing memo after reaction", e)
        }
    }

    fun updateAttachmentCellWidth(width: Float) {
        viewModelScope.launch {
            dataStoreManager.saveAttachmentCellWidth(width)
        }
    }

    // --- Shortcut Management ---

    private fun parseError(e: Exception): String {
        return if (e is HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                val errorResponse = gson.fromJson(errorBody, MemosErrorResponse::class.java)
                errorResponse.message ?: e.localizedMessage ?: "Unknown error"
            } catch (parseEx: Exception) {
                e.localizedMessage ?: "Unknown error"
            }
        } else {
            e.localizedMessage ?: "Unknown error"
        }
    }

    fun createShortcut(title: String, filter: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val user = _uiState.value.currUser ?: return
        val userId = user.name?.removePrefix("users/") ?: return
        
        viewModelScope.launch {
            try {
                val shortcut = api.createShortcut(userId, Shortcut(title = title, filter = filter))
                _uiState.value = _uiState.value.copy(
                    shortcuts = _uiState.value.shortcuts + shortcut
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error creating shortcut", e)
                val errorMessage = parseError(e)
                _uiState.value = _uiState.value.copy(error = "Failed to create shortcut: $errorMessage")
                onError(errorMessage)
            }
        }
    }

    fun updateShortcut(shortcut: Shortcut, title: String, filter: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val user = _uiState.value.currUser ?: return
        val userId = user.name?.removePrefix("users/") ?: return
        val shortcutName = shortcut.name ?: return
        val shortcutId = shortcutName.substringAfterLast("/")

        viewModelScope.launch {
            try {
                val updated = api.updateShortcut(
                    userId, 
                    shortcutId, 
                    Shortcut(name = shortcutName, title = title, filter = filter),
                    updateMask = "title,filter"
                )
                _uiState.value = _uiState.value.copy(
                    shortcuts = _uiState.value.shortcuts.map { if (it.name == shortcutName) updated else it }
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error updating shortcut", e)
                val errorMessage = parseError(e)
                _uiState.value = _uiState.value.copy(error = "Failed to update shortcut: $errorMessage")
                onError(errorMessage)
            }
        }
    }

    fun deleteShortcut(shortcut: Shortcut) {
        val user = _uiState.value.currUser ?: return
        val userId = user.name?.removePrefix("users/") ?: return
        val shortcutName = shortcut.name ?: return
        val shortcutId = shortcutName.substringAfterLast("/")

        viewModelScope.launch {
            try {
                api.deleteShortcut(userId, shortcutId)
                _uiState.value = _uiState.value.copy(
                    shortcuts = _uiState.value.shortcuts.filter { it.name != shortcutName }
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error deleting shortcut", e)
                _uiState.value = _uiState.value.copy(error = "Failed to delete shortcut: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        fun provideFactory(baseUrl: String, token: String, dataStoreManager: DataStoreManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MemosViewModel(baseUrl, token, dataStoreManager) as T
                }
            }
    }
}
