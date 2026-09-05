package com.kira.companion.model

/**
 * All expressions Kira can show. The system is intentionally a flat enum plus a small
 * amount of per-emotion metadata (below) so new emotions can be added later without
 * touching the engine, controller or overlay code.
 */
enum class KiraEmotion {
    IDLE,
    HAPPY,
    LOVE,
    SHY,
    THINKING,
    SURPRISED,
    SAD,
    ANGRY,
    SLEEPY,
    EXCITED,
    CONFUSED,
    WINK,
    LAUGHING,
    CRYING,
    WORRIED,
    EMBARRASSED,
    SMUG;

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
    KiraEmotion.LOVE -> EmotionSpec(autoReturnToIdleMillis = 3200L, eligibleForRandomReaction = true)
    KiraEmotion.SHY -> EmotionSpec(autoReturnToIdleMillis = 2800L, eligibleForRandomReaction = true)
    KiraEmotion.THINKING -> EmotionSpec(autoReturnToIdleMillis = 0L, eligibleForRandomReaction = false)
    KiraEmotion.SURPRISED -> EmotionSpec(autoReturnToIdleMillis = 1600L, eligibleForRandomReaction = true)
    KiraEmotion.SAD -> EmotionSpec(autoReturnToIdleMillis = 4000L, eligibleForRandomReaction = false)
    KiraEmotion.ANGRY -> EmotionSpec(autoReturnToIdleMillis = 3200L, eligibleForRandomReaction = false)
    KiraEmotion.SLEEPY -> EmotionSpec(autoReturnToIdleMillis = 5000L, eligibleForRandomReaction = true)
    KiraEmotion.EXCITED -> EmotionSpec(autoReturnToIdleMillis = 2600L, eligibleForRandomReaction = true)
    KiraEmotion.CONFUSED -> EmotionSpec(autoReturnToIdleMillis = 3000L, eligibleForRandomReaction = true)
    KiraEmotion.WINK -> EmotionSpec(autoReturnToIdleMillis = 1800L, eligibleForRandomReaction = true)
    KiraEmotion.LAUGHING -> EmotionSpec(autoReturnToIdleMillis = 2600L, eligibleForRandomReaction = true)
    KiraEmotion.CRYING -> EmotionSpec(autoReturnToIdleMillis = 4200L, eligibleForRandomReaction = false)
    KiraEmotion.WORRIED -> EmotionSpec(autoReturnToIdleMillis = 3200L, eligibleForRandomReaction = false)
    KiraEmotion.EMBARRASSED -> EmotionSpec(autoReturnToIdleMillis = 2800L, eligibleForRandomReaction = true)
    KiraEmotion.SMUG -> EmotionSpec(autoReturnToIdleMillis = 2400L, eligibleForRandomReaction = true)
}
