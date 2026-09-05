package com.kira.companion.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GazeControllerTest {

    @Test
    fun `starts looking forward`() {
        val gaze = GazeController()
        assertEquals(0f, gaze.current.eyeAngles.yawDegrees, 0.01f)
        assertEquals(0f, gaze.current.eyeAngles.pitchDegrees, 0.01f)
    }

    @Test
    fun `lookAt immediately starts blending toward the target`() {
        val gaze = GazeController(minHoldMillis = 60_000L, maxHoldMillis = 60_000L, blendMillis = 400L)

        gaze.lookAt(dx = 1f, dy = 0f, durationMillis = 2000L)
        gaze.update(200L) // halfway through the 400ms blend

        assertTrue("expected yaw to have moved toward the target, was ${gaze.current.eyeAngles.yawDegrees}", gaze.current.eyeAngles.yawDegrees > 1f)
    }

    @Test
    fun `lookAt fully reaches its target once the blend completes`() {
        val gaze = GazeController(minHoldMillis = 60_000L, maxHoldMillis = 60_000L, blendMillis = 400L)

        gaze.lookAt(dx = 1f, dy = 0f, durationMillis = 5000L)
        gaze.update(400L)

        val expected = GazeMath.eyeAnglesTo(1f, 0f)
        assertEquals(expected.yawDegrees, gaze.current.eyeAngles.yawDegrees, 0.01f)
    }

    @Test
    fun `head follows the eyes only partially`() {
        val gaze = GazeController(minHoldMillis = 60_000L, maxHoldMillis = 60_000L, blendMillis = 100L)

        gaze.lookAt(dx = 1f, dy = 0f, durationMillis = 5000L)
        gaze.update(100L)

        val pose = gaze.current
        assertTrue(pose.headAngles.yawDegrees > 0f)
        assertTrue(pose.headAngles.yawDegrees < pose.eyeAngles.yawDegrees)
    }

    @Test
    fun `idle wandering eventually moves gaze away from forward`() {
        val gaze = GazeController(random = Random(1), minHoldMillis = 100L, maxHoldMillis = 100L, blendMillis = 50L)

        var everMoved = false
        repeat(200) {
            gaze.update(50L)
            if (gaze.current.eyeAngles.yawDegrees != 0f || gaze.current.eyeAngles.pitchDegrees != 0f) everMoved = true
        }

        assertTrue("expected idle wandering to move the gaze away from forward at some point", everMoved)
    }

    @Test
    fun `an override is not interrupted by idle wandering until it expires`() {
        val gaze = GazeController(random = Random(2), minHoldMillis = 10L, maxHoldMillis = 10L, blendMillis = 10L)

        gaze.lookAt(dx = 1f, dy = 0f, durationMillis = 300L)
        repeat(5) { gaze.update(50L) } // 250ms elapsed, override (300ms) still active

        val expected = GazeMath.eyeAnglesTo(1f, 0f)
        assertEquals(expected.yawDegrees, gaze.current.eyeAngles.yawDegrees, 0.01f)
    }
}
