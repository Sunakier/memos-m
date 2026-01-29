package org.example.memosm.ui.nav

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Draft
import org.example.memosm.ui.component.composer.MemoComposerDialog
import org.example.memosm.viewmodel.MemosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    viewModel: MemosViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val drafts = uiState.draft.drafts
    
    var draftToEdit by remember { mutableStateOf<Draft?>(null) }
    var draftToDelete by remember { mutableStateOf<Draft?>(null) }
    var draftToPost by remember { mutableStateOf<Draft?>(null) }
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drafts_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.memo_detail_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.drafts_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        dateFormat = dateFormat,
                        onEdit = { draftToEdit = draft },
                        onPost = { draftToPost = draft },
                        onDelete = { draftToDelete = draft }
                    )
                }
            }
        }
    }

    // Edit Draft Dialog
    if (draftToEdit != null) {
        viewModel.setCurrentEditingDraft(draftToEdit!!.id)
        MemoComposerDialog(
            onDismiss = {
                draftToEdit = null
                viewModel.setCurrentEditingDraft(null)
            },
            viewModel = viewModel,
            hostUrl = uiState.session.hostUrl,
            title = stringResource(R.string.drafts_action_edit),
            initialContent = draftToEdit!!.content,
            initialAttachments = draftToEdit!!.attachments,
            initialVisibility = draftToEdit!!.visibility,
            initialLocation = draftToEdit!!.location
        )
    }

    // Post Draft (create memo and delete draft)
    LaunchedEffect(draftToPost) {
        draftToPost?.let { draft ->
            viewModel.setCurrentEditingDraft(draft.id)
            viewModel.createMemo(
                content = draft.content,
                visibility = draft.visibility,
                attachments = draft.attachments,
                location = draft.location
            ) {
                // Draft is automatically deleted by createMemo via clearCurrentEditingDraft
            }
            draftToPost = null
        }
    }

    // Delete confirmation dialog
    if (draftToDelete != null) {
        AlertDialog(
            onDismissRequest = { draftToDelete = null },
            title = { Text(stringResource(R.string.drafts_delete_confirm_title)) },
            text = { Text(stringResource(R.string.drafts_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draftToDelete!!.id)
                        draftToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun DraftCard(
    draft: Draft,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onPost: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp)
        ) {
            // Content preview
            Text(
                text = draft.content.ifBlank { stringResource(R.string.drafts_no_content) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (draft.content.isBlank()) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Metadata row: timestamp, visibility, attachments, location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp
                Text(
                    text = dateFormat.format(Date(draft.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Visibility
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = draft.visibility,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )
                
                // Attachments count
                if (draft.attachments.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Attachment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${draft.attachments.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Location indicator
                if (draft.location != null) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.drafts_action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // Edit button
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.drafts_action_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Post button
                FilledTonalButton(
                    onClick = onPost,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.drafts_action_post))
                }
            }
        }
    }
}
