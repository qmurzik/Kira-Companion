package com.kira.companion.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchGeometryTest {

    @Test
    fun `top of the bubble is the head`() {
        assertEquals(TouchRegion.HEAD, TouchGeometry.regionFor(0.5f, 0.1f))
    }

    @Test
    fun `left and right edges are arms`() {
        assertEquals(TouchRegion.ARM, TouchGeometry.regionFor(0.05f, 0.6f))
        assertEquals(TouchRegion.ARM, TouchGeometry.regionFor(0.95f, 0.6f))
    }

    @Test
    fun `center-lower area is the body`() {
        assertEquals(TouchRegion.BODY, TouchGeometry.regionFor(0.5f, 0.6f))
    }

    @Test
    fun `center tap has a near-zero gaze offset`() {
        val (dx, dy) = TouchGeometry.gazeOffsetFor(0.5f, 0.5f)
        assertEquals(0f, dx, 0.001f)
        assertEquals(0f, dy, 0.001f)
    }

    @Test
    fun `a tap on the right maps to a positive x offset`() {
        val (dx, _) = TouchGeometry.gazeOffsetFor(1f, 0.5f)
        assertEquals(1f, dx, 0.001f)
    }

    @Test
    fun `a tap near the top maps to a positive (up) y offset`() {
        val (_, dy) = TouchGeometry.gazeOffsetFor(0.5f, 0f)
        assertEquals(1f, dy, 0.001f)
    }

    @Test
    fun `a tap near the bottom maps to a negative (down) y offset`() {
        val (_, dy) = TouchGeometry.gazeOffsetFor(0.5f, 1f)
        assertEquals(-1f, dy, 0.001f)
    }
}
