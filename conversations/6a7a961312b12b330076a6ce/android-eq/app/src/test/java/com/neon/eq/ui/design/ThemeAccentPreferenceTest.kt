package com.neon.eq.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAccentPreferenceTest {
    @Test
    fun serialiseRoundTripsEveryOfferedSwatch() {
        assertTrue(ThemeAccentPreference.SWATCHES.size >= 4)
        ThemeAccentPreference.SWATCHES.forEach { (_, argb) ->
            assertEquals(argb, ThemeAccentPreference.parse(ThemeAccentPreference.serialize(argb)))
        }
    }

    @Test
    fun invalidValuesFallBackToTheDefault() {
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse(null))
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse(""))
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse("   "))
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse("not a colour"))
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse("#GGGGGG"))
        assertEquals(ThemeAccentPreference.DEFAULT, ThemeAccentPreference.parse("#123"))
    }

    @Test
    fun sixDigitHexIsTreatedAsOpaque() {
        assertEquals(0xFF00D9FF.toInt(), ThemeAccentPreference.parse("#00D9FF"))
        assertEquals(0xFF8B5CF6.toInt(), ThemeAccentPreference.parse("8B5CF6"))
    }

    @Test
    fun eightDigitHexKeepsItsAlpha() {
        assertEquals(0x8000D9FF.toInt(), ThemeAccentPreference.parse("#8000D9FF"))
    }

    @Test
    fun labelNamesOfferedSwatchesAndCallsTheRestCustom() {
        ThemeAccentPreference.SWATCHES.forEach { (name, argb) ->
            assertEquals(name, ThemeAccentPreference.label(argb))
        }
        assertEquals("Custom", ThemeAccentPreference.label(0xFF123456.toInt()))
    }
}
