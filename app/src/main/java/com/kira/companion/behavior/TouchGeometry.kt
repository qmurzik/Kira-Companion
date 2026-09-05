package com.kira.companion.behavior

/**
 * Maps a raw tap position within Kira's bubble/body into the [TouchRegion] it landed on and
 * a normalized gaze look-at offset - pure geometry, no Android dependency, so it's directly
 * unit-testable. The overlay/UI layer supplies pixel coordinates and the bubble's size;
 * everything else about *how* Kira reacts lives in [TouchReactionController].
 */
object TouchGeometry {

    /**
     * Classifies a tap at normalized position ([normalizedX], [normalizedY], both 0..1 with
     * the origin at the top-left) into a body region: the top band reads as her head, the
     * left/right bands as her arms/sides, everything else as her body/torso.
     */
    fun regionFor(normalizedX: Float, normalizedY: Float): TouchRegion = when {
        normalizedY < HEAD_BAND_HEIGHT -> TouchRegion.HEAD
        normalizedX < SIDE_BAND_WIDTH || normalizedX > 1f - SIDE_BAND_WIDTH -> TouchRegion.ARM
        else -> TouchRegion.BODY
    }

    /**
     * Converts a normalized tap position (0..1, origin top-left) into a gaze look-at offset
     * (roughly -1..1, origin center, y-up) for [GazeMath] - so Kira looks toward wherever she
     * was actually touched.
     */
    fun gazeOffsetFor(normalizedX: Float, normalizedY: Float): Pair<Float, Float> =
        (normalizedX - 0.5f) * 2f to -(normalizedY - 0.5f) * 2f

    private const val HEAD_BAND_HEIGHT = 0.38f
    private const val SIDE_BAND_WIDTH = 0.22f
}
