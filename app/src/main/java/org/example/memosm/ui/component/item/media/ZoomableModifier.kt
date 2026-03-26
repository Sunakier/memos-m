package org.example.memosm.ui.component.item.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

fun Modifier.zoomable(
    enabled: Boolean,
    onDismiss: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this

    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                do {
                    val event = awaitPointerEvent()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()

                    coroutineScope.launch {
                        val newScale = (scale.value * zoom).coerceIn(0.5f, 5f)
                        scale.snapTo(newScale)

                        if (newScale > 1f) {
                            offset.snapTo(offset.value + pan)
                        } else {
                            offset.snapTo(Offset.Zero)
                        }
                    }

                    if (scale.value > 1f) {
                        event.changes.forEach { it.consume() }
                    }

                } while (event.changes.any { it.pressed })

                // Gesture ended (all pointers up)
                coroutineScope.launch {
                    if (scale.value < 0.8f) {
                        onDismiss?.invoke()
                        // Don't snap immediately so it looks smoother as it closes
                        scale.animateTo(1f)
                        offset.animateTo(Offset.Zero)
                    } else if (scale.value < 1f) {
                        // Bounced back if they didn't pinch enough
                        scale.animateTo(1f)
                        offset.animateTo(Offset.Zero)
                    }
                }
            }
        }
        .graphicsLayer(
            scaleX = scale.value,
            scaleY = scale.value,
            translationX = offset.value.x,
            translationY = offset.value.y
        )
}
