package org.example.memosm.ui.component.composer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoComposerDialog(
    onDismiss: () -> Unit,
    viewModel: MemosViewModel,
    hostUrl: String,
    title: String,
    initialMemo: Memo? = null,
    parentMemo: Memo? = null, // If provided, it's a comment
    placeholder: String = stringResource(R.string.memo_composer_placeholder)
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
        contentWindowInsets = { WindowInsets(0) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 24.dp else 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                MemoComposer(
                    onPublish = { content, visibility, attachments, location ->
                        when {
                            initialMemo != null -> {
                                viewModel.updateMemo(
                                    initialMemo, content, visibility, attachments, location
                                ) {
                                    onDismiss()
                                }
                            }

                            parentMemo != null -> {
                                viewModel.createComment(parentMemo, content)
                                onDismiss()
                            }

                            else -> {
                                viewModel.createMemo(content, visibility, attachments, location) {
                                    onDismiss()
                                }
                            }
                        }
                    },
                    onUploadFile = { uri, context ->
                        viewModel.uploadAttachment(uri, context)
                    },
                    availableTags = uiState.session.userStats?.tagCount?.keys ?: emptySet(),
                    token = uiState.session.token,
                    hostUrl = hostUrl,
                    isPosting = uiState.isPosting,
                    initialContent = initialMemo?.content ?: "",
                    initialVisibility = initialMemo?.visibility ?: parentMemo?.visibility
                    ?: uiState.session.userSettings?.memoVisibility ?: "PRIVATE",
                    initialAttachments = initialMemo?.attachments ?: emptyList(),
                    initialLocation = initialMemo?.location,
                    placeholder = placeholder,
                    autoFocus = true,
                )
            }
        }
    }
}

@Composable
fun MemoEditDialog(
    memo: Memo, onDismiss: () -> Unit, viewModel: MemosViewModel, hostUrl: String
) {
    MemoComposerDialog(
        onDismiss = onDismiss,
        viewModel = viewModel,
        hostUrl = hostUrl,
        title = stringResource(R.string.memo_dialog_edit_title),
        initialMemo = memo
    )
}

@Composable
fun DeleteConfirmationDialog(
    memo: Memo, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memo_dialog_delete_title)) },
        text = { Text(stringResource(R.string.memo_dialog_delete_confirm)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.memo_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        })
}
