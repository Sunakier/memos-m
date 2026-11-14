package org.example.memosm.ui.component

import DeleteConfirmationDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.ui.component.item.MemoItem
import org.example.memosm.viewmodel.MemosViewModel
import org.example.memosm.viewmodel.isOffline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailView(
    modifier: Modifier = Modifier,
    memo: Memo,
    token: String,
    hostUrl: String = "",
    showBackButton: Boolean,
    onBack: () -> Unit,
    viewModel: MemosViewModel,
    reactionOptions: List<String> = emptyList(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val comments = viewModel.comments.collectAsLazyPagingItems()
    val snapshot by viewModel.commentSnapshot.collectAsStateWithLifecycle()
    val offline = uiState.connectionState.isOffline
    val loading = comments.loadState.refresh is LoadState.Loading
    val loadError = comments.loadState.refresh as? LoadState.Error
    val onCommentMemo = LocalMemoCommenter.current
    val onEditMemo = LocalMemoEditor.current
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }
    var commentsOverride by rememberSaveable(memo.name) { mutableStateOf<Boolean?>(null) }
    val settings = uiState.appSettings
    val showComments = commentsOverride ?: !(settings.hideComments || settings.hideEmptyComments && comments.itemCount == 0)
    val quickReactionOptions = remember(settings.quickReactions) {
        settings.quickReactions.split(Regex("\\s+")).filter(String::isNotBlank)
    }

    val listState = rememberLazyListState()
    var isFabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            isFabExpanded =
                when {
                    index == 0 && offset == 0 -> true
                    index > previousIndex -> false
                    index < previousIndex -> true
                    offset > previousScrollOffset + 10 -> false
                    offset < previousScrollOffset - 10 -> true
                    else -> isFabExpanded
                }
            previousIndex = index
            previousScrollOffset = offset
        }
    }

    val userName = uiState.session.currUser?.name
    val isOwner = userName != null && memo.creator == userName

    Surface(
        modifier = modifier.fillMaxSize(),
        shape =
            MaterialTheme.shapes.large.copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                stringResource(R.string.memo_detail_title),
                                modifier = Modifier.widthIn(max = 600.dp),
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.memo_detail_back),
                                )
                            }
                        }
                    },
                    // Set to empty because parent Scaffolds are already handling system bar insets
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            floatingActionButton = {
                if (uiState.session.currUser != null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            commentsOverride = true
                            onCommentMemo(memo)
                        },
                        expanded = isFabExpanded,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                            )
                        },
                        text = {
                            Text(text = stringResource(R.string.memo_detail_add_comment))
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize() // Use fillMaxSize instead of fillMaxHeight
                            .widthIn(max = 800.dp)
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Original memo
                    item(key = "original_${memo.name ?: memo.content.hashCode()}") {
                        MemoItem(
                            modifier = if (isOwner) Modifier.editOnDoubleTap(memo, onEditMemo) else Modifier,
                            memo = memo,
                            user = uiState.users[memo.creator],
                            currentUser = uiState.session.currUser,
                            token = token,
                            hostUrl = hostUrl,
                            colors = CardDefaults.cardColors(),
                            onEdit =
                                if (isOwner) {
                                    { onEditMemo(memo) }
                                } else {
                                    null
                                },
                            onPin =
                                if (isOwner) {
                                    { pinned ->
                                        viewModel.memoActionDelegate.updateMemoPinned(memo, pinned)
                                    }
                                } else {
                                    null
                                },
                            onDelete =
                                if (isOwner) {
                                    { memoToDelete = memo }
                                } else {
                                    null
                                },
                            onUpsertReaction = { emoji ->
                                viewModel.memoActionDelegate.upsertMemoReaction(memo, emoji)
                            },
                            onDeleteReaction = { reaction ->
                                viewModel.memoActionDelegate.deleteMemoReaction(memo, reaction)
                            },
                            onContentUpdate =
                                if (isOwner) {
                                    { newContent ->
                                        viewModel.memoActionDelegate.updateMemo(
                                            memo,
                                            newContent,
                                            memo.visibility,
                                            memo.attachments ?: emptyList(),
                                            memo.location,
                                        )
                                    }
                                } else {
                                    null
                                },
                            isDetailView = true,
                            headerScale = uiState.appSettings.headerScale,
                            quickReactionOptions = quickReactionOptions,
                            reactionOptions = reactionOptions,
                        )
                    }

                    item(key = "comments_toggle") {
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (showComments) 180f else 0f,
                            label = "commentsToggleRotation",
                        )
                        FilledTonalButton(
                            onClick = { commentsOverride = !showComments },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Outlined.Forum, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(if (showComments) R.string.quick_edit_hide_comments_action else R.string.quick_edit_show_comments),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(comments.itemCount.toString(), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrowRotation },
                            )
                        }
                    }

                    if (showComments) {
                        // Cached comments hint (offline: showing local cache)
                        if (offline && snapshot?.loaded == true && !loading) {
                            item(key = "comments_cached_hint") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.memo_detail_comments_cached),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }

                        // Loading indicator for comments
                        if (loading) {
                            item(key = "loading") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        when (val append = comments.loadState.append) {
                            is LoadState.Loading -> {
                                item(key = "comments_append") { CircularProgressIndicator() }
                            }

                            is LoadState.Error -> {
                                item(key = "comments_retry") {
                                    ErrorView(
                                        title = stringResource(R.string.common_error_failed_to_load),
                                        message = append.error.message.orEmpty(),
                                        onRetry = comments::retry,
                                    )
                                }
                            }

                            else -> {}
                        }

                        // Comments list
                        if (!loading && comments.itemCount == 0 && loadError == null && snapshot?.loaded == true) {
                            item(key = "empty_comments") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.memo_detail_no_comments),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (!loading && comments.itemCount == 0 && (loadError != null || offline && snapshot?.loaded != true)) {
                            item(key = "comments_unavailable") {
                                ErrorView(
                                    title = stringResource(R.string.common_error_failed_to_load),
                                    message = loadError?.error?.message ?: stringResource(R.string.offline_unavailable),
                                    onRetry = comments::retry,
                                )
                            }
                        }
                        items(count = comments.itemCount, key = comments.itemKey { "comment_${it.name}" }) { index ->
                            val comment = comments[index] ?: return@items
                            val isCommentOwner = userName != null && comment.creator == userName
                            MemoItem(
                                memo = comment,
                                user = uiState.users[comment.creator],
                                currentUser = uiState.session.currUser,
                                token = token,
                                hostUrl = hostUrl,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                onEdit =
                                    if (isCommentOwner) {
                                        { onEditMemo(comment) }
                                    } else {
                                        null
                                    },
                                onDelete =
                                    if (isCommentOwner) {
                                        { memoToDelete = comment }
                                    } else {
                                        null
                                    },
                                onUpsertReaction = { emoji ->
                                    viewModel.memoActionDelegate.upsertMemoReaction(comment, emoji)
                                },
                                onDeleteReaction = { reaction ->
                                    viewModel.memoActionDelegate.deleteMemoReaction(comment, reaction)
                                },
                                onContentUpdate =
                                    if (isCommentOwner) {
                                        { newContent ->
                                            viewModel.memoActionDelegate.updateMemo(
                                                comment,
                                                newContent,
                                                comment.visibility,
                                                comment.attachments ?: emptyList(),
                                                comment.location,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                isDetailView = true,
                                headerScale = uiState.appSettings.headerScale,
                                quickReactionOptions = quickReactionOptions,
                                reactionOptions = reactionOptions,
                            )
                        }
                    }
                }
            }
        }
    }

    memoToDelete?.let { m ->
        DeleteConfirmationDialog(memo = m, onDismiss = { memoToDelete = null }, onConfirm = {
            viewModel.memoActionDelegate.deleteMemo(m) {
                memoToDelete = null
                if (m == memo) onBack()
            }
        })
    }
}

private fun Modifier.editOnDoubleTap(memo: Memo, onEdit: (Memo) -> Unit): Modifier =
    pointerInput(memo, onEdit) {
        var previousUpTime = 0L
        var previousPosition = Offset.Zero
        awaitEachGesture {
            // Observe before selectable Markdown handles a double tap. Leave single taps to child controls.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            if (up == null || up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis ||
                (up.position - down.position).getDistance() >= viewConfiguration.touchSlop
            ) {
                previousUpTime = 0L
                return@awaitEachGesture
            }
            val elapsed = up.uptimeMillis - previousUpTime
            if (elapsed in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                (up.position - previousPosition).getDistance() < viewConfiguration.touchSlop * 2
            ) {
                up.consume()
                previousUpTime = 0L
                onEdit(memo)
            } else {
                previousUpTime = up.uptimeMillis
                previousPosition = up.position
            }
        }
    }
