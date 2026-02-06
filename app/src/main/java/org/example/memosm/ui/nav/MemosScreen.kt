package org.example.memosm.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.ui.component.GenericMemosListPane
import org.example.memosm.ui.component.MemoSearchBar
import org.example.memosm.ui.component.MemosScaffold
import org.example.memosm.ui.component.composer.ComposerMode
import org.example.memosm.ui.component.composer.MemoComposerDialog
import org.example.memosm.ui.component.rememberScrollContext
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun MemosScreen(
    viewModel: MemosViewModel,
    onToggleNavBar: ((Boolean) -> Unit)? = null,
    isNavBarVisible: Boolean = true,
    openComposer: Boolean = false,
    onComposerOpened: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showComposerDialog by remember { mutableStateOf(false) }
    var showDraftsScreen by remember { mutableStateOf(false) }
    var showDraftPrompt by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(true) }

    // Track if we should start fresh (skip draft loading)
    var startFresh by remember { mutableStateOf(false) }

    // FAB expansion based on scroll direction
    val scrollContext = rememberScrollContext(listState = listState, onScrollDown = {
        onToggleNavBar?.invoke(false)
        isFabExpanded = false
    }, onScrollUp = {
        onToggleNavBar?.invoke(true)
        isFabExpanded = true
    })

    // Explicitly handle initial state or non-scroll updates if needed
    LaunchedEffect(scrollContext.isScrollingDown) {
        isFabExpanded = !scrollContext.isScrollingDown
    }

    val bottomPadding by animateDpAsState(
        targetValue = if (isNavBarVisible) 80.dp else 16.dp, label = "BottomPadding"
    )

    // Handle external composer open request (e.g. from widget)
    LaunchedEffect(openComposer) {
        if (openComposer) {
            // Same logic as FAB click
            if (uiState.draft.drafts.isNotEmpty()) {
                showDraftPrompt = true
            } else {
                viewModel.initializeNewDraftSession()
                startFresh = true
                showComposerDialog = true
            }
            onComposerOpened()
        }
    }

    // Double tap refresh logic: scroll to top

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
        onToggleNavBar = { onToggleNavBar?.invoke(it) },
        isNavBarVisible = isNavBarVisible,
        listPane = { onMemoClick ->
            MemosListPane(
                viewModel = viewModel,
                listState = listState,
                onMemoClick = onMemoClick,
                contentPadding = PaddingValues(
                    start = 16.dp, top = 88.dp, end = 16.dp, bottom = bottomPadding
                ),
                onDraftsCardClick = { showDraftsScreen = true },
                onHashtagClick = { tag -> viewModel.toggleHashtagFilter(tag) }
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

            // FAB for creating new memo
            if (uiState.session.currUser != null && !isSearchExpanded) {
                // Animate FAB position only if nav bar can be toggled (onToggleNavBar provided)
                val fabBottomPadding by animateDpAsState(
                    targetValue = if (onToggleNavBar != null && isFabExpanded) 96.dp else 16.dp,
                    label = "fabBottomPadding"
                )

                ExtendedFloatingActionButton(
                    onClick = {
                        // If drafts exist, show prompt; otherwise show composer directly
                        if (uiState.draft.drafts.isNotEmpty()) {
                            showDraftPrompt = true
                        } else {
                            // Start fresh with a new draft session ID
                            viewModel.initializeNewDraftSession()
                            startFresh = true
                            showComposerDialog = true
                        }
                    },
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add, contentDescription = null
                        )
                    },
                    text = {
                        Text(text = stringResource(R.string.memo_composer_fab_new_memo))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding)
                )
            }
        })

    // Draft prompt dialog
    if (showDraftPrompt) {
        AlertDialog(
            onDismissRequest = { showDraftPrompt = false },
            title = { Text(stringResource(R.string.drafts_prompt_title)) },
            text = { Text(stringResource(R.string.drafts_prompt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDraftPrompt = false
                        // Load latest draft
                        val latestDraft = viewModel.getLatestDraft()
                        if (latestDraft != null) {
                            viewModel.setCurrentEditingDraft(latestDraft.id)
                        }
                        startFresh = false
                        showComposerDialog = true
                    }) {
                    Text(stringResource(R.string.drafts_prompt_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDraftPrompt = false
                        // Start fresh with a new draft session ID
                        viewModel.initializeNewDraftSession()
                        startFresh = true
                        showComposerDialog = true
                    }) {
                    Text(stringResource(R.string.drafts_prompt_start_fresh))
                }
            })
    }

    // Composer dialog
    if (showComposerDialog) {
        val latestDraft = if (!startFresh) viewModel.getLatestDraft() else null
        val currentDraftId = uiState.draft.currentEditingDraftId
        val draftToLoad = if (!startFresh && currentDraftId != null) {
            uiState.draft.drafts.find { it.id == currentDraftId }
        } else if (!startFresh) {
            latestDraft
        } else {
            null
        }

        MemoComposerDialog(
            onDismiss = {
                showComposerDialog = false
                startFresh = false
            },
            viewModel = viewModel,
            hostUrl = uiState.session.hostUrl,
            title = stringResource(R.string.memo_composer_fab_new_memo),
            // Pre-populate with saved draft if available
            initialContent = draftToLoad?.content ?: "",
            initialAttachments = draftToLoad?.attachments ?: emptyList(),
            initialVisibility = draftToLoad?.visibility,
            initialLocation = draftToLoad?.location,
            mode = ComposerMode.PUBLISH
        )
    }

    // Drafts screen (full-screen)
    // Drafts screen (full-screen)
    // Material Expressive easing
    val enterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val exitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    AnimatedVisibility(
        visible = showDraftsScreen, enter = slideInVertically(
            animationSpec = tween(400, easing = enterEasing), initialOffsetY = { it }) + fadeIn(
            animationSpec = tween(400, easing = enterEasing)
        ), exit = slideOutVertically(
            animationSpec = tween(200, easing = exitEasing), targetOffsetY = { it }) + fadeOut(
            animationSpec = tween(200, easing = exitEasing)
        )
    ) {
        DraftsScreen(
            viewModel = viewModel, onDismiss = { showDraftsScreen = false })
    }
}

@Composable
private fun MemosListPane(
    viewModel: MemosViewModel,
    listState: LazyListState,
    onMemoClick: (Memo) -> Unit,
    contentPadding: PaddingValues,
    onDraftsCardClick: () -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasDrafts = uiState.draft.drafts.isNotEmpty()
    val shortcutListState = rememberLazyListState()
    val draftCount = uiState.draft.drafts.size
    var showDeleteAllDialog by remember { mutableStateOf(false) }

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
        contentPadding = contentPadding,
        errorTitle = stringResource(R.string.common_error_failed_to_load_memos),
        isOffline = uiState.userMemoList.list.isOffline,
        errorMessage = uiState.userMemoList.list.errorMessage,
        onHashtagClick = onHashtagClick,
        header = {
            val hasShortcuts = uiState.userMemoList.shortcuts.isNotEmpty()
            val selectedHashtag = uiState.userMemoList.selectedHashtag
            val showFilterRow = hasShortcuts || selectedHashtag != null

            if (hasDrafts || showFilterRow) {
                item(key = "header_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Drafts Card (shown when drafts exist)
                        AnimatedVisibility(
                            visible = hasDrafts,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable(onClick = onDraftsCardClick),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Column {
                                            Text(
                                                text = stringResource(R.string.drafts_card_message),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.drafts_count, draftCount
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.common_delete),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showDeleteAllDialog = true }
                                            .padding(4.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = 0.5f
                                        ))
                                }
                            }
                        }

                        // Horizontal Shortcut Row
                        AnimatedVisibility(
                            visible = showFilterRow,
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
                                            drawRect(
                                                brush = startGradient, blendMode = BlendMode.DstIn
                                            )
                                        }
                                        if (shortcutListState.canScrollForward) {
                                            drawRect(
                                                brush = endGradient, blendMode = BlendMode.DstIn
                                            )
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selectedHashtag != null) {
                                    item {
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                viewModel.toggleHashtagFilter(
                                                    selectedHashtag
                                                )
                                            },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Tag,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(selectedHashtag.removePrefix("#"))
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }

                                items(uiState.userMemoList.shortcuts, key = {
                                    it.name.takeUnless { n -> n.isNullOrBlank() }
                                        ?: "${it.title?.hashCode() ?: 0}_${it.filter?.hashCode() ?: 0}"
                                }) { shortcut ->
                                    val isSelected =
                                        uiState.userMemoList.selectedShortcut?.name == shortcut.name
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
                                                    imageVector = Icons.Outlined.Close,
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
            }
        })

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.drafts_delete_all_confirmation_title)) },
            text = { Text(stringResource(R.string.drafts_delete_all_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDrafts()
                        showDeleteAllDialog = false
                    }, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            })
    }
}

