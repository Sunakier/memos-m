package org.example.memosm.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.api.MemosApi
import org.example.memosm.data.DataStoreManager
import org.example.memosm.model.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream

data class MemosUiState(
    val memos: List<Memo> = emptyList(),
    val exploreMemos: List<Memo> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val user: User? = null,
    val users: Map<String, User> = emptyMap(), // Cache for users in explore
    val userStats: UserStats? = null,
    val userSettings: UserGeneralSetting? = null,
    val shortcuts: List<Shortcut> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val isLoading: Boolean = false,
    val isExploring: Boolean = false,
    val isPosting: Boolean = false,
    val isFetchingAttachments: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val exploreNextPageToken: String? = null,
    val nextAttachmentsPageToken: String? = null,
    val isRefreshing: Boolean = false,
    val token: String = "",
    // Detail pane state
    val selectedMemo: Memo? = null,
    val selectedMemoComments: List<Memo> = emptyList(),
    val isLoadingComments: Boolean = false,
    val attachmentCellWidth: Float = 240f
)

class MemosViewModel(
    private val baseUrl: String, 
    private val token: String,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState(token = token))
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private val sanitizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val api: MemosApi by lazy {
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

        Retrofit.Builder().baseUrl(sanitizedBaseUrl).client(client)
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
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Fetch memos first
                val memoResponse = api.listMemos()
                val newMemos = memoResponse.memos?.map { processMemo(it) } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    memos = newMemos,
                    nextPageToken = memoResponse.nextPageToken
                )

                // Fetch attachments
                loadAttachmentsInternal(loadMore = false)

                // Fetch explore memos
                fetchExplore(refresh = true)

                // Fetch instance profile
                try {
                    val instance = api.getInstanceProfile()
                    _uiState.value = _uiState.value.copy(instanceProfile = instance)
                } catch (e: Exception) {
                    Log.e("MemosViewModel", "Error fetching instance profile", e)
                }

                // Now get user info
                val firstMemo = newMemos.firstOrNull()
                if (firstMemo != null) {
                    val userId = firstMemo.creator?.removePrefix("users/") ?: ""
                    if (userId.isNotEmpty()) {
                        fetchUserDetails(userId)
                    } else {
                        fallbackFetchUser()
                    }
                } else {
                    fallbackFetchUser()
                }
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error during refreshAll", e)
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
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
            _uiState.value = _uiState.value.copy(isFetchingAttachments = true)
            loadAttachmentsInternal(loadMore)
            _uiState.value = _uiState.value.copy(isFetchingAttachments = false)
        }
    }

    private suspend fun loadAttachmentsInternal(loadMore: Boolean) {
        val currentToken = if (loadMore) _uiState.value.nextAttachmentsPageToken else null
        if (loadMore && currentToken == null) return

        try {
            val response = api.listAttachments(pageToken = currentToken)
            val rawAttachments = response.attachments ?: emptyList()
            val processedAttachments = rawAttachments.map { processAttachment(it) }

            _uiState.value = _uiState.value.copy(
                attachments = if (loadMore) _uiState.value.attachments + processedAttachments else processedAttachments,
                nextAttachmentsPageToken = response.nextPageToken
            )
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching attachments", e)
        }
    }

    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        Log.d("MemosViewModel", "Starting upload for URI: $uri")
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return null
            inputStream.close()

            val fileName = getFileName(uri, context) ?: "upload_${System.currentTimeMillis()}"
            
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

    private suspend fun fetchUserDetails(userId: String) {
        try {
            val user = api.getUser(userId)
            val processedUser = processUser(user)
            _uiState.value = _uiState.value.copy(
                user = processedUser,
                users = _uiState.value.users + ((user.name ?: "") to processedUser)
            )

            val stats = api.getUserStats(userId)
            val shortcuts = api.getShortcuts(userId)
            
            // Try to fetch general settings directly using "GENERAL" as documented
            val generalSetting = try {
                api.getUserSetting(userId, "GENERAL").generalSetting
            } catch (e: Exception) {
                Log.w("MemosViewModel", "Failed to fetch 'GENERAL' setting, trying 'general'", e)
                try {
                    api.getUserSetting(userId, "general").generalSetting
                } catch (e2: Exception) {
                    Log.e("MemosViewModel", "Failed to fetch general settings", e2)
                    null
                }
            }

            _uiState.value = _uiState.value.copy(
                userStats = stats,
                shortcuts = shortcuts.shortcuts ?: emptyList(),
                userSettings = generalSetting
            )
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching user details for: $userId", e)
            fallbackFetchUser()
        }
    }

    private suspend fun fallbackFetchUser() {
        try {
            val response = api.getCurrentUserAuth()
            response.user?.let { user ->
                val processedUser = processUser(user)
                _uiState.value = _uiState.value.copy(
                    user = processedUser,
                    users = _uiState.value.users + ((user.name ?: "") to processedUser)
                )
                user.name?.removePrefix("users/")?.let { userId ->
                    val stats = api.getUserStats(userId)
                    val shortcuts = api.getShortcuts(userId)
                    
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
                        userSettings = generalSetting
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Fallback user fetch failed", e)
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.listMemos(pageToken = _uiState.value.nextPageToken)
                val newMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    memos = _uiState.value.memos + newMemos,
                    nextPageToken = response.nextPageToken,
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching more memos", e)
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun fetchExplore(refresh: Boolean = false) {
        if (_uiState.value.isExploring && !refresh) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExploring = true)
            try {
                val currentToken = if (refresh) null else _uiState.value.exploreNextPageToken
                val response = api.listMemos(
                    pageToken = currentToken,
                    filter = "visibility in ['PUBLIC', 'PROTECTED']"
                )
                val newMemos = response.memos?.map { processMemo(it) } ?: emptyList()
                
                // Collect creators that we don't have details for
                val unknownCreators = newMemos
                    .mapNotNull { it.creator }
                    .filter { it !in _uiState.value.users }
                    .distinct()
                
                // Fetch missing users
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

                _uiState.value = _uiState.value.copy(
                    exploreMemos = if (refresh) newMemos else _uiState.value.exploreMemos + newMemos,
                    exploreNextPageToken = response.nextPageToken,
                    isExploring = false
                )
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error fetching explore memos", e)
                _uiState.value = _uiState.value.copy(isExploring = false)
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

    fun createMemo(
        content: String, 
        visibility: String = "PRIVATE", 
        attachments: List<Attachment>? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (content.isBlank() && attachments.isNullOrEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            try {
                val memo = api.createMemo(Memo(
                    content = content, 
                    visibility = visibility,
                    attachments = attachments,
                    state = "NORMAL"
                ))
                _uiState.value = _uiState.value.copy(
                    memos = listOf(processMemo(memo)) + _uiState.value.memos, isPosting = false
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("MemosViewModel", "Error creating memo", e)
                _uiState.value = _uiState.value.copy(
                    isPosting = false, error = "Failed to create memo: ${e.localizedMessage}"
                )
            }
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
                        attachments = attachments
                    ),
                    updateMask = "content,visibility,attachments"
                )
                
                val processed = processMemo(updatedMemo)
                
                // Update in all lists
                val updatedMemos = _uiState.value.memos.map {
                    if (it.name == memoName) processed else it
                }
                val updatedExploreMemos = _uiState.value.exploreMemos.map {
                    if (it.name == memoName) processed else it
                }
                val updatedComments = _uiState.value.selectedMemoComments.map {
                    if (it.name == memoName) processed else it
                }
                
                _uiState.value = _uiState.value.copy(
                    memos = updatedMemos,
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
                val updatedMemos = _uiState.value.memos.filter { it.name != memoName }
                val updatedExploreMemos = _uiState.value.exploreMemos.filter { it.name != memoName }
                val updatedComments = _uiState.value.selectedMemoComments.filter { it.name != memoName }
                
                _uiState.value = _uiState.value.copy(
                    memos = updatedMemos,
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
        // Extract memo ID from name (format: "memos/{id}")
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
        val user = _uiState.value.user ?: return
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
                // Protobuf field names (snake_case) are standard for update masks in gRPC-gateways
                if (locale != null) updateMask.add("general_setting.locale")
                if (memoVisibility != null) updateMask.add("general_setting.memo_visibility")

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

    fun updateAttachmentCellWidth(width: Float) {
        viewModelScope.launch {
            dataStoreManager.saveAttachmentCellWidth(width)
            // No need to update _uiState manually here because init block collects from DataStore
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
