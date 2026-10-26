package com.soniccore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the AutoEQ profile import.
 *
 * Everything here runs on the JVM against the real parser — the same code the
 * Audio → Presets import calls — so the format handling, the refusals and the
 * mapping onto SonicCore's 31-slot curve are pinned without a device.
 */
class AutoEqProfileTest {

    private val parametric = """
        Preamp: -6.5 dB
        Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 220 Hz Gain -2.1 dB Q 1.41
        Filter 3: ON PK Fc 1000 Hz Gain 6.0 dB Q 1.00
        Filter 4: ON HSC Fc 10000 Hz Gain 3.0 dB Q 0.70
        Filter 5: OFF PK Fc 3000 Hz Gain 4.0 dB Q 1.00
    """.trimIndent()

    private fun ok(text: String, name: String = AutoEqProfile.DEFAULT_NAME): AutoEqProfile.Profile {
        val result = AutoEqProfile.parse(text, name)
        assertTrue("expected a parsed profile, got $result", result is AutoEqProfile.Ok)
        return (result as AutoEqProfile.Ok).profile
    }

    // ── ParametricEQ ──

    @Test
    fun parsesParametricProfileWithPreampAndFilters() {
        val profile = ok(parametric, "Sennheiser HD 650")
        assertEquals("Sennheiser HD 650", profile.name)
        assertEquals("ParametricEQ", profile.sourceFormat)
        assertEquals(-6.5f, AutoEqProfile.preampDb(profile), 0.0001f)
        // The OFF slot is not a filter: four enabled filters remain.
        assertEquals(4, profile.filters.size)
        assertEquals(
            AutoEqProfile.Filter(AutoEqProfile.FilterType.LOW_SHELF, 105.0, 5.5, 0.70),
            profile.filters.first()
        )
        assertEquals(AutoEqProfile.FilterType.HIGH_SHELF, profile.filters.last().type)
    }

    @Test
    fun evaluatesParametricFiltersOntoTheThirtyOneBandCurve() {
        val levels = AutoEqProfile.toBandLevels(ok(parametric, "HD 650"))
        assertEquals(AutoEqProfile.BAND_FREQUENCIES.size, levels.size)
        // 1 kHz is the +6.0 dB peaking filter's own centre.
        assertEquals(6, levels[17].toInt())
        // The 105 Hz low shelf lifts the bottom of the curve and leaves the top alone.
        assertTrue("low shelf did not lift the bottom band", levels[0] in 4..6)
        assertTrue("low shelf lifted the top band too", levels[30] <= 3)
        assertTrue(levels[0] > levels[30])
        levels.forEach {
            assertTrue("band $it left the storable range", it in AutoEqProfile.MIN_BAND_DB..AutoEqProfile.MAX_BAND_DB)
        }
    }

    @Test
    fun clampsAnExtremeGainToWhatTheEngineCanStore() {
        val levels = AutoEqProfile.toBandLevels(
            ok("Filter 1: ON PK Fc 1000 Hz Gain 30.0 dB Q 1.00")
        )
        assertEquals(AutoEqProfile.MAX_BAND_DB, levels[17].toInt())
    }

    @Test
    fun preampIsClampedToTheAppControlRange() {
        val filter = "Filter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00"
        assertEquals(30f, AutoEqProfile.preampDb(ok("Preamp: 80.0 dB\n$filter")), 0.0001f)
        assertEquals(-30f, AutoEqProfile.preampDb(ok("Preamp: -80.0 dB\n$filter")), 0.0001f)
    }

    // ── GraphicEQ ──

    @Test
    fun parsesGraphicProfileAndInterpolatesInLogFrequency() {
        val profile = ok("GraphicEQ: 20 0.0; 1000 0.0; 20000 -6.0", "Graphic test")
        assertEquals("GraphicEQ", profile.sourceFormat)
        assertTrue(AutoEqProfile.isGraphic(profile))
        val levels = AutoEqProfile.toBandLevels(profile)
        // The three measurement points sit exactly on bands.
        assertEquals(0, levels[0].toInt())
        assertEquals(0, levels[17].toInt())
        assertEquals(-6, levels[30].toInt())
        // Halfway in log-frequency between 1 kHz and 20 kHz: 0 dB → -6 dB.
        assertTrue("interpolated band was ${levels[26]}", levels[26] in -4..-2)
    }

    // ── Refusals, never guesses ──

    @Test
    fun refusesTextThatIsNotAnAutoEqProfile() {
        assertTrue(AutoEqProfile.parse("""{"neoneq_preset":1}""") is AutoEqProfile.Unsupported)
        assertTrue(AutoEqProfile.parse("hello world") is AutoEqProfile.Unsupported)
        assertTrue(AutoEqProfile.parse("") is AutoEqProfile.Empty)
        assertTrue(AutoEqProfile.parse("   \n  ") is AutoEqProfile.Empty)
    }

    @Test
    fun refusesAFilterTypeItCannotRepresentAsABand() {
        val lowPass = "Filter 1: ON LP Fc 1000 Hz Gain 0.0 dB Q 0.70"
        assertTrue(AutoEqProfile.parse(lowPass) is AutoEqProfile.Unsupported)
        // A profile with only disabled slots carries no correction either.
        assertTrue(
            AutoEqProfile.parse("Filter 1: OFF PK Fc 1000 Hz Gain 3.0 dB Q 1.00") is
                AutoEqProfile.Unsupported
        )
    }

    @Test
    fun refusesAnUnreasonablyLargePaste() {
        val huge = "Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.00\n" + "x".repeat(AutoEqProfile.MAX_TEXT_CHARS)
        assertTrue(AutoEqProfile.parse(huge) is AutoEqProfile.Unsupported)
    }

    @Test
    fun sanitisesTheSuppliedName() {
        val profile = ok(parametric, "  \"HD\t650\"\n ")
        assertEquals("HD 650", profile.name)
        assertEquals(AutoEqProfile.DEFAULT_NAME, ok(parametric, "   ").name)
    }

    @Test
    fun detectsAutoEqTextButNotAJsonBundle() {
        assertTrue(AutoEqProfile.looksLikeAutoEq(parametric))
        assertTrue(AutoEqProfile.looksLikeAutoEq("GraphicEQ: 20 0; 200 1"))
        assertFalse(AutoEqProfile.looksLikeAutoEq("""{"neoneq_preset":1,"levels":[0,1]}"""))
    }
}
