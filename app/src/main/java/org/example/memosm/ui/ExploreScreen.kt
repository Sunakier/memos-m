package org.example.memosm.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import kotlinx.coroutines.launch
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        defaultPanePreferredWidth = 600.dp
    )
    
    val navigator = rememberListDetailPaneScaffoldNavigator<MemoKey>(
        scaffoldDirective = scaffoldDirective
    )
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(navigator.currentDestination) {
        val currentMemoKey = navigator.currentDestination?.contentKey
        if (currentMemoKey?.name != uiState.selectedMemo?.name) {
            if (currentMemoKey != null) {
                val memo = uiState.exploreMemos.find { it.name == currentMemoKey.name }
                if (memo != null) {
                    viewModel.selectMemo(memo)
                }
            } else {
                viewModel.clearSelectedMemo()
            }
        }
    }

    SharedTransitionLayout {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                val isVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300))
                ) {
                    ExploreMemosListPane(
                        viewModel = viewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onMemoClick = { memo ->
                            focusManager.clearFocus()
                            scope.launch {
                                memo.name?.let { name ->
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail, MemoKey(name)
                                    )
                                }
                            }
                        })
                }
            },
            detailPane = {
                val selectedMemo = uiState.selectedMemo
                val isListAndDetailVisible = navigator.scaffoldValue.primary != navigator.scaffoldValue.secondary
                val isVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300))
                ) {
                    AnimatedContent(
                        targetState = selectedMemo,
                        transitionSpec = {
                            if (isListAndDetailVisible) {
                                (slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn())
                                    .togetherWith(slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut())
                            } else {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            }
                        },
                        label = "ExploreDetailAnimation"
                    ) { memo ->
                        if (memo != null) {
                            MemoDetailPane(
                                memo = memo,
                                comments = uiState.selectedMemoComments,
                                isLoadingComments = uiState.isLoadingComments,
                                token = uiState.token,
                                showBackButton = navigator.canNavigateBack(),
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                onBack = {
                                    focusManager.clearFocus()
                                    scope.launch {
                                        navigator.navigateBack()
                                    }
                                })
                        } else {
                            MemoDetailPlaceholder()
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExploreMemosListPane(
    viewModel: MemosViewModel,
    onMemoClick: (Memo) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
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
            viewModel.loadMoreExplore()
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
        if (uiState.isExploring && uiState.exploreMemos.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        with(sharedTransitionScope) {
                            MemoItem(
                                memo = memo,
                                user = uiState.users[memo.creator],
                                token = uiState.token,
                                isSelected = memo == uiState.selectedMemo,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = {
                                    focusManager.clearFocus()
                                    onMemoClick(memo)
                                },
                                modifier = Modifier
                                    .widthIn(max = 800.dp)
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "memo_${memo.name}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
                                    )
                            )
                        }
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
}
