package org.example.memosm.ui.component.item

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import com.mikepenz.markdown.model.rememberMarkdownState
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.model.Reaction
import org.example.memosm.model.User
import org.example.memosm.ui.component.item.markdown.MemoMarkdown
import org.example.memosm.ui.component.resolveResourceUrl
import org.example.memosm.ui.getVisibilityIcon
import org.example.memosm.ui.getVisibilityLabel
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
    hostUrl: String = "",
    colors: CardColors = CardDefaults.cardColors(),
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onPin: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUpsertReaction: ((String) -> Unit)? = null,
    onDeleteReaction: ((Reaction) -> Unit)? = null,
    onContentUpdate: ((String) -> Unit)? = null,
    maxHeight: Dp = Dp.Unspecified,
    isDetailView: Boolean = false,
    reactionOptions: List<String> = emptyList(),
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showRawTextDialog by remember { mutableStateOf(false) }
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
                        val avatarUrl = remember(user.avatarUrl, hostUrl) {
                            resolveResourceUrl(hostUrl, user.avatarUrl)
                        }
                        if (avatarUrl != null) {
                            val imageRequest = remember(avatarUrl, token) {
                                val headers = coil3.network.NetworkHeaders.Builder().apply {
                                    if (token.isNotEmpty()) {
                                        set("Authorization", "Bearer $token")
                                    }
                                }.build()

                                coil3.request.ImageRequest.Builder(context).data(avatarUrl)
                                    .httpHeaders(headers).build()
                            }
                            AsyncImage(
                                model = imageRequest,
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
                                if (memo.pinned == true) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = stringResource(R.string.profile_stats_pinned),
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Icon(
                                    imageVector = getVisibilityIcon(memo.visibility),
                                    contentDescription = getVisibilityLabel(memo.visibility),
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
                            if (memo.pinned == true) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = stringResource(R.string.profile_stats_pinned),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Icon(
                                imageVector = getVisibilityIcon(memo.visibility),
                                contentDescription = getVisibilityLabel(memo.visibility),
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
                            if (memo.name != null && hostUrl.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_open_web)) },
                                    onClick = {
                                        showMenu = false
                                        val memoId = memo.name.removePrefix("memos/")
                                        val baseUrl =
                                            if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
                                        val webUrl = "${baseUrl}memos/$memoId"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, webUrl.toUri())
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e(
                                                "MemoItem", "Failed to open web URL: $webUrl", e
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Language, contentDescription = null)
                                    })
                            }

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.memo_action_show_raw)) },
                                onClick = {
                                    showMenu = false
                                    showRawTextDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Description, contentDescription = null)
                                })


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
                            if (onPin != null && memo.state == "NORMAL") {
                                val isPinned = memo.pinned == true
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (isPinned) R.string.memo_action_unpin else R.string.memo_action_pin)) },
                                    onClick = {
                                        showMenu = false
                                        onPin(!isPinned)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.PushPin, contentDescription = null
                                        )
                                    })
                            }
                            if (onArchive != null && memo.state == "NORMAL") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_archive)) },
                                    onClick = {
                                        showMenu = false
                                        onArchive()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Archive, contentDescription = null
                                        )
                                    })
                            }
                            if (onUnarchive != null && memo.state == "ARCHIVED") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_unarchive)) },
                                    onClick = {
                                        showMenu = false
                                        onUnarchive()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Unarchive, contentDescription = null
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

            Column(modifier = Modifier.padding(start = 0.dp, end = 0.dp)) {
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
                    MemoMarkdown(
                        content = memo.content,
                        markdownState = markdownState,
                        onContentUpdate = onContentUpdate,
                        token = token,
                        hostUrl = hostUrl,
                        selectable = isDetailView,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, bottom = 8.dp)
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
                                imageVector = Icons.Outlined.Place,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        ) {
                            attachments.forEach { attachment ->
                                var aspectRatio by remember(
                                    attachment.name ?: attachment.filename
                                ) {
                                    mutableFloatStateOf(16f / 9f)
                                }
                                AttachmentCard(
                                    attachment = attachment,
                                    token = token,
                                    hostUrl = hostUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatio),
                                    compactMode = AttachmentCompactMode.Never,
                                    onRatioAvailable = { aspectRatio = it })
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
                                            ), blendMode = BlendMode.DstIn
                                        )
                                    }
                                }) {
                            items(
                                attachments,
                                key = { "${it.externalLink ?: "link"}_${it.filename}_${it.createTime ?: 0}" }) { attachment ->
                                AttachmentCard(
                                    attachment = attachment,
                                    token = token,
                                    hostUrl = hostUrl,
                                    modifier = Modifier.size(width = 240.dp, height = 160.dp),
                                    showInfo = false,
                                    compactMode = AttachmentCompactMode.Area
                                )
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
                                        onDeleteReaction?.invoke(myReaction)
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
            reactionOptions = reactionOptions,
            onDismiss = { showReactionPicker = false },
            onReactionSelected = { emoji ->
                onUpsertReaction?.invoke(emoji)
                showReactionPicker = false
            })
    }

    if (showRawTextDialog) {
        AlertDialog(
            onDismissRequest = { showRawTextDialog = false },
            title = { Text(stringResource(R.string.memo_dialog_raw_title)) },
            text = {
                SelectionContainer {
                    Text(
                        text = memo.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawTextDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerDialog(
    reactionOptions: List<String>, onDismiss: () -> Unit, onReactionSelected: (String) -> Unit
) {
    val commonEmojis = reactionOptions.ifEmpty {
        listOf(
            "\uD83D\uDE2D"
        )
    }

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
