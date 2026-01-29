package org.example.memosm.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.ui.component.ErrorView
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(viewModel: MemosViewModel, onToggleNavBar: ((Boolean) -> Unit)? = null) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyStaggeredGridState()
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }

    // Limits for zooming
    val minCellWidth = 100.dp
    val maxCellWidth = 600.dp

    // Animate the cell width changes for a smoother transition
    val animatedCellWidth by animateDpAsState(
        targetValue = uiState.attachmentList.cellWidth.dp, animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow
        ), label = "CellWidthAnimation"
    )

    // Scroll direction tracking for nav bar visibility
    var isScrollingDown by remember { mutableStateOf(false) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        viewModel.fetchAttachments(refresh = false)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
            val wasScrollingDown = isScrollingDown
            if (currentIndex > previousIndex) {
                isScrollingDown = true
            } else if (currentIndex < previousIndex) {
                isScrollingDown = false
            } else if (currentOffset > previousScrollOffset + 10) {
                isScrollingDown = true
            } else if (currentOffset < previousScrollOffset - 10) {
                isScrollingDown = false
            }

            if (wasScrollingDown != isScrollingDown) {
                onToggleNavBar?.invoke(!isScrollingDown)
            }

            previousIndex = currentIndex
            previousScrollOffset = currentOffset
        }
    }

    // Double tap refresh logic: scroll to top
    // We keep track of the last processed trigger to avoid scrolling to top 
    // when just navigating back to this screen.
    var lastProcessedTrigger by rememberSaveable { mutableLongStateOf(uiState.refreshTrigger) }
    LaunchedEffect(uiState.refreshTrigger) {
        if (uiState.refreshTrigger > lastProcessedTrigger) {
            listState.animateScrollToItem(0)
        }
        lastProcessedTrigger = uiState.refreshTrigger
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            if (totalItemsCount == 0 || uiState.attachmentList.list.isLoading) return@derivedStateOf false

            val lastVisibleItem =
                listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false

            uiState.attachmentList.list.nextPageToken != null && !uiState.attachmentList.list.nextPageToken.isNullOrBlank() && lastVisibleItem.index >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreAttachments()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing, onRefresh = {
            viewModel.fetchAttachments(refresh = true)
        }, modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Detect pinch-to-zoom gestures globally using the Initial pass
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedChanges = event.changes.filter { it.pressed }

                            if (pressedChanges.size >= 2) {
                                val p1 = pressedChanges[0].position
                                val p2 = pressedChanges[1].position
                                val p1Prev = pressedChanges[0].previousPosition
                                val p2Prev = pressedChanges[1].previousPosition

                                val currentDistance = (p1 - p2).getDistance()
                                val previousDistance = (p1Prev - p2Prev).getDistance()

                                if (previousDistance > 0f && currentDistance > 0f) {
                                    val zoomFactor = currentDistance / previousDistance
                                    if (zoomFactor != 1f) {
                                        val newWidth =
                                            (uiState.attachmentList.cellWidth * zoomFactor).coerceIn(
                                                minCellWidth.value, maxCellWidth.value
                                            )
                                        viewModel.updateAttachmentCellWidth(newWidth)
                                        // Consume the event to prevent the list from scrolling while zooming
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                }) {
            if (uiState.attachmentList.list.items.isEmpty() && uiState.attachmentList.list.isLoading && !uiState.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.attachmentList.list.items.isEmpty() && !uiState.attachmentList.list.isLoading) {
                if (uiState.error != null) {
                    ErrorView(
                        title = stringResource(R.string.common_error_failed_to_load_attachments),
                        message = uiState.error!!,
                        onRetry = { viewModel.fetchAttachments(refresh = false) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.attachments_none_found))
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(animatedCellWidth),
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, top = 12.dp, end = 12.dp, bottom = 80.dp
                    ),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.attachmentList.list.items,
                        key = { it.name ?: it.filename }) { attachment ->
                        val key = attachment.name ?: attachment.filename
                        val ratio = aspectRatios[key] ?: 1.0f

                        AttachmentCard(
                            attachment = attachment,
                            token = uiState.session.token,
                            hostUrl = uiState.session.hostUrl,
                            compactMode = AttachmentCompactMode.Width,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                                .animateItem(),
                            onRatioAvailable = { newRatio ->
                                aspectRatios[key] = newRatio
                            })
                    }

                    if (uiState.attachmentList.list.isLoading && uiState.attachmentList.list.items.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (!uiState.attachmentList.list.isLoading && uiState.attachmentList.list.nextPageToken.isNullOrBlank() && uiState.attachmentList.list.items.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
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
    }
}
