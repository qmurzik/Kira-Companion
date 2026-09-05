package com.kira.companion.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubbleControllerTest {

    @Test
    fun `starts with no bubble`() {
        val controller = SpeechBubbleController()
        assertFalse(controller.current.visible)
        assertNull(controller.current.text)
    }

    @Test
    fun `show makes the bubble visible with the given text`() {
        val controller = SpeechBubbleController()
        controller.show("Приветик~ 💜")

        assertTrue(controller.current.visible)
        assertEquals("Приветик~ 💜", controller.current.text)
    }

    @Test
    fun `bubble disappears after its duration elapses`() {
        val controller = SpeechBubbleController()
        controller.show("Хихи", durationMillis = 1000L)

        controller.update(600L)
        assertTrue(controller.current.visible)

        controller.update(500L)
        assertFalse(controller.current.visible)
        assertNull(controller.current.text)
    }

    @Test
    fun `showing new text resets the timer`() {
        val controller = SpeechBubbleController()
        controller.show("Ты чего?", durationMillis = 500L)
        controller.update(400L)

        controller.show("Я здесь ♡", durationMillis = 500L)
        controller.update(400L)

        assertTrue(controller.current.visible)
        assertEquals("Я здесь ♡", controller.current.text)
    }

    @Test
    fun `dismiss hides the bubble immediately`() {
        val controller = SpeechBubbleController()
        controller.show("Подожди, я думаю...")

        controller.dismiss()

        assertFalse(controller.current.visible)
    }

    @Test
    fun `update is a no-op when there is no bubble`() {
        val controller = SpeechBubbleController()
        controller.update(10_000L)
        assertFalse(controller.current.visible)
    }
}
