package com.kira.companion.render

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.kira.companion.behavior.SpringBoneSimulator
import com.kira.companion.vrm.VrmHumanBone
import com.kira.companion.vrm.VrmModelData

/**
 * Ties together everything needed to drive Kira's loaded 3D model each frame: the rig
 * ([VrmRig]) and one [SpringBoneSimulator] per hair/cloth spring bone chain the model
 * defines. The Compose 3D host owns loading the actual [FilamentAsset] via SceneView's
 * ModelLoader/ModelNode and constructs this once it's ready; from then on, feed it
 * emotion/gaze/blink state every frame via [update].
 */
class KiraModelAsset(
    engine: Engine,
    asset: FilamentAsset,
    private val modelData: VrmModelData,
) {
    val rig = VrmRig(engine, asset, modelData)

    private val springBones: List<Pair<List<Int>, SpringBoneSimulator>> = modelData.springBoneChains.map { chain ->
        chain.rootNodeIndices to SpringBoneSimulator(chain, jointCount = chain.rootNodeIndices.size)
    }

    private var lastHeadYawDegrees = 0f

    /**
     * Advances one frame:
     *  - applies [blendShapeWeights] (already resolved against this model, see
     *    [com.kira.companion.behavior.BlendShapePresets.resolve]),
     *  - poses the head and both eyes at the given gaze angles,
     *  - steps hair/cloth physics based on how much the head just turned, and applies the
     *    resulting sway to each spring bone chain's nodes.
     */
    fun update(
        deltaSeconds: Float,
        blendShapeWeights: Map<String, Float>,
        headYawDegrees: Float,
        headPitchDegrees: Float,
        eyeYawDegrees: Float,
        eyePitchDegrees: Float,
    ) {
        rig.applyBlendShapeWeights(blendShapeWeights)

        val headDelta = headYawDegrees - lastHeadYawDegrees
        rig.withPoseUpdate {
            rig.setBoneLocalRotation(VrmHumanBone.HEAD, headPitchDegrees, headYawDegrees, 0f)
            rig.setBoneLocalRotation(VrmHumanBone.LEFT_EYE, eyePitchDegrees, eyeYawDegrees, 0f)
            rig.setBoneLocalRotation(VrmHumanBone.RIGHT_EYE, eyePitchDegrees, eyeYawDegrees, 0f)

            for ((rootNodeIndices, simulator) in springBones) {
                simulator.update(deltaSeconds, headDelta)
                for ((jointIndex, nodeIndex) in rootNodeIndices.withIndex()) {
                    val joint = simulator.jointStates.getOrNull(jointIndex) ?: continue
                    rig.setNodeLocalRotation(nodeIndex, 0f, 0f, joint.angleDegrees)
                }
            }
        }

        lastHeadYawDegrees = headYawDegrees
    }
}
