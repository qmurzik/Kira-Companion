package com.kira.companion.behavior

import kotlin.random.Random

/** Where a blink currently is in its (closing -> closed -> opening) cycle. */
enum class BlinkPhase { OPEN, CLOSING, CLOSED, OPENING }

/**
 * Drives natural blinking: a randomized interval between blinks, a randomized duration per
 * blink (closing/held-closed/opening each vary independently), and an occasional
 * double-blink - never the naive, robotic "open -> closed -> open on a fixed timer".
 *
 * Pure state machine advanced by [update] with an elapsed-millis delta, so it can be driven
 * from any render/coroutine loop and unit-tested with synthetic time and an injected
 * [Random] for determinism.
 */
class BlinkTimer(
    private val random: Random = Random.Default,
    private val minIntervalMillis: Long = 2200L,
    private val maxIntervalMillis: Long = 6000L,
    private val doubleBlinkChance: Float = 0.15f,
) {
    private var phase: BlinkPhase = BlinkPhase.OPEN
    private var phaseElapsedMillis: Long = 0L
    private var nextBlinkInMillis: Long = randomInterval()
    private var pendingDoubleBlink: Boolean = false

    private var closingDurationMillis: Long = randomCloseDuration()
    private var closedDurationMillis: Long = randomHoldDuration()
    private var openingDurationMillis: Long = randomOpenDuration()

    val currentPhase: BlinkPhase get() = phase

    /**
     * Advances the blink state machine by [deltaMillis]. Returns the eyelid openness for
     * this frame: 1 = fully open, 0 = fully closed, with smooth values in between while
     * closing/opening.
     */
    fun update(deltaMillis: Long): Float {
        phaseElapsedMillis += deltaMillis
        return when (phase) {
            BlinkPhase.OPEN -> {
                nextBlinkInMillis -= deltaMillis
                if (nextBlinkInMillis <= 0L) startBlink()
                1f
            }
            BlinkPhase.CLOSING -> {
                val t = (phaseElapsedMillis.toFloat() / closingDurationMillis).coerceIn(0f, 1f)
                if (t >= 1f) transitionTo(BlinkPhase.CLOSED)
                1f - t
            }
            BlinkPhase.CLOSED -> {
                if (phaseElapsedMillis >= closedDurationMillis) transitionTo(BlinkPhase.OPENING)
                0f
            }
            BlinkPhase.OPENING -> {
                val t = (phaseElapsedMillis.toFloat() / openingDurationMillis).coerceIn(0f, 1f)
                if (t >= 1f) finishBlink()
                t
            }
        }
    }

    /** Force a blink to start now (e.g. as part of a random idle behavior tick), unless one is already underway. */
    fun triggerBlink() {
        if (phase == BlinkPhase.OPEN) nextBlinkInMillis = 0L
    }

    private fun startBlink() {
        pendingDoubleBlink = random.nextFloat() < doubleBlinkChance
        transitionTo(BlinkPhase.CLOSING)
    }

    private fun finishBlink() {
        if (pendingDoubleBlink) {
            pendingDoubleBlink = false
            transitionTo(BlinkPhase.CLOSING)
        } else {
            phase = BlinkPhase.OPEN
            phaseElapsedMillis = 0L
            nextBlinkInMillis = randomInterval()
        }
    }

    private fun transitionTo(next: BlinkPhase) {
        phase = next
        phaseElapsedMillis = 0L
        when (next) {
            BlinkPhase.CLOSING -> closingDurationMillis = randomCloseDuration()
            BlinkPhase.CLOSED -> closedDurationMillis = randomHoldDuration()
            BlinkPhase.OPENING -> openingDurationMillis = randomOpenDuration()
            BlinkPhase.OPEN -> Unit
        }
    }

    private fun randomInterval() = if (minIntervalMillis >= maxIntervalMillis) {
        minIntervalMillis
    } else {
        random.nextLong(minIntervalMillis, maxIntervalMillis)
    }
    private fun randomCloseDuration() = 40L + random.nextLong(30L)
    private fun randomHoldDuration() = 30L + random.nextLong(60L)
    private fun randomOpenDuration() = 60L + random.nextLong(60L)
}
