package com.soniccore.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassMaterialTest {
    @Test
    fun intensityLevelsIncreaseDepthAndReflectionWithoutReducingReadabilitySurface() {
        val subtle = liquidGlassMaterialSpec(0)
        val liquid = liquidGlassMaterialSpec(1)
        val crystal = liquidGlassMaterialSpec(2)

        assertTrue(subtle.surfaceAlpha > liquid.surfaceAlpha)
        assertTrue(liquid.surfaceAlpha > crystal.surfaceAlpha)
        assertTrue(subtle.reflectionAlpha < liquid.reflectionAlpha)
        assertTrue(liquid.reflectionAlpha < crystal.reflectionAlpha)
        assertTrue(subtle.edgeAlpha < liquid.edgeAlpha)
        assertTrue(liquid.edgeAlpha < crystal.edgeAlpha)
    }

    @Test
    fun invalidLevelsUseBoundedEndpointMaterials() {
        assertEquals(liquidGlassMaterialSpec(0), liquidGlassMaterialSpec(-1))
        assertEquals(liquidGlassMaterialSpec(2), liquidGlassMaterialSpec(99))
    }
}
