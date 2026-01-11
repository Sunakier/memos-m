package org.example.memosm.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import org.example.memosm.ui.MemoKey
import org.example.memosm.ui.components.composer.DeleteConfirmationDialog
import org.example.memosm.ui.components.composer.MemoEditDialog
import org.example.memosm.ui.components.item.MemoItem
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MemosScaffold(
    viewModel: MemosViewModel,
    memos: List<Memo>,
    listState: LazyListState,
    listPane: @Composable BoxScope.(onMemoClick: (Memo) -> Unit) -> Unit,
    topBar: @Composable (isDetailVisible: Boolean, isDualPane: Boolean) -> Unit = { _, _ -> },
    overlay: @Composable BoxScope.(onMemoClick: (Memo) -> Unit, showSearchBar: Boolean, isSearchExpanded: Boolean, onSearchExpandedChange: (Boolean) -> Unit, isDualPane: Boolean, isDetailVisible: Boolean) -> Unit = { _, _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        defaultPanePreferredWidth = 600.dp
    )

    val navigator = rememberListDetailPaneScaffoldNavigator<MemoKey>(
        scaffoldDirective = scaffoldDirective
    )
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Capture initial focus to prevent child inputs (like composer or search) from auto-focusing
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Sync selected memo with navigator
    LaunchedEffect(navigator.currentDestination) {
        focusManager.clearFocus()

        val currentMemoKey = navigator.currentDestination?.contentKey
        if (currentMemoKey != null) {
            val selectedId =
                uiState.selectedMemo?.let { it.name ?: it.content.hashCode().toString() }
            if (currentMemoKey.id != selectedId) {
                val pool = if (currentMemoKey.fromSearch) uiState.searchMemos else memos
                val memo = pool.find {
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

    // Scroll direction tracking for search bar visibility
    var isScrollingDown by remember { mutableStateOf(false) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
            if (currentIndex > previousIndex) {
                isScrollingDown = true
            } else if (currentIndex < previousIndex) {
                isScrollingDown = false
            } else if (currentOffset > previousScrollOffset + 10) {
                isScrollingDown = true
            } else if (currentOffset < previousScrollOffset - 10) {
                isScrollingDown = false
            }
            previousIndex = currentIndex
            previousScrollOffset = currentOffset
        }
    }

    val isDetailVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val isListVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val isDualPane = isListVisible && isDetailVisible

    var isSearchExpanded by remember { mutableStateOf(false) }
    val showSearchBar by remember {
        derivedStateOf { !isScrollingDown || listState.firstVisibleItemIndex == 0 }
    }

    Scaffold(
        topBar = { topBar(isDetailVisible, isDualPane) }
    ) { paddingValues ->
        NavigableListDetailPaneScaffold(
            modifier = Modifier
                .padding(paddingValues)
                .focusRequester(focusRequester)
                .focusable(),
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    Box(modifier = Modifier.fillMaxSize()) {
                        listPane { memo ->
                            focusManager.clearFocus()
                            scope.launch {
                                val id = memo.name ?: memo.content.hashCode().toString()
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail, MemoKey(id)
                                )
                            }
                        }

                        overlay(
                            { memo ->
                                focusManager.clearFocus()
                                scope.launch {
                                    val id = memo.name ?: memo.content.hashCode().toString()
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        MemoKey(id, fromSearch = true)
                                    )
                                }
                            },
                            showSearchBar,
                            isSearchExpanded,
                            { isSearchExpanded = it },
                            isDualPane,
                            isDetailVisible
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val currentMemoKey = navigator.currentDestination?.contentKey

                    AnimatedContent(
                        targetState = currentMemoKey, transitionSpec = {
                            if (isDualPane) {
                                if (initialState == null) {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(
                                        initialScale = 0.92f,
                                        animationSpec = tween(220, delayMillis = 90)
                                    )).togetherWith(fadeOut(animationSpec = tween(90)))
                                } else {
                                    fadeIn(animationSpec = tween(300)).togetherWith(
                                        fadeOut(animationSpec = tween(300))
                                    )
                                }
                            } else {
                                (slideInVertically(
                                    initialOffsetY = { it }, animationSpec = tween(300)
                                ) + fadeIn()).togetherWith(
                                    slideOutVertically(
                                        targetOffsetY = { it }, animationSpec = tween(300)
                                    ) + fadeOut()
                                )
                            }
                        }, label = "DetailPaneTransition"
                    ) { memoKey ->
                        val memo = remember(memoKey, memos, uiState.searchMemos) {
                            memoKey?.let { key ->
                                val pool = if (key.fromSearch) uiState.searchMemos else memos
                                pool.find {
                                    (it.name ?: it.content.hashCode().toString()) == key.id
                                }
                            }
                        }

                        if (memo != null) {
                            MemoDetailView(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericMemosListPane(
    viewModel: MemosViewModel,
    memos: List<Memo>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    nextPageToken: String?,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    userProvider: (Memo) -> User? = { null },
    header: (LazyListScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = 16.dp, top = 88.dp, end = 16.dp, bottom = 80.dp
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var memoToEdit by remember { mutableStateOf<Memo?>(null) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }

    LaunchedEffect(listState, isLoading, nextPageToken) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
            if (lastIndex != null && !isLoading && nextPageToken != null && lastIndex >= listState.layoutInfo.totalItemsCount - 5) {
                onLoadMore()
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    ) {
        if (isLoading && memos.isEmpty() && !isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null && memos.isEmpty()) {
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
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                header?.invoke(this)

                items(memos, key = { it.name ?: it.content.hashCode() }) { memo ->
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        val isOwner = memo.creator == uiState.user?.name
                        MemoItem(
                            memo = memo,
                            user = userProvider(memo),
                            currentUser = uiState.user,
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
                            onUpsertReaction = { emoji ->
                                viewModel.upsertMemoReaction(memo, emoji)
                            },
                            onDeleteReaction = { reactionName ->
                                viewModel.deleteMemoReaction(memo, reactionName)
                            },
                            onContentUpdate = if (isOwner) { newContent ->
                                viewModel.updateMemo(
                                    memo,
                                    newContent,
                                    memo.visibility,
                                    memo.attachments ?: emptyList(),
                                    memo.location
                                )
                            } else null,
                            maxHeight = 400.dp,
                            modifier = Modifier.widthIn(max = 800.dp))
                    }
                }

                if (isLoading && memos.isNotEmpty()) {
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
                } else if (!isLoading && nextPageToken == null && memos.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.memo_list_end),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
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
