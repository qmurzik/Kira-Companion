package com.kira.companion.behavior

import com.kira.companion.model.ReactionFrequency
import kotlin.random.Random

/** A small autonomous idle behavior Kira can do on her own, unprompted. */
enum class RandomBehaviorKind { LOOK_AWAY, BLINK, SMILE, ADJUST_HAIR, TILT_HEAD, SLEEPY, YAWN }

/**
 * Pure timing/selection logic for Kira's small autonomous idle behaviors - looking away,
 * a small smile, adjusting her hair, tilting her head, and so on. This is deliberately
 * separate from full emotion changes (see [com.kira.companion.emotion.startRandomReactionLoop]):
 * those change what she's *feeling*, this only ever adds a small bit of life to however she's
 * currently posed, so it never conflicts with an active emotion or interaction.
 *
 * Advance with [update]; a non-null return means a behavior fired and the timer to the next
 * one has already been rescheduled. Reuses [ReactionFrequency] (OFF/LOW/NORMAL/HIGH) as the
 * single "how alive should Kira feel" setting shared with emotion-level random reactions.
 */
class RandomBehaviorScheduler(
    private val random: Random = Random.Default,
) {
    private var elapsedSinceLastMillis: Long = 0L
    private var nextIntervalMillis: Long = 0L
    private var frequency: ReactionFrequency = ReactionFrequency.NORMAL
    private var scheduled: Boolean = false

    fun setFrequency(frequency: ReactionFrequency) {
        this.frequency = frequency
        if (frequency == ReactionFrequency.OFF) scheduled = false
    }

    /** Advances the scheduler by [deltaMillis]; returns the behavior that fired, if any. */
    fun update(deltaMillis: Long): RandomBehaviorKind? {
        if (frequency == ReactionFrequency.OFF) return null
        if (!scheduled) {
            nextIntervalMillis = randomInterval()
            elapsedSinceLastMillis = 0L
            scheduled = true
        }

        elapsedSinceLastMillis += deltaMillis
        if (elapsedSinceLastMillis < nextIntervalMillis) return null

        elapsedSinceLastMillis = 0L
        nextIntervalMillis = randomInterval()
        return RandomBehaviorKind.entries.random(random)
    }

    private fun randomInterval(): Long {
        val (min, max) = when (frequency) {
            ReactionFrequency.OFF -> return 0L
            ReactionFrequency.LOW -> 60_000L to 180_000L
            ReactionFrequency.NORMAL -> 30_000L to 120_000L
            ReactionFrequency.HIGH -> 15_000L to 45_000L
        }
        return random.nextLong(min, max)
    }
}
