package com.kira.companion.behavior

import com.kira.companion.model.KiraEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchReactionControllerTest {

    @Test
    fun `first head tap reacts with happy`() {
        val controller = TouchReactionController()
        val reactions = controller.onTap(TouchRegion.HEAD, nowMillis = 0L, touchDx = 0f, touchDy = 0f)

        assertEquals(listOf(TouchReaction.Emotion(KiraEmotion.HAPPY)), reactions)
    }

    @Test
    fun `second head tap in quick succession reacts with shy`() {
        val controller = TouchReactionController()
        controller.onTap(TouchRegion.HEAD, nowMillis = 0L, touchDx = 0f, touchDy = 0f)

        val reactions = controller.onTap(TouchRegion.HEAD, nowMillis = 500L, touchDx = 0f, touchDy = 0f)

        assertEquals(listOf(TouchReaction.Emotion(KiraEmotion.SHY)), reactions)
    }

    @Test
    fun `repeated petting past the threshold gets the teasing line`() {
        val controller = TouchReactionController(repeatThreshold = 3)
        controller.onTap(TouchRegion.HEAD, nowMillis = 0L, touchDx = 0f, touchDy = 0f)
        controller.onTap(TouchRegion.HEAD, nowMillis = 200L, touchDx = 0f, touchDy = 0f)

        val reactions = controller.onTap(TouchRegion.HEAD, nowMillis = 400L, touchDx = 0f, touchDy = 0f)

        assertTrue(reactions.any { it is TouchReaction.SpeechLine && it.text == "Эй~ хватит меня гладить >///<" })
        assertTrue(reactions.any { it == TouchReaction.Emotion(KiraEmotion.SHY) })
    }

    @Test
    fun `the tap counter resets after the repeat window elapses`() {
        val controller = TouchReactionController(repeatWindowMillis = 1000L)
        controller.onTap(TouchRegion.HEAD, nowMillis = 0L, touchDx = 0f, touchDy = 0f)

        val reactions = controller.onTap(TouchRegion.HEAD, nowMillis = 5000L, touchDx = 0f, touchDy = 0f)

        // Enough time passed that this should read as a "first tap" again, not the second.
        assertEquals(listOf(TouchReaction.Emotion(KiraEmotion.HAPPY)), reactions)
    }

    @Test
    fun `arm tap looks at the touch location and never changes emotion`() {
        val controller = TouchReactionController()
        val reactions = controller.onTap(TouchRegion.ARM, nowMillis = 0L, touchDx = 0.5f, touchDy = -0.2f)

        assertEquals(listOf(TouchReaction.LookAt(0.5f, -0.2f)), reactions)
    }

    @Test
    fun `body tap is a neutral reaction`() {
        val controller = TouchReactionController()
        val reactions = controller.onTap(TouchRegion.BODY, nowMillis = 0L, touchDx = 0f, touchDy = 0f)

        assertEquals(listOf(TouchReaction.Emotion(KiraEmotion.IDLE)), reactions)
    }
}
