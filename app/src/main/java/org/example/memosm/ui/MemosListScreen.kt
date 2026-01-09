package org.example.memosm.ui

import android.os.Parcelable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@Parcelize
data class MemoKey(val id: String) : Parcelable

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MemosListScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
        // Clear focus whenever navigation happens to prevent unwanted keyboard/focus
        focusManager.clearFocus()
        
        val currentMemoKey = navigator.currentDestination?.contentKey
        if (currentMemoKey != null) {
            val selectedId =
                uiState.selectedMemo?.let { it.name ?: it.content.hashCode().toString() }
            if (currentMemoKey.id != selectedId) {
                // Find the memo in current list if possible, or just use the name to fetch
                val memo = uiState.memos.find {
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

    // Aggressively clear focus when this screen is entered or returned to
    LaunchedEffect(Unit) {
        repeat(5) {
            focusManager.clearFocus()
            delay(100)
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                MemosListPane(
                    viewModel = viewModel, onMemoClick = { memo ->
                        focusManager.clearFocus()
                        scope.launch {
                            val id = memo.name ?: memo.content.hashCode().toString()
                            navigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail, MemoKey(id)
                            )
                        }
                    })
            }
        },
        detailPane = {
            AnimatedPane {
                val currentMemoKey = navigator.currentDestination?.contentKey
                val isListVisible =
                    navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
                val isDetailVisible =
                    navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
                val isDualPane = isListVisible && isDetailVisible

                AnimatedContent(
                    targetState = currentMemoKey,
                    transitionSpec = {
                        if (isDualPane) {
                            if (initialState == null) {
                                // First time appearing: scale + fade
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = tween(220, delayMillis = 90)
                                )).togetherWith(fadeOut(animationSpec = tween(90)))
                            } else {
                                // Switching between memos: smooth crossfade
                                fadeIn(animationSpec = tween(300)).togetherWith(
                                    fadeOut(animationSpec = tween(300))
                                )
                            }
                        } else {
                            // Mobile: swipe up (slide from bottom)
                            (slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn()).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(300)
                                ) + fadeOut()
                            )
                        }
                    },
                    label = "DetailPaneTransition"
                ) { memoKey ->
                    val memo = remember(memoKey, uiState.memos) {
                        memoKey?.let { key ->
                            uiState.memos.find {
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

@Composable
private fun MemosListPane(
    viewModel: MemosViewModel, onMemoClick: (Memo) -> Unit, modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem =
                listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }, contentAlignment = Alignment.TopCenter
    ) {
        when {
            uiState.isLoading && uiState.memos.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.memos.isEmpty() -> {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                        ) {
                            Card(modifier = Modifier.widthIn(max = 800.dp)) {
                                MemoComposer(
                                    onPublish = { content, visibility, attachments ->
                                        viewModel.createMemo(content, visibility, attachments)
                                    },
                                    onUploadFile = { uri, context ->
                                        viewModel.uploadAttachment(uri, context)
                                    },
                                    availableTags = uiState.userStats?.tagCount?.keys ?: emptySet(),
                                    token = uiState.token,
                                    modifier = Modifier.padding(16.dp),
                                    isPosting = uiState.isPosting,
                                    defaultVisibility = uiState.userSettings?.memoVisibility ?: "PRIVATE"
                                )
                            }
                        }
                    }

                    items(uiState.memos, key = { it.name ?: it.content.hashCode() }) { memo ->
                        Box(
                            modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                        ) {
                            MemoItem(
                                memo = memo,
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
                                modifier = Modifier.widthIn(max = 800.dp)
                            )
                        }
                    }

                    if (uiState.isLoading && uiState.memos.isNotEmpty()) {
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
    }
}
