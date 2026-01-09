package org.example.memosm.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import org.example.memosm.model.Attachment
import org.example.memosm.viewmodel.MemosViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AttachmentsScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyStaggeredGridState()
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }
    
    // Limits for zooming
    val minCellWidth = 120.dp
    val maxCellWidth = 600.dp

    // Animate the cell width changes for a smoother transition
    val animatedCellWidth by animateDpAsState(
        targetValue = uiState.attachmentCellWidth.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CellWidthAnimation"
    )

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            if (totalItemsCount == 0 || uiState.isFetchingAttachments) return@derivedStateOf false

            val lastVisibleItem =
                listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false

            uiState.nextAttachmentsPageToken != null &&
                    !uiState.nextAttachmentsPageToken.isNullOrBlank() &&
                    lastVisibleItem.index >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.fetchAttachments(loadMore = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // Detect pinch-to-zoom gestures globally using the Initial pass
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedChanges = event.changes.filter { it.pressed }
                        
                        if (pressedChanges.size >= 2) {
                            val p1 = pressedChanges[0].position
                            val p2 = pressedChanges[1].position
                            val p1Prev = pressedChanges[0].previousPosition
                            val p2Prev = pressedChanges[1].previousPosition
                            
                            val currentDistance = (p1 - p2).getDistance()
                            val previousDistance = (p1Prev - p2Prev).getDistance()
                            
                            if (previousDistance > 0f && currentDistance > 0f) {
                                val zoomFactor = currentDistance / previousDistance
                                if (zoomFactor != 1f) {
                                    val newWidth = (uiState.attachmentCellWidth * zoomFactor).coerceIn(
                                        minCellWidth.value, 
                                        maxCellWidth.value
                                    )
                                    viewModel.updateAttachmentCellWidth(newWidth)
                                    // Consume the event to prevent the list from scrolling while zooming
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (uiState.attachments.isEmpty() && uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.attachments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attachments found")
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(animatedCellWidth),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.attachments, 
                    key = { it.name ?: it.filename }
                ) { attachment ->
                    val key = attachment.name ?: attachment.filename
                    val ratio = aspectRatios[key] ?: 1.0f

                    AttachmentItem(
                        attachment = attachment,
                        token = uiState.token,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .animateItem(),
                        onRatioAvailable = { newRatio ->
                            aspectRatios[key] = newRatio
                        })
                }

                if (!uiState.nextAttachmentsPageToken.isNullOrBlank()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentItem(
    attachment: Attachment,
    token: String,
    modifier: Modifier = Modifier,
    onRatioAvailable: (Float) -> Unit
) {
    val context = LocalContext.current

    val formattedDate = remember(attachment.createTime) {
        try {
            // "2024-03-20T10:00:00Z"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(attachment.createTime ?: "")
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val formattedSize = remember(attachment.size) {
        val bytes = attachment.size?.toLongOrNull() ?: return@remember attachment.size ?: ""
        Formatter.formatFileSize(context, bytes)
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = {
            attachment.externalLink?.let { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                context.startActivity(intent)
            }
        }
    ) {
        Column {
            val displayType = attachment.displayType
            val isImage = remember(displayType) {
                displayType.startsWith(
                    "image/", ignoreCase = true
                ) || displayType.contains("image", ignoreCase = true)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    val externalLink = attachment.externalLink
                    val imageRequest = remember(externalLink, token) {
                        ImageRequest.Builder(context).data(externalLink)
                            .addHeader("Authorization", "Bearer $token").crossfade(true).size(800)
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
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Text(
                        text = attachment.filename,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                val filename = attachment.filename
                val lastDotIndex = filename.lastIndexOf('.')

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (lastDotIndex != -1 && lastDotIndex > 0) {
                        val namePart = filename.substring(0, lastDotIndex)
                        val extensionPart = filename.substring(lastDotIndex)
                        Text(
                            text = namePart,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = extensionPart,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = filename,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
