package org.example.memosm.viewmodel.delegates

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.memosm.api.MemosApi
import org.example.memosm.model.Shortcut
import org.example.memosm.viewmodel.MemosUiState

interface ShortcutDelegate {
    suspend fun fetchShortcuts(userResourceName: String)
    fun toggleShortcutFilter(shortcut: Shortcut)
    fun toggleHashtagFilter(tag: String)
    fun createShortcut(
        title: String, filter: String, onSuccess: () -> Unit, onError: (String) -> Unit
    )

    fun updateShortcut(
        shortcut: Shortcut,
        title: String,
        filter: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun deleteShortcut(shortcut: Shortcut)
}

class ShortcutDelegateImpl(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MemosUiState>,
    private val apiProvider: () -> MemosApi?,
    private val onRefreshUserMemos: () -> Unit
) : ShortcutDelegate {

    private val api: MemosApi? get() = apiProvider()

    override suspend fun fetchShortcuts(userResourceName: String) {
        try {
            val response = api?.getShortcuts(userResourceName)
            val shortcuts = response?.shortcuts ?: emptyList()
            uiState.update {
                it.copy(userMemoList = it.userMemoList.copy(shortcuts = shortcuts))
            }
        } catch (e: Exception) {
            Log.e("MemosViewModel", "Error fetching shortcuts", e)
        }
    }

    override fun toggleShortcutFilter(shortcut: Shortcut) {
        val currShortcut = uiState.value.userMemoList.selectedShortcut
        val newSelection = if (currShortcut == shortcut) null else shortcut

        uiState.update {
            it.copy(
                userMemoList = it.userMemoList.copy(
                    selectedShortcut = newSelection, selectedHashtag = null
                )
            )
        }

        onRefreshUserMemos()
    }

    override fun toggleHashtagFilter(tag: String) {
        val currTag = uiState.value.userMemoList.selectedHashtag
        val newSelection = if (currTag == tag) null else tag

        uiState.update {
            it.copy(
                userMemoList = it.userMemoList.copy(
                    selectedHashtag = newSelection, selectedShortcut = null
                )
            )
        }

        onRefreshUserMemos()
    }

    override fun createShortcut(
        title: String, filter: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val user = uiState.value.session.currUser ?: return@launch
                val shortcut = Shortcut(title = title, filter = filter)
                api?.createShortcut(user.name!!, shortcut)
                fetchShortcuts(user.name!!)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
            }
        }
    }

    override fun updateShortcut(
        shortcut: Shortcut,
        title: String,
        filter: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val user = uiState.value.session.currUser ?: return@launch
                val currentApi = api ?: return@launch
                val update = shortcut.copy(title = title, filter = filter)
                // shortcut.name is in format "users/{uid}/shortcuts/{id}"
                val shortcutId = shortcut.name?.substringAfterLast("/") ?: ""

                val constants = currentApi.constants
                currentApi.updateShortcut(
                    user.name!!,
                    shortcutId,
                    update,
                    "${constants.shortcutMaskTitle},${constants.shortcutMaskFilter}"
                )
                fetchShortcuts(user.name)
                onSuccess()
            } catch (e: Exception) {
                onError(getErrorResponse(e))
            }
        }
    }

    override fun deleteShortcut(shortcut: Shortcut) {
        scope.launch {
            try {
                val user = uiState.value.session.currUser ?: return@launch
                val shortcutId = shortcut.name?.substringAfterLast("/") ?: ""
                api?.deleteShortcut(user.name!!, shortcutId)
                fetchShortcuts(user.name!!)
            } catch (e: Exception) {
            }
        }
    }

    private fun getErrorResponse(e: Exception): String {
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val errorObj =
                        Gson().fromJson(errorBody, com.google.gson.JsonObject::class.java)
                    if (errorObj.has("message")) {
                        return errorObj.get("message").asString
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return e.message ?: "Unknown error"
    }
}
