package com.docvault.app.ui.screens.scan.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers only the pure numeric helpers — deliberately split out of
 * [PageTransforms.apply] so this arithmetic is testable without a device or
 * Robolectric (see the type's KDoc).
 */
class PageTransformsTest {

    @Test
    fun `normalizeRotation wraps a positive multiple of 360 to zero`() {
        assertEquals(0, PageTransforms.normalizeRotation(360))
        assertEquals(0, PageTransforms.normalizeRotation(720))
    }

    @Test
    fun `normalizeRotation wraps a negative rotation into 0-359`() {
        assertEquals(270, PageTransforms.normalizeRotation(-90))
        assertEquals(180, PageTransforms.normalizeRotation(-540))
    }

    @Test
    fun `normalizeRotation keeps an in-range value unchanged`() {
        assertEquals(90, PageTransforms.normalizeRotation(90))
        assertEquals(180, PageTransforms.normalizeRotation(180))
        assertEquals(270, PageTransforms.normalizeRotation(270))
    }

    @Test
    fun `normalizeRotation composes repeated 90-degree steps correctly`() {
        var rotation = 0
        repeat(5) { rotation = PageTransforms.normalizeRotation(rotation + 90) }
        // Five quarter-turns is one full turn plus one more quarter-turn.
        assertEquals(90, rotation)
    }

    @Test
    fun `clampBrightness leaves an in-range value unchanged`() {
        assertEquals(0f, PageTransforms.clampBrightness(0f))
        assertEquals(50f, PageTransforms.clampBrightness(50f))
    }

    @Test
    fun `clampBrightness clamps to the -100 to 100 range`() {
        assertEquals(100f, PageTransforms.clampBrightness(500f))
        assertEquals(-100f, PageTransforms.clampBrightness(-500f))
    }

    @Test
    fun `clampContrast leaves the identity value unchanged`() {
        assertEquals(1f, PageTransforms.clampContrast(1f))
    }

    @Test
    fun `clampContrast clamps to the 0_5 to 2 range`() {
        assertEquals(2f, PageTransforms.clampContrast(10f))
        assertEquals(0.5f, PageTransforms.clampContrast(0f))
    }
}
