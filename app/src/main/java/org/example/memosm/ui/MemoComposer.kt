package org.example.memosm.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.example.memosm.model.Attachment

@Composable
fun MemoComposer(
    onPublish: (String, String, List<Attachment>) -> Unit,
    onUploadFile: suspend (Uri, Context) -> Attachment?,
    availableTags: Set<String>,
    token: String,
    modifier: Modifier = Modifier,
    isPosting: Boolean = false,
    initialContent: String = "",
    initialVisibility: String = "PRIVATE",
    initialAttachments: List<Attachment> = emptyList(),
    placeholder: String = "What's on your mind?",
    autoFocus: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onDraftChanged: ((String, String, List<Attachment>) -> Unit)? = null,
    submitLabel: String? = null
) {
    val contentState = rememberTextFieldState(initialContent)
    var visibility by remember(initialVisibility) { mutableStateOf(initialVisibility) }
    var expanded by remember { mutableStateOf(false) }

    // Use a more explicit state declaration to avoid type inference issues with Pair and nullable types
    val draftAttachmentsState = remember(initialAttachments) {
        val initial: List<Pair<Uri, Attachment?>> =
            initialAttachments.map { Uri.EMPTY to (it as Attachment?) }
        mutableStateOf(initial)
    }
    var draftAttachments by draftAttachmentsState

    var isUploadingCount by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                draftAttachments = draftAttachments + (uri to null)
                isUploadingCount++
                scope.launch {
                    val attachment = onUploadFile(uri, context)
                    if (attachment != null) {
                        val updated = draftAttachments.map {
                            if (it.first == uri) uri to attachment else it
                        }
                        draftAttachments = updated
                        onDraftChanged?.invoke(
                            contentState.text.toString(),
                            visibility,
                            updated.mapNotNull { it.second }
                        )
                    } else {
                        val updated = draftAttachments.filter { it.first != uri }
                        draftAttachments = updated
                        onDraftChanged?.invoke(
                            contentState.text.toString(),
                            visibility,
                            updated.mapNotNull { it.second }
                        )
                    }
                    isUploadingCount--
                }
            }
        }
    }

    // Monitor content changes
    LaunchedEffect(contentState.text) {
        onDraftChanged?.invoke(
            contentState.text.toString(),
            visibility,
            draftAttachments.mapNotNull { it.second }
        )
    }

    // Monitor visibility changes
    LaunchedEffect(visibility) {
        onDraftChanged?.invoke(
            contentState.text.toString(),
            visibility,
            draftAttachments.mapNotNull { it.second }
        )
    }

    BoxWithConstraints(modifier = modifier) {
        // Breakpoints for hiding text to save space. 
        // We're less aggressive now: hiding visibility text only when very narrow, 
        // and publish text only as a last resort.
        val showVisibilityLabel = maxWidth > 440.dp
        val showPublishLabel = maxWidth > 300.dp

        Column {
            MemoInput(
                contentState = contentState,
                placeholder = placeholder,
                availableTags = availableTags,
                enabled = !isPosting,
                autoFocus = autoFocus,
                minHeightInLines = if (autoFocus) 5 else 3
            )

            if (draftAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(top = 8.dp, end = 8.dp)
                ) {
                    items(draftAttachments) { (uri, attachment) ->
                        val isImage = remember(uri, attachment) {
                            if (uri != Uri.EMPTY) {
                                context.contentResolver.getType(uri)?.startsWith("image/") == true
                            } else {
                                attachment?.displayType?.startsWith(
                                    "image/", ignoreCase = true
                                ) == true || attachment?.displayType?.contains(
                                    "image", ignoreCase = true
                                ) == true
                            }
                        }

                        Box(modifier = Modifier.size(80.dp)) {
                            if (isImage) {
                                val model = if (uri != Uri.EMPTY) uri else attachment?.externalLink
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(model)
                                        .addHeader("Authorization", "Bearer $token").crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(4.dp), contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                        contentDescription = null
                                    )
                                }
                            }

                            if (attachment == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)
                                        ), contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp), color = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val updated = draftAttachments.filter { it.second != attachment || (it.first != uri && uri != Uri.EMPTY) }
                                    draftAttachments = updated
                                    onDraftChanged?.invoke(
                                        contentState.text.toString(),
                                        visibility,
                                        updated.mapNotNull { it.second }
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .zIndex(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { pickerLauncher.launch("*/*") }, enabled = !isPosting
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attach File"
                        )
                    }
                    IconButton(
                        onClick = { pickerLauncher.launch("image/*") }, enabled = !isPosting
                    ) {
                        Icon(imageVector = Icons.Outlined.Image, contentDescription = "Add Image")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel, enabled = !isPosting) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(if (showPublishLabel) 8.dp else 4.dp))
                    }

                    Box {
                        TextButton(onClick = { expanded = true }, enabled = !isPosting) {
                            Icon(
                                imageVector = getVisibilityIcon(visibility),
                                contentDescription = visibility,
                                modifier = Modifier.size(18.dp)
                            )
                            if (showVisibilityLabel) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(visibility)
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("PRIVATE", "PROTECTED", "PUBLIC").forEach { option ->
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = getVisibilityIcon(option),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(option)
                                    }
                                }, onClick = {
                                    visibility = option
                                    expanded = false
                                })
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val finalAttachments = draftAttachments.mapNotNull { it.second }
                            onPublish(contentState.text.toString(), visibility, finalAttachments)
                        },
                        enabled = (contentState.text.isNotBlank() || draftAttachments.isNotEmpty()) && !isPosting && isUploadingCount == 0,
                        contentPadding = if (showPublishLabel) ButtonDefaults.ContentPadding else PaddingValues(
                            horizontal = 12.dp
                        )
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Publish"
                            )
                            if (showPublishLabel) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val label = submitLabel ?: run {
                                    val isEdit = initialContent.isNotEmpty() || initialAttachments.isNotEmpty()
                                    if (isEdit) "Update" else if (autoFocus) "Post" else "Publish"
                                }
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}
