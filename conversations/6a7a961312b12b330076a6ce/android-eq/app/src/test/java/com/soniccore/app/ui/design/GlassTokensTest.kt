package com.soniccore.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soniccore.app.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Liquid Glass tokens.
 *
 * These invariants are what the whole app's appearance rests on, and they are
 * exactly the ones that had drifted apart before the rebuild: a legibility
 * floor for surface opacity, one blur policy, and a single ink pair shared by
 * all twelve themes. See `docs/liquid-glass-defects.md` (GL-02, GL-08, GL-09).
 */
class GlassTokensTest {

    @Test
    fun darkPaletteIsTheSpecifiedAppearance() {
        assertEquals(Color(0xFF080D18), SonicCoreGlassDark.background)
        assertEquals(Color(0xFF0D1422), SonicCoreGlassDark.backgroundRaised)
        assertEquals(Color(0xFF121C2C), SonicCoreGlassDark.card)
        assertEquals(Color(0xFF182438), SonicCoreGlassDark.elevated)
        assertEquals(Color(0xFFF3F7FF), SonicCoreGlassDark.textPrimary)
        assertEquals(Color(0xFF9EACC2), SonicCoreGlassDark.textSecondary)
        assertEquals(Color(0xFF48DDF5), SonicCoreGlassDark.audioAccent)
        assertEquals(Color(0xFFA78BFA), SonicCoreGlassDark.customizationAccent)
    }

    @Test
    fun themeDefaultsAreTheSpecifiedAppearance() {
        val palette = SonicCorePalette()
        assertEquals(SonicCoreGlassDark.background, palette.background)
        assertEquals(SonicCoreGlassDark.backgroundRaised, palette.surface)
        assertEquals(SonicCoreGlassDark.elevated, palette.elevatedSurface)
        assertEquals(SonicCoreGlassDark.textPrimary, palette.primaryText)
        assertEquals(SonicCoreGlassDark.textSecondary, palette.secondaryText)
        assertEquals(SonicCoreGlassDark.audioAccent, palette.primary)
        assertEquals(SonicCoreGlassDark.customizationAccent, palette.secondary)
    }

    @Test
    fun noGlassLevelFallsBelowTheLegibilityFloor() {
        (0..2).forEach { level ->
            assertTrue(
                "backing at level $level must stay readable",
                GlassOpacity.backing(level) >= GlassOpacity.MIN_BACKING
            )
        }
        // Out-of-range values must land on a bounded endpoint, not on 0.
        assertTrue(GlassOpacity.backing(-5) >= GlassOpacity.MIN_BACKING)
        assertTrue(GlassOpacity.backing(99) >= GlassOpacity.MIN_BACKING)
    }

    @Test
    fun higherGlassLevelsGetCrisperNotLessReadable() {
        assertTrue(GlassOpacity.backing(0) > GlassOpacity.backing(1))
        assertTrue(GlassOpacity.backing(1) > GlassOpacity.backing(2))
        assertTrue(GlassOpacity.edge(0) < GlassOpacity.edge(1))
        assertTrue(GlassOpacity.edge(1) < GlassOpacity.edge(2))
        assertTrue(GlassOpacity.highlight(0) < GlassOpacity.highlight(1))
        assertTrue(GlassOpacity.highlight(1) < GlassOpacity.highlight(2))
    }

    @Test
    fun blurRunsOnlyWhereThePlatformSupportsIt() {
        assertFalse(glassNativeBlurSupported(30))
        assertTrue(glassNativeBlurSupported(31))
        assertTrue(glassNativeBlurSupported(34))

        // No RenderEffect: no blur pass at all — the translucent surface carries
        // the glass instead.
        (0..2).forEach { level ->
            assertEquals(0.dp, GlassBlur.radiusDp(level, nativeBlurAvailable = false))
        }
        assertTrue(GlassBlur.usesFallbackSurface(nativeBlurAvailable = false))
        assertFalse(GlassBlur.usesFallbackSurface(nativeBlurAvailable = true))

        // With blur available the subtle level still skips the pass, and the
        // higher levels stay bounded so they cannot stack into a heavy effect.
        assertEquals(0.dp, GlassBlur.radiusDp(0, nativeBlurAvailable = true))
        assertTrue(GlassBlur.radiusDp(2, true) > GlassBlur.radiusDp(1, true))
        assertTrue(GlassBlur.radiusDp(1, true) > 0.dp)
    }

    @Test
    fun everyThemeSharesTheSpecifiedInkAndOneGlassVocabulary() {
        val themes = Themes.ALL
        assertEquals(12, themes.size)
        themes.forEach { theme ->
            assertEquals(
                "${theme.id} must use the shared primary ink",
                Color(0xFFF3F7FF),
                theme.darkSurfaces.text
            )
            assertEquals(
                "${theme.id} must use the shared secondary ink",
                Color(0xFF9EACC2),
                theme.darkSurfaces.textSoft
            )
        }
    }

    @Test
    fun theDefaultThemeIsTheSpecifiedSurfaceRamp() {
        val classic = Themes.byId("classic").darkSurfaces
        assertEquals(Color(0xFF080D18), classic.bg)
        assertEquals(Color(0xFF0D1422), classic.surface)
        assertEquals(Color(0xFF121C2C), classic.card)
        assertEquals(Color(0xFF182438), classic.cardDeep)

        val theme = Themes.byId("classic")
        assertEquals(Color(0xFF48DDF5), theme.primary)
        assertEquals(Color(0xFFA78BFA), theme.accent)
    }

    @Test
    fun radiiAreOrderedSoSurfacesCannotDriftApart() {
        assertTrue(GlassRadius.chip < GlassRadius.tile)
        assertTrue(GlassRadius.tile < GlassRadius.control)
        assertTrue(GlassRadius.control < GlassRadius.card)
        assertTrue(GlassRadius.card < GlassRadius.surface)
        assertTrue(GlassRadius.surface < GlassRadius.sheet)
        assertTrue(GlassRadius.sheet < GlassRadius.navigation)
        assertEquals(50.dp, GlassRadius.pill)
    }

    @Test
    fun touchTargetMeetsTheAccessibilityMinimum() {
        assertEquals(48.dp, GlassSpacing.touchTarget)
    }
}
