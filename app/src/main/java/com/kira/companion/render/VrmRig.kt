package com.kira.companion.render

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.kira.companion.vrm.VrmHumanBone
import com.kira.companion.vrm.VrmModelData

/**
 * Bridges parsed VRM data ([VrmModelData]) to a loaded Filament asset: resolves humanoid
 * bones and blend shape targets to actual entities/instances by node name - VRM binds
 * reference glTF node/mesh *indices*, but Filament only exposes name-based entity lookup
 * ([FilamentAsset.getFirstEntityByName]) - then applies bone rotations and blend shape
 * weights each frame.
 *
 * This is the one place in the app that touches raw Filament APIs directly. Everything it
 * depends on (which bone to move, which weights to apply) is computed by pure, unit-tested
 * logic elsewhere ([com.kira.companion.behavior], [Mat4]); this class itself cannot be
 * unit-tested (no Filament runtime outside a real device/emulator), so it is kept as thin
 * as possible: resolve entities once, then just push numbers into Filament's managers.
 *
 * API usage here (Engine.getTransformManager/getRenderableManager, FilamentAsset's entity
 * lookup, RenderableManager.setMorphWeights/getMorphTargetCount, TransformManager's
 * get/setTransform + open/commitLocalTransformTransaction) was verified against Filament
 * 1.72.1 source (the version SceneView 4.34.0 pins) rather than guessed.
 */
class VrmRig(
    engine: Engine,
    private val asset: FilamentAsset,
    private val modelData: VrmModelData,
) {
    private val transformManager = engine.transformManager
    private val renderableManager = engine.renderableManager

    /** Bind-pose local transform per *node index* (not just humanoid bones - hair/cloth
     *  spring bone chains reference arbitrary non-humanoid nodes too), resolved lazily on
     *  first use and cached so poses are always applied relative to rest, never clobbering
     *  translation/scale. Declared before [boneEntities] below, which resolves entities
     *  through the same cache during construction. */
    private val bindLocalTransforms = mutableMapOf<Int, FloatArray>()
    private val entityByNodeIndex = mutableMapOf<Int, Int?>()

    /** Entity id per humanoid bone, resolved once at load time by node name. Bones the model
     *  doesn't define (or whose node has no name) are simply absent from this map. */
    private val boneEntities: Map<VrmHumanBone, Int> = modelData.humanBoneNodeIndices
        .mapNotNull { (bone, nodeIndex) ->
            val entity = resolveEntity(nodeIndex) ?: return@mapNotNull null
            bone to entity
        }.toMap()

    /** Per-mesh-entity morph weight buffers, reused across frames to avoid reallocating. */
    private val morphBuffers = mutableMapOf<Int, FloatArray>()

    fun hasBone(bone: VrmHumanBone): Boolean = boneEntities.containsKey(bone)

    private fun resolveEntity(nodeIndex: Int): Int? = entityByNodeIndex.getOrPut(nodeIndex) {
        val name = modelData.nodeNameFor(nodeIndex) ?: return@getOrPut null
        asset.getFirstEntityByName(name).takeIf { it != 0 }
    }

    private fun bindTransformFor(entity: Int, nodeIndex: Int): FloatArray = bindLocalTransforms.getOrPut(nodeIndex) {
        val out = FloatArray(16)
        transformManager.getTransform(transformManager.getInstance(entity), out)
        out
    }

    /** Rotates [bone] by the given Euler angles (degrees), relative to its bind pose. */
    fun setBoneLocalRotation(bone: VrmHumanBone, pitchDegrees: Float, yawDegrees: Float, rollDegrees: Float) {
        val nodeIndex = modelData.humanBoneNodeIndices[bone] ?: return
        setNodeLocalRotation(nodeIndex, pitchDegrees, yawDegrees, rollDegrees)
    }

    /**
     * Rotates the entity at glTF node [nodeIndex] by the given Euler angles (degrees),
     * relative to its bind pose. Used both for humanoid bones (via [setBoneLocalRotation])
     * and for arbitrary hair/cloth spring-bone nodes, which aren't part of the humanoid
     * bone set at all.
     */
    fun setNodeLocalRotation(nodeIndex: Int, pitchDegrees: Float, yawDegrees: Float, rollDegrees: Float) {
        val entity = resolveEntity(nodeIndex) ?: return
        val bind = bindTransformFor(entity, nodeIndex)
        val rotation = Mat4.fromEulerDegrees(pitchDegrees, yawDegrees, rollDegrees)
        val composed = Mat4.multiply(bind, rotation)
        transformManager.setTransform(transformManager.getInstance(entity), composed)
    }

    /** Wraps several [setBoneLocalRotation] calls so the hierarchy is only walked once. */
    fun withPoseUpdate(block: () -> Unit) {
        transformManager.openLocalTransformTransaction()
        try {
            block()
        } finally {
            transformManager.commitLocalTransformTransaction()
        }
    }

    /**
     * Applies a set of blend shape group weights (group name -> 0..1, from
     * [com.kira.companion.behavior.BlendShapePresets.resolve]) to every mesh entity they
     * bind to. A group's binds can span multiple meshes at different weights each; this
     * accumulates all active groups per mesh entity before pushing a single
     * [com.google.android.filament.RenderableManager.setMorphWeights] call per entity.
     */
    fun applyBlendShapeWeights(groupWeights: Map<String, Float>) {
        for (buffer in morphBuffers.values) buffer.fill(0f)

        for ((groupName, weight) in groupWeights) {
            val group = modelData.blendShapeGroup(groupName) ?: continue
            for (bind in group.binds) {
                val nodeIndex = modelData.meshNodeIndex[bind.meshIndex] ?: continue
                val nodeName = modelData.nodeNameFor(nodeIndex) ?: continue
                val entity = asset.getFirstEntityByName(nodeName)
                if (entity == 0) continue
                val instance = renderableManager.getInstance(entity)
                if (instance == 0) continue
                val buffer = morphBuffers.getOrPut(entity) {
                    FloatArray(renderableManager.getMorphTargetCount(instance))
                }
                if (bind.morphTargetIndex in buffer.indices) {
                    buffer[bind.morphTargetIndex] =
                        (buffer[bind.morphTargetIndex] + bind.weight * weight).coerceIn(0f, 1f)
                }
            }
        }

        for ((entity, buffer) in morphBuffers) {
            val instance = renderableManager.getInstance(entity)
            if (instance != 0) renderableManager.setMorphWeights(instance, buffer, 0)
        }
    }
}
