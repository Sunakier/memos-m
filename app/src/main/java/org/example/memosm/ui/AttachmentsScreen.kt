package org.example.memosm.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    val listState = rememberLazyStaggeredGridState()
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            if (totalItemsCount == 0 || uiState.isFetchingAttachments) return@derivedStateOf false
            
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            
            // Trigger load more when we are 5 items away from the end,
            // but only if there actually IS a next page.
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
    ) {
        if (uiState.attachments.isEmpty() && uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.attachments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attachments found")
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(240.dp),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.attachments, key = { it.name ?: it.filename }) { attachment ->
                    val key = attachment.name ?: attachment.filename
                    val ratio = aspectRatios[key] ?: 1.0f

                    AttachmentItem(
                        attachment = attachment,
                        token = uiState.token,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio),
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
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = attachment.size ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
