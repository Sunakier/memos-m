package org.example.memosm.ui.theme

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

@Stable
internal class PaperToneScrubState {
    var active by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    val index: Int get() = position.roundToInt()
}

/** Luori's default one-hand mapping: selected tab is the anchor, 160dp reach, sensitivity 1.35. */
private fun scrubPosition(selected: Int, down: Float, current: Float, extent: Float, last: Int, maxReach: Float): Float {
    if (last <= 0 || extent <= 0f) return 0f
    val origin = selected.coerceIn(0, last).toFloat()
    val delta = current - down
    val available = if (delta >= 0f) extent - down.coerceIn(0f, extent) else down.coerceIn(0f, extent)
    val reach = ((available * .7f).coerceAtMost(maxReach) / 1.35f).coerceAtLeast(1f)
    val distance = abs(delta)
    val span = if (delta >= 0f) last - origin else origin
    val bounded = origin + sign(delta) * span * (distance / reach).coerceIn(0f, 1f)
    val beyond = if (span == 0f) distance else (distance - reach).coerceAtLeast(0f)
    val step = (extent / (last + 1)).coerceAtLeast(1f)
    val position = bounded + sign(delta) * beyond * 1.35f / step
    val clamped = position.coerceIn(0f, last.toFloat())
    val excess = position - clamped
    return clamped + sign(excess) * .22f * (1f - exp(-abs(excess) / .22f))
}

/**
 * Ported from PaperWorld/shared/components/ScrubSelection.kt, restricted to this bottom bar.
 * Taps stay with child buttons. Hold/drag captures the gesture; cancel/multi-touch never commits.
 */
@Composable
internal fun Modifier.paperToneScrubSelection(
    state: PaperToneScrubState, count: Int, selectedIndex: Int, maxReach: Float,
    onPreview: (Int) -> Unit, onCommit: (Int) -> Unit, onCancel: () -> Unit
): Modifier {
    val selected by rememberUpdatedState(selectedIndex)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val haptics = LocalHapticFeedback.current
    return pointerInput(state, count, maxReach) {
        if (count < 2) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val extent = size.width.toFloat()
                if (extent <= 0f) return@awaitEachGesture
                val origin = selected.coerceIn(0, count - 1)
                var lostPointer = false
                val first = withTimeoutOrNull(180L) {
                    var change: PointerInputChange?
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) lostPointer = true
                        if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull change
                    } while (change != null && change.pressed && !change.isConsumed &&
                        (change.position - down.position).getDistance() < viewConfiguration.touchSlop)
                    change
                }
                if (lostPointer) return@awaitEachGesture
                if (first != null && (!first.pressed || first.isConsumed ||
                    abs(first.position.y - down.position.y) > abs(first.position.x - down.position.x))) return@awaitEachGesture
                if (currentEvent.changes.count { it.pressed } > 1) return@awaitEachGesture
                state.active = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                var previous = origin
                fun update(change: PointerInputChange) {
                    state.position = scrubPosition(origin, down.position.x, change.position.x, extent, count - 1, maxReach)
                    val next = state.index.coerceIn(0, count - 1)
                    if (next != previous) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    previous = next
                    preview(next)
                }
                update(first ?: down)
                first?.consume()
                var accepted = false
                var drain = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        event.changes.forEach { it.consume() }
                        drain = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    change.consume()
                    update(change)
                    if (!change.pressed) { accepted = true; break }
                }
                state.active = false
                if (accepted) commit(state.index.coerceIn(0, count - 1)) else cancel()
                if (drain) while (currentEvent.changes.any { it.pressed }) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        } finally {
            if (state.active) { state.active = false; cancel() }
        }
    }
}
