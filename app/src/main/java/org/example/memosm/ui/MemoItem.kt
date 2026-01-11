package org.example.memosm.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.intellij.markdown.ast.getTextInNode
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MemoItem(
    modifier: Modifier = Modifier,
    memo: Memo,
    user: User? = null,
    currentUser: User? = null,
    token: String,
    colors: CardColors = CardDefaults.cardColors(),
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUpsertReaction: ((String) -> Unit)? = null,
    onDeleteReaction: ((String) -> Unit)? = null,
    onContentUpdate: ((String) -> Unit)? = null,
    maxHeight: Dp = Dp.Unspecified,
    isDetailView: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val unknownTime = stringResource(R.string.memo_unknown_time)
    val formattedTime = remember(memo.displayTime) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(memo.displayTime ?: "")
            date?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } ?: memo.displayTime ?: unknownTime
        } catch (_: Exception) {
            memo.displayTime ?: unknownTime
        }
    }

    // Configure markdown to treat single newlines as line breaks (memos-style)
    val markdownState = if (memo.content.length < 1000) rememberMarkdownState(
        memo.content, retainState = true, immediate = true
    ) else rememberMarkdownState(
        memo.content, retainState = true, immediate = false
    )

    Card(
        modifier = modifier.fillMaxWidth(), colors = colors
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(
                    start = 12.dp,
                    top = if (user != null) 12.dp else 4.dp,
                    end = 4.dp,
                    bottom = 12.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
                ) {
                    if (user != null) {
                        val avatarUrl = user.avatarUrl
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = stringResource(R.string.profile_avatar_description),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = user.displayName ?: user.username
                                ?: stringResource(R.string.memo_unknown_user),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = getVisibilityIcon(memo.visibility),
                                    contentDescription = memo.visibility,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = getVisibilityIcon(memo.visibility),
                                contentDescription = memo.visibility,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (onEdit != null || onDelete != null || onUpsertReaction != null || memo.name != null) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true }, modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.memo_action_more),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (memo.name != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_open_web)) },
                                    onClick = {
                                        showMenu = false
                                        val memoId = memo.name.removePrefix("memos/")
                                        val webUrl = "https://memos.nannoda.com/memos/$memoId"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, webUrl.toUri())
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e(
                                                "MemoItem",
                                                "Failed to open web URL: $webUrl",
                                                e
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Language, contentDescription = null)
                                    })
                            }


                            if (onUpsertReaction != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_add_reaction)) },
                                    onClick = {
                                        showMenu = false
                                        showReactionPicker = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AddReaction, contentDescription = null)
                                    })
                            }
                            if (onEdit != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_edit)) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Edit, contentDescription = null
                                        )
                                    })
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_delete)) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete, contentDescription = null
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.error,
                                        leadingIconColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (user != null) 10.dp else 2.dp))

            Column(modifier = Modifier.padding(start = 0.dp, end = 8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (maxHeight != Dp.Unspecified) {
                                Modifier
                                    .heightIn(max = maxHeight)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        if (size.height >= maxHeight.toPx() - 1.dp.toPx()) {
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    0.7f to Color.Black, 1.0f to Color.Transparent
                                                ), blendMode = BlendMode.DstIn
                                            )
                                        }
                                    }
                            } else {
                                Modifier
                            })) {
                    Markdown(
                        markdownState = markdownState,
                        imageTransformer = Coil2ImageTransformerImpl,
                        annotator = markdownAnnotator(
                            config = markdownAnnotatorConfig(eolAsNewLine = true)
                        ),
                        components = markdownComponents(
                            checkbox = { model ->
                                ClickableCheckbox(
                                    model = model,
                                    content = memo.content,
                                    onToggle = if (onContentUpdate != null) { newContent ->
                                        onContentUpdate(newContent)
                                    } else null
                                )
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, bottom = 4.dp)
                    )
                }

                memo.location?.let { loc ->
                    val isClickable = loc.latitude != null && loc.longitude != null
                    Surface(
                        onClick = {
                            if (isClickable) {
                                val label = loc.placeholder ?: ""
                                val geoUri =
                                    "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}${
                                        if (label.isNotEmpty()) "(${
                                            Uri.encode(label)
                                        })" else ""
                                    }"
                                val intent = Intent(Intent.ACTION_VIEW, geoUri.toUri())
                                context.startActivity(intent)
                            }
                        },
                        enabled = isClickable,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val attachments = remember(memo.attachments) {
                    memo.attachments ?: emptyList()
                }

                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isDetailView) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            attachments.forEach { attachment ->
                                AttachmentDisplay(attachment, token, isDetailView)
                            }
                        }
                    } else {
                        val scrollState = rememberLazyListState()
                        LazyRow(
                            state = scrollState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val canScrollBackward = scrollState.canScrollBackward
                                    val canScrollForward = scrollState.canScrollForward

                                    if (canScrollBackward || canScrollForward) {
                                        val leftFade =
                                            if (canScrollBackward) Color.Transparent else Color.Black
                                        val rightFade =
                                            if (canScrollForward) Color.Transparent else Color.Black

                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to leftFade,
                                                0.05f to Color.Black,
                                                0.95f to Color.Black,
                                                1f to rightFade
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        ) {
                            items(
                                attachments,
                                key = { it.externalLink ?: it.filename }) { attachment ->
                                AttachmentDisplay(attachment, token, isDetailView)
                            }
                        }
                    }
                }

                // Reactions
                val reactions = remember(memo.reactions) {
                    memo.reactions ?: emptyList()
                }
                if (reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val groupedReactions = remember(reactions) {
                        reactions.groupBy { it.reactionType }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedReactions.forEach { (type, reactionList) ->
                            val myReaction = reactionList.find { it.creator == currentUser?.name }
                            Surface(
                                shape = RoundedCornerShape(12.dp), color = if (myReaction != null) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }, border = if (myReaction != null) {
                                    null
                                } else {
                                    null
                                }, onClick = {
                                    if (myReaction != null) {
                                        // Pass the emoji type or the reaction name to the viewmodel
                                        onDeleteReaction?.invoke(type)
                                    } else {
                                        onUpsertReaction?.invoke(type)
                                    }
                                }) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = type, style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = reactionList.size.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (myReaction != null) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReactionPicker) {
        ReactionPickerDialog(
            onDismiss = { showReactionPicker = false },
            onReactionSelected = { emoji ->
                onUpsertReaction?.invoke(emoji)
                showReactionPicker = false
            })
    }
}

@Composable
fun AttachmentDisplay(
    attachment: org.example.memosm.model.Attachment,
    token: String,
    isDetailView: Boolean
) {
    val isImage = remember(attachment.displayType) {
        attachment.displayType.startsWith(
            "image/", ignoreCase = true
        ) || attachment.displayType.contains("image", ignoreCase = true)
    }

    val isAudio = remember(attachment.displayType) {
        attachment.displayType.startsWith(
            "audio/", ignoreCase = true
        ) || attachment.displayType.contains("audio", ignoreCase = true)
    }

    val isVideo = remember(attachment.displayType) {
        attachment.displayType.startsWith(
            "video/", ignoreCase = true
        ) || attachment.displayType.contains("video", ignoreCase = true)
    }

    if (isImage) {
        val context = LocalContext.current
        val imageRequest = remember(attachment.externalLink, token) {
            ImageRequest.Builder(context).data(attachment.externalLink)
                .addHeader("Authorization", "Bearer $token").crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = attachment.filename,
            modifier = Modifier
                .then(
                    if (isDetailView) Modifier.fillMaxWidth() else Modifier.size(
                        width = 240.dp,
                        height = 160.dp
                    )
                )
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isDetailView && !attachment.externalLink.isNullOrBlank()) {
                        Modifier.clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    attachment.externalLink.toUri()
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "MemoItem",
                                    "Failed to open attachment URL: ${attachment.externalLink}",
                                    e
                                )
                            }
                        }
                    } else Modifier),
            contentScale = if (isDetailView) ContentScale.FillWidth else ContentScale.Crop)
    } else if (isVideo && !attachment.externalLink.isNullOrBlank()) {
        VideoPlayer(
            url = attachment.externalLink,
            token = token,
            modifier = Modifier
                .then(
                    if (isDetailView) Modifier
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f) else Modifier.size(width = 280.dp, height = 180.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        )
    } else if (isAudio && !attachment.externalLink.isNullOrBlank()) {
        AudioPlayer(
            url = attachment.externalLink,
            filename = attachment.filename,
            token = token,
            modifier = Modifier.then(
                if (isDetailView) Modifier.fillMaxWidth() else Modifier.width(
                    240.dp
                )
            )
        )
    } else {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .then(
                    if (isDetailView) Modifier.fillMaxWidth() else Modifier.size(
                        width = 200.dp,
                        height = 100.dp
                    )
                )
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isDetailView && !attachment.externalLink.isNullOrBlank()) {
                        Modifier.clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    attachment.externalLink.toUri()
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "MemoItem",
                                    "Failed to open attachment URL: ${attachment.externalLink}",
                                    e
                                )
                            }
                        }
                    } else Modifier),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = attachment.displayType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String, token: String, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }

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
                if (playbackState == Player.STATE_READY) {
                    isReady = true
                }
            }
        }
        exoPlayer.addListener(listener)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setFullscreenButtonClickListener {
                        isFullscreen = true
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = if (isFullscreen) null else exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isReady) 1f else 0f)
        )

        if (!isReady) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            val activity = context.findActivity()
            DisposableEffect(Unit) {
                val originalOrientation =
                    activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                onDispose {
                    activity?.requestedOrientation = originalOrientation
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                            setFullscreenButtonClickListener {
                                isFullscreen = false
                            }
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    url: String, filename: String, token: String, modifier: Modifier = Modifier
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

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
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
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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

    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = filename,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (isPrepared) {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    }, enabled = isPrepared
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = progress, onValueChange = {
                            if (isPrepared) {
                                progress = it
                                exoPlayer.seekTo((it * duration).toLong())
                            }
                        }, modifier = Modifier.height(24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formatTime(duration), style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerDialog(
    onDismiss: () -> Unit, onReactionSelected: (String) -> Unit
) {
    val commonEmojis =
        listOf("👍", "👎", "❤️", "🔥", "🥰", "👏", "😄", "🤔", "🥳", "👀", "😕", "😢", "😡", "\uD83D\uDE2D")

    ModalBottomSheet(
        onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.memo_action_add_reaction),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                commonEmojis.forEach { emoji ->
                    Surface(
                        onClick = { onReactionSelected(emoji) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A clickable checkbox component for markdown task lists.
 * When clicked, it toggles the checkbox state in the content and calls onToggle with the updated content.
 */
@Composable
private fun ClickableCheckbox(
    model: MarkdownComponentModel,
    content: String,
    onToggle: ((String) -> Unit)?
) {
    val nodeText = model.node.getTextInNode(content)
    val isChecked = nodeText.contains("[x]") || nodeText.contains("[X]")

    val isClickable = onToggle != null

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = if (isClickable) { _ ->
                // Find the checkbox pattern in the content and toggle it
                val startOffset = model.node.startOffset
                val endOffset = model.node.endOffset

                // Get the text of this specific checkbox node
                val checkboxText = content.substring(startOffset, endOffset)

                // Toggle the checkbox state
                val newCheckboxText = if (isChecked) {
                    checkboxText.replace("[x]", "[ ]", ignoreCase = true)
                } else {
                    checkboxText.replace("[ ]", "[x]")
                }

                // Create the new content with the toggled checkbox
                val newContent = content.take(startOffset) +
                        newCheckboxText +
                        content.substring(endOffset)
                onToggle(newContent)
            } else null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            enabled = isClickable
        )
    }
}

fun getVisibilityIcon(visibility: String): ImageVector {
    return when (visibility.uppercase()) {
        "PUBLIC" -> Icons.Default.Public
        "PROTECTED" -> Icons.Default.Group
        "PRIVATE" -> Icons.Default.Lock
        else -> Icons.Default.Lock
    }
}
