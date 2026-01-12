package org.example.memosm.ui.components.item.media

import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import org.example.memosm.R
import org.example.memosm.model.Attachment

@Composable
fun MemoImage(
    modifier: Modifier = Modifier,
    attachment: Attachment?,
    token: String?,
    uri: Uri = Uri.EMPTY,
    filename: String,
    onRatioAvailable: (Float) -> Unit = {},
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
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

    val cacheKey = remember(uri, attachment) {
        when {
            uri != Uri.EMPTY -> uri.toString()
            !attachment?.externalLink.isNullOrBlank() -> attachment.externalLink
            attachment?.name != null -> attachment.name
            else -> null
        }
    }

    // Use cached ratio if available
    LaunchedEffect(cacheKey) {
        MediaCache.getAspectRatio(cacheKey)?.let {
            onRatioAvailable(it)
        }
    }

    val headers = remember(token) {
        val builder = NetworkHeaders.Builder()
        if (token != null) builder.set("Authorization", "Bearer $token")
        builder.build()
    }

    val imageRequest = remember(model, headers) {
        ImageRequest.Builder(context)
            .data(model)
            .httpHeaders(headers)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = filename,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
            contentScale = ContentScale.Crop,
            onLoading = { isLoading = true; isError = false },
            onSuccess = { state ->
                isLoading = false
                isError = false
                val size = state.painter.intrinsicSize
                if (size.width > 0 && size.height > 0) {
                    val ratio = size.width / size.height
                    MediaCache.setAspectRatio(cacheKey, ratio)
                    onRatioAvailable(ratio)
                }
            },
            onError = { isLoading = false; isError = true }
        )

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
    }
}
