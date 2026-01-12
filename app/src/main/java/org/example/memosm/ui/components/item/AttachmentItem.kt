package org.example.memosm.ui.components.item

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.example.memosm.R
import org.example.memosm.model.Attachment
import java.text.SimpleDateFormat
import java.util.*

enum class AttachmentCompactMode {
    Area, Width, Height, Always, Never
}

@Composable
fun AttachmentCard(
    attachment: Attachment,
    token: String,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    compactMode: AttachmentCompactMode = AttachmentCompactMode.Area,
    onRatioAvailable: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val formattedDate = remember(attachment.createTime) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(attachment.createTime ?: "")
            val outputFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            Log.e("AttachmentCard", "Failed to parse date: ${attachment.createTime}", e)
            attachment.createTime ?: ""
        }
    }

    val formattedSize = remember(attachment.size) {
        val bytes = attachment.size?.toLongOrNull() ?: return@remember attachment.size ?: ""
        Formatter.formatFileSize(context, bytes)
    }

    val displayType = attachment.displayType
    val isImage = remember(displayType) {
        displayType.startsWith("image/", ignoreCase = true) || displayType.contains("image", ignoreCase = true)
    }
    val isAudio = remember(displayType) {
        displayType.startsWith("audio/", ignoreCase = true) || displayType.contains("audio", ignoreCase = true)
    }
    val isVideo = remember(displayType) {
        displayType.startsWith("video/", ignoreCase = true) || displayType.contains("video", ignoreCase = true)
    }

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
            val showFooter = showInfo && !isCompact

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImage) {
                        val externalLink = attachment.externalLink
                        val headers = NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()
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
                            compact = isCompact,
                            showContainer = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = attachment.filename,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    // Floating menu button for compact view
                    if (showInfo && isCompact) {
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
                                        if (!attachment.externalLink.isNullOrBlank()) {
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
                                if (!attachment.externalLink.isNullOrBlank()) {
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

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.attachments_info_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AttachmentInfoRow(stringResource(R.string.attachments_info_filename), attachment.filename)
                    AttachmentInfoRow(stringResource(R.string.attachments_info_type), attachment.displayType)
                    AttachmentInfoRow(stringResource(R.string.attachments_info_size), formattedSize)
                    AttachmentInfoRow(stringResource(R.string.attachments_info_created), formattedDate)
                    if (attachment.name != null) AttachmentInfoRow(stringResource(R.string.attachments_info_id), attachment.name)
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
            text = { Text(stringResource(R.string.attachments_download_dialog_confirm, attachment.filename)) },
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
fun VideoPlayer(url: String, token: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isFullscreen by mutableStateOf(false)
    var isReady by mutableStateOf(false)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(
                OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                    .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            )
        ).build()
    }
    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) isReady = true
            }
        }
        exoPlayer.addListener(listener)
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
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
            }, update = { view -> view.player = if (isFullscreen) null else exoPlayer },
            modifier = Modifier.fillMaxSize().alpha(if (isReady) 1f else 0f)
        )
        if (!isReady) CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
    if (isFullscreen) {
        Dialog(onDismissRequest = { isFullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
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
    showContainer: Boolean = true
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(
                OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                    .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            )
        ).build()
    }
    var isPlaying by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var duration by mutableLongStateOf(0L)
    var currentPosition by mutableLongStateOf(0L)
    var isPrepared by mutableStateOf(false)
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
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
