package org.example.memosm.ui.components.item

import AudioPlayer
import FullScreenImageViewer
import VideoPlayer
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import org.example.memosm.ui.components.item.media.FileThumbnail
import org.example.memosm.ui.components.item.media.FileThumbnailMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class AttachmentCompactMode {
    Area, Width, Height, Always, Never
}

@Composable
fun AttachmentCard(
    modifier: Modifier = Modifier,
    attachment: Attachment?,
    token: String,
    uri: Uri = Uri.EMPTY,
    showInfo: Boolean = true,
    showActions: Boolean = true,
    showSize: Boolean = true,
    showFilename: Boolean = true,
    compactMode: AttachmentCompactMode = AttachmentCompactMode.Area,
    onRatioAvailable: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }
    var isAudioPlaying by remember { mutableStateOf(false) }

    val formattedDate = remember(attachment?.createTime) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(attachment?.createTime ?: "")
            val outputFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            Log.e("AttachmentCard", "Failed to parse date: ${attachment?.createTime}", e)
            attachment?.createTime ?: ""
        }
    }

    val formattedSize = remember(attachment?.size) {
        val bytes = attachment?.size?.toLongOrNull() ?: return@remember attachment?.size ?: ""
        Formatter.formatFileSize(context, bytes)
    }

    val displayType = remember(uri, attachment?.displayType) {
        if (uri != Uri.EMPTY) {
            val crType = context.contentResolver.getType(uri)
            if (crType != null) crType
            else {
                val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
            }
        } else {
            attachment?.displayType ?: ""
        }
    }

    val filename = remember(attachment?.filename, uri) {
        attachment?.filename ?: uri.lastPathSegment ?: "file"
    }

    val isImage = remember(displayType) {
        displayType.startsWith("image/", ignoreCase = true) || displayType.contains(
            "image",
            ignoreCase = true
        )
    }
    val isAudio = remember(displayType) {
        displayType.startsWith("audio/", ignoreCase = true) || displayType.contains(
            "audio",
            ignoreCase = true
        )
    }
    val isVideo = remember(displayType) {
        displayType.startsWith("video/", ignoreCase = true) || displayType.contains(
            "video",
            ignoreCase = true
        )
    }

    // Audio handling (temp file for base64 if needed)
    val audioUrl = remember(uri, attachment, displayType) {
        if (!isAudio) return@remember null
        when {
            uri != Uri.EMPTY -> uri.toString()
            !attachment?.externalLink.isNullOrBlank() -> attachment.externalLink
            !attachment?.content.isNullOrBlank() -> {
                try {
                    val bytes = Base64.decode(attachment.content, Base64.NO_WRAP)
                    val ext = when {
                        displayType.contains("aac") -> "aac"
                        displayType.contains("mp3") || displayType.contains("mpeg") -> "mp3"
                        displayType.contains("ogg") -> "ogg"
                        displayType.contains("wav") -> "wav"
                        displayType.contains("m4a") -> "m4a"
                        else -> "aac"
                    }
                    val tempFile =
                        File(context.cacheDir, "cached_audio_${filename.hashCode()}.$ext")
                    if (!tempFile.exists() || tempFile.length() != bytes.size.toLong()) {
                        tempFile.writeBytes(bytes)
                    }
                    tempFile.toUri().toString()
                } catch (e: Exception) {
                    Log.e("AttachmentCard", "Error creating temp audio file", e)
                    null
                }
            }

            else -> null
        }
    }

    // Default ratios before loading
    var intrinsicRatio by remember {
        mutableFloatStateOf(
            when {
                isVideo -> 1.777f // 16:9 as a better default for videos
                else -> 1.0f
            }
        )
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isAudioPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "AttachmentCardBackground",
        animationSpec = tween(durationMillis = 300)
    )

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = when (compactMode) {
                AttachmentCompactMode.Always -> true
                AttachmentCompactMode.Never -> false
                AttachmentCompactMode.Width -> maxWidth < 160.dp
                AttachmentCompactMode.Height -> maxHeight < 140.dp
                AttachmentCompactMode.Area -> {
                    val area = maxWidth.value * maxHeight.value
                    area < 25000f || maxWidth < 160.dp || maxHeight < 140.dp
                }
            }
            val isWide = !isCompact && maxWidth > 240.dp
            val showFooter = showInfo && !isCompact && (showFilename || showActions || showSize)

            // Report total ratio to parent
            LaunchedEffect(
                intrinsicRatio,
                maxWidth,
                isCompact,
                isWide,
                showInfo,
                showFilename,
                showActions,
                showSize
            ) {
                val w = maxWidth.value
                val currentIntrinsic =
                    if (!isImage && !isVideo && isWide) 3.0f else intrinsicRatio

                val footerHeight =
                    if (showInfo && !isCompact && (showFilename || showActions || showSize)) 56f else 0f

                val totalRatio = if (footerHeight > 0f) {
                    if (w > 0) w / (w / currentIntrinsic + footerHeight) else currentIntrinsic
                } else {
                    currentIntrinsic
                }
                onRatioAvailable(totalRatio)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImage) {
                        val model = remember(uri, attachment) {
                            when {
                                uri != Uri.EMPTY -> uri
                                !attachment?.externalLink.isNullOrBlank() -> attachment.externalLink
                                !attachment?.content.isNullOrBlank() -> {
                                    try {
                                        Base64.decode(attachment.content, Base64.NO_WRAP)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                else -> null
                            }
                        }

                        val headers =
                            NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()
                        val imageRequest = remember(model, token) {
                            ImageRequest.Builder(context).data(model).httpHeaders(headers)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED).build()
                        }

                        var isLoading by remember { mutableStateOf(true) }
                        var isError by remember { mutableStateOf(false) }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = filename,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showFullScreenImage = true },
                            contentScale = ContentScale.Crop,
                            onLoading = { isLoading = true; isError = false },
                            onSuccess = { state ->
                                isLoading = false
                                isError = false
                                val size = state.painter.intrinsicSize
                                if (size.width > 0 && size.height > 0) {
                                    intrinsicRatio = size.width / size.height
                                }
                            },
                            onError = { isLoading = false; isError = true })

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
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
                    } else if (isVideo && (!attachment?.externalLink.isNullOrBlank() || uri != Uri.EMPTY)) {
                        VideoPlayer(
                            url = if (uri != Uri.EMPTY) uri.toString() else attachment?.externalLink
                                ?: "",
                            token = token,
                            modifier = Modifier.fillMaxSize(),
                            onRatioAvailable = { intrinsicRatio = it }
                        )
                    } else if (isAudio && !audioUrl.isNullOrBlank()) {
                        AudioPlayer(
                            url = audioUrl,
                            filename = filename,
                            token = token,
                            mode = when {
                                isWide -> AudioPlayerMode.WIDE
                                isCompact -> AudioPlayerMode.COMPACT
                                else -> AudioPlayerMode.NORMAL
                            },
                            showContainer = false,
                            onPlayingStateChanged = { isAudioPlaying = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        FileThumbnail(
                            displayType = displayType,
                            filename = filename,
                            mode = when {
                                isWide -> FileThumbnailMode.WIDE
                                isCompact -> FileThumbnailMode.COMPACT
                                else -> FileThumbnailMode.NORMAL
                            },
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating menu button for compact view
                    if (showInfo && showActions && isCompact) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showMenu = true }
                                ) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.attachments_info_title)) },
                                            onClick = { showMenu = false; showInfoDialog = true },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.Info,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.attachments_download_button)) },
                                            onClick = {
                                                showMenu = false; showDownloadDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.Download,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        if (attachment?.externalLink != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.memo_action_open_web)) },
                                                onClick = {
                                                    showMenu = false
                                                    try {
                                                        val intent = Intent(
                                                            Intent.ACTION_VIEW,
                                                            attachment.externalLink.toUri()
                                                        )
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "AttachmentCard",
                                                            "Failed to open link",
                                                            e
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Language,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showFooter) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .height(48.dp)
                    ) {
                        if (showFilename) {
                            Text(
                                text = filename,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (showActions) {
                                    IconButton(
                                        onClick = { showInfoDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = stringResource(R.string.attachments_info_title),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showDownloadDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = stringResource(R.string.attachments_download_button),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (attachment?.externalLink != null) {
                                        val openLinkText =
                                            stringResource(R.string.attachments_error_open_link)
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        attachment.externalLink.toUri()
                                                    )
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "AttachmentCard",
                                                        "Failed to open link: ${attachment.externalLink}",
                                                        e
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "$openLinkText: ${e.localizedMessage}",
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
                            }
                            if (showSize && attachment?.size != null) {
                                Text(
                                    text = formattedSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                    AttachmentInfoRow(stringResource(R.string.attachments_info_filename), filename)
                    AttachmentInfoRow(stringResource(R.string.attachments_info_type), displayType)
                    if (attachment?.size != null) AttachmentInfoRow(
                        stringResource(R.string.attachments_info_size),
                        formattedSize
                    )
                    if (attachment?.createTime != null) AttachmentInfoRow(
                        stringResource(R.string.attachments_info_created),
                        formattedDate
                    )
                    if (attachment?.name != null) AttachmentInfoRow(
                        stringResource(R.string.attachments_info_id),
                        attachment.name
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
            text = { Text(stringResource(R.string.attachments_download_dialog_confirm, filename)) },
            confirmButton = {
                TextButton(onClick = {
                    if (attachment != null) downloadAttachmentFile(context, attachment, token)
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

    if (showFullScreenImage && isImage && (uri != Uri.EMPTY || !attachment?.externalLink.isNullOrBlank() || !attachment?.content.isNullOrBlank())) {
        val model = remember(uri, attachment) {
            when {
                uri != Uri.EMPTY -> uri
                !attachment?.externalLink.isNullOrBlank() -> attachment.externalLink
                !attachment?.content.isNullOrBlank() -> {
                    try {
                        Base64.decode(attachment.content, Base64.NO_WRAP)
                    } catch (_: Exception) {
                        null
                    }
                }

                else -> null
            }
        }
        if (model != null) {
            FullScreenImageViewer(
                model = model,
                filename = filename,
                token = token,
                onDismiss = { showFullScreenImage = false }
            )
        }
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
            context,
            context.getString(R.string.attachments_download_started),
            Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        val message = context.getString(R.string.attachments_error_download_failed, e.message ?: "")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}


fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun mutableLongPositionOf() = remember { mutableLongStateOf(0L) }
