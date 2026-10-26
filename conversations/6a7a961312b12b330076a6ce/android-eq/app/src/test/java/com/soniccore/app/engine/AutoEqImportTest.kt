package com.soniccore.app.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the AutoEQ import *flow* — the seven things an import must do.
 *
 * `AutoEqProfileTest` pins the parsing. This pins what happens around it: that a
 * review changes nothing, that a deliberate apply stores the profile on the
 * engine's existing preset store and writes its preamp exactly once, that a
 * cancellation or a failure leaves the current sound and every other profile
 * untouched, and that the boundary inputs behave.
 *
 * The host side effects are lambdas here (that is exactly how `MainActivity`
 * wires them), so the real code path runs on the JVM without a device.
 */
class AutoEqImportTest {

    private val parametric = """
        Preamp: -6.5 dB
        Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 220 Hz Gain -2.1 dB Q 1.41
        Filter 3: ON PK Fc 1000 Hz Gain 6.0 dB Q 1.00
        Filter 4: ON HSC Fc 10000 Hz Gain 3.0 dB Q 0.70
    """.trimIndent()

    private val graphic = "GraphicEQ: 20 6.0; 1000 0.0; 20000 -6.0"

    /** A stand-in for the engine + Audio control path, recording every side effect. */
    private class Host {
        val presets = mutableListOf<Presets.CustomPreset>()   // the engine's preset store
        val saves = mutableListOf<String>()
        val activations = mutableListOf<String>()
        val preampWrites = mutableListOf<Float>()
        var saveFails = false
        var activateFails = false
        var preampFails = false

        fun save(preset: Presets.CustomPreset) {
            if (saveFails) throw IllegalStateException("storage error")
            // The engine keeps exactly one profile per name.
            presets.removeAll { it.name == preset.name }
            presets += preset
            saves += preset.name
        }

        fun activate(name: String): Boolean {
            if (activateFails) return false
            if (presets.none { it.name == name }) return false
            activations += name
            return true
        }

        fun setPreamp(value: Float) {
            if (preampFails) throw IllegalStateException("no dsp")
            preampWrites += value
        }

        fun importProfile(review: AutoEqImport.Review, applyNow: Boolean): String =
            AutoEqImport.apply(review, applyNow, ::save, ::activate, ::setPreamp)
    }

    private fun plan(text: String, name: String = "Test profile"): AutoEqImport.Plan {
        val review = AutoEqImport.review(text, name)
        assertTrue("expected a ready plan, got $review", review is AutoEqImport.Ready)
        return (review as AutoEqImport.Ready).plan
    }

    // ── 1. Valid parametric profile import ──

    @Test
    fun aParametricProfileReviewsWithItsFiltersItsPreampAndItsCurve() {
        val plan = plan(parametric, "Sennheiser HD 650")
        assertEquals("Sennheiser HD 650", plan.name)
        assertEquals("ParametricEQ", plan.sourceFormat)
        assertEquals(4, plan.filterCount)
        assertEquals(0, plan.measurementPoints)
        assertFalse(plan.isGraphic)
        assertEquals("4 filters", plan.shape)
        assertEquals(-6.5f, plan.preampDb, 0.0001f)
        assertTrue(plan.appliesPreamp)
        assertEquals(AutoEqProfile.BAND_FREQUENCIES.size, plan.bandCount)
        assertTrue("stored curve left ±15 dB: ${plan.curveMinDb}…${plan.curveMaxDb}",
            plan.curveMinDb >= AutoEqProfile.MIN_BAND_DB && plan.curveMaxDb <= AutoEqProfile.MAX_BAND_DB)
        assertFalse(plan.curveClamped)
        // The 1 kHz peaking filter is the strongest band of this profile.
        assertEquals(6, plan.bandLevels[17].toInt())
    }

    // ── 2. Valid graphic profile import ──

    @Test
    fun aGraphicProfileReviewsWithItsMeasurementPoints() {
        val plan = plan(graphic, "Graphic test")
        assertEquals("GraphicEQ", plan.sourceFormat)
        assertTrue(plan.isGraphic)
        assertEquals(3, plan.measurementPoints)
        assertEquals("3 measurement points", plan.shape)
        assertEquals(0, plan.filterCount)
        assertEquals(6, plan.bandLevels[0].toInt())
        assertEquals(-6, plan.bandLevels[30].toInt())
        // A GraphicEQ file normally carries no Preamp line — so nothing is written.
        assertFalse(plan.appliesPreamp)
    }

    // ── 3. The preamp is extracted and applied exactly once ──

    @Test
    fun theProfilePreampIsAppliedExactlyOnceThroughTheSingleNativeWrite() {
        val host = Host()
        val plan = plan(parametric)
        val message = host.importProfile(AutoEqImport.Ready(plan), applyNow = true)
        assertEquals("the native preamp parameter must be written once", 1, host.preampWrites.size)
        assertEquals(-6.5f, host.preampWrites.single(), 0.0001f)
        assertEquals(listOf(plan.name), host.activations)
        assertTrue(message.contains("once"))
    }

    @Test
    fun savingWithoutApplyingNeverTouchesThePreampOrTheActiveCurve() {
        val host = Host()
        host.importProfile(AutoEqImport.Ready(plan(parametric)), applyNow = false)
        assertTrue(host.presets.isNotEmpty())
        assertTrue(host.preampWrites.isEmpty())
        assertTrue(host.activations.isEmpty())
    }

    @Test
    fun aProfileWithNoPreampLineLeavesTheUsersPreampAlone() {
        val host = Host()
        val plan = plan(graphic)
        val writes = mutableListOf<Float>()
        assertFalse("nothing must be written for a profile with no Preamp line",
            AutoEqImport.applyPreamp(plan, writes::add))
        assertTrue(writes.isEmpty())
        // The value the app is left with is the one it already had.
        assertEquals(3.4f, AutoEqImport.preampAfter(plan, 3.4f), 0.0001f)
        assertEquals(-6.5f, AutoEqImport.preampAfter(plan(parametric), 3.4f), 0.0001f)
        host.importProfile(AutoEqImport.Ready(plan), applyNow = true)
        assertTrue(host.preampWrites.isEmpty())
    }

    @Test
    fun theStoredPreampIsTheClampedOneAndTheClampIsStated() {
        val plan = plan("Preamp: -45.0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00", "Loud")
        assertEquals(-30f, plan.preampDb, 0.0001f)
        assertTrue(plan.preampClamped)
        assertTrue(AutoEqImport.reviewLines(plan).any { it.contains("±30 dB") })
        val host = Host()
        host.importProfile(AutoEqImport.Ready(plan), applyNow = true)
        assertEquals(-30f, host.preampWrites.single(), 0.0001f)
    }

    // ── 4. Save, reload and reapply a custom preset ──

    @Test
    fun anImportedProfileSavesReloadsAndReappliesUnchanged() {
        val host = Host()
        val plan = plan(parametric, "HD 650")
        host.importProfile(AutoEqImport.Ready(plan), applyNow = false)

        // Saved on the engine's own preset model...
        val stored = host.presets.single()
        assertEquals("HD 650", stored.name)
        assertArrayEquals(plan.bandLevels, stored.levels)

        // ...reloadable through the engine's own persistence round-trip...
        val reloaded = Presets.importFromJson(Presets.exportToJson(host.presets))
        assertEquals(1, reloaded.size)
        assertEquals("HD 650", reloaded[0].name)
        assertArrayEquals(plan.bandLevels, reloaded[0].levels)

        // ...and re-appliable: the curve the engine samples for a custom preset is
        // the curve that was imported, band for band.
        assertArrayEquals(reloaded[0].levels, Presets.levelsForCount(reloaded[0], 31))

        // Applying the reloaded profile goes through the activation path once.
        host.importProfile(AutoEqImport.Ready(plan), applyNow = true)
        assertEquals(listOf("HD 650"), host.activations)
        assertEquals(1, host.preampWrites.size)
    }

    @Test
    fun importingTwiceReplacesThatProfileAndKeepsEveryOtherPreset() {
        val host = Host()
        host.presets += Presets.CustomPreset("My own curve", ShortArray(31) { 4 }, 0, 0, 0)
        host.importProfile(AutoEqImport.Ready(plan(parametric, "HD 650")), applyNow = false)
        host.importProfile(AutoEqImport.Ready(plan(graphic, "HD 650")), applyNow = false)

        assertEquals("one profile per name", 1, host.presets.count { it.name == "HD 650" })
        assertEquals("the other profile is untouched", 1, host.presets.count { it.name == "My own curve" })
        // The replacement is the second import, not the first: the graphic profile
        // asks for +6 dB at 20 Hz, the parametric one for less than that.
        assertEquals(6, host.presets.first { it.name == "HD 650" }.levels[0].toInt())
    }

    // ── 5. Malformed input, empty input and unsupported filters ──

    @Test
    fun malformedEmptyAndUnsupportedInputAreRefusedWithNothingApplied() {
        val host = Host()
        val inputs = listOf(
            "",
            "   \n ",
            "hello world",
            """{"neoneq_preset":1,"levels":[0,1]}""",
            "Filter 1: ON LP Fc 1000 Hz Gain 0.0 dB Q 0.70",
            "Filter 1: OFF PK Fc 1000 Hz Gain 3.0 dB Q 1.00",
            "GraphicEQ: 20 0.0; oops; 20000 -6.0"
        )
        for (text in inputs) {
            val review = AutoEqImport.review(text, "Bad")
            assertTrue("“$text” should not have been accepted", review !is AutoEqImport.Ready)
            val message = host.importProfile(review, applyNow = true)
            assertTrue("“$text” gave “$message”", message.contains("Nothing was changed"))
        }
        assertTrue(host.saves.isEmpty())
        assertTrue(host.activations.isEmpty())
        assertTrue(host.preampWrites.isEmpty())
    }

    @Test
    fun anOversizedPasteIsRefusedAndNothingIsApplied() {
        val host = Host()
        val review = AutoEqImport.review("Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.00" + "x".repeat(AutoEqProfile.MAX_TEXT_CHARS), "Huge")
        assertTrue(review is AutoEqImport.Unsupported)
        assertTrue((review as AutoEqImport.Unsupported).reason.contains("128 KB"))
        assertTrue(host.importProfile(review, applyNow = true).contains("Nothing was changed"))
        assertTrue(host.saves.isEmpty())
    }

    // ── 6. Cancellation and failed imports leave the current sound unchanged ──

    @Test
    fun reviewingAndThenCancellingChangesNothing() {
        val host = Host()
        // A review is pure: the host is not even consulted until the user confirms.
        val review = AutoEqImport.review(parametric, "Cancelled")
        assertTrue(review is AutoEqImport.Ready)
        assertTrue(host.saves.isEmpty())
        assertTrue(host.activations.isEmpty())
        assertTrue(host.preampWrites.isEmpty())
        assertTrue(host.presets.isEmpty())
    }

    @Test
    fun aFailedSaveAppliesNothingAndKeepsTheExistingProfiles() {
        val host = Host()
        host.presets += Presets.CustomPreset("Existing", ShortArray(31) { 2 }, 0, 0, 0)
        host.saveFails = true
        val message = host.importProfile(AutoEqImport.Ready(plan(parametric)), applyNow = true)

        assertTrue("message was “$message”", message.contains("nothing was changed"))
        assertTrue(host.activations.isEmpty())
        assertTrue(host.preampWrites.isEmpty())
        assertEquals(listOf("Existing"), host.presets.map { it.name })
    }

    @Test
    fun aCurveThatCannotBeActivatedIsReportedAndThePreampIsNotWritten() {
        val host = Host()
        host.activateFails = true
        val message = host.importProfile(AutoEqImport.Ready(plan(parametric)), applyNow = true)

        assertTrue("message was “$message”", message.contains("could not be made the active curve"))
        assertTrue("the profile is still saved for later", host.presets.any { it.name == "Test profile" })
        assertTrue("the preamp follows the curve, never leads it", host.preampWrites.isEmpty())
    }

    @Test
    fun aPreampWriteThatFailsIsReportedInsteadOfClaimed() {
        val host = Host()
        host.preampFails = true
        val message = host.importProfile(AutoEqImport.Ready(plan(parametric)), applyNow = true)
        assertEquals(listOf("Test profile"), host.activations)
        assertTrue("message was “$message”", message.contains("preamp could not be set"))
    }

    // ── 7. Boundary values, and what the review tells the user ──

    @Test
    fun theReviewStatesWhatIsReplacedAndWhatIsKept() {
        val plan = plan(parametric, "HD 650")
        val scope = AutoEqImport.scope(plan)
        assertTrue(scope.contains("HD 650"))
        assertTrue(scope.contains("replaced"))
        assertTrue(scope.contains("Bass, virtualizer and loudness keep"))

        val lines = AutoEqImport.reviewLines(plan).joinToString("\n")
        assertTrue(lines.contains("ParametricEQ"))
        assertTrue(lines.contains("4 filters"))
        assertTrue(lines.contains("-6.5 dB"))
        assertTrue(lines.contains("31 bands"))
    }

    @Test
    fun aGraphicReviewSaysThePreampIsKeptRatherThanImplyingItChanges() {
        val graphicPlan = plan(graphic, "Graphic")
        assertTrue(AutoEqImport.scope(graphicPlan).contains("keeps your current preamp"))
        assertTrue(
            AutoEqImport.reviewLines(graphicPlan).joinToString("\n")
                .contains("your current preamp is left unchanged")
        )
    }

    @Test
    fun aClampedCurveAndACleanedUpNameAreBothStatedInTheReview() {
        val clamped = plan("Filter 1: ON PK Fc 1000 Hz Gain 24.0 dB Q 1.00", "Loud")
        assertTrue(clamped.curveClamped)
        assertTrue(AutoEqImport.reviewLines(clamped).joinToString("\n").contains("clamped"))

        val named = plan(parametric, "  \"HD\t650\"\n ")
        assertEquals("HD 650", named.name)
        assertTrue(named.notes.any { it.contains("cleaned up") })
    }

    @Test
    fun aBlankNameFallsBackToTheDefaultAndSaysSo() {
        val named = plan(parametric, "   ")
        assertEquals(AutoEqProfile.DEFAULT_NAME, named.name)
        assertTrue(named.notes.any { it.contains("cleaned up") })
    }

    @Test
    fun boundaryInputsAreAcceptedAndTheResultIsStorable() {
        val atCap = plan(
            "Filter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00" +
                "x".repeat(AutoEqProfile.MAX_TEXT_CHARS - "Filter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.00".length),
            "At the cap"
        )
        assertEquals(AutoEqProfile.BAND_FREQUENCIES.size, atCap.bandCount)

        // A profile that asks for the extreme of the range is stored clamped, and
        // its stored curve still fits what the engine accepts.
        val extreme = plan("Filter 1: ON PK Fc 1000 Hz Gain 60.0 dB Q 1.00", "Extreme")
        assertTrue(extreme.curveClamped)
        assertEquals(AutoEqProfile.MAX_BAND_DB, extreme.bandLevels[17].toInt())
        val host = Host()
        host.importProfile(AutoEqImport.Ready(extreme), applyNow = true)
        assertArrayEquals(extreme.bandLevels, host.presets.single().levels)
    }
}
