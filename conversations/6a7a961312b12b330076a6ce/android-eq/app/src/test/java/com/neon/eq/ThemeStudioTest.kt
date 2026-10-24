package com.neon.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioTest {
    @Test
    fun themeStudioOffersEightCompleteThemes() {
        assertEquals(8, Themes.ALL.size)
        assertEquals(8, Themes.ALL.map { it.id }.toSet().size)
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
    fun palettesHaveDistinctSurfaceAndAccentTokens() {
        assertNotEquals(Themes.OBSIDIAN.darkSurfaces.bg, Themes.MIDNIGHT.darkSurfaces.bg)
        assertNotEquals(Themes.AURORA.primary, Themes.PURPLE_NEBULA.primary)
    }
}
