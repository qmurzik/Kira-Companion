package com.kira.companion.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlinkTimerTest {

    @Test
    fun `stays open before the first scheduled blink`() {
        // Seed chosen so the fixed minInterval below is what gates the first blink.
        val timer = BlinkTimer(random = Random(1), minIntervalMillis = 3000L, maxIntervalMillis = 3000L)

        val openness = timer.update(1000L)

        assertEquals(1f, openness, 0.001f)
        assertEquals(BlinkPhase.OPEN, timer.currentPhase)
    }

    @Test
    fun `a full blink cycle fully closes then returns to open`() {
        val timer = BlinkTimer(random = Random(1), minIntervalMillis = 100L, maxIntervalMillis = 100L, doubleBlinkChance = 0f)

        var sawFullyClosed = false
        var reopenedAfterClosing = false
        repeat(200) {
            val openness = timer.update(10L)
            if (openness <= 0f) sawFullyClosed = true
            if (sawFullyClosed && timer.currentPhase == BlinkPhase.OPEN) reopenedAfterClosing = true
        }

        assertTrue("expected the blink to fully close at some point", sawFullyClosed)
        assertTrue("expected the blink to return to OPEN after closing", reopenedAfterClosing)
    }

    @Test
    fun `triggerBlink starts a blink immediately when open`() {
        val timer = BlinkTimer(random = Random(1), minIntervalMillis = 60_000L, maxIntervalMillis = 60_000L)

        timer.triggerBlink()
        timer.update(1L) // this tick transitions OPEN -> CLOSING
        val openness = timer.update(1L) // this tick reports the eyelid actually moving

        assertTrue(openness < 1f)
        assertEquals(BlinkPhase.CLOSING, timer.currentPhase)
    }

    @Test
    fun `triggerBlink does nothing while already blinking`() {
        val timer = BlinkTimer(random = Random(1), minIntervalMillis = 10L, maxIntervalMillis = 10L)
        timer.update(20L) // forces into CLOSING
        val phaseBefore = timer.currentPhase
        assertTrue(phaseBefore != BlinkPhase.OPEN)

        timer.triggerBlink() // should be a no-op; only fully-open state can be re-triggered
        assertEquals(phaseBefore, timer.currentPhase)
    }

    @Test
    fun `double blinks eventually occur given a high enough chance`() {
        val timer = BlinkTimer(random = Random(42), minIntervalMillis = 50L, maxIntervalMillis = 50L, doubleBlinkChance = 1f)

        var closingCount = 0
        var previousPhase = timer.currentPhase
        repeat(500) {
            timer.update(5L)
            if (timer.currentPhase == BlinkPhase.CLOSING && previousPhase != BlinkPhase.CLOSING) {
                closingCount++
            }
            previousPhase = timer.currentPhase
        }

        // With doubleBlinkChance = 1f, every blink is a double blink, so we should see at
        // least two separate CLOSING entries within the sampled window.
        assertTrue("expected multiple blink cycles (double-blink) but saw $closingCount", closingCount >= 2)
    }
}
