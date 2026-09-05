package com.kira.companion.emotion

import com.kira.companion.model.KiraEmotion
import com.kira.companion.model.spec
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionControllerTest {

    @Test
    fun `starts in idle`() = runTest {
        val controller = EmotionController(this)
        assertEquals(KiraEmotion.IDLE, controller.emotion.value)
    }

    @Test
    fun `setEmotion updates state immediately`() = runTest {
        val controller = EmotionController(this)
        controller.setEmotion(KiraEmotion.HAPPY)
        assertEquals(KiraEmotion.HAPPY, controller.emotion.value)
    }

    @Test
    fun `setEmotion auto-reverts to idle after its configured duration`() = runTest {
        val controller = EmotionController(this)
        controller.setEmotion(KiraEmotion.HAPPY)

        advanceTimeBy(KiraEmotion.HAPPY.spec().autoReturnToIdleMillis + 200)
        assertEquals(KiraEmotion.IDLE, controller.emotion.value)
    }

    @Test
    fun `onTap only reacts when currently idle`() = runTest {
        val controller = EmotionController(this)
        controller.setEmotion(KiraEmotion.SAD)

        controller.onTap()

        assertEquals(KiraEmotion.SAD, controller.emotion.value)
    }

    @Test
    fun `onTap reacts with happy when idle`() = runTest {
        val controller = EmotionController(this)
        controller.onTap()
        assertEquals(KiraEmotion.HAPPY, controller.emotion.value)
    }

    @Test
    fun `triggerRandomReaction does nothing unless idle`() = runTest {
        val controller = EmotionController(this)
        controller.setEmotion(KiraEmotion.ANGRY)

        controller.triggerRandomReaction()

        assertEquals(KiraEmotion.ANGRY, controller.emotion.value)
    }

    @Test
    fun `triggerRandomReaction picks an eligible emotion when idle`() = runTest {
        val controller = EmotionController(this)
        controller.triggerRandomReaction()

        val result = controller.emotion.value
        assertNotEquals(KiraEmotion.IDLE, result)
        assertTrue(result.spec().eligibleForRandomReaction)
    }
}
