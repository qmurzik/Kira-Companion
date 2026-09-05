package com.kira.companion.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kira.companion.behavior.BlendShapePresets
import com.kira.companion.behavior.KiraBehaviorController
import com.kira.companion.vrm.VrmModelData
import io.github.sceneview.ModelNode
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader

/**
 * Renders Kira's real 3D model, loaded from [assetPath] (see [ModelStatusRepository] - this
 * is only ever shown once a model has already been validated as [ModelStatus.Ready]; there
 * is no placeholder/fake character rendered here).
 *
 * Every frame ([SceneView]'s own `onFrame` callback - Filament schedules this at the
 * display's refresh rate while the view is visible, and SceneView stops issuing frames
 * entirely once it isn't, which is the "don't render at full rate all the time" battery
 * behavior for the in-app screens; the overlay applies its own additional throttling, see
 * task tracking for that work) this reads [controller]'s current emotion/gaze and pushes
 * the resulting blend shape weights and bone rotations into the loaded model via
 * [KiraModelAsset].
 */
@Composable
fun VrmCharacterHost(
    modelData: VrmModelData,
    assetPath: String,
    controller: KiraBehaviorController,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = remember(modelLoader, assetPath) { modelLoader.createModelInstance(assetPath) }
    val kiraModelAsset = remember(modelInstance) { KiraModelAsset(engine, modelInstance.model, modelData) }

    // A single-element array used as a mutable cell that survives recomposition without
    // being Compose state - unlike mutableStateOf, writing to it never triggers a
    // recomposition, which matters since this is written on every single rendered frame.
    val lastFrameTimeNanos = remember { longArrayOf(0L) }

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        onFrame = { frameTimeNanos ->
            val previous = lastFrameTimeNanos[0]
            val deltaSeconds = if (previous == 0L) 0f else (frameTimeNanos - previous) / 1_000_000_000f
            lastFrameTimeNanos[0] = frameTimeNanos

            controller.update((deltaSeconds * 1000f).toLong())
            val gaze = controller.gaze.current
            kiraModelAsset.update(
                deltaSeconds = deltaSeconds,
                blendShapeWeights = BlendShapePresets.resolve(controller.emotion.emotion.value, modelData),
                headYawDegrees = gaze.headAngles.yawDegrees,
                headPitchDegrees = gaze.headAngles.pitchDegrees,
                eyeYawDegrees = gaze.eyeAngles.yawDegrees,
                eyePitchDegrees = gaze.eyeAngles.pitchDegrees,
            )
        },
    ) {
        ModelNode(modelInstance = modelInstance)
    }
}
