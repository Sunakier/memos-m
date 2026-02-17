package org.example.memosm.viewmodel.delegates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.memosm.data.DataStoreManager
import org.example.memosm.viewmodel.MemosUiState

interface AppSettingsDelegate {
    fun loadPageSize()
    fun loadHeaderScale()
    fun updatePageSize(size: Int)
    fun updateHeaderScale(scale: Float)
}

class AppSettingsDelegateImpl(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MemosUiState>,
    private val dataStoreManager: DataStoreManager,
    private val onPageSizeChanged: () -> Unit
) : AppSettingsDelegate {

    override fun loadPageSize() {
        scope.launch {
            dataStoreManager.pageSize.collect { size ->
                uiState.update { it.copy(appSettings = it.appSettings.copy(pageSize = size)) }
            }
        }
    }

    override fun loadHeaderScale() {
        scope.launch {
            dataStoreManager.headerScale.collect { scale ->
                uiState.update { it.copy(appSettings = it.appSettings.copy(headerScale = scale)) }
            }
        }
    }

    override fun updatePageSize(size: Int) {
        scope.launch {
            dataStoreManager.savePageSize(size)
            uiState.update { it.copy(appSettings = it.appSettings.copy(pageSize = size)) }
            // Refresh all lists with new page size
            onPageSizeChanged()
        }
    }

    override fun updateHeaderScale(scale: Float) {
        scope.launch {
            dataStoreManager.saveHeaderScale(scale)
            uiState.update { it.copy(appSettings = it.appSettings.copy(headerScale = scale)) }
        }
    }
}
