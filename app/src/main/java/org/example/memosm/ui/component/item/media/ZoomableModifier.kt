package org.example.memosm.ui.component.item.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.zoomable(
    enabled: Boolean,
    onDismiss: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                do {
                    val event = awaitPointerEvent()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()

                    scale = (scale * zoom).coerceIn(0.5f, 5f)

                    if (scale < 0.8f) {
                        onDismiss?.invoke()
                        scale = 1f
                        offset = Offset.Zero
                    } else if (scale > 1f) {
                        offset += pan
                        // Consume the gesture so parent pagers/scrolls don't intercept it
                        event.changes.forEach { it.consume() }
                    } else {
                        offset = Offset.Zero
                        // Do not consume the gesture if scale <= 1f, allowing HorizontalPager to swipe
                    }

                } while (event.changes.any { it.pressed })

                // Reset scale and offset on release if scale is less than 1f
                if (scale < 1f) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }
        }
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
        )
}
