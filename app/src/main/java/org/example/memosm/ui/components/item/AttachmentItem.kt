package org.example.memosm.ui.components.item

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import org.example.memosm.R
import org.example.memosm.model.Attachment
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AttachmentItem(
    attachment: Attachment,
    token: String,
    modifier: Modifier = Modifier,
    onRatioAvailable: (Float) -> Unit
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(attachment.createTime) {
        try {
            // "2024-03-20T10:00:00Z"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(attachment.createTime ?: "")
            val outputFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            Log.e(
                "AttachmentItem", "Failed to parse date: ${attachment.createTime}", e
            )
            attachment.createTime ?: ""
        }
    }

    val formattedSize = remember(attachment.size) {
        val bytes = attachment.size?.toLongOrNull() ?: return@remember attachment.size ?: ""
        Formatter.formatFileSize(context, bytes)
    }

    val displayType = attachment.displayType
    val isImage = remember(displayType) {
        displayType.startsWith(
            "image/", ignoreCase = true
        ) || displayType.contains("image", ignoreCase = true)
    }

    val isAudio = remember(displayType) {
        displayType.startsWith(
            "audio/", ignoreCase = true
        ) || displayType.contains("audio", ignoreCase = true)
    }

    val isVideo = remember(displayType) {
        displayType.startsWith(
            "video/", ignoreCase = true
        ) || displayType.contains("video", ignoreCase = true)
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    val externalLink = attachment.externalLink
                    val headers = NetworkHeaders.Builder().set(
                        "Authorization", "Bearer $token"
                    ).build()
                    val imageRequest = remember(externalLink, token) {
                        ImageRequest.Builder(context).data(externalLink).httpHeaders(headers)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED).build()
                    }

                    var isLoading by remember { mutableStateOf(true) }
                    var isError by remember { mutableStateOf(false) }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = attachment.filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onLoading = { isLoading = true; isError = false },
                        onSuccess = { state ->
                            isLoading = false
                            isError = false
                            val size = state.painter.intrinsicSize
                            if (size.width > 0 && size.height > 0) {
                                onRatioAvailable(size.width / size.height)
                            }
                        },
                        onError = { isLoading = false; isError = true })

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp
                        )
                    }

                    if (isError) {
                        Icon(
                            imageVector = Icons.Outlined.BrokenImage,
                            contentDescription = stringResource(R.string.attachments_error),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (isVideo && !attachment.externalLink.isNullOrBlank()) {
                    VideoPlayer(
                        url = attachment.externalLink,
                        token = token,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isAudio && !attachment.externalLink.isNullOrBlank()) {
                    AudioPlayer(
                        url = attachment.externalLink,
                        filename = attachment.filename,
                        token = token,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                } else {
                    Text(
                        text = attachment.filename,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showInfoDialog = true }, modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.attachments_info_title),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showDownloadDialog = true }, modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.attachments_download_button),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        val attachmentErrorOpenLinkText =
                            stringResource(R.string.attachments_error_open_link)

                        if (!attachment.externalLink.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW, attachment.externalLink.toUri()
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "AttachmentItem",
                                            "Failed to open link: ${attachment.externalLink}",
                                            e
                                        )
                                        val errMsg = e.localizedMessage ?: e.javaClass.simpleName
                                        Toast.makeText(
                                            context,
                                            attachmentErrorOpenLinkText + ": $errMsg",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }, modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = stringResource(R.string.memo_action_open_web),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.attachments_info_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AttachmentInfoRow(
                        stringResource(R.string.attachments_info_filename), attachment.filename
                    )
                    AttachmentInfoRow(
                        stringResource(R.string.attachments_info_type), attachment.displayType
                    )
                    AttachmentInfoRow(stringResource(R.string.attachments_info_size), formattedSize)
                    AttachmentInfoRow(
                        stringResource(R.string.attachments_info_created), formattedDate
                    )
                    if (attachment.name != null) AttachmentInfoRow(
                        stringResource(R.string.attachments_info_id), attachment.name
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            })
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.attachments_download_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.attachments_download_dialog_confirm, attachment.filename
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadAttachmentFile(context, attachment, token)
                    showDownloadDialog = false
                }) {
                    Text(stringResource(R.string.attachments_download_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            })
    }
}

@Composable
fun AttachmentInfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun downloadAttachmentFile(context: Context, attachment: Attachment, token: String) {
    val url = attachment.externalLink ?: return
    try {
        val request = DownloadManager.Request(url.toUri()).setTitle(attachment.filename)
            .setDescription(context.getString(R.string.attachments_download_started))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.filename)
            .addRequestHeader("Authorization", "Bearer $token")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(
            context, context.getString(R.string.attachments_download_started), Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        val message = context.getString(R.string.attachments_error_download_failed, e.message ?: "")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
