package org.example.memosm.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoItem(
    memo: Memo,
    user: User? = null,
    token: String,
    colors: CardColors = CardDefaults.cardColors(),
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    maxHeight: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
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
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = user.displayName ?: user.username ?: stringResource(R.string.memo_unknown_user),
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

                if (onEdit != null || onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true }, modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.memo_action_more),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (onEdit != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.memo_action_edit)) }, onClick = {
                                    showMenu = false
                                    onEdit()
                                }, leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit, contentDescription = null
                                    )
                                })
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.memo_action_delete)) }, onClick = {
                                    showMenu = false
                                    onDelete()
                                }, leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete, contentDescription = null
                                    )
                                }, colors = MenuDefaults.itemColors(
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

            Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (maxHeight != Dp.Unspecified) {
                                Modifier
                                    .heightIn(max = maxHeight)
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        if (size.height >= maxHeight.toPx() - 1.dp.toPx()) {
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    0.7f to Color.Black,
                                                    1.0f to Color.Transparent
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                    }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Markdown(
                        content = memo.content,
                        imageTransformer = Coil2ImageTransformerImpl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                memo.location?.let { loc ->
//                    Spacer(modifier = Modifier.height(6.dp))
                    val isClickable = loc.latitude != null && loc.longitude != null
                    Surface(
                        onClick = {
                            if (isClickable) {
                                val label = loc.placeholder ?: ""
                                val geoUri = "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}${if (label.isNotEmpty()) "(${Uri.encode(label)})" else ""}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
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
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        items(attachments, key = { it.externalLink ?: it.filename }) { attachment ->
                            val isImage = remember(attachment.displayType) {
                                attachment.displayType.startsWith(
                                    "image/", ignoreCase = true
                                ) || attachment.displayType.contains("image", ignoreCase = true)
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
                                        .size(width = 240.dp, height = 160.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Card(
                                    modifier = Modifier
                                        .size(width = 200.dp, height = 100.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
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
                    }
                }
            }
        }
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
