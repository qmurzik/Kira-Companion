package com.kira.companion.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GazeMathTest {

    @Test
    fun `looking straight ahead is zero yaw and pitch`() {
        val angles = GazeMath.eyeAnglesTo(0f, 0f, 1f)
        assertEquals(0f, angles.yawDegrees, 0.01f)
        assertEquals(0f, angles.pitchDegrees, 0.01f)
    }

    @Test
    fun `looking right produces positive yaw`() {
        val angles = GazeMath.eyeAnglesTo(1f, 0f, 1f)
        assertTrue(angles.yawDegrees > 0f)
    }

    @Test
    fun `looking left produces negative yaw`() {
        val angles = GazeMath.eyeAnglesTo(-1f, 0f, 1f)
        assertTrue(angles.yawDegrees < 0f)
    }

    @Test
    fun `looking up produces positive pitch`() {
        val angles = GazeMath.eyeAnglesTo(0f, 1f, 1f)
        assertTrue(angles.pitchDegrees > 0f)
    }

    @Test
    fun `eye angles are clamped to a realistic range even for extreme targets`() {
        val angles = GazeMath.eyeAnglesTo(100f, 100f, 0.01f)
        assertTrue(abs(angles.yawDegrees) <= 30f)
        assertTrue(abs(angles.pitchDegrees) <= 20f)
    }

    @Test
    fun `head angles have a narrower or equal range than eye angles`() {
        val eye = GazeMath.eyeAnglesTo(100f, 100f, 0.01f)
        val head = GazeMath.headAnglesTo(100f, 100f, 0.01f)
        assertTrue(abs(head.yawDegrees) <= abs(eye.yawDegrees) + 5f)
    }

    @Test
    fun `lerp at t=0 returns from and at t=1 returns to`() {
        val from = GazeAngles(0f, 0f)
        val to = GazeAngles(20f, 10f)

        assertEquals(from, GazeMath.lerp(from, to, 0f))
        assertEquals(to, GazeMath.lerp(from, to, 1f))
    }

    @Test
    fun `lerp at t=0_5 is the midpoint`() {
        val from = GazeAngles(0f, 0f)
        val to = GazeAngles(20f, 10f)

        val mid = GazeMath.lerp(from, to, 0.5f)

        assertEquals(10f, mid.yawDegrees, 0.01f)
        assertEquals(5f, mid.pitchDegrees, 0.01f)
    }

    @Test
    fun `lerp clamps t outside 0 to 1`() {
        val from = GazeAngles(0f, 0f)
        val to = GazeAngles(20f, 10f)

        assertEquals(to, GazeMath.lerp(from, to, 5f))
        assertEquals(from, GazeMath.lerp(from, to, -5f))
    }

    @Test
    fun `idle wander offsets include forward and are not all identical`() {
        assertTrue(GazeMath.idleWanderOffsets.contains(0f to 0f))
        assertTrue(GazeMath.idleWanderOffsets.toSet().size > 1)
    }
}
