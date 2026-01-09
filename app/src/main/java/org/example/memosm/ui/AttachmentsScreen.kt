package org.example.memosm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import org.example.memosm.model.Attachment
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun AttachmentsScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Map to store aspect ratios as images load
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }

    // Use LocalConfiguration to get screen width stably
    val configuration = LocalConfiguration.current
    val maxWidthDp = configuration.screenWidthDp.dp - 16.dp

    // Group attachments into justified rows using derivedStateOf
    val justifiedRows by remember(uiState.attachments) {
        derivedStateOf {
            val rows = mutableListOf<List<Attachment>>()
            var currentRow = mutableListOf<Attachment>()
            var currentWidthFactor = 0f
            val maxRowWidthFactor = 2.2f

            uiState.attachments.forEach { attachment ->
                val ratio = aspectRatios[attachment.name] ?: 1.0f
                if (currentRow.isNotEmpty() && currentWidthFactor + ratio > maxRowWidthFactor) {
                    rows.add(currentRow)
                    currentRow = mutableListOf(attachment)
                    currentWidthFactor = ratio
                } else {
                    currentRow.add(attachment)
                    currentWidthFactor += ratio
                }
            }
            if (currentRow.isNotEmpty()) rows.add(currentRow)
            rows
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
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
    ) {
        if (uiState.attachments.isEmpty() && uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.attachments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attachments found")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(justifiedRows, key = { it.firstOrNull()?.name ?: "" }) { rowItems ->
                    val totalRatio =
                        rowItems.sumOf { (aspectRatios[it.name] ?: 1.0f).toDouble() }.toFloat()

                    // Calculate height that preserves ratio: Height = Width / TotalRatio
                    val spacingSum = 8.dp * (rowItems.size - 1)
                    val justifiedHeight =
                        ((maxWidthDp - spacingSum) / totalRatio).coerceIn(180.dp, 360.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(justifiedHeight),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { attachment ->
                            val ratio = aspectRatios[attachment.name] ?: 1.0f
                            Box(modifier = Modifier.weight(ratio)) {
                                AttachmentItem(
                                    attachment = attachment,
                                    token = uiState.token,
                                    onRatioAvailable = { newRatio ->
                                        if (aspectRatios[attachment.name] != newRatio) {
                                            aspectRatios[attachment.name] = newRatio
                                        }
                                    })
                            }
                        }
                    }
                }

                if (uiState.nextAttachmentsPageToken != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
    attachment: Attachment, token: String, onRatioAvailable: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            val isImage = remember(attachment.displayType) {
                attachment.displayType.startsWith(
                    "image/",
                    ignoreCase = true
                ) || attachment.displayType.contains("image", ignoreCase = true)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    val context = LocalContext.current
                    val imageRequest = remember(attachment.externalLink, token) {
                        ImageRequest.Builder(context).data(attachment.externalLink)
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
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Text(
                        text = attachment.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = attachment.size ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
