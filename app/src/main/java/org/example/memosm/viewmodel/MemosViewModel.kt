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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.api.MemosApi
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
    val shortcuts: List<Shortcut> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val isLoading: Boolean = false,
    val isExploring: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val exploreNextPageToken: String? = null,
    val nextAttachmentsPageToken: String? = null,
    val isRefreshing: Boolean = false,
    val token: String = "",
    // Detail pane state
    val selectedMemo: Memo? = null,
    val selectedMemoComments: List<Memo> = emptyList(),
    val isLoadingComments: Boolean = false
)

class MemosViewModel(
    private val baseUrl: String, private val token: String
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
        if (_uiState.value.isLoading && !loadMore) return
        viewModelScope.launch {
            loadAttachmentsInternal(loadMore)
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

            _uiState.value = _uiState.value.copy(
                userStats = stats, shortcuts = shortcuts.shortcuts ?: emptyList()
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
                    _uiState.value = _uiState.value.copy(
                        userStats = stats, shortcuts = shortcuts.shortcuts ?: emptyList()
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

    companion object {
        fun provideFactory(baseUrl: String, token: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MemosViewModel(baseUrl, token) as T
                }
            }
    }
}
