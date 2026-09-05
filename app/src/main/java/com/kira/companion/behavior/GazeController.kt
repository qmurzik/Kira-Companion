package com.kira.companion.behavior

import kotlin.random.Random

/** Where the eyes and head should currently be pointed. */
data class GazePose(val eyeAngles: GazeAngles, val headAngles: GazeAngles)

/**
 * Drives Kira's gaze: idle wandering between small look-around targets when nothing else
 * is going on ("look forward, sometimes glance slightly right, then back"), always blended
 * smoothly between targets (see [GazeMath.lerp]) rather than snapping, with support for a
 * temporary explicit look-at override (e.g. "look at where the user just touched") that
 * takes priority for a while before idle wandering resumes. The head follows the eyes only
 * partially, since a real glance is mostly in the eyes.
 */
class GazeController(
    private val random: Random = Random.Default,
    private val minHoldMillis: Long = 1500L,
    private val maxHoldMillis: Long = 4000L,
    private val blendMillis: Long = 400L,
) {
    private var fromAngles = GazeAngles.FORWARD
    private var toAngles = GazeAngles.FORWARD
    private var blendElapsedMillis = blendMillis
    private var holdRemainingMillis = randomHold()
    private var overrideRemainingMillis = 0L

    val current: GazePose
        get() {
            val t = (blendElapsedMillis.toFloat() / blendMillis).coerceIn(0f, 1f)
            val eyeAngles = GazeMath.lerp(fromAngles, toAngles, t)
            val headAngles = GazeMath.lerp(GazeAngles.FORWARD, eyeAngles, HEAD_FOLLOW_FRACTION)
            return GazePose(eyeAngles = eyeAngles, headAngles = headAngles)
        }

    /** Look at a specific normalized target (e.g. where the user tapped) for a while, then resume idle wandering. */
    fun lookAt(dx: Float, dy: Float, durationMillis: Long = 1200L) {
        fromAngles = current.eyeAngles
        toAngles = GazeMath.eyeAnglesTo(dx, dy)
        blendElapsedMillis = 0L
        overrideRemainingMillis = durationMillis
    }

    fun update(deltaMillis: Long) {
        blendElapsedMillis += deltaMillis

        if (overrideRemainingMillis > 0L) {
            overrideRemainingMillis -= deltaMillis
            return
        }

        holdRemainingMillis -= deltaMillis
        if (holdRemainingMillis <= 0L) {
            val (dx, dy) = GazeMath.idleWanderOffsets.random(random)
            fromAngles = current.eyeAngles
            toAngles = GazeMath.eyeAnglesTo(dx, dy)
            blendElapsedMillis = 0L
            holdRemainingMillis = randomHold()
        }
    }

    private fun randomHold() = if (minHoldMillis >= maxHoldMillis) {
        minHoldMillis
    } else {
        random.nextLong(minHoldMillis, maxHoldMillis)
    }

    private companion object {
        const val HEAD_FOLLOW_FRACTION = 0.4f
    }
}
