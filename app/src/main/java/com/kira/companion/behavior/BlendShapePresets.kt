package com.kira.companion.behavior

import com.kira.companion.model.KiraEmotion
import com.kira.companion.vrm.VrmModelData

/**
 * Standard VRM 0.x blend shape preset names (blendShapeMaster.blendShapeGroups[].presetName).
 * See the VRM 0.0 spec's BlendShapeMaster section. These are the only expressions/visemes a
 * VRM file is guaranteed to define; anything else (a specific "heart eyes" or "blush" shape)
 * is a custom-named group that a well-made Kira model may or may not provide, which is why
 * [BlendShapePresets.resolve] treats groups the model doesn't have as simply absent rather
 * than a hard failure.
 */
object VrmBlendShapePreset {
    const val NEUTRAL = "neutral"
    const val JOY = "joy"
    const val ANGRY = "angry"
    const val SORROW = "sorrow"
    const val FUN = "fun"
    const val BLINK = "blink"
    const val BLINK_L = "blink_l"
    const val BLINK_R = "blink_r"
    const val LOOKUP = "lookup"
    const val LOOKDOWN = "lookdown"
    const val LOOKLEFT = "lookleft"
    const val LOOKRIGHT = "lookright"
    const val VISEME_A = "a"
    const val VISEME_I = "i"
    const val VISEME_U = "u"
    const val VISEME_E = "e"
    const val VISEME_O = "o"
}

/** One blend-shape target (a standard preset name or a custom group name) and its weight (0..1). */
data class BlendShapeWeight(val presetOrGroupName: String, val weight: Float)

/**
 * Maps each [KiraEmotion] to the combination of blend shapes that expresses it. Every entry
 * here has more than one shape active at once - e.g. HAPPY combines "joy" with a touch of
 * "fun" rather than being a single on/off face - matching the requirement that an emotion
 * changes several facial parameters together, not just one.
 *
 * Weights are the *full-intensity* (1.0) targets; callers scale them by the analyzed
 * intensity (see [com.kira.companion.emotion.EmotionResult]) and by transition progress
 * (see [GazeMath.lerp] for the equivalent on gaze) before sending them to the rig, so a mild
 * reaction never snaps to the same extreme as a strong one.
 */
object BlendShapePresets {

    private val table: Map<KiraEmotion, List<BlendShapeWeight>> = mapOf(
        KiraEmotion.IDLE to listOf(
            BlendShapeWeight(VrmBlendShapePreset.NEUTRAL, 1f),
        ),
        KiraEmotion.HAPPY to listOf(
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.8f),
            BlendShapeWeight(VrmBlendShapePreset.FUN, 0.2f),
        ),
        KiraEmotion.LOVE to listOf(
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.6f),
            BlendShapeWeight("Love", 1f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK_L, 0.15f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK_R, 0.15f),
        ),
        KiraEmotion.SHY to listOf(
            BlendShapeWeight("Blush", 0.8f),
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.3f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKDOWN, 0.4f),
        ),
        KiraEmotion.THINKING to listOf(
            BlendShapeWeight("Thinking", 0.5f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKUP, 0.3f),
        ),
        KiraEmotion.SURPRISED to listOf(
            BlendShapeWeight("Surprised", 0.8f),
            BlendShapeWeight(VrmBlendShapePreset.VISEME_O, 0.5f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKUP, 0.15f),
        ),
        KiraEmotion.SAD to listOf(
            BlendShapeWeight(VrmBlendShapePreset.SORROW, 0.9f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKDOWN, 0.4f),
        ),
        KiraEmotion.ANGRY to listOf(
            BlendShapeWeight(VrmBlendShapePreset.ANGRY, 0.9f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKLEFT, 0.1f),
        ),
        KiraEmotion.SLEEPY to listOf(
            BlendShapeWeight(VrmBlendShapePreset.BLINK_L, 0.6f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK_R, 0.6f),
            BlendShapeWeight("Sleepy", 0.5f),
        ),
        KiraEmotion.EXCITED to listOf(
            BlendShapeWeight(VrmBlendShapePreset.JOY, 1f),
            BlendShapeWeight(VrmBlendShapePreset.FUN, 0.3f),
            BlendShapeWeight("Excited", 0.5f),
        ),
        KiraEmotion.CONFUSED to listOf(
            BlendShapeWeight("Confused", 0.8f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKLEFT, 0.2f),
        ),
        KiraEmotion.WINK to listOf(
            BlendShapeWeight(VrmBlendShapePreset.BLINK_R, 1f),
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.4f),
        ),
        KiraEmotion.LAUGHING to listOf(
            BlendShapeWeight(VrmBlendShapePreset.JOY, 1f),
            BlendShapeWeight(VrmBlendShapePreset.FUN, 0.5f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK, 0.3f),
            BlendShapeWeight(VrmBlendShapePreset.VISEME_A, 0.6f),
        ),
        KiraEmotion.CRYING to listOf(
            BlendShapeWeight(VrmBlendShapePreset.SORROW, 1f),
            BlendShapeWeight("Crying", 1f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK_L, 0.2f),
        ),
        KiraEmotion.WORRIED to listOf(
            BlendShapeWeight(VrmBlendShapePreset.SORROW, 0.5f),
            BlendShapeWeight("Worried", 0.7f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKDOWN, 0.2f),
        ),
        KiraEmotion.EMBARRASSED to listOf(
            BlendShapeWeight("Blush", 1f),
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.2f),
            BlendShapeWeight(VrmBlendShapePreset.LOOKDOWN, 0.3f),
        ),
        KiraEmotion.SMUG to listOf(
            BlendShapeWeight("Smug", 0.8f),
            BlendShapeWeight(VrmBlendShapePreset.JOY, 0.3f),
            BlendShapeWeight(VrmBlendShapePreset.BLINK_R, 0.2f),
        ),
    )

    /** Full-intensity blend shape weights for [emotion], before scaling by intensity/transition. */
    fun weightsFor(emotion: KiraEmotion): List<BlendShapeWeight> =
        table[emotion] ?: table.getValue(KiraEmotion.IDLE)

    /**
     * Resolves [emotion]'s blend shape weights against a loaded model's actual blend shape
     * groups (looked up by name, case-insensitively - see [VrmModelData.blendShapeGroup]).
     * A shape this particular Kira.vrm doesn't define is silently dropped rather than
     * guessed or substituted, since sending a weight for a non-existent group would be a
     * no-op at best and a crash at worst depending on the rig layer.
     */
    fun resolve(emotion: KiraEmotion, modelData: VrmModelData): Map<String, Float> {
        val resolved = mutableMapOf<String, Float>()
        for (target in weightsFor(emotion)) {
            val group = modelData.blendShapeGroup(target.presetOrGroupName) ?: continue
            resolved[group.name] = target.weight
        }
        return resolved
    }
}
