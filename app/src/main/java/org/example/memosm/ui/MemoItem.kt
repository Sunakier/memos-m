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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val formattedTime = remember(memo.displayTime) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(memo.displayTime ?: "")
            date?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } ?: memo.displayTime ?: "UNKNOWN"
        } catch (_: Exception) {
            memo.displayTime ?: "UNKNOWN"
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
                                contentDescription = null,
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
                                text = user.displayName ?: user.username ?: "Unknown",
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
                                contentDescription = "More",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (onEdit != null) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = {
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
                                    text = { Text("Delete") }, onClick = {
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

            Column(modifier = Modifier.padding(start = 4.dp, end = 12.dp)) {
                Text(text = memo.content, style = MaterialTheme.typography.bodyLarge)

                memo.location?.let { loc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isClickable = loc.latitude != null && loc.longitude != null
                    Surface(
                        onClick = {
                            if (isClickable) {
                                val uri = "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=17/${loc.latitude}/${loc.longitude}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                context.startActivity(intent)
                            }
                        },
                        enabled = isClickable,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
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
                                style = MaterialTheme.typography.labelSmall,
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
