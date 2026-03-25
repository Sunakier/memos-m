package org.example.memosm.ui.component.item.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.model.Attachment
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import kotlin.math.abs

@Composable
fun FullScreenAttachmentViewer(
    attachments: List<Attachment>,
    initialIndex: Int,
    token: String?,
    hostUrl: String,
    onDismiss: () -> Unit,
    onPageChanged: ((Int) -> Unit)? = null
) {
    if (attachments.isEmpty() || initialIndex < 0 || initialIndex >= attachments.size) {
        return
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
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

        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { attachments.size })
        val coroutineScope = rememberCoroutineScope()

        // Vertical drag to dismiss
        val dismissOffset = remember { Animatable(0f) }
        val dismissDragProgress = (abs(dismissOffset.value) / 300f).coerceIn(0f, 1f)
        val backgroundAlpha = (1f - dismissDragProgress).coerceIn(0f, 1f)
        val dismissScale = (1f - (abs(dismissOffset.value) / 1000f)).coerceIn(0.6f, 1f)

        LaunchedEffect(pagerState.currentPage) {
            onPageChanged?.invoke(pagerState.currentPage)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (abs(dismissOffset.value) > 200f) {
                                        onDismiss()
                                    } else {
                                        dismissOffset.animateTo(0f)
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dismissOffset.snapTo(dismissOffset.value + dragAmount)
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = dismissScale
                        scaleY = dismissScale
                        translationY = dismissOffset.value
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val attachment = attachments[page]

                    // The AttachmentCard should handle its own zooming internally if isFullScreen=true.
                    // The swipe up/down gesture is captured by the parent box above,
                    // unless the child consumes it (which it shouldn't if we just want swipe to dismiss).
                    AttachmentCard(
                        attachment = attachment,
                        token = token,
                        hostUrl = hostUrl,
                        modifier = Modifier.fillMaxSize(),
                        showInfo = false,
                        showActions = false,
                        showSize = false,
                        showFilename = false,
                        compactMode = AttachmentCompactMode.Never,
                        isFullScreen = true,
                        onDismiss = onDismiss
                    )
                }
            }

            // Close Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
                    .graphicsLayer { alpha = backgroundAlpha }) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
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
