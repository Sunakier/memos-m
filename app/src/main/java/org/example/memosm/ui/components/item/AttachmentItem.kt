package org.example.memosm.ui.components.item

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.example.memosm.R
import org.example.memosm.model.Attachment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class AttachmentCompactMode {
    Area, Width, Height, Always, Never
}

@Composable
fun AttachmentCard(
    attachment: Attachment?,
    token: String,
    modifier: Modifier = Modifier,
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
        displayType.startsWith("image/", ignoreCase = true) || displayType.contains("image", ignoreCase = true)
    }
    val isAudio = remember(displayType) {
        displayType.startsWith("audio/", ignoreCase = true) || displayType.contains("audio", ignoreCase = true)
    }
    val isVideo = remember(displayType) {
        displayType.startsWith("video/", ignoreCase = true) || displayType.contains("video", ignoreCase = true)
    }

    // Audio handling (temp file for base64 if needed)
    val audioUrl = remember(uri, attachment, displayType) {
        if (!isAudio) return@remember null
        when {
            uri != Uri.EMPTY -> uri.toString()
            !attachment?.externalLink.isNullOrBlank() -> attachment.externalLink
            !attachment?.content.isNullOrBlank() -> {
                try {
                    val bytes = Base64.decode(attachment?.content, Base64.NO_WRAP)
                    val ext = when {
                        displayType.contains("aac") -> "aac"
                        displayType.contains("mp3") || displayType.contains("mpeg") -> "mp3"
                        displayType.contains("ogg") -> "ogg"
                        displayType.contains("wav") -> "wav"
                        displayType.contains("m4a") -> "m4a"
                        else -> "aac"
                    }
                    val tempFile = File(context.cacheDir, "cached_audio_${filename.hashCode()}.$ext")
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
                isAudio -> 2.0f
                isVideo -> 1.777f // 16:9 as a better default for videos
                else -> 1.0f
            }
        )
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isAudioPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "AttachmentCardBackground"
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
            LaunchedEffect(intrinsicRatio, maxWidth, isCompact, isWide, showInfo, showFilename, showActions, showSize) {
                val w = maxWidth.value
                val currentIntrinsic = if (!isImage && !isVideo && !isAudio && isWide) 3.0f else intrinsicRatio
                
                val footerHeight = if (showInfo && !isCompact && (showFilename || showActions || showSize)) 56f else 0f
                
                val totalRatio = if (isAudio && !isCompact) {
                    val h = 100f + footerHeight
                    if (w > 0) w / h else 2.0f
                } else if (footerHeight > 0f) {
                    if (w > 0) w / (w / currentIntrinsic + footerHeight) else currentIntrinsic
                } else {
                    if (isAudio && !isVideo && !isImage) 1.0f else currentIntrinsic
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
                                !attachment?.externalLink.isNullOrBlank() -> attachment?.externalLink
                                !attachment?.content.isNullOrBlank() -> {
                                    try { Base64.decode(attachment?.content, Base64.NO_WRAP) } catch (_: Exception) { null }
                                }
                                else -> null
                            }
                        }

                        val headers = NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                            url = if (uri != Uri.EMPTY) uri.toString() else attachment?.externalLink ?: "",
                            token = token,
                            modifier = Modifier.fillMaxSize(),
                            onRatioAvailable = { intrinsicRatio = it }
                        )
                    } else if (isAudio && !audioUrl.isNullOrBlank()) {
                        AudioPlayer(
                            url = audioUrl,
                            filename = filename,
                            token = token,
                            compact = isCompact,
                            showContainer = false,
                            onPlayingStateChanged = { isAudioPlaying = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val fileIcon = remember(displayType) {
                            when {
                                displayType.contains("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
                                displayType.contains("text", ignoreCase = true) || displayType.contains("markdown", ignoreCase = true) -> Icons.Outlined.Description
                                displayType.contains("zip", ignoreCase = true) || displayType.contains("archive", ignoreCase = true) || displayType.contains("tar", ignoreCase = true) -> Icons.Outlined.FolderZip
                                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                            }
                        }

                        if (isWide) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = filename,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isCompact) 24.dp else 32.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                if (!isCompact) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = filename,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
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
                                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.attachments_download_button)) },
                                            onClick = { showMenu = false; showDownloadDialog = true },
                                            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) }
                                        )
                                        if (attachment?.externalLink != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.memo_action_open_web)) },
                                                onClick = {
                                                    showMenu = false
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, attachment.externalLink.toUri())
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Log.e("AttachmentCard", "Failed to open link", e)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showFooter) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).height(48.dp)) {
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
                                    IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = stringResource(R.string.attachments_info_title),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { showDownloadDialog = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = stringResource(R.string.attachments_download_button),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (attachment?.externalLink != null) {
                                        val openLinkText = stringResource(R.string.attachments_error_open_link)
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, attachment.externalLink.toUri())
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e("AttachmentCard", "Failed to open link: ${attachment.externalLink}", e)
                                                    Toast.makeText(context, "$openLinkText: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                    if (attachment?.size != null) AttachmentInfoRow(stringResource(R.string.attachments_info_size), formattedSize)
                    if (attachment?.createTime != null) AttachmentInfoRow(stringResource(R.string.attachments_info_created), formattedDate)
                    if (attachment?.name != null) AttachmentInfoRow(stringResource(R.string.attachments_info_id), attachment.name)
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
                !attachment?.externalLink.isNullOrBlank() -> attachment?.externalLink
                !attachment?.content.isNullOrBlank() -> {
                    try { Base64.decode(attachment?.content, Base64.NO_WRAP) } catch (_: Exception) { null }
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
fun FullScreenImageViewer(
    model: Any,
    filename: String,
    token: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        if (window != null) {
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val scale = remember { Animatable(1f) }
            val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
            var imageSize by remember { mutableStateOf(IntSize.Zero) }
            val coroutineScope = rememberCoroutineScope()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewWidth = constraints.maxWidth.toFloat()
                val viewHeight = constraints.maxHeight.toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(imageSize) {
                            coroutineScope {
                                while (true) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale.value * zoom).coerceIn(0.8f, 5f)
                                        
                                        if (imageSize.width > 0 && imageSize.height > 0) {
                                            val imageWidth = imageSize.width.toFloat()
                                            val imageHeight = imageSize.height.toFloat()
                                            
                                            val scaleFactor = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
                                            val fitWidth = imageWidth * scaleFactor
                                            val fitHeight = imageHeight * scaleFactor
                                            
                                            val scaledWidth = fitWidth * newScale
                                            val scaledHeight = fitHeight * newScale
                                            
                                            val maxX = maxOf(0f, (scaledWidth - viewWidth) / 2f)
                                            val maxY = maxOf(0f, (scaledHeight - viewHeight) / 2f)
                                            
                                            val targetOffset = if (newScale > 1f) {
                                                (offset.value + pan).let {
                                                    Offset(it.x.coerceIn(-maxX, maxX), it.y.coerceIn(-maxY, maxY))
                                                }
                                            } else {
                                                Offset.Zero
                                            }
                                            
                                            launch {
                                                scale.snapTo(newScale)
                                                offset.snapTo(targetOffset)
                                            }
                                        } else {
                                            launch { scale.snapTo(newScale) }
                                        }
                                    }
                                    
                                    if (scale.value < 1f) {
                                        launch {
                                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                        launch {
                                            offset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationX = offset.value.x
                            translationY = offset.value.y
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val headers = NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()
                    val fullImageRequest = remember(model, token) {
                        ImageRequest.Builder(context)
                            .data(model)
                            .httpHeaders(headers)
                            .build()
                    }
                    
                    AsyncImage(
                        model = fullImageRequest,
                        contentDescription = filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            imageSize = IntSize(
                                state.painter.intrinsicSize.width.toInt(),
                                state.painter.intrinsicSize.height.toInt()
                            )
                        }
                    )
                }
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun AttachmentInfoRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
        Toast.makeText(context, context.getString(R.string.attachments_download_started), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        val message = context.getString(R.string.attachments_error_download_failed, e.message ?: "")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    token: String,
    modifier: Modifier = Modifier,
    onRatioAvailable: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    
    val exoPlayer = remember(url, token) {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        )
        ExoPlayer.Builder(context).setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory)
        ).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isReady = true
                    val videoSize = exoPlayer.videoSize
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onRatioAvailable(videoSize.width.toFloat() / videoSize.height)
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onRatioAvailable(videoSize.width.toFloat() / videoSize.height)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setFullscreenButtonClickListener { isFullscreen = true }
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }, update = { view -> 
                view.player = if (isFullscreen) null else exoPlayer
            },
            modifier = Modifier.fillMaxSize().alpha(if (isReady) 1f else 0f)
        )
        if (!isReady) CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
    
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false }, 
            properties = DialogProperties(
                usePlatformDefaultWidth = false, 
                dismissOnBackPress = true, 
                dismissOnClickOutside = false, 
                decorFitsSystemWindows = false
            )
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            if (window != null) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            val activity = context.findActivity()
            DisposableEffect(Unit) {
                val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                onDispose { activity?.requestedOrientation = originalOrientation }
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setFullscreenButtonClickListener { isFullscreen = false }
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
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

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    url: String,
    filename: String,
    token: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showContainer: Boolean = true,
    onPlayingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        )
        ExoPlayer.Builder(context).setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory)
        ).build()
    }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by mutableLongPositionOf()
    var isPrepared by remember { mutableStateOf(false) }
    
    DisposableEffect(url) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true
                    duration = exoPlayer.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    progress = 0f
                    currentPosition = 0
                    onPlayingStateChanged(false)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { 
                isPlaying = playing
                onPlayingStateChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { 
            exoPlayer.removeListener(listener)
            exoPlayer.release() 
        }
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            delay(500)
        }
    }
    
    val content = @Composable {
        if (compact) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { if (isPrepared) { if (isPlaying) exoPlayer.pause() else exoPlayer.play() } },
                    enabled = isPrepared,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.memo_action_pause) else stringResource(R.string.memo_action_play),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
                Text(text = filename, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (isPrepared) { if (isPlaying) exoPlayer.pause() else exoPlayer.play() } }, enabled = isPrepared) {
                        Icon(imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(value = progress, onValueChange = { if (isPrepared) { progress = it; exoPlayer.seekTo((it * duration).toLong()) } }, modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                            Text(text = formatTime(duration), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    
    if (showContainer) {
        Card(modifier = modifier.then(if (!compact) Modifier.height(100.dp) else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) { content() }
    } else { Box(modifier = modifier) { content() } }
}

@Composable
fun mutableLongPositionOf() = remember { mutableLongStateOf(0L) }

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
