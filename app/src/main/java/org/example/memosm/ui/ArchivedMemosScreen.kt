package org.example.memosm.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMemosScreen(viewModel: MemosViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Refresh archived memos on start
    LaunchedEffect(Unit) {
        viewModel.fetchArchivedMemos(refresh = true)
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
                val pool = uiState.archivedMemos
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

    val isDetailVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val isListVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val isDualPane = isListVisible && isDetailVisible

    Scaffold(
        topBar = {
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
        }
    ) { paddingValues ->
        NavigableListDetailPaneScaffold(
            modifier = Modifier.padding(paddingValues),
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    ArchivedMemosListPane(
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
                        }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val currentMemoKey = navigator.currentDestination?.contentKey

                    AnimatedContent(
                        targetState = currentMemoKey,
                        transitionSpec = {
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
                        },
                        label = "ArchivedDetailPaneTransition"
                    ) { memoKey ->
                        val memo = remember(memoKey, uiState.archivedMemos) {
                            memoKey?.let { key ->
                                uiState.archivedMemos.find {
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
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedMemosListPane(
    viewModel: MemosViewModel,
    listState: LazyListState,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var memoToEdit by remember { mutableStateOf<Memo?>(null) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }

    LaunchedEffect(listState, uiState.isFetchingArchived, uiState.archivedNextPageToken) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
            if (lastIndex != null && !uiState.isFetchingArchived && uiState.archivedNextPageToken != null && lastIndex >= listState.layoutInfo.totalItemsCount - 5) {
                viewModel.loadMoreArchived()
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.fetchArchivedMemos(refresh = true) },
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        if (uiState.isFetchingArchived && uiState.archivedMemos.isEmpty() && !uiState.isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null && uiState.archivedMemos.isEmpty()) {
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
                Button(onClick = { viewModel.fetchArchivedMemos(refresh = true) }) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(uiState.archivedMemos, key = { it.name ?: it.content.hashCode() }) { memo ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val isOwner = memo.creator == uiState.user?.name
                        MemoItem(
                            memo = memo,
                            user = uiState.user,
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
                            modifier = Modifier.widthIn(max = 800.dp)
                        )
                    }
                }

                if (uiState.isFetchingArchived && uiState.archivedMemos.isNotEmpty()) {
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
                } else if (!uiState.isFetchingArchived && uiState.archivedNextPageToken == null && uiState.archivedMemos.isNotEmpty()) {
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
