package org.example.memosm.ui.component.item

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val radiusDp = if (choices.size > 5) 82.dp else 56.dp
    val outerDp = radiusDp + 24.dp
    val paddingDp = 12.dp
    val gutterDp = 20.dp
    val centerXDp = paddingDp + gutterDp
    val centerYDp = paddingDp + outerDp
    val centerX = with(density) { centerXDp.toPx() }
    val centerY = with(density) { centerYDp.toPx() }
    val innerRadius = with(density) { (radiusDp - 18.dp).toPx() }
    val outerRadius = with(density) { (radiusDp + 22.dp).toPx() }
    val edgeTolerance = with(density) { 12.dp.toPx() }
    val armDistance = with(density) { 14.dp.toPx() }
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
    val outline = MaterialTheme.colorScheme.outlineVariant
    val panelShape = remember(density, outerDp) {
        GenericShape { size, _ ->
            val gutter = with(density) { gutterDp.toPx() }
            val radius = size.height / 2
            val corner = with(density) { 20.dp.toPx() }
            moveTo(gutter, 0f)
            cubicTo(gutter + radius * .5523f, 0f, size.width, radius * .4477f, size.width, radius)
            cubicTo(size.width, radius * 1.5523f, gutter + radius * .5523f, size.height, gutter, size.height)
            lineTo(corner, size.height)
            quadraticTo(0f, size.height, 0f, size.height - corner)
            lineTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
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
                        border = BorderStroke(.5.dp, outline.copy(alpha = .4f)),
                        shadowElevation = 8.dp, tonalElevation = 3.dp
                    ) {}
                    Surface(onClick = { holding = false; hover = null; ready = false },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.absoluteOffset(x = centerXDp - 18.dp, y = centerYDp - 18.dp).size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    choices.forEachIndexed { index, emoji ->
                        val angle = if (choices.size == 1) 0.0 else -PI / 2 + index * PI / (choices.size - 1)
                        val x = centerXDp + radiusDp * cos(angle).toFloat() - 18.dp
                        val y = centerYDp + radiusDp * sin(angle).toFloat() - 18.dp
                        val selected = hover == emoji
                        val scale by animateFloatAsState(if (selected) 1.15f else 1f,
                            animationSpec = spring(dampingRatio = .7f, stiffness = 650f), label = "reactionScale")
                        val color by animateColorAsState(if (selected) highlight else Color.Transparent,
                            label = "reactionHighlight")
                        Surface(onClick = {
                            holding = false; hover = null; ready = false
                            react(emoji)
                        }, modifier = Modifier.absoluteOffset(x = x, y = y).size(36.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                            shape = RoundedCornerShape(12.dp), color = color) {
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
