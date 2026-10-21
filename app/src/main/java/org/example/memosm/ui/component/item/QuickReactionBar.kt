package org.example.memosm.ui.component.item

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.Rect
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
    val radiusDp = if (choices.size > 5) 104.dp else 80.dp
    val outerDp = radiusDp + 28.dp
    val paddingDp = 12.dp
    val gutterDp = 28.dp
    val centerXDp = paddingDp + gutterDp
    val centerYDp = paddingDp + outerDp
    val centerX = with(density) { centerXDp.toPx() }
    val centerY = with(density) { centerYDp.toPx() }
    val innerRadius = with(density) { (radiusDp - 26.dp).toPx() }
    val outerRadius = with(density) { (radiusDp + 26.dp).toPx() }
    val edgeTolerance = with(density) { 12.dp.toPx() }
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
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    val ringColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val panelShape = remember(density, outerDp) {
        GenericShape { size, _ ->
            val gutter = with(density) { gutterDp.toPx() }
            val radius = size.height / 2
            moveTo(gutter, 0f)
            arcTo(Rect(gutter - radius, 0f, gutter + radius, size.height), -90f, 180f, false)
            lineTo(0f, size.height)
            lineTo(0f, 0f)
            close()
        }
    }
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
                val delta = windowPoint?.minus(Offset(origin.x + centerX, origin.y + centerY))
                val distance = delta?.getDistance() ?: 0f
                val next = if (!ready || !armed || choices.isEmpty() || delta == null ||
                    distance < innerRadius || distance > outerRadius || delta.x < -edgeTolerance) null else {
                    val angle = atan2(delta.y.toDouble(), delta.x.toDouble()).coerceIn(-PI / 2, PI / 2)
                    val index = if (choices.size == 1) 0 else
                        ((angle + PI / 2) / (PI / (choices.size - 1))).roundToInt()
                    choices[index.coerceIn(choices.indices)]
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
            Popup(popupPositionProvider = remember(pressWindow, margin, centerX, centerY) {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        fun clamp(value: Int, available: Int, length: Int): Int {
                            val max = (available - length - margin).coerceAtLeast(0)
                            return value.coerceIn(margin.coerceAtMost(max), max)
                        }
                        return IntOffset(
                            clamp((pressWindow.x - centerX).roundToInt(), windowSize.width, popupContentSize.width),
                            clamp((pressWindow.y - centerY).roundToInt(), windowSize.height, popupContentSize.height)
                        ).also { origin = it }
                    }
                }
            }) {
                Box(Modifier.size(width = centerXDp + outerDp + paddingDp,
                    height = (outerDp + paddingDp) * 2).onGloballyPositioned { ready = true }) {
                    Surface(
                        modifier = Modifier.absoluteOffset(x = paddingDp, y = paddingDp)
                            .size(width = gutterDp + outerDp, height = outerDp * 2),
                        shape = panelShape, color = panelColor,
                        border = BorderStroke(1.dp, outline),
                        shadowElevation = 12.dp, tonalElevation = 4.dp
                    ) {
                        Canvas(Modifier.size(width = gutterDp + outerDp, height = outerDp * 2)) {
                            val index = choices.indexOf(hover)
                            if (index >= 0) {
                                val radius = radiusDp.toPx()
                                val step = if (choices.size == 1) 180f else 180f / (choices.size - 1)
                                val angle = if (choices.size == 1) 0f else -90f + index * step
                                val start = (angle - step / 2).coerceAtLeast(-90f)
                                val end = (angle + step / 2).coerceAtMost(90f)
                                drawArc(highlight, startAngle = start, sweepAngle = end - start,
                                    useCenter = false,
                                    topLeft = Offset(gutterDp.toPx() - radius, outerDp.toPx() - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = Stroke(width = 50.dp.toPx()))
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = ringColor,
                        border = BorderStroke(1.dp, outline),
                        modifier = Modifier.absoluteOffset(x = centerXDp - 24.dp, y = centerYDp - 24.dp).size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    choices.forEachIndexed { index, emoji ->
                        val angle = if (choices.size == 1) 0.0 else -PI / 2 + index * PI / (choices.size - 1)
                        val x = centerXDp + radiusDp * cos(angle).toFloat() - 22.dp
                        val y = centerYDp + radiusDp * sin(angle).toFloat() - 22.dp
                        Surface(modifier = Modifier.absoluteOffset(x = x, y = y).size(44.dp),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, if (hover == emoji) primary else outline),
                            color = if (hover == emoji) highlight else ringColor,
                            shadowElevation = if (hover == emoji) 4.dp else 1.dp) {
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
