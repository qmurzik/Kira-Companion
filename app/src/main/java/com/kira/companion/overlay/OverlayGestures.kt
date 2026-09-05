package com.kira.companion.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Combines tap, long-press and free-drag detection on Kira's floating bubble (tap = quick
 * reaction, long-press = open menu, drag = move her around). Two independent pointer input
 * handlers are layered on the same modifier chain: [detectTapGestures] recognizes the tap/
 * long-press pair, while [detectDragGestures] independently recognizes drags past the touch
 * slop. Both are standard Compose Foundation gesture detectors used together this way in many
 * real drag-and-drop / floating-bubble implementations.
 */
fun Modifier.kiraOverlayGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { onLongPress() },
        )
    }
    .pointerInput(Unit) {
        detectDragGestures(
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            },
            onDragEnd = { onDragEnd() },
        )
    }
