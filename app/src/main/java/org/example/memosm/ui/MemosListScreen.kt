package org.example.memosm.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun MemosListScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Double tap refresh logic: scroll to top
    var lastProcessedTrigger by remember { mutableLongStateOf(uiState.refreshTrigger) }
    LaunchedEffect(uiState.refreshTrigger) {
        if (uiState.refreshTrigger > lastProcessedTrigger) {
            listState.animateScrollToItem(0)
        }
        lastProcessedTrigger = uiState.refreshTrigger
    }

    MemosScaffold(
        viewModel = viewModel,
        memos = uiState.memos,
        listState = listState,
        listPane = { onMemoClick ->
            MemosListPane(
                viewModel = viewModel,
                listState = listState,
                onMemoClick = onMemoClick
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
                    onMemoClick = onMemoClick,
                    onExpandedChange = onSearchExpandedChange
                )
            }
        }
    )
}

@Composable
private fun MemosListPane(
    viewModel: MemosViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onMemoClick: (org.example.memosm.model.Memo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tagListState = rememberLazyListState()

    GenericMemosListPane(
        viewModel = viewModel,
        memos = uiState.memos,
        isLoading = uiState.isLoading,
        isRefreshing = uiState.isRefreshing,
        nextPageToken = uiState.nextPageToken,
        onLoadMore = { viewModel.loadMore() },
        onRefresh = { viewModel.refreshAll() },
        onMemoClick = onMemoClick,
        listState = listState,
        header = {
            // Top input card
            item {
                if (uiState.isDraftLoaded) {
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        Card(modifier = Modifier.widthIn(max = 800.dp)) {
                            MemoComposer(
                                onPublish = { content, visibility, attachments, location ->
                                    viewModel.createMemo(
                                        content, visibility, attachments, location
                                    )
                                },
                                onUploadFile = { uri, context ->
                                    viewModel.uploadAttachment(uri, context)
                                },
                                onDraftChanged = { content, visibility, attachments, location ->
                                    viewModel.saveDraft(
                                        content, visibility, attachments, location
                                    )
                                },
                                availableTags = uiState.userStats?.tagCount?.keys ?: emptySet(),
                                token = uiState.token,
                                modifier = Modifier.padding(16.dp),
                                isPosting = uiState.isPosting,
                                initialContent = uiState.draftMemo?.content ?: "",
                                initialAttachments = uiState.draftMemo?.attachments ?: emptyList(),
                                initialVisibility = uiState.draftMemo?.visibility
                                    ?: uiState.userSettings?.memoVisibility ?: "PRIVATE",
                                initialLocation = uiState.draftMemo?.location,
                                submitLabel = stringResource(R.string.memo_publish),
                                resetToken = uiState.composerResetToken
                            )
                        }
                    }
                }
            }

            // Horizontal Tag Row
            val tagMap = uiState.userStats?.tagCount ?: emptyMap()
            item(key = "tag_row") {
                AnimatedVisibility(
                    visible = tagMap.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val sortedTags = remember(tagMap) {
                        tagMap.keys.toList().sortedByDescending { tagMap[it] ?: 0 }
                    }

                    LazyRow(
                        state = tagListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val startGradient = Brush.horizontalGradient(
                                    0f to Color.Transparent, 0.15f to Color.Black
                                )
                                val endGradient = Brush.horizontalGradient(
                                    0.85f to Color.Black, 1f to Color.Transparent
                                )
                                if (tagListState.canScrollBackward) {
                                    drawRect(brush = startGradient, blendMode = BlendMode.DstIn)
                                }
                                if (tagListState.canScrollForward) {
                                    drawRect(brush = endGradient, blendMode = BlendMode.DstIn)
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedTags, key = { it }) { tag ->
                            val count = tagMap[tag] ?: 0
                            val isSelected = tag in uiState.selectedTags
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleTagFilter(tag) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("#$tag")
                                        if (count > 0) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                    alpha = 0.7f
                                                )
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null)
                        }
                    }
                }
            }
        }
    )
}
