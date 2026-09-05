package com.kira.companion.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.getDistance
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Combines tap, long-press and free-drag detection into a single pointer input handler.
 * We need all three at once on Kira's floating bubble (tap = quick reaction, long-press =
 * open menu, drag = move her around), which the individual Compose Foundation gesture
 * detectors don't support together out of the box.
 */
fun Modifier.kiraOverlayGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectKiraGestures(onTap, onLongPress, onDrag, onDragEnd)
}

private suspend fun PointerInputScope.detectKiraGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var phase = "pending"

        val notTimedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.changedToUpIgnoreConsumed()) {
                    phase = "tap"
                    return@withTimeoutOrNull Unit
                }
                val moved = change.position - down.position
                if (moved.getDistance() > viewConfiguration.touchSlop) {
                    phase = "drag"
                    return@withTimeoutOrNull Unit
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Unit
        }

        if (notTimedOut == null) {
            // Long-press timeout elapsed while the finger stayed down and (roughly) still.
            onLongPress()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (change.changedToUpIgnoreConsumed()) break
            }
            return@awaitEachGesture
        }

        when (phase) {
            "tap" -> onTap()
            "drag" -> {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed()) {
                        onDragEnd()
                        break
                    }
                    val delta = change.positionChange()
                    change.consume()
                    onDrag(delta.x, delta.y)
                }
            }
        }
    }
}
