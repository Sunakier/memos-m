package org.example.memosm.ui.nav

import androidx.compose.animation.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.example.memosm.R
import org.example.memosm.ui.components.GenericMemosListPane
import org.example.memosm.ui.components.MemoSearchBar
import org.example.memosm.ui.components.MemosScaffold
import org.example.memosm.viewmodel.MemosViewModel
import kotlin.collections.get

@Composable
fun ExploreScreen(viewModel: MemosViewModel, onToggleNavBar: (Boolean) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Double tap refresh logic: scroll to top
    var lastProcessedTrigger by rememberSaveable { mutableLongStateOf(uiState.refreshTrigger) }
    LaunchedEffect(uiState.refreshTrigger) {
        if (uiState.refreshTrigger > lastProcessedTrigger) {
            listState.animateScrollToItem(0)
        }
        lastProcessedTrigger = uiState.refreshTrigger
    }

    MemosScaffold(
        viewModel = viewModel,
        memos = uiState.exploreMemos,
        listState = listState,
        onToggleNavBar = onToggleNavBar,
        listPane = { onMemoClick ->
            GenericMemosListPane(
                viewModel = viewModel,
                memos = uiState.exploreMemos,
                isLoading = uiState.isExploring,
                isRefreshing = uiState.isRefreshing,
                nextPageToken = uiState.exploreNextPageToken,
                onLoadMore = { viewModel.loadMoreExplore() },
                onRefresh = { viewModel.fetchExplore(refresh = true) },
                onMemoClick = onMemoClick,
                listState = listState,
                userProvider = { memo -> uiState.users[memo.creator] },
                errorTitle = stringResource(R.string.common_error_failed_to_load_explore)
            )
        },
        overlay = { onMemoClick, showSearchBar, isSearchExpanded, onSearchExpandedChange, isDualPane, isDetailVisible ->
            AnimatedVisibility(
                visible = showSearchBar && (!isSearchExpanded || isDualPane || !isDetailVisible),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                MemoSearchBar(
                    viewModel = viewModel,
                    isExplore = true,
                    onMemoClick = onMemoClick,
                    onExpandedChange = onSearchExpandedChange,
                    placeholder = stringResource(R.string.memo_search_explore_placeholder)
                )
            }
        }
    )
}
