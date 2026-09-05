package com.kira.companion.behavior

import com.kira.companion.emotion.EmotionController
import com.kira.companion.model.ReactionFrequency
import kotlinx.coroutines.CoroutineScope

/**
 * The single place that owns *which* sub-controller handles what - it does not itself
 * decide emotions, gaze angles, blink timing, or speech text. Each concern lives in its own
 * class ([EmotionController], [BlinkTimer], [GazeController], [RandomBehaviorScheduler],
 * [SpeechBubbleController], [TouchReactionController]); this class' only job is to plug them
 * into each other and expose one `update`/`onTap`/`onUserMessage` surface for the render
 * layer and overlay to drive, so nothing about Kira's behavior ends up tangled into a
 * single monolithic class.
 */
class KiraBehaviorController(
    scope: CoroutineScope,
    val emotion: EmotionController = EmotionController(scope),
    val blink: BlinkTimer = BlinkTimer(),
    val gaze: GazeController = GazeController(),
    val randomBehavior: RandomBehaviorScheduler = RandomBehaviorScheduler(),
    val speechBubble: SpeechBubbleController = SpeechBubbleController(),
    private val touchReaction: TouchReactionController = TouchReactionController(),
) {
    fun setRandomBehaviorFrequency(frequency: ReactionFrequency) {
        randomBehavior.setFrequency(frequency)
    }

    /** Advances every time-driven sub-controller by one frame/tick ([deltaMillis] elapsed). */
    fun update(deltaMillis: Long) {
        blink.update(deltaMillis)
        gaze.update(deltaMillis)
        speechBubble.update(deltaMillis)

        when (randomBehavior.update(deltaMillis)) {
            RandomBehaviorKind.BLINK -> blink.triggerBlink()
            RandomBehaviorKind.LOOK_AWAY -> {
                val (dx, dy) = GazeMath.idleWanderOffsets.random()
                gaze.lookAt(dx, dy)
            }
            RandomBehaviorKind.SMILE -> emotion.triggerRandomReaction()
            RandomBehaviorKind.TILT_HEAD,
            RandomBehaviorKind.ADJUST_HAIR,
            RandomBehaviorKind.SLEEPY,
            RandomBehaviorKind.YAWN,
            null,
            -> Unit
        }
    }

    /** Handle a tap at the given region, with a normalized touch offset used for look-at targeting. */
    fun onTap(region: TouchRegion, touchDx: Float, touchDy: Float, nowMillis: Long) {
        for (reaction in touchReaction.onTap(region, nowMillis, touchDx, touchDy)) {
            when (reaction) {
                is TouchReaction.Emotion -> emotion.setEmotion(reaction.emotion)
                is TouchReaction.SpeechLine -> speechBubble.show(reaction.text)
                is TouchReaction.LookAt -> gaze.lookAt(reaction.dx, reaction.dy)
            }
        }
    }

    /** Feed a new user chat message through the emotion engine and react to it. */
    fun onUserMessage(text: String) {
        emotion.onUserMessage(text)
    }

    /** React to Kira's own reply once it arrives: matches her expression and shows it as a speech bubble. */
    fun onKiraReply(text: String) {
        emotion.onKiraReply(text)
        speechBubble.show(text)
    }

    /** Called while Kira is waiting on an AI response. */
    fun onWaitingForReply() {
        emotion.onWaitingForReply()
        speechBubble.show("Подожди, я думаю...")
    }
}
