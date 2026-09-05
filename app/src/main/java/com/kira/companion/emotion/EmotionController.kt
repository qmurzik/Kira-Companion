package com.kira.companion.emotion

import com.kira.companion.model.KiraEmotion
import com.kira.companion.model.spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Kira's current emotional state. Responsible for:
 *  - Applying a new emotion (e.g. from [EmotionEngine] after a chat message, or from a tap).
 *  - Automatically returning to IDLE after each emotion's configured duration.
 *  - Exposing how long the current state has been active, so future logic can factor in
 *    "how long has Kira felt this way" (e.g. to avoid flip-flopping too fast).
 *  - Applying a spontaneous "random reaction" when asked to (see [triggerRandomReaction]) -
 *    the *timing* of those requests belongs to [com.kira.companion.behavior.RandomBehaviorScheduler],
 *    not this class.
 */
class EmotionController(private val scope: CoroutineScope) {

    private val _emotion = MutableStateFlow(KiraEmotion.default)
    val emotion: StateFlow<KiraEmotion> = _emotion.asStateFlow()

    /** The full analysis (emotion + intensity + duration) behind the current [emotion], if it
     *  came from [onUserMessage]/[onKiraReply]. Null for manually-set or tap/random reactions -
     *  those don't carry a meaningful intensity. Consumed by the render layer to scale blend
     *  shape weights (e.g. a mild "грустно" vs. a "мне очень плохо" should not look identical). */
    private val _lastAnalysis = MutableStateFlow<EmotionResult?>(null)
    val lastAnalysis: StateFlow<EmotionResult?> = _lastAnalysis.asStateFlow()

    private var revertJob: Job? = null
    private var stateEnteredAtMillis: Long = System.currentTimeMillis()

    fun currentStateDurationMillis(): Long = System.currentTimeMillis() - stateEnteredAtMillis

    /**
     * Directly set an emotion (e.g. manually from the debug/emotions menu, or a tap/random
     * reaction). [autoReturnOverrideMillis] lets callers that already computed a duration (via
     * [EmotionEngine.analyze]) use it instead of the emotion's default spec duration.
     */
    fun setEmotion(emotion: KiraEmotion, autoReturnOverrideMillis: Long? = null) {
        revertJob?.cancel()
        _emotion.value = emotion
        stateEnteredAtMillis = System.currentTimeMillis()

        val autoReturnMillis = autoReturnOverrideMillis ?: emotion.spec().autoReturnToIdleMillis
        if (autoReturnMillis > 0) {
            revertJob = scope.launch {
                delay(autoReturnMillis)
                _emotion.value = KiraEmotion.IDLE
            }
        }
    }

    /** Feed a new user chat message through the emotion engine and react to it. */
    fun onUserMessage(text: String) {
        val result = EmotionEngine.analyze(text, _emotion.value)
        _lastAnalysis.value = result
        setEmotion(result.emotion, result.durationMillis)
    }

    /** React to Kira's own reply once it arrives, so her expression matches what she "said". */
    fun onKiraReply(text: String) {
        val result = EmotionEngine.analyze(text, _emotion.value)
        _lastAnalysis.value = result
        setEmotion(result.emotion, result.durationMillis)
    }

    /** Called while Kira is waiting on an AI response. */
    fun onWaitingForReply() {
        setEmotion(KiraEmotion.THINKING)
    }

    /**
     * A short, low-effort reaction fired when the user simply taps Kira: a small random
     * cute reaction that always fades back to IDLE - it never leaves Kira in a new
     * permanent state. Only fires when she's currently doing nothing else.
     */
    fun onTap() {
        if (_emotion.value == KiraEmotion.IDLE) {
            setEmotion(TAP_REACTIONS.random())
        }
    }

    /** Spontaneous reaction, only when Kira is currently doing nothing in particular. */
    fun triggerRandomReaction() {
        if (_emotion.value != KiraEmotion.IDLE) return
        val candidates = KiraEmotion.entries.filter { it.spec().eligibleForRandomReaction }
        if (candidates.isNotEmpty()) {
            setEmotion(candidates.random())
        }
    }

    private companion object {
        val TAP_REACTIONS = listOf(KiraEmotion.HAPPY, KiraEmotion.WINK, KiraEmotion.LOVE)
    }
}

