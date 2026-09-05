package com.kira.companion.render

import org.junit.Assert.assertEquals
import org.junit.Test

class Mat4Test {

    private fun assertMatrixEquals(expected: FloatArray, actual: FloatArray, delta: Float = 0.001f) {
        assertEquals(16, actual.size)
        for (i in 0 until 16) {
            assertEquals("mismatch at index $i", expected[i], actual[i], delta)
        }
    }

    @Test
    fun `multiplying by identity returns the same matrix`() {
        val m = floatArrayOf(
            2f, 0f, 0f, 0f,
            0f, 3f, 0f, 0f,
            0f, 0f, 4f, 0f,
            5f, 6f, 7f, 1f,
        )

        assertMatrixEquals(m, Mat4.multiply(Mat4.IDENTITY, m))
        assertMatrixEquals(m, Mat4.multiply(m, Mat4.IDENTITY))
    }

    @Test
    fun `zero rotation is identity`() {
        assertMatrixEquals(Mat4.IDENTITY, Mat4.fromEulerDegrees(0f, 0f, 0f))
    }

    @Test
    fun `90 degree yaw maps forward (0,0,1) to (1,0,0)`() {
        val rotation = Mat4.rotationY(Math.toRadians(90.0).toFloat())
        val (x, y, z) = transformPoint(rotation, 0f, 0f, 1f)

        assertEquals(1f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
        assertEquals(0f, z, 0.001f)
    }

    @Test
    fun `90 degree pitch rotates up (0,1,0) onto the z axis`() {
        val rotation = Mat4.rotationX(Math.toRadians(90.0).toFloat())
        val (x, y, z) = transformPoint(rotation, 0f, 1f, 0f)

        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
        assertEquals(1f, z, 0.001f)
    }

    @Test
    fun `combining two rotations matches applying them in sequence`() {
        val yaw90 = Mat4.rotationY(Math.toRadians(90.0).toFloat())
        val roll90 = Mat4.rotationZ(Math.toRadians(90.0).toFloat())
        val combined = Mat4.multiply(roll90, yaw90)

        val point = floatArrayOf(0f, 0f, 1f)
        val viaCombined = transformPoint(combined, point[0], point[1], point[2])
        val intermediate = transformPoint(yaw90, point[0], point[1], point[2])
        val viaSequence = transformPoint(roll90, intermediate.first, intermediate.second, intermediate.third)

        assertEquals(viaSequence.first, viaCombined.first, 0.001f)
        assertEquals(viaSequence.second, viaCombined.second, 0.001f)
        assertEquals(viaSequence.third, viaCombined.third, 0.001f)
    }

    /** Applies a column-major 4x4 matrix to a point, returning (x, y, z). */
    private fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val rx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val ry = m[1] * x + m[5] * y + m[9] * z + m[13]
        val rz = m[2] * x + m[6] * y + m[10] * z + m[14]
        return Triple(rx, ry, rz)
    }
}
