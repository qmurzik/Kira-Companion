package com.kira.companion.behavior

/** Kira's current speech bubble, if any. */
data class SpeechBubbleState(val text: String?, val visible: Boolean)

/**
 * Owns Kira's current speech bubble: what it says and for how long, so it appears/
 * disappears on a timer rather than callers having to manage that themselves. Drive with
 * [update] each tick and read [current] to render (the UI layer is responsible for the
 * actual fade in/out animation - this only owns *what* and *for how long*).
 */
class SpeechBubbleController(private val defaultDurationMillis: Long = 2600L) {
    private var text: String? = null
    private var remainingMillis: Long = 0L

    val current: SpeechBubbleState get() = SpeechBubbleState(text = text, visible = text != null)

    fun show(text: String, durationMillis: Long = defaultDurationMillis) {
        this.text = text
        this.remainingMillis = durationMillis
    }

    fun update(deltaMillis: Long) {
        if (text == null) return
        remainingMillis -= deltaMillis
        if (remainingMillis <= 0L) {
            text = null
            remainingMillis = 0L
        }
    }

    fun dismiss() {
        text = null
        remainingMillis = 0L
    }
}
