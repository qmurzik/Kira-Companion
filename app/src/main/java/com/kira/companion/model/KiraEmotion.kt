package com.kira.companion.model

/**
 * All expressions Kira can show. The system is intentionally a flat enum plus a small
 * amount of per-emotion metadata (below) so new emotions (SHY, LAUGHING, CRYING, ...)
 * can be added later without touching the engine, controller or overlay code.
 */
enum class KiraEmotion {
    IDLE,
    HAPPY,
    SAD,
    THINKING,
    SURPRISED,
    ANGRY,
    SLEEPY,
    CONFUSED,
    LOVE,
    EXCITED;

    companion object {
        val default: KiraEmotion = IDLE
    }
}

/**
 * How long a non-idle emotion should linger before automatically returning to IDLE,
 * and whether it is allowed to fire as a spontaneous "random reaction".
 */
data class EmotionSpec(
    val autoReturnToIdleMillis: Long,
    val eligibleForRandomReaction: Boolean,
)

fun KiraEmotion.spec(): EmotionSpec = when (this) {
    KiraEmotion.IDLE -> EmotionSpec(autoReturnToIdleMillis = 0L, eligibleForRandomReaction = false)
    KiraEmotion.HAPPY -> EmotionSpec(autoReturnToIdleMillis = 2600L, eligibleForRandomReaction = true)
    KiraEmotion.SAD -> EmotionSpec(autoReturnToIdleMillis = 4000L, eligibleForRandomReaction = false)
    KiraEmotion.THINKING -> EmotionSpec(autoReturnToIdleMillis = 0L, eligibleForRandomReaction = false)
    KiraEmotion.SURPRISED -> EmotionSpec(autoReturnToIdleMillis = 1600L, eligibleForRandomReaction = true)
    KiraEmotion.ANGRY -> EmotionSpec(autoReturnToIdleMillis = 3200L, eligibleForRandomReaction = false)
    KiraEmotion.SLEEPY -> EmotionSpec(autoReturnToIdleMillis = 5000L, eligibleForRandomReaction = true)
    KiraEmotion.CONFUSED -> EmotionSpec(autoReturnToIdleMillis = 3000L, eligibleForRandomReaction = true)
    KiraEmotion.LOVE -> EmotionSpec(autoReturnToIdleMillis = 3200L, eligibleForRandomReaction = true)
    KiraEmotion.EXCITED -> EmotionSpec(autoReturnToIdleMillis = 2600L, eligibleForRandomReaction = true)
}
