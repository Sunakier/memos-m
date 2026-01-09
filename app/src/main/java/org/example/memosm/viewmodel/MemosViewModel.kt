package org.example.memosm.viewmodel

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
import org.example.memosm.api.MemoRequest
import org.example.memosm.api.MemosApi
import org.example.memosm.model.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class MemosUiState(
    val memos: List<Memo> = emptyList(),
    val user: User? = null,
    val userStats: UserStats? = null,
    val shortcuts: List<Shortcut> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val isRefreshing: Boolean = false
)

class MemosViewModel(
    private val baseUrl: String, private val token: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private val api: MemosApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
                val request =
                    chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
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
                val response = api.listMemos()
                _uiState.value = _uiState.value.copy(
                    memos = response.memos, nextPageToken = response.nextPageToken
                )

                // Fetch instance profile
                try {
                    val instance = api.getInstanceProfile()
                    _uiState.value = _uiState.value.copy(instanceProfile = instance)
                } catch (e: Exception) {
                    Log.e("MemosViewModel", "Error fetching instance profile", e)
                }

                // Now get user info from the creator of the first memo
                val firstMemo = response.memos.firstOrNull()
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
        val processedUser = if (user.avatarUrl != null && !user.avatarUrl.startsWith("http")) {
            user.copy(avatarUrl = baseUrl.removeSuffix("/") + user.avatarUrl)
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
                _uiState.value = _uiState.value.copy(
                    memos = _uiState.value.memos + response.memos,
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

    fun createMemo(content: String, visibility: String = "PRIVATE", onSuccess: () -> Unit = {}) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            try {
                val memo = api.createMemo(MemoRequest(content = content, visibility = visibility))
                _uiState.value = _uiState.value.copy(
                    memos = listOf(memo) + _uiState.value.memos, isPosting = false
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
