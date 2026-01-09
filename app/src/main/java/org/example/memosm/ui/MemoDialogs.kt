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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowWidthSizeClass
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

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
    val isTablet = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        val surfaceModifier = if (isTablet) {
            Modifier
                .widthIn(max = 800.dp)
                .fillMaxWidth(0.8f)
                .wrapContentHeight()
        } else {
            Modifier.fillMaxSize()
        }

        val shape = if (isTablet) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)

        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = surfaceModifier,
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTablet) 24.dp else 16.dp)
                        .statusBarsPadding()
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
                        if (!isTablet) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close, contentDescription = "Close"
                                )
                            }
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
