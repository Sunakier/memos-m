package org.example.memosm.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailPane(
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
                            Text("Memo Details", modifier = Modifier.widthIn(max = 600.dp))
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCommentDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Comment")
                }
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
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
                            token = token,
                            colors = CardDefaults.cardColors(),
                            onEdit = if (isOwner) { { memoToEdit = memo } } else null,
                            onDelete = if (isOwner) { { memoToDelete = memo } } else null
                        )
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
                                text = "Comments (${comments.size})",
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
                                    text = "No comments yet",
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
                            token = token,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onEdit = if (isCommentOwner) { { memoToEdit = comment } } else null,
                            onDelete = if (isCommentOwner) { { memoToDelete = comment } } else null
                        )
                    }
                }
            }
        }
    }

    if (showCommentDialog) {
        MemoComposerDialog(
            onDismiss = { showCommentDialog = false },
            viewModel = viewModel,
            title = "Add Comment",
            parentMemo = memo,
            placeholder = "Write your comment here..."
        )
    }

    memoToEdit?.let { m ->
        MemoEditDialog(
            memo = m,
            onDismiss = { memoToEdit = null },
            viewModel = viewModel
        )
    }

    memoToDelete?.let { m ->
        DeleteConfirmationDialog(
            memo = m,
            onDismiss = { memoToDelete = null },
            onConfirm = {
                viewModel.deleteMemo(m) {
                    memoToDelete = null
                    if (m == memo) onBack()
                }
            }
        )
    }
}
