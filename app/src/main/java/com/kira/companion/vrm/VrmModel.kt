package com.kira.companion.vrm

/**
 * The standard VRM/Unity humanoid bone names we care about for facial/upper-body
 * animation. VRM files may define more (fingers, legs, feet); we only resolve the
 * subset actually used by [com.kira.companion.render.VrmRig].
 */
enum class VrmHumanBone(val vrmName: String) {
    HIPS("hips"),
    SPINE("spine"),
    CHEST("chest"),
    UPPER_CHEST("upperChest"),
    NECK("neck"),
    HEAD("head"),
    LEFT_EYE("leftEye"),
    RIGHT_EYE("rightEye"),
    JAW("jaw"),
    LEFT_SHOULDER("leftShoulder"),
    LEFT_UPPER_ARM("leftUpperArm"),
    RIGHT_SHOULDER("rightShoulder"),
    RIGHT_UPPER_ARM("rightUpperArm"),
    ;

    companion object {
        private val byVrmName = entries.associateBy { it.vrmName }
        fun fromVrmName(name: String): VrmHumanBone? = byVrmName[name]
    }
}

/** One glTF morph-target binding inside a VRM blend shape group. */
data class VrmBlendShapeBind(
    val meshIndex: Int,
    val morphTargetIndex: Int,
    /** Normalized 0..1 (VRM stores 0..100 in the file; we divide by 100 on parse). */
    val weight: Float,
)

/** A named facial expression group (VRM 0.x "blendShapeGroup"), e.g. "Joy", "Blink". */
data class VrmBlendShapeGroup(
    val name: String,
    val presetName: String,
    val binds: List<VrmBlendShapeBind>,
)

/** One VRM 0.x "secondary animation" spring-bone chain (hair, cloth, accessories). */
data class VrmSpringBoneChain(
    val rootNodeIndices: List<Int>,
    val stiffness: Float,
    val gravityPower: Float,
    val dragForce: Float,
    val hitRadius: Float,
)

enum class VrmSpecVersion { VRM_0, VRM_1 }

/**
 * Everything we extracted from a .vrm file's JSON chunk (see [GlbReader] +
 * [VrmJsonParser]). Node indices refer to the glTF `nodes` array; resolving them to
 * actual renderable Filament entities happens in [com.kira.companion.render.VrmRig]
 * using [nodeNameFor] (Filament's `FilamentAsset.getFirstEntityByName` looks up
 * entities by their original glTF node name).
 */
data class VrmModelData(
    val specVersion: VrmSpecVersion,
    val humanBoneNodeIndices: Map<VrmHumanBone, Int>,
    val nodeNames: List<String?>,
    val meshNodeIndex: Map<Int, Int>,
    val blendShapeGroups: List<VrmBlendShapeGroup>,
    val springBoneChains: List<VrmSpringBoneChain>,
    val title: String?,
    val authorName: String?,
) {
    fun nodeNameFor(nodeIndex: Int): String? = nodeNames.getOrNull(nodeIndex)

    fun nodeNameForMesh(meshIndex: Int): String? =
        meshNodeIndex[meshIndex]?.let { nodeNameFor(it) }

    fun blendShapeGroup(name: String): VrmBlendShapeGroup? =
        blendShapeGroups.firstOrNull { it.name.equals(name, ignoreCase = true) || it.presetName.equals(name, ignoreCase = true) }

    /** True if the file has at minimum a head bone - the minimum needed for our facial system. */
    fun isUsable(): Boolean = humanBoneNodeIndices.containsKey(VrmHumanBone.HEAD)
}
