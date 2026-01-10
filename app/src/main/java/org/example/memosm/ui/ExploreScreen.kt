package org.example.memosm.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExploreScreen(viewModel: MemosViewModel) {
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

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        defaultPanePreferredWidth = 600.dp
    )

    val navigator = rememberListDetailPaneScaffoldNavigator<MemoKey>(
        scaffoldDirective = scaffoldDirective
    )
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Sync selected memo with navigator
    LaunchedEffect(navigator.currentDestination) {
        val currentMemoKey = navigator.currentDestination?.contentKey
        if (currentMemoKey != null) {
            val selectedId =
                uiState.selectedMemo?.let { it.name ?: it.content.hashCode().toString() }
            if (currentMemoKey.id != selectedId) {
                val memo = uiState.exploreMemos.find {
                    (it.name ?: it.content.hashCode().toString()) == currentMemoKey.id
                }
                if (memo != null) {
                    viewModel.selectMemo(memo)
                }
            }
        } else if (uiState.selectedMemo != null) {
            viewModel.clearSelectedMemo()
        }
    }

    NavigableListDetailPaneScaffold(navigator = navigator, listPane = {
        AnimatedPane {
            ExploreMemosListPane(
                viewModel = viewModel,
                listState = listState,
                onMemoClick = { memo ->
                    focusManager.clearFocus()
                    scope.launch {
                        val id = memo.name ?: memo.content.hashCode().toString()
                        navigator.navigateTo(
                            ListDetailPaneScaffoldRole.Detail, MemoKey(id)
                        )
                    }
                })
        }
    }, detailPane = {
        AnimatedPane {
            val currentMemoKey = navigator.currentDestination?.contentKey
            val isListVisible =
                navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
            val isDetailVisible =
                navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
            val isDualPane = isListVisible && isDetailVisible

            AnimatedContent(
                targetState = currentMemoKey, transitionSpec = {
                    if (isDualPane) {
                        if (initialState == null) {
                            // First time appearing: scale + fade
                            (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(
                                initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)
                            )).togetherWith(fadeOut(animationSpec = tween(90)))
                        } else {
                            // Switching between memos: smooth crossfade
                            fadeIn(animationSpec = tween(300)).togetherWith(
                                fadeOut(animationSpec = tween(300))
                            )
                        }
                    } else {
                        // Mobile: slide from bottom
                        (slideInVertically(
                            initialOffsetY = { it }, animationSpec = tween(300)
                        ) + fadeIn()).togetherWith(
                            slideOutVertically(
                                targetOffsetY = { it }, animationSpec = tween(300)
                            ) + fadeOut()
                        )
                    }
                }, label = "ExploreDetailPaneTransition"
            ) { memoKey ->
                val memo = remember(memoKey, uiState.exploreMemos) {
                    memoKey?.let { key ->
                        uiState.exploreMemos.find {
                            (it.name ?: it.content.hashCode().toString()) == key.id
                        }
                    }
                }

                if (memo != null) {
                    MemoDetailPane(
                        memo = memo,
                        comments = uiState.selectedMemoComments,
                        isLoadingComments = uiState.isLoadingComments,
                        token = uiState.token,
                        showBackButton = navigator.canNavigateBack(),
                        onBack = {
                            focusManager.clearFocus()
                            scope.launch {
                                navigator.navigateBack()
                            }
                        },
                        viewModel = viewModel
                    )
                } else if (isDualPane) {
                    MemoDetailPlaceholder()
                }
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreMemosListPane(
    viewModel: MemosViewModel,
    listState: LazyListState,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var memoToEdit by remember { mutableStateOf<Memo?>(null) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }

    // Use a snapshotFlow for more robust infinite scroll detection
    LaunchedEffect(listState, uiState.isExploring, uiState.exploreNextPageToken) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && 
                    !uiState.isExploring && 
                    uiState.exploreNextPageToken != null && 
                    lastIndex >= listState.layoutInfo.totalItemsCount - 5) {
                    viewModel.loadMoreExplore()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            viewModel.fetchExplore(refresh = true)
        },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        if (uiState.isExploring && uiState.exploreMemos.isEmpty() && !uiState.isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null && uiState.exploreMemos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { viewModel.fetchExplore(refresh = true) }) {
                    Text("Retry")
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(uiState.exploreMemos, key = { it.name ?: it.content.hashCode() }) { memo ->
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        val isOwner = memo.creator == uiState.user?.name
                        MemoItem(
                            memo = memo,
                            user = uiState.users[memo.creator],
                            token = uiState.token,
                            colors = if (memo == uiState.selectedMemo) {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                CardDefaults.cardColors()
                            },
                            onClick = {
                                focusManager.clearFocus()
                                onMemoClick(memo)
                            },
                            onEdit = if (isOwner) {
                                { memoToEdit = memo }
                            } else null,
                            onDelete = if (isOwner) {
                                { memoToDelete = memo }
                            } else null,
                            modifier = Modifier.widthIn(max = 800.dp))
                    }
                }

                if (uiState.isExploring && uiState.exploreMemos.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    memoToEdit?.let { memo ->
        MemoEditDialog(
            memo = memo, onDismiss = { memoToEdit = null }, viewModel = viewModel
        )
    }

    memoToDelete?.let { memo ->
        DeleteConfirmationDialog(memo = memo, onDismiss = { memoToDelete = null }, onConfirm = {
            viewModel.deleteMemo(memo)
            memoToDelete = null
        })
    }
}
