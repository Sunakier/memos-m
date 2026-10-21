package org.example.memosm.ui.component.item

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.example.memosm.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A short radial drag previews a reaction; returning to the center cancels it. */
@Composable
fun QuickReactionBar(options: List<String>, onReact: (String) -> Unit, onOpenPicker: () -> Unit) {
    val choices = options.distinct().take(8)
    val react by rememberUpdatedState(onReact)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val half = with(density) { 108.dp.toPx() }
    val innerRadius = with(density) { 48.dp.toPx() }
    val outerRadius = with(density) { 104.dp.toPx() }
    val armDistance = with(density) { 20.dp.toPx() }
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var holding by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var pressWindow by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<String?>(null) }
    var hoverSince by remember { mutableStateOf(0L) }
    var lastSent by remember { mutableStateOf(0L) }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val hint = stringResource(R.string.quick_edit_reaction_hint)
    val ringColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.primaryContainer

    Box(Modifier.size(48.dp).onGloballyPositioned { anchor = it }
        .semantics { contentDescription = hint }
        .pointerInput(choices, density) {
            var pointer = Offset.Zero
            var start = Offset.Zero
            var armed = false
            fun updateHover() {
                if ((pointer - start).getDistance() >= armDistance) armed = true
                val windowPoint = anchor?.takeIf { it.isAttached }?.localToWindow(pointer)
                val delta = windowPoint?.minus(Offset(origin.x + half, origin.y + half))
                val distance = delta?.getDistance() ?: 0f
                val next = if (!ready || !armed || choices.isEmpty() || delta == null ||
                    distance < innerRadius || distance > outerRadius) null else {
                    val step = 2 * PI / choices.size
                    val angle = (atan2(delta.y.toDouble(), delta.x.toDouble()) + PI / 2 + 2 * PI) % (2 * PI)
                    choices[((angle + step / 2) / step).toInt() % choices.size]
                }
                if (next != hover) {
                    hover = next
                    hoverSince = SystemClock.uptimeMillis()
                    if (next != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    pointer = it; start = it; armed = false; hover = null; ready = false
                    val point = anchor?.takeIf { coords -> coords.isAttached }?.localToWindow(it)
                    holding = choices.isNotEmpty() && point != null
                    if (holding) {
                        pressWindow = point!!
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, delta -> change.consume(); pointer += delta; updateHover() },
                onDragCancel = { holding = false; hover = null; ready = false },
                onDragEnd = {
                    updateHover()
                    val chosen = hover
                    val now = SystemClock.uptimeMillis()
                    holding = false; hover = null; ready = false
                    if (chosen != null && now - hoverSince >= 100 && now - lastSent >= 400) {
                        lastSent = now
                        react(chosen)
                    }
                })
        }) {
        IconButton(onClick = { if (!holding) onOpenPicker() }) {
            Icon(Icons.Outlined.AddReaction,
                contentDescription = stringResource(R.string.memo_action_add_reaction),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (holding) {
            Popup(popupPositionProvider = remember(pressWindow, margin) {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        fun clamp(value: Int, available: Int, length: Int): Int {
                            val max = (available - length - margin).coerceAtLeast(0)
                            return value.coerceIn(margin.coerceAtMost(max), max)
                        }
                        return IntOffset(
                            clamp((pressWindow.x - popupContentSize.width / 2).roundToInt(), windowSize.width, popupContentSize.width),
                            clamp((pressWindow.y - popupContentSize.height / 2).roundToInt(), windowSize.height, popupContentSize.height)
                        ).also { origin = it }
                    }
                }
            }) {
                Box(Modifier.size(216.dp).onGloballyPositioned { ready = true }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(204.dp)) {
                        val radius = 76.dp.toPx()
                        val stroke = Stroke(width = 52.dp.toPx())
                        drawCircle(ringColor, radius = radius, style = stroke)
                        val index = choices.indexOf(hover)
                        if (index >= 0) {
                            val sweep = 360f / choices.size
                            val inset = (size.width - radius * 2) / 2
                            drawArc(highlight, startAngle = -90f + index * sweep - sweep / 2,
                                sweepAngle = sweep - 3f, useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = stroke)
                        }
                    }
                    Surface(shape = CircleShape, color = ringColor, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    choices.forEachIndexed { index, emoji ->
                        val angle = -PI / 2 + index * 2 * PI / choices.size
                        val x = (cos(angle) * 76).toFloat().dp
                        val y = (sin(angle) * 76).toFloat().dp
                        Surface(modifier = Modifier.absoluteOffset(x = x, y = y).size(44.dp),
                            shape = CircleShape,
                            color = if (hover == emoji) highlight else ringColor,
                            tonalElevation = if (hover == emoji) 4.dp else 0.dp) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
