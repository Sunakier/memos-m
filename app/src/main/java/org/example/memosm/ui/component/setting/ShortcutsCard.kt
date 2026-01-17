package org.example.memosm.ui.component.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.Shortcut
import androidx.core.net.toUri

@Composable
fun ShortcutsCard(
    shortcuts: List<Shortcut>,
    onCreate: (String, String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onUpdate: (Shortcut, String, String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDelete: (Shortcut) -> Unit
) {
    var showEditDialog by remember { mutableStateOf<Shortcut?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Shortcut?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_shortcuts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.profile_shortcuts_add))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (shortcuts.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_shortcuts_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                shortcuts.forEach { shortcut ->
                    ListItem(
                        headlineContent = { Text(shortcut.title ?: "") },
                        supportingContent = { 
                            Text(
                                shortcut.filter ?: "", 
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            ) 
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Shortcut, contentDescription = null
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { showEditDialog = shortcut }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.memo_action_edit))
                                }
                                IconButton(onClick = { showDeleteConfirm = shortcut }) {
                                    Icon(
                                        Icons.Outlined.Delete, 
                                        contentDescription = stringResource(R.string.memo_action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ShortcutEditDialog(
            title = stringResource(R.string.profile_shortcuts_add),
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, filter, onSuccess, onError ->
                onCreate(title, filter, onSuccess, onError)
            }
        )
    }

    showEditDialog?.let { shortcut ->
        ShortcutEditDialog(
            title = stringResource(R.string.profile_shortcuts_edit),
            initialTitle = shortcut.title ?: "",
            initialFilter = shortcut.filter ?: "",
            onDismiss = { showEditDialog = null },
            onConfirm = { title, filter, onSuccess, onError ->
                onUpdate(shortcut, title, filter, onSuccess, onError)
            }
        )
    }

    showDeleteConfirm?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.profile_shortcuts_delete_title)) },
            text = { Text(stringResource(R.string.profile_shortcuts_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(shortcut)
                    showDeleteConfirm = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun ShortcutEditDialog(
    title: String,
    initialTitle: String = "",
    initialFilter: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var titleText by remember { mutableStateOf(initialTitle) }
    var filterText by remember { mutableStateOf(initialFilter) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val helpUrl = stringResource(R.string.profile_shortcuts_help_url)

    AlertDialog(
        onDismissRequest = if (isSaving) ({}) else onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { 
                        titleText = it
                        errorMessage = null 
                    },
                    label = { Text(stringResource(R.string.profile_shortcuts_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { 
                        filterText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.profile_shortcuts_filter)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("\"work\" in tags && has_task_list") },
                    enabled = !isSaving,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, helpUrl.toUri())
                            context.startActivity(intent)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.profile_shortcuts_help)
                            )
                        }
                    }
                )
                
                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSaving = true
                    errorMessage = null
                    onConfirm(
                        titleText, 
                        filterText,
                        { 
                            isSaving = false
                            onDismiss() 
                        },
                        { error ->
                            isSaving = false
                            errorMessage = error
                        }
                    )
                },
                enabled = titleText.isNotBlank() && filterText.isNotBlank() && !isSaving
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
