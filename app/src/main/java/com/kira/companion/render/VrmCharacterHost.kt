package com.kira.companion.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kira.companion.behavior.BlendShapePresets
import com.kira.companion.behavior.KiraBehaviorController
import com.kira.companion.vrm.VrmModelData
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.model.model
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
 * behavior baked into SceneView itself) this reads [controller]'s current emotion/gaze and
 * pushes the resulting blend shape weights and bone rotations into the loaded model via
 * [KiraModelAsset]. [renderQuality] is the other half of the battery story - see its default
 * value below for how the overlay trades fidelity for cost.
 */
@Composable
fun VrmCharacterHost(
    modelData: VrmModelData,
    assetPath: String,
    controller: KiraBehaviorController,
    modifier: Modifier = Modifier,
    // The overlay bubble is small and always on screen, so it renders with
    // RenderQuality.Performance (shadows/SSAO/bloom off, dynamic resolution) - the
    // in-app screens keep the higher-fidelity Default preset since they're a deliberate,
    // full-attention view rather than a constant background companion.
    renderQuality: RenderQuality = RenderQuality.Default,
    // Caps how often behavior/physics are actually re-evaluated (0 = every rendered frame).
    // Filament still presents frames at the display's native rate either way - only the
    // CPU-side pose/blend-shape update is throttled, which is invisible (the same pose is
    // simply redrawn a couple of extra times) but meaningfully cheaper. The overlay, which
    // is on screen indefinitely rather than for a deliberate viewing session, passes a real
    // value here; the in-app screens leave it uncapped.
    minUpdateIntervalMillis: Long = 0L,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = remember(modelLoader, assetPath) { modelLoader.createModelInstance(assetPath) }
    val kiraModelAsset = remember(modelInstance) { KiraModelAsset(engine, modelInstance.model, modelData) }

    // Single-element arrays used as mutable cells that survive recomposition without being
    // Compose state - unlike mutableStateOf, writing to them never triggers a recomposition,
    // which matters since these are written on every single rendered frame.
    val lastFrameTimeNanos = remember { longArrayOf(0L) }
    val accumulatedNanos = remember { longArrayOf(0L) }
    val minUpdateIntervalNanos = minUpdateIntervalMillis * 1_000_000L

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        renderQuality = renderQuality,
        onFrame = { frameTimeNanos ->
            val previous = lastFrameTimeNanos[0]
            val frameDeltaNanos = if (previous == 0L) 0L else frameTimeNanos - previous
            lastFrameTimeNanos[0] = frameTimeNanos
            accumulatedNanos[0] += frameDeltaNanos

            if (accumulatedNanos[0] >= minUpdateIntervalNanos) {
                val deltaSeconds = accumulatedNanos[0] / 1_000_000_000f
                accumulatedNanos[0] = 0L

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
            }
        },
    ) {
        ModelNode(modelInstance = modelInstance)
    }
}
