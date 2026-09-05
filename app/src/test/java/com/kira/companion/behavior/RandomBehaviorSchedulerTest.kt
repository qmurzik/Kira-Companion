package com.kira.companion.behavior

import com.kira.companion.model.ReactionFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomBehaviorSchedulerTest {

    @Test
    fun `off frequency never fires`() {
        val scheduler = RandomBehaviorScheduler(random = Random(1))
        scheduler.setFrequency(ReactionFrequency.OFF)

        var fired: RandomBehaviorKind? = null
        repeat(50) {
            fired = scheduler.update(60_000L) ?: fired
        }

        assertNull(fired)
    }

    @Test
    fun `high frequency fires sooner than low frequency on average`() {
        fun ticksUntilFirstFire(frequency: ReactionFrequency, seed: Long): Int {
            val scheduler = RandomBehaviorScheduler(random = Random(seed))
            scheduler.setFrequency(frequency)
            var ticks = 0
            while (scheduler.update(1000L) == null && ticks < 1000) {
                ticks++
            }
            return ticks
        }

        val highTicks = (0 until 10).map { ticksUntilFirstFire(ReactionFrequency.HIGH, it.toLong()) }.average()
        val lowTicks = (0 until 10).map { ticksUntilFirstFire(ReactionFrequency.LOW, it.toLong()) }.average()

        assertTrue("HIGH ($highTicks) should fire sooner on average than LOW ($lowTicks)", highTicks < lowTicks)
    }

    @Test
    fun `eventually fires a behavior for normal frequency`() {
        val scheduler = RandomBehaviorScheduler(random = Random(7))
        scheduler.setFrequency(ReactionFrequency.NORMAL)

        var fired: RandomBehaviorKind? = null
        var elapsed = 0L
        while (fired == null && elapsed < 200_000L) {
            fired = scheduler.update(1000L)
            elapsed += 1000L
        }

        assertTrue(fired != null)
        assertTrue(RandomBehaviorKind.entries.contains(fired))
    }

    @Test
    fun `switching to off after being scheduled stops future firing`() {
        val scheduler = RandomBehaviorScheduler(random = Random(3))
        scheduler.setFrequency(ReactionFrequency.HIGH)
        scheduler.update(100L)

        scheduler.setFrequency(ReactionFrequency.OFF)

        repeat(100) {
            assertEquals(null, scheduler.update(60_000L))
        }
    }
}
