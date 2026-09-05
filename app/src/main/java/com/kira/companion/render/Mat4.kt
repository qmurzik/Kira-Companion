package com.kira.companion.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal column-major 4x4 matrix math for composing bone rotations on top of a VRM rig's
 * bind pose. Pure Kotlin, no Filament dependency, so it's directly unit-testable - only
 * [VrmRig] feeds the results into Filament's actual TransformManager.
 *
 * Column-major (element (row, col) at index col*4+row) matches OpenGL/Filament's own
 * matrix convention.
 */
object Mat4 {

    val IDENTITY: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** A rotation matrix for the given Euler angles in degrees, applied pitch (X) then yaw (Y) then roll (Z). */
    fun fromEulerDegrees(pitchDegrees: Float, yawDegrees: Float, rollDegrees: Float): FloatArray {
        val rx = rotationX(Math.toRadians(pitchDegrees.toDouble()).toFloat())
        val ry = rotationY(Math.toRadians(yawDegrees.toDouble()).toFloat())
        val rz = rotationZ(Math.toRadians(rollDegrees.toDouble()).toFloat())
        return multiply(multiply(rz, ry), rx)
    }

    fun rotationX(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun rotationY(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun rotationZ(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return floatArrayOf(
            c, s, 0f, 0f,
            -s, c, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /** Column-major 4x4 matrix multiplication: returns a * b. */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
        return result
    }
}
