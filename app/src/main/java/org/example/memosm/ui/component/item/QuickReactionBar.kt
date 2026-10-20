package org.example.memosm.ui.component.item

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

/** A long press previews; only a deliberate release over a target sends a reaction. */
@Composable
fun QuickReactionBar(options: List<String>, selected: Set<String>, onReact: (String) -> Unit) {
    val choices = options.distinct().take(8)
    if (choices.isEmpty()) return
    val react by rememberUpdatedState(onReact)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var holding by remember { mutableStateOf(false) }
    var hover by remember { mutableStateOf<String?>(null) }
    var hoverSince by remember { mutableStateOf(0L) }
    var lastSent by remember { mutableStateOf(0L) }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val targets = remember(choices) { mutableMapOf<String, Rect>() }
    val hint = stringResource(R.string.quick_edit_reaction_hint)
    val send: (String) -> Unit = { emoji ->
        val now = SystemClock.uptimeMillis()
        if (now - lastSent >= 400) { lastSent = now; react(emoji) }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { anchor = it }
        .semantics { contentDescription = hint }
        .pointerInput(choices) {
            var pointer = Offset.Zero
            fun updateHover() {
                val windowPoint = anchor?.takeIf { it.isAttached }?.localToWindow(pointer)
                val local = windowPoint?.minus(Offset(origin.x.toFloat(), origin.y.toFloat()))
                val next = if (local == null) null else targets.entries.firstOrNull { it.value.contains(local) }?.key
                if (next != hover) {
                    hover = next
                    hoverSince = SystemClock.uptimeMillis()
                    if (next != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { pointer = it; hover = null; targets.clear(); holding = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDrag = { change, delta -> change.consume(); pointer += delta; updateHover() },
                onDragCancel = { holding = false; hover = null; targets.clear() },
                onDragEnd = {
                    updateHover()
                    val chosen = hover
                    holding = false; hover = null; targets.clear()
                    if (chosen != null && SystemClock.uptimeMillis() - hoverSince >= 100) send(chosen)
                })
        }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { emoji ->
                Surface(onClick = { if (!holding) send(emoji) },
                    modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.small,
                    color = if (emoji in selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji) }
                }
            }
        }
        if (holding) {
            Popup(popupPositionProvider = remember(gap) {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val above = anchorBounds.top - popupContentSize.height - gap
                        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom + gap).also { origin = it }
                    }
                }
            }) {
                Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    androidx.compose.foundation.layout.Column(Modifier.width(288.dp).padding(8.dp)) {
                        Text(hint, style = MaterialTheme.typography.labelSmall)
                        choices.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { emoji ->
                                    Surface(modifier = Modifier.size(64.dp).onGloballyPositioned {
                                        val pos = it.positionInRoot()
                                        targets[emoji] = Rect(pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height)
                                    }, shape = MaterialTheme.shapes.small,
                                        color = if (hover == emoji) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
