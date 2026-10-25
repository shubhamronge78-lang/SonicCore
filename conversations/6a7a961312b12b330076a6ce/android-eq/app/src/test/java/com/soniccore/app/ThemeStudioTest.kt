package com.soniccore.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioTest {
    /** The ids every stored preference in the wild can still resolve to. */
    private val legacyIds = listOf(
        "classic", "aurora", "synthwave", "glacier", "emerald", "ember", "midnight", "adaptive"
    )

    @Test
    fun themeStudioOffersTwelveCompleteThemes() {
        assertEquals(12, Themes.ALL.size)
        assertEquals(12, Themes.ALL.map { it.id }.toSet().size)
        assertTrue(Themes.ALL.all { it.darkSurfaces.bg != it.lightSurfaces.bg })
    }

    @Test
    fun savedThemeIdsRemainSupported() {
        assertEquals(Themes.OBSIDIAN, Themes.byId("classic"))
        assertEquals(Themes.PURPLE_NEBULA, Themes.byId("synthwave"))
        assertEquals(Themes.CHAMPAGNE, Themes.byId("ember"))
        assertEquals(Themes.EMERALD, Themes.byId("emerald"))
        assertEquals(Themes.ARCTIC, Themes.byId("glacier"))
    }

    @Test
    fun catalogueKeepsEveryLegacyIdAndAddsThePlatformThemes() {
        val ids = Themes.ALL.map { it.id }
        legacyIds.forEach { id -> assertTrue("$id must stay in the catalogue", ids.contains(id)) }
        listOf(
            Themes.SYSTEM_ID,
            Themes.LIGHT_STUDIO_ID,
            Themes.CUSTOM_ACCENT_ID,
            Themes.DYNAMIC_WALLPAPER_ID
        ).forEach { id -> assertTrue("$id must be in the catalogue", ids.contains(id)) }
        assertEquals(legacyIds + Themes.ALL.takeLast(4).map { it.id }, ids)
    }

    @Test
    fun unknownThemeIdsStillFallBackToTheClassicPalette() {
        assertEquals(Themes.OBSIDIAN, Themes.byId(null))
        assertEquals(Themes.OBSIDIAN, Themes.byId("does-not-exist"))
    }

    @Test
    fun platformThemesDeclareTheAppearanceModeTheyOwn() {
        assertEquals("system", Themes.SYSTEM_DEFAULT.preferredMode)
        assertEquals("light", Themes.LIGHT_STUDIO.preferredMode)
        // Legacy themes must keep leaving the user's appearance mode alone.
        legacyIds.forEach { id -> assertNull(Themes.byId(id).preferredMode) }
    }

    @Test
    fun customAccentRebuildsThePaletteFromTheChosenSwatch() {
        val cyan = Themes.customAccent(0xFF00D9FF.toInt())
        val violet = Themes.customAccent(0xFF8B5CF6.toInt())
        assertEquals(Themes.CUSTOM_ACCENT_ID, cyan.id)
        assertEquals(Color(0xFF00D9FF), cyan.primary)
        assertNotEquals(cyan.primary, violet.primary)
        // The derived tokens differ from the raw swatch, so the theme still has depth.
        assertNotEquals(cyan.primary, cyan.accent)
        assertNotEquals(cyan.primary, cyan.secondary)
    }

    @Test
    fun dynamicWallpaperUsesTheWallpaperTintAndFallsBackWhenUnavailable() {
        val tinted = Themes.dynamicWallpaper(0xFFFF4FD8.toInt())
        assertEquals(Themes.DYNAMIC_WALLPAPER_ID, tinted.id)
        assertEquals(Color(0xFFFF4FD8), tinted.primary)

        val fallback = Themes.dynamicWallpaper(null)
        assertEquals(Themes.DYNAMIC_WALLPAPER_ID, fallback.id)
        assertNotEquals(tinted.primary, fallback.primary)
    }

    @Test
    fun catalogueCarriesTheLiveAccentAndWallpaperInputs() {
        val catalog = Themes.catalog(0xFF2BE8A5.toInt(), 0xFFFFB347.toInt())
        assertEquals(12, catalog.size)
        assertEquals(Color(0xFF2BE8A5), catalog[10].primary)
        assertEquals(Color(0xFFFFB347), catalog[11].primary)
    }

    @Test
    fun palettesHaveDistinctSurfaceAndAccentTokens() {
        assertNotEquals(Themes.OBSIDIAN.darkSurfaces.bg, Themes.MIDNIGHT.darkSurfaces.bg)
        assertNotEquals(Themes.AURORA.primary, Themes.PURPLE_NEBULA.primary)
        assertNotEquals(Themes.SYSTEM_DEFAULT.primary, Themes.LIGHT_STUDIO.primary)
    }
}
