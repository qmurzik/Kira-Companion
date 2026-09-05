package com.kira.companion.behavior

import kotlin.math.atan2

/** A look direction expressed as yaw (left/right, positive = right) and pitch (up/down,
 *  positive = up), in degrees, relative to looking straight ahead. */
data class GazeAngles(val yawDegrees: Float, val pitchDegrees: Float) {
    companion object {
        val FORWARD = GazeAngles(0f, 0f)
    }
}

/**
 * Pure trigonometry for where Kira's eyes/head should point - no Android or rendering
 * dependency, so it's directly unit-testable. Eyes and head have separate rotation limits
 * so a glance to the side moves mostly the eyes while a full look-at also turns the head,
 * and gaze blending uses [lerp] so a look-target change is never an instant snap.
 */
object GazeMath {

    private const val EYE_YAW_LIMIT_DEGREES = 30f
    private const val EYE_PITCH_LIMIT_DEGREES = 20f
    private const val HEAD_YAW_LIMIT_DEGREES = 35f
    private const val HEAD_PITCH_LIMIT_DEGREES = 20f

    /**
     * Small look-around offsets used for idle gaze wandering ("look forward, sometimes
     * glance slightly right/left/up/down, then back") - expressed as normalized
     * (dx, dy) target offsets consumed by [eyeAnglesTo]/[headAnglesTo].
     */
    val idleWanderOffsets: List<Pair<Float, Float>> = listOf(
        0f to 0f,
        0.4f to 0f,
        -0.35f to 0f,
        0f to 0.3f,
        0f to -0.2f,
        0.25f to 0.15f,
    )

    /**
     * Computes yaw/pitch (degrees) to look at a target given as normalized offsets from
     * Kira's eye/head origin: [dx]/[dy] in roughly [-1, 1] (right/up positive), and [dz]
     * the forward distance to the target (must be positive; a target "behind" her is
     * treated as straight ahead at the limit rather than spinning around).
     */
    private fun anglesTo(dx: Float, dy: Float, dz: Float, yawLimit: Float, pitchLimit: Float): GazeAngles {
        val safeDz = if (dz <= 0f) 0.01f else dz
        val yaw = Math.toDegrees(atan2(dx.toDouble(), safeDz.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2(dy.toDouble(), safeDz.toDouble())).toFloat()
        return GazeAngles(
            yawDegrees = yaw.coerceIn(-yawLimit, yawLimit),
            pitchDegrees = pitch.coerceIn(-pitchLimit, pitchLimit),
        )
    }

    /** Where the eyes should point to look at a target at normalized offset (dx, dy, dz). */
    fun eyeAnglesTo(dx: Float, dy: Float, dz: Float = 1f): GazeAngles =
        anglesTo(dx, dy, dz, EYE_YAW_LIMIT_DEGREES, EYE_PITCH_LIMIT_DEGREES)

    /** Where the head should turn to (partially) face a target at normalized offset (dx, dy, dz). */
    fun headAnglesTo(dx: Float, dy: Float, dz: Float = 1f): GazeAngles =
        anglesTo(dx, dy, dz, HEAD_YAW_LIMIT_DEGREES, HEAD_PITCH_LIMIT_DEGREES)

    /** Smoothly blends between two gaze targets; [t] in [0, 1], 0 = [from], 1 = [to]. */
    fun lerp(from: GazeAngles, to: GazeAngles, t: Float): GazeAngles {
        val clampedT = t.coerceIn(0f, 1f)
        return GazeAngles(
            yawDegrees = from.yawDegrees + (to.yawDegrees - from.yawDegrees) * clampedT,
            pitchDegrees = from.pitchDegrees + (to.pitchDegrees - from.pitchDegrees) * clampedT,
        )
    }
}
