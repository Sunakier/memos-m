package org.example.memosm.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.ui.component.composer.DeleteConfirmationDialog
import org.example.memosm.ui.component.composer.MemoComposerDialog
import org.example.memosm.ui.component.composer.MemoEditDialog
import org.example.memosm.ui.component.item.MemoItem
import org.example.memosm.viewmodel.MemosViewModel
import org.example.memosm.viewmodel.PaginatedListState
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailView(
    modifier: Modifier = Modifier,
    memo: Memo,
    comments: PaginatedListState<Memo>,

    token: String,
    hostUrl: String = "",
    showBackButton: Boolean,
    onBack: () -> Unit,
    viewModel: MemosViewModel,
    reactionOptions: List<String> = emptyList(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCommentDialog by remember { mutableStateOf(false) }
    var memoToEdit by remember { mutableStateOf<Memo?>(null) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }

    val listState = rememberLazyListState()
    var isFabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            isFabExpanded = when {
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

    val isOwner = remember(memo.creator, uiState.session.currUser?.name) {
        memo.creator == uiState.session.currUser?.name
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            TopAppBar(
                title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        stringResource(R.string.memo_detail_title),
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.memo_detail_back)
                            )
                        }
                    }
                },
                // Set to empty because parent Scaffolds are already handling system bar insets
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }, floatingActionButton = {
            if (uiState.session.currUser != null) {
                ExtendedFloatingActionButton(
                    onClick = { showCommentDialog = true },
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add, contentDescription = null
                        )
                    },
                    text = {
                        Text(text = stringResource(R.string.memo_detail_add_comment))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }, containerColor = Color.Transparent, modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize() // Use fillMaxSize instead of fillMaxHeight
                        .widthIn(max = 800.dp)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Original memo
                    item(key = "original_${memo.name ?: memo.content.hashCode()}") {
                        MemoItem(
                            memo = memo,
                            user = uiState.users[memo.creator],
                            currentUser = uiState.session.currUser,
                            token = token,
                            hostUrl = hostUrl,
                            colors = CardDefaults.cardColors(),
                            onEdit = if (isOwner) {
                                { memoToEdit = memo }
                            } else null,
                            onPin = if (isOwner) { pinned ->
                                viewModel.updateMemoPinned(memo, pinned)
                            } else null,
                            onDelete = if (isOwner) {
                                { memoToDelete = memo }
                            } else null,
                            onUpsertReaction = { emoji ->
                                viewModel.upsertMemoReaction(memo, emoji)
                            },
                            onDeleteReaction = { reaction ->
                                viewModel.deleteMemoReaction(memo, reaction)
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
                            isDetailView = true,
                            reactionOptions = reactionOptions)
                    }

                    // Comments section header
                    item(key = "comments_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.memo_detail_comments, comments.items.size
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Loading indicator for comments
                    if (comments.isLoading) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // Comments list
                    if (!comments.isLoading && comments.items.isEmpty()) {
                        item(key = "empty_comments") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.memo_detail_no_comments),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        comments.items,
                        key = { "comment_${it.name ?: it.content.hashCode()}" }) { comment ->
                        val isCommentOwner = comment.creator == uiState.session.currUser?.name
                        MemoItem(
                            memo = comment,
                            user = uiState.users[comment.creator],
                            currentUser = uiState.session.currUser,
                            token = token,
                            hostUrl = hostUrl,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onEdit = if (isCommentOwner) {
                                { memoToEdit = comment }
                            } else null,
                            onDelete = if (isCommentOwner) {
                                { memoToDelete = comment }
                            } else null,
                            onUpsertReaction = { emoji ->
                                viewModel.upsertMemoReaction(comment, emoji)
                            },
                            onDeleteReaction = { reaction ->
                                viewModel.deleteMemoReaction(comment, reaction)
                            },
                            onContentUpdate = if (isCommentOwner) { newContent ->
                                viewModel.updateMemo(
                                    comment,
                                    newContent,
                                    comment.visibility,
                                    comment.attachments ?: emptyList(),
                                    comment.location
                                )
                            } else null,
                            isDetailView = true,
                            reactionOptions = reactionOptions)
                    }
                }
            }
        }
    }

    if (showCommentDialog) {
        MemoComposerDialog(
            onDismiss = { showCommentDialog = false },
            viewModel = viewModel,
            hostUrl = hostUrl,
            title = stringResource(R.string.memo_detail_add_comment),
            parentMemo = memo,
            placeholder = stringResource(R.string.memo_detail_comment_placeholder)
        )
    }

    memoToEdit?.let { m ->
        MemoEditDialog(
            memo = m, onDismiss = { memoToEdit = null }, viewModel = viewModel, hostUrl = hostUrl
        )
    }

    memoToDelete?.let { m ->
        DeleteConfirmationDialog(memo = m, onDismiss = { memoToDelete = null }, onConfirm = {
            viewModel.deleteMemo(m) {
                memoToDelete = null
                if (m == memo) onBack()
            }
        })
    }
}
