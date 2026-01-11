package org.example.memosm.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.ui.components.composer.DeleteConfirmationDialog
import org.example.memosm.ui.components.composer.MemoComposerDialog
import org.example.memosm.ui.components.composer.MemoEditDialog
import org.example.memosm.ui.components.item.MemoItem
import org.example.memosm.viewmodel.MemosViewModel
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailView(
    memo: Memo,
    comments: List<Memo>,
    isLoadingComments: Boolean,
    token: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    viewModel: MemosViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCommentDialog by remember { mutableStateOf(false) }
    var memoToEdit by remember { mutableStateOf<Memo?>(null) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }

    val listState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                isFabVisible = when {
                    index == 0 && offset == 0 -> true
                    index > previousIndex -> false
                    index < previousIndex -> true
                    offset > previousScrollOffset + 10 -> false
                    offset < previousScrollOffset - 10 -> true
                    else -> isFabVisible
                }
                previousIndex = index
                previousScrollOffset = offset
            }
    }

    val isOwner = remember(memo.creator, uiState.user?.name) {
        memo.creator == uiState.user?.name
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Scaffold(
            topBar = {
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
                if (uiState.user != null) {
                    val density = LocalDensity.current
                    val fabTranslationY by animateFloatAsState(
                        targetValue = if (isFabVisible) 0f else with(density) { 100.dp.toPx() },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "fabTranslationY"
                    )

                    AnimatedVisibility(
                        visible = isFabVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FloatingActionButton(
                            onClick = { showCommentDialog = true },
                            modifier = Modifier.graphicsLayer {
                                translationY = fabTranslationY
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.memo_detail_add_comment)
                            )
                        }
                    }
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
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Original memo
                    item(key = "original_${memo.name ?: memo.content.hashCode()}") {
                        MemoItem(
                            memo = memo,
                            user = uiState.users[memo.creator],
                            currentUser = uiState.user,
                            token = token,
                            colors = CardDefaults.cardColors(),
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
                            isDetailView = true)
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
                                text = stringResource(R.string.memo_detail_comments, comments.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Loading indicator for comments
                    if (isLoadingComments) {
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
                    if (!isLoadingComments && comments.isEmpty()) {
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
                        comments,
                        key = { "comment_${it.name ?: it.content.hashCode()}" }) { comment ->
                        val isCommentOwner = comment.creator == uiState.user?.name
                        MemoItem(
                            memo = comment,
                            user = uiState.users[comment.creator],
                            currentUser = uiState.user,
                            token = token,
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
                            onDeleteReaction = { reactionName ->
                                viewModel.deleteMemoReaction(comment, reactionName)
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
                            isDetailView = true)
                    }
                }
            }
        }
    }

    if (showCommentDialog) {
        MemoComposerDialog(
            onDismiss = { showCommentDialog = false },
            viewModel = viewModel,
            title = stringResource(R.string.memo_detail_add_comment),
            parentMemo = memo,
            placeholder = stringResource(R.string.memo_detail_comment_placeholder)
        )
    }

    memoToEdit?.let { m ->
        MemoEditDialog(
            memo = m, onDismiss = { memoToEdit = null }, viewModel = viewModel
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
