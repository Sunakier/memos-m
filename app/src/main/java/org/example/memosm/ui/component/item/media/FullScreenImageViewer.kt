package org.example.memosm.ui.component.item.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.example.memosm.R

@Composable
fun FullScreenImageViewer(
    model: Any,
    filename: String,
    token: String?,
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
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

                                            val scaleFactor = minOf(
                                                viewWidth / imageWidth,
                                                viewHeight / imageHeight
                                            )
                                            val fitWidth = imageWidth * scaleFactor
                                            val fitHeight = imageHeight * scaleFactor

                                            val scaledWidth = fitWidth * newScale
                                            val scaledHeight = fitHeight * newScale

                                            val maxX = maxOf(0f, (scaledWidth - viewWidth) / 2f)
                                            val maxY = maxOf(0f, (scaledHeight - viewHeight) / 2f)

                                            val targetOffset = if (newScale > 1f) {
                                                (offset.value + pan).let {
                                                    Offset(
                                                        it.x.coerceIn(-maxX, maxX),
                                                        it.y.coerceIn(-maxY, maxY)
                                                    )
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
                                            scale.animateTo(
                                                1f,
                                                spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                        launch {
                                            offset.animateTo(
                                                Offset.Zero,
                                                spring(stiffness = Spring.StiffnessMediumLow)
                                            )
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
//                    val headers =
//                        NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()
                    var headersBuilder = NetworkHeaders.Builder()
                    if (token != null) headersBuilder =
                        headersBuilder.set("Authorization", "Bearer $token")
                    val headers = headersBuilder.build()

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

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
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
}
