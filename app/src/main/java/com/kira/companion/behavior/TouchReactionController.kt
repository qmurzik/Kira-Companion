package com.kira.companion.behavior

import com.kira.companion.model.KiraEmotion

/** Where on Kira the user tapped. */
enum class TouchRegion { HEAD, ARM, BODY }

/** One effect a tap should have - interpreted by [com.kira.companion.behavior.KiraBehaviorController]
 *  against the relevant sub-controller (emotion, gaze, or speech bubble). */
sealed interface TouchReaction {
    data class Emotion(val emotion: KiraEmotion) : TouchReaction
    data class SpeechLine(val text: String) : TouchReaction
    data class LookAt(val dx: Float, val dy: Float) : TouchReaction
}

/**
 * Decides how Kira reacts to a tap, based on where she was touched. Explicitly limited to
 * warm, platonic reactions - never sexualized - per the product requirement: a head tap
 * reads as a friendly pat (HAPPY, then shyer if it keeps happening, with a teasing line
 * after several taps in a row), an arm tap just makes her look at where she was touched,
 * and a body tap gets a neutral reaction. [nowMillis] is passed in explicitly (rather than
 * read from the system clock) so this stays pure and deterministically testable.
 */
class TouchReactionController(
    private val repeatWindowMillis: Long = 4000L,
    private val repeatThreshold: Int = 3,
) {
    private var headTapCount = 0
    private var lastHeadTapAtMillis = 0L

    fun onTap(region: TouchRegion, nowMillis: Long, touchDx: Float, touchDy: Float): List<TouchReaction> =
        when (region) {
            TouchRegion.HEAD -> onHeadTap(nowMillis)
            TouchRegion.ARM -> listOf(TouchReaction.LookAt(touchDx, touchDy))
            TouchRegion.BODY -> listOf(TouchReaction.Emotion(KiraEmotion.IDLE))
        }

    private fun onHeadTap(nowMillis: Long): List<TouchReaction> {
        if (nowMillis - lastHeadTapAtMillis > repeatWindowMillis) headTapCount = 0
        headTapCount++
        lastHeadTapAtMillis = nowMillis

        return if (headTapCount >= repeatThreshold) {
            headTapCount = 0
            listOf(TouchReaction.SpeechLine("Эй~ хватит меня гладить >///<"), TouchReaction.Emotion(KiraEmotion.SHY))
        } else {
            listOf(TouchReaction.Emotion(if (headTapCount == 1) KiraEmotion.HAPPY else KiraEmotion.SHY))
        }
    }
}
