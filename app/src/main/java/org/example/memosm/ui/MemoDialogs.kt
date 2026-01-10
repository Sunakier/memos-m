package org.example.memosm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoComposerDialog(
    onDismiss: () -> Unit,
    viewModel: MemosViewModel,
    title: String,
    initialMemo: Memo? = null,
    parentMemo: Memo? = null, // If provided, it's a comment
    placeholder: String = "What's on your mind?"
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    // WindowSizeClass.isWidthAtLeastBreakpoint(600) is the modern way to check for medium/expanded width
    val isTablet = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(600)
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = if (isTablet) {
            Modifier
                .widthIn(max = 800.dp)
                .fillMaxWidth(0.8f)
        } else {
            Modifier.fillMaxWidth()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 24.dp else 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close, contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MemoComposer(
                onPublish = { content, visibility, attachments ->
                    when {
                        initialMemo != null -> {
                            viewModel.updateMemo(
                                initialMemo, content, visibility, attachments
                            ) {
                                onDismiss()
                            }
                        }

                        parentMemo != null -> {
                            viewModel.createComment(parentMemo, content)
                            onDismiss()
                        }

                        else -> {
                            viewModel.createMemo(content, visibility, attachments) {
                                onDismiss()
                            }
                        }
                    }
                },
                onUploadFile = { uri, context ->
                    viewModel.uploadAttachment(uri, context)
                },
                availableTags = uiState.userStats?.tagCount?.keys ?: emptySet(),
                token = uiState.token,
                isPosting = uiState.isPosting,
                initialContent = initialMemo?.content ?: "",
                initialVisibility = initialMemo?.visibility ?: parentMemo?.visibility
                ?: uiState.userSettings?.memoVisibility ?: "PRIVATE",
                initialAttachments = initialMemo?.attachments ?: emptyList(),
                placeholder = placeholder,
                autoFocus = true,
                onCancel = onDismiss
            )
        }
    }
}

@Composable
fun MemoEditDialog(
    memo: Memo, onDismiss: () -> Unit, viewModel: MemosViewModel
) {
    MemoComposerDialog(
        onDismiss = onDismiss, viewModel = viewModel, title = "Edit Memo", initialMemo = memo
    )
}

@Composable
fun DeleteConfirmationDialog(
    memo: Memo, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Memo") },
        text = { Text("Are you sure you want to delete this memo? This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        })
}
