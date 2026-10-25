package com.soniccore.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassIntensityPreferenceTest {
    @Test
    fun existingPreferenceKeyAndStringValuesRemainStable() {
        assertEquals("glass", GlassIntensityPreference.KEY)
        assertEquals("0", GlassIntensityPreference.serialize(0))
        assertEquals("1", GlassIntensityPreference.serialize(1))
        assertEquals("2", GlassIntensityPreference.serialize(2))
    }

    @Test
    fun persistedLevelsRestoreAndInvalidValuesUseTheExistingDefault() {
        assertEquals(0, GlassIntensityPreference.parse("0"))
        assertEquals(1, GlassIntensityPreference.parse("1"))
        assertEquals(2, GlassIntensityPreference.parse("2"))
        assertEquals(1, GlassIntensityPreference.parse(null))
        assertEquals(1, GlassIntensityPreference.parse("invalid"))
    }

    @Test
    fun outOfRangeValuesAreClampedToSupportedIntensityLevels() {
        assertEquals(0, GlassIntensityPreference.parse("-8"))
        assertEquals(2, GlassIntensityPreference.parse("9"))
        assertEquals("0", GlassIntensityPreference.serialize(-8))
        assertEquals("2", GlassIntensityPreference.serialize(9))
    }

    @Test
    fun namedGlassLevelsKeepTheExistingPersistedNumericValues() {
        assertEquals(
            listOf("Subtle" to 0, "Liquid" to 1, "Crystal" to 2),
            GlassIntensityPreference.LEVELS
        )
    }
}
