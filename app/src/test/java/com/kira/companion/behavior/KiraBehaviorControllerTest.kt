package com.kira.companion.behavior

import com.kira.companion.model.KiraEmotion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiraBehaviorControllerTest {

    @Test
    fun `head tap reacts through the emotion controller`() = runTest {
        val controller = KiraBehaviorController(scope = this)

        controller.onTap(TouchRegion.HEAD, touchDx = 0f, touchDy = 0f, nowMillis = 0L)

        assertEquals(KiraEmotion.HAPPY, controller.emotion.emotion.value)
    }

    @Test
    fun `arm tap redirects gaze toward the touch location`() = runTest {
        val controller = KiraBehaviorController(scope = this)

        controller.onTap(TouchRegion.ARM, touchDx = 1f, touchDy = 0f, nowMillis = 0L)
        controller.update(400L)

        assertTrue(
            "expected gaze yaw to move toward the touch, was ${controller.gaze.current.eyeAngles.yawDegrees}",
            controller.gaze.current.eyeAngles.yawDegrees > 1f,
        )
    }

    @Test
    fun `body tap gives a neutral reaction with no speech bubble`() = runTest {
        val controller = KiraBehaviorController(scope = this)

        controller.onTap(TouchRegion.BODY, touchDx = 0f, touchDy = 0f, nowMillis = 0L)

        assertEquals(KiraEmotion.IDLE, controller.emotion.emotion.value)
        assertFalse(controller.speechBubble.current.visible)
    }

    @Test
    fun `onKiraReply shows the reply as a speech bubble and updates emotion`() = runTest {
        val controller = KiraBehaviorController(scope = this)

        controller.onKiraReply("я тебя люблю")

        assertEquals(KiraEmotion.LOVE, controller.emotion.emotion.value)
        assertEquals("я тебя люблю", controller.speechBubble.current.text)
    }

    @Test
    fun `onWaitingForReply shows a thinking bubble`() = runTest {
        val controller = KiraBehaviorController(scope = this)

        controller.onWaitingForReply()

        assertEquals(KiraEmotion.THINKING, controller.emotion.emotion.value)
        assertEquals("Подожди, я думаю...", controller.speechBubble.current.text)
    }

    @Test
    fun `update advances the speech bubble timer`() = runTest {
        val controller = KiraBehaviorController(scope = this)
        controller.speechBubble.show("Хихи", durationMillis = 500L)

        controller.update(600L)

        assertFalse(controller.speechBubble.current.visible)
    }
}
