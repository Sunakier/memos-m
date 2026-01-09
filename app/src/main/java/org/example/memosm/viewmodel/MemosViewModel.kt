package org.example.memosm.viewmodel

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
import org.example.memosm.model.Memo
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class MemosUiState(
    val memos: List<Memo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextPageToken: String? = null,
    val isRefreshing: Boolean = false
)

class MemosViewModel(
    private val baseUrl: String,
    private val token: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private val api: MemosApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MemosApi::class.java)
    }

    init {
        fetchMemos()
    }

    fun fetchMemos(refresh: Boolean = false) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            if (refresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            try {
                val currentToken = if (refresh) null else _uiState.value.nextPageToken
                val response = api.listMemos(pageToken = currentToken)
                
                val updatedMemos = if (refresh) {
                    response.memos
                } else {
                    _uiState.value.memos + response.memos
                }

                _uiState.value = _uiState.value.copy(
                    memos = updatedMemos,
                    nextPageToken = response.nextPageToken,
                    isLoading = false,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.nextPageToken != null && !_uiState.value.isLoading) {
            fetchMemos()
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
