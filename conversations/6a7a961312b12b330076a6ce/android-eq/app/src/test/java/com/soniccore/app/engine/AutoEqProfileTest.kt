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
    fun aHostileNameCannotBecomeAPathOrAMalformedStoredName() {
        // The name comes from the user's paste or from a picked file name, so it
        // is untrusted input that is stored and matched on later.
        val traversal = ok(parametric, "../../data/data/com.soniccore.app/evil").name
        assertFalse("no path separator may survive: “$traversal”", traversal.contains("/"))
        assertFalse("no path separator may survive: “$traversal”", traversal.contains("\\"))
        assertFalse("no traversal may survive: “$traversal”", traversal.contains(".."))

        // Control characters are flattened to spaces, never carried into a stored
        // name (they would break anything that renders, matches or logs it).
        val control = ok(parametric, "H\u0001D\t6\r\n50").name
        assertFalse("no control character may survive", control.any { it.isISOControl() })
        assertEquals("H D 6 50", control)

        // Quotes and a dot prefix are handled too, so a stored name never starts
        // with a dot and never carries a doubled space.
        assertEquals("hidden", ok(parametric, "  \".hidden\"  ").name)

        // A name longer than the cap is truncated to it, never stored whole.
        assertEquals(AutoEqProfile.MAX_NAME_CHARS, ok(parametric, "x".repeat(400)).name.length)
    }

    @Test
    fun detectsAutoEqTextButNotAJsonBundle() {
        assertTrue(AutoEqProfile.looksLikeAutoEq(parametric))
        assertTrue(AutoEqProfile.looksLikeAutoEq("GraphicEQ: 20 0; 200 1"))
        assertFalse(AutoEqProfile.looksLikeAutoEq("""{"neoneq_preset":1,"levels":[0,1]}"""))
    }

    // ── Refusals carry their reason (Phase 4 completion) ──

    private fun refusal(text: String): String {
        val result = AutoEqProfile.parse(text)
        assertTrue("expected a refusal, got $result", result is AutoEqProfile.Unsupported)
        return (result as AutoEqProfile.Unsupported).reason
    }

    @Test
    fun aRefusedFilterTypeIsNamedInsteadOfBeingSilentlyDropped() {
        val reason = refusal("Filter 1: ON LP Fc 1000 Hz Gain 0.0 dB Q 0.70")
        assertTrue("reason was “$reason”", reason.contains("LP"))
        assertTrue(reason.contains("Filter 1"))
        assertTrue(reason.contains("refused"))
    }

    @Test
    fun anOffSlotWithATypeItCannotRepresentIsStillIgnored() {
        // The OFF slot is not part of the profile, so its type never matters.
        val profile = ok(
            "Filter 1: OFF LP Fc 1000 Hz Gain 0.0 dB Q 0.70\n" +
                "Filter 2: ON PK Fc 1000 Hz Gain 2.0 dB Q 1.00"
        )
        assertEquals(1, profile.filters.size)
    }

    @Test
    fun aProfileOfOnlyDisabledFiltersSaysWhyItCarriesNothing() {
        val reason = refusal("Filter 1: OFF PK Fc 1000 Hz Gain 3.0 dB Q 1.00")
        assertTrue("reason was “$reason”", reason.contains("switched off"))
    }

    @Test
    fun plainTextAndForeignJsonAreRefusedWithTheirOwnReason() {
        assertTrue(refusal("hello world").contains("not an AutoEQ"))
        assertTrue(refusal("""{"neoneq_preset":1}""").contains("not an AutoEQ"))
    }

    @Test
    fun anUnreadableFilterValueIsNamedInsteadOfBeingSkipped() {
        val reason = refusal("Filter 7: ON PK Fc 1.2.3 Hz Gain 1.0 dB Q 1.00")
        assertTrue("reason was “$reason”", reason.contains("Filter 7"))
    }

    @Test
    fun anUnusableCentreFrequencyIsRefusedRatherThanMoved() {
        val reason = refusal("Filter 2: ON PK Fc 0 Hz Gain 1.0 dB Q 1.00")
        assertTrue("reason was “$reason”", reason.contains("Filter 2"))
        assertTrue(reason.contains("0 Hz"))
    }

    @Test
    fun anOversizePasteIsRefusedWithItsSizeAndTheLimit() {
        val reason = refusal("Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.00" + "x".repeat(AutoEqProfile.MAX_TEXT_CHARS))
        assertTrue("reason was “$reason”", reason.contains("128 KB"))
    }

    @Test
    fun aGraphicLineThatIsNotFrequencyGainPairsIsRefused() {
        val reason = refusal("GraphicEQ: 20 0.0; oops; 20000 -6.0")
        assertTrue("reason was “$reason”", reason.contains("oops"))
        assertTrue(refusal("GraphicEQ: 20 0.0; 1000 abc; 20000 -6.0").contains("1000 abc"))
        assertTrue(refusal("GraphicEQ: 20 0.0; -5 1.0; 20000 -6.0").contains("unusable frequency"))
    }

    @Test
    fun aGraphicLineWithTooFewPointsIsRefusedWithItsCount() {
        assertTrue(refusal("GraphicEQ: 20 0.0").contains("at least two"))
    }

    // ── Normalisation is reported, never silent ──

    @Test
    fun aFilterGainOutsideTheEvaluatedRangeIsReportedAndNormalised() {
        val profile = ok("Filter 1: ON PK Fc 1000 Hz Gain 60.0 dB Q 1.00")
        assertEquals(AutoEqProfile.MAX_FILTER_GAIN_DB, profile.filters.single().gainDb, 0.0001)
        assertTrue(profile.adjustments.any { it.contains("Filter 1") && it.contains("outside") })
    }

    @Test
    fun anOutOfRangeQIsReportedAndNormalised() {
        val profile = ok("Filter 1: ON PK Fc 1000 Hz Gain 2.0 dB Q 250.0")
        assertEquals(AutoEqProfile.MAX_Q, profile.filters.single().q, 0.0001)
        assertTrue(profile.adjustments.any { it.contains("Q 250.0") })
    }

    @Test
    fun aCurveThatDoesNotFitTheStoredRangeReportsBothWhatItWantedAndTheClamp() {
        val curve = AutoEqProfile.toCurve(ok("Filter 1: ON PK Fc 1000 Hz Gain 24.0 dB Q 1.00"))
        assertTrue("the curve should report its clamp", curve.clamped)
        assertEquals(24.0, curve.rawMaxDb, 0.5)
        assertEquals(AutoEqProfile.MAX_BAND_DB, curve.levels[17].toInt())

        // A profile that fits says nothing about clamping.
        val fits = AutoEqProfile.toCurve(ok(parametric))
        assertFalse(fits.clamped)
    }

    @Test
    fun anOutOfRangeGraphicPointIsReportedRatherThanQuietlyMoved() {
        val profile = ok("GraphicEQ: 20 90.0; 1000 0.0; 20000 -6.0", "Wild")
        assertTrue(profile.adjustments.any { it.contains("outside") })
        assertEquals(AutoEqProfile.MAX_FILTER_GAIN_DB, profile.graphicCurve.first().second, 0.0001)
    }

    // ── Preamp presence, and the input limits ──

    @Test
    fun aDeclaredPreampIsDistinguishedFromAProfileWithNoPreampLine() {
        assertTrue(ok(parametric).preampDeclared)
        // A GraphicEQ file normally carries none — that is not "preamp 0".
        assertFalse(ok("GraphicEQ: 20 0.0; 1000 0.0").preampDeclared)
        // ...but a declared zero is the profile's own instruction and is applied.
        val declaredZero = ok("Preamp: 0.0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00")
        assertTrue(declaredZero.preampDeclared)
        assertEquals(0f, AutoEqProfile.preampDb(declaredZero), 0.0001f)
    }

    @Test
    fun preampClampingIsReportedNotSilent() {
        val clamped = ok("Preamp: -45.0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00")
        assertEquals(-30f, AutoEqProfile.preampDb(clamped), 0.0001f)
        assertTrue(AutoEqProfile.preampClamped(clamped))
        assertFalse(AutoEqProfile.preampClamped(ok(parametric)))
        // a profile with no preamp line is never "clamped"
        assertFalse(AutoEqProfile.preampClamped(ok("GraphicEQ: 20 0.0; 1000 0.0")))
    }

    @Test
    fun exactlyTheSupportedNumberOfFiltersIsReadAndOneMoreIsRefused() {
        fun line(i: Int) = "Filter $i: ON PK Fc ${100 + i} Hz Gain 1.0 dB Q 1.00"
        val atLimit = (1..AutoEqProfile.MAX_FILTERS).joinToString("\n") { line(it) }
        val overLimit = (1..AutoEqProfile.MAX_FILTERS + 1).joinToString("\n") { line(it) }
        assertEquals(AutoEqProfile.MAX_FILTERS, ok(atLimit).filters.size)
        assertTrue(refusal(overLimit).contains("more than ${AutoEqProfile.MAX_FILTERS} filters"))
    }

    @Test
    fun exactlyTheMaximumTextLengthIsReadAndOneCharacterMoreIsRefused() {
        val base = "Filter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00"
        val atLimit = base + "x".repeat(AutoEqProfile.MAX_TEXT_CHARS - base.length)
        assertEquals(AutoEqProfile.MAX_TEXT_CHARS, atLimit.length)
        assertEquals(1, ok(atLimit).filters.size)

        val overLimit = atLimit + "x"
        assertTrue(AutoEqProfile.parse(overLimit) is AutoEqProfile.Unsupported)
    }

    @Test
    fun exactlyTheMaximumNumberOfMeasurementPointsIsReadAndOneMoreIsRefused() {
        fun graphic(count: Int) = "GraphicEQ: " + (1..count).joinToString("; ") { "$it 0.0" }
        val atLimit = ok(graphic(AutoEqProfile.MAX_GRAPHIC_POINTS), "Big")
        assertEquals(AutoEqProfile.MAX_GRAPHIC_POINTS, atLimit.graphicCurve.size)
        assertTrue(
            refusal(graphic(AutoEqProfile.MAX_GRAPHIC_POINTS + 1))
                .contains("more than ${AutoEqProfile.MAX_GRAPHIC_POINTS} measurement points")
        )
    }
}
