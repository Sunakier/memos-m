package org.example.memosm.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.api.AttachmentRequest
import org.example.memosm.api.MemoRequest
import org.example.memosm.api.MemosApi
import org.example.memosm.model.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream

data class MemosUiState(
    val memos: List<Memo> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val uploadingAttachments: List<Attachment> = emptyList(),
    val user: User? = null,
    val userStats: UserStats? = null,
    val shortcuts: List<Shortcut> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val nextAttachmentsPageToken: String? = null,
    val isRefreshing: Boolean = false,
    val token: String = ""
)

class MemosViewModel(
    private val baseUrl: String, private val token: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState(token = token))
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

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

        Retrofit.Builder().baseUrl(baseUrl).client(client)
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
                    val userId = firstMemo.creator.removePrefix("users/")
                    fetchUserDetails(userId)
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
            "${baseUrl.removeSuffix("/")}/file/${attachment.name}/${attachment.filename}"
        } else if (!attachment.externalLink.startsWith("http")) {
            "${baseUrl.removeSuffix("/")}${if (attachment.externalLink.startsWith("/")) "" else "/"}${attachment.externalLink}"
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
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return null
            inputStream.close()

            val fileName = getFileName(uri, context) ?: "upload_${System.currentTimeMillis()}"
            
            val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", fileName, requestFile)

            val attachment = api.uploadAttachment(body)
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
            updateUser(user)

            val stats = api.getUserStats(userId)
            val shortcuts = api.getShortcuts(userId)

            _uiState.value = _uiState.value.copy(
                userStats = stats, shortcuts = shortcuts.shortcuts
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
                updateUser(user)
                user.name?.removePrefix("users/")?.let { userId ->
                    val stats = api.getUserStats(userId)
                    val shortcuts = api.getShortcuts(userId)
                    _uiState.value = _uiState.value.copy(
                        userStats = stats, shortcuts = shortcuts.shortcuts
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Fallback user fetch failed", e)
        }
    }

    private fun updateUser(user: User) {
        val avatarUrl = user.avatarUrl
        val processedUser = if (avatarUrl != null && !avatarUrl.startsWith("http")) {
            user.copy(avatarUrl = baseUrl.removeSuffix("/") + avatarUrl)
        } else {
            user
        }
        _uiState.value = _uiState.value.copy(user = processedUser)
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

    fun loadMore() {
        if (_uiState.value.nextPageToken != null && !_uiState.value.isLoading) {
            fetchMemos()
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
                val memo = api.createMemo(MemoRequest(
                    content = content, 
                    visibility = visibility,
                    attachments = attachments
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
