package org.example.memosm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMemosScreen(
    modifier: Modifier = Modifier,
    viewModel: MemosViewModel,
    onBack: () -> Unit,
    onToggleNavBar: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Refresh archived memos on start
    LaunchedEffect(Unit) {
        viewModel.fetchArchivedMemos(refresh = true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        MemosScaffold(
            viewModel = viewModel,
            memos = uiState.archivedMemos,
            listState = listState,
            onToggleNavBar = onToggleNavBar,
            topBar = { isDetailVisible, isDualPane ->
                if (!isDetailVisible || isDualPane) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.profile_archived)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                    )
                }
            },
            listPane = { onMemoClick ->
                GenericMemosListPane(
                    viewModel = viewModel,
                    memos = uiState.archivedMemos,
                    isLoading = uiState.isFetchingArchived,
                    isRefreshing = uiState.isRefreshing,
                    nextPageToken = uiState.archivedNextPageToken,
                    onLoadMore = { viewModel.loadMoreArchived() },
                    onRefresh = { viewModel.fetchArchivedMemos(refresh = true) },
                    onMemoClick = onMemoClick,
                    listState = listState,
                    userProvider = { uiState.user },
                    contentPadding = PaddingValues(16.dp)
                )
            }
        )
    }
}
