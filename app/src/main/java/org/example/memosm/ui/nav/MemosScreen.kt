package org.example.memosm.ui.nav

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
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
import org.example.memosm.model.Memo
import org.example.memosm.ui.component.GenericMemosListPane
import org.example.memosm.ui.component.composer.MemoComposer
import org.example.memosm.ui.component.MemoSearchBar
import org.example.memosm.ui.component.MemosScaffold
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun MemosScreen(viewModel: MemosViewModel, onToggleNavBar: (Boolean) -> Unit = {}) {
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
        memos = uiState.userMemoList.list.items,
        listState = listState,
        onToggleNavBar = onToggleNavBar,
        listPane = { onMemoClick ->
            MemosListPane(
                viewModel = viewModel, listState = listState, onMemoClick = onMemoClick
            )
        },
        overlay = { onMemoClick, showSearchBar, isSearchExpanded, onSearchExpandedChange, isDualPane, isDetailVisible ->
            AnimatedVisibility(
                visible = showSearchBar && (!isSearchExpanded || isDualPane || !isDetailVisible),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)) {
                MemoSearchBar(
                    viewModel = viewModel,
                    onMemoClick = onMemoClick,
                    onExpandedChange = onSearchExpandedChange
                )
            }
        })
}

@Composable
private fun MemosListPane(
    viewModel: MemosViewModel, listState: LazyListState, onMemoClick: (Memo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val shortcutListState = rememberLazyListState()

    GenericMemosListPane(
        viewModel = viewModel,
        memos = uiState.userMemoList.list.items,
        isLoading = uiState.userMemoList.list.isLoading,
        isRefreshing = uiState.isRefreshing,
        nextPageToken = uiState.userMemoList.list.nextPageToken,
        onLoadMore = { viewModel.loadMoreUserMemos() },
        onRefresh = { viewModel.fetchUserMemos(refresh = true) },
        onMemoClick = onMemoClick,
        listState = listState,
        errorTitle = stringResource(R.string.common_error_failed_to_load_memos),
        header = {
            // Top input card
            item {
                if (uiState.draft.isDraftLoaded) {
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
                                availableTags = uiState.session.userStats?.tagCount?.keys ?: emptySet(),
                                token = uiState.session.token,
                                hostUrl = uiState.session.hostUrl,
                                modifier = Modifier.padding(16.dp),
                                isPosting = uiState.isPosting,
                                initialContent = uiState.draft.draftMemo?.content ?: "",
                                initialAttachments = uiState.draft.draftMemo?.attachments ?: emptyList(),
                                initialVisibility = uiState.draft.draftMemo?.visibility
                                    ?: uiState.session.userSettings?.memoVisibility ?: "PRIVATE",
                                initialLocation = uiState.draft.draftMemo?.location,
                                submitLabel = stringResource(R.string.memo_publish),
                                resetToken = uiState.draft.composerResetToken
                            )
                        }
                    }
                }
            }

            // Horizontal Shortcut Row
            item(key = "shortcut_row") {
                AnimatedVisibility(
                    visible = uiState.userMemoList.shortcuts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyRow(
                        state = shortcutListState,
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
                                if (shortcutListState.canScrollBackward) {
                                    drawRect(brush = startGradient, blendMode = BlendMode.DstIn)
                                }
                                if (shortcutListState.canScrollForward) {
                                    drawRect(brush = endGradient, blendMode = BlendMode.DstIn)
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.userMemoList.shortcuts, key = { it.name ?: it.title ?: "" }) { shortcut ->
                            val isSelected = uiState.userMemoList.selectedShortcut?.name == shortcut.name
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleShortcutFilter(shortcut) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(shortcut.title ?: "")
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
        })
}
