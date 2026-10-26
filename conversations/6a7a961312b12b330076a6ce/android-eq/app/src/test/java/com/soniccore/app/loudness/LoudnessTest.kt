package com.soniccore.app.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the offline loudness module's own decisions.
 *
 * The native measurement is pinned by `tools/dspharness/loudness_harness.cpp`
 * (the real EBU R 128 numbers, under ASan+UBSan). What is pinned *here* is
 * everything this app decides around it: which buffers are refused and why, how
 * a native reply becomes a result, and what the report does and does not claim —
 * including that a value the measurement did not produce is never shown as one.
 */
class LoudnessTest {

    private fun pcm(channels: Int, sampleRate: Int, frames: Int) =
        Loudness.Pcm16(channels, sampleRate, frames, ShortArray(frames * channels))

    /** Exactly the array the native module returns for a successful call. */
    private fun native(integrated: Double, range: Double, peak: Double, status: Int) =
        doubleArrayOf(integrated, range, peak, status.toDouble())

    // ── What is refused, and in words the screen can show ──

    @Test
    fun aBufferWithNothingInItIsRefused() {
        assertTrue(Loudness.validate(2, 48_000, 0, ShortArray(0))!!.contains("no frames"))
    }

    @Test
    fun formatsTheMeasurementCannotHonestlyReportAreRefusedByName() {
        val frames = 64
        val samples = ShortArray(frames * 2)
        assertTrue(Loudness.validate(3, 48_000, frames, ShortArray(frames * 3))!!.contains("mono or stereo"))
        assertTrue(Loudness.validate(6, 48_000, frames, ShortArray(frames * 6))!!.contains("6 channels"))
        assertTrue(Loudness.validate(2, 4_000, frames, samples)!!.contains("8–192 kHz"))
        assertTrue(Loudness.validate(2, 400_000, frames, samples)!!.contains("8–192 kHz"))
        assertTrue(
            Loudness.validate(2, 48_000, frames, ShortArray(frames * 2 - 1))!!
                .contains("shorter than its own format claims")
        )
        assertNull(Loudness.validate(1, 48_000, frames, ShortArray(frames)))
        assertNull(Loudness.validate(2, 192_000, frames, samples))
        assertNull(Loudness.validate(2, 8_000, frames, samples))
    }

    @Test
    fun theMeasuredSpanIsCappedAtThreeMinutesOfOneBuffer() {
        assertEquals(48_000 * 180, Loudness.maxFrames(48_000))
        assertEquals(8_000 * 180, Loudness.maxFrames(8_000))
        assertEquals(0, Loudness.maxFrames(-1))
    }

    // ── A native reply becomes a result, never a guess ──

    @Test
    fun aMeasuredBufferReportsTheNumbersTheNativeModuleProduced() {
        val analysis = Loudness.interpret(
            native(-19.98, 5.31, -0.01, LoudnessNative.STATUS_OK),
            channels = 2, sampleRate = 48_000, measuredFrames = 240_000, sourceFrames = 240_000,
            source = WavPcm.SOURCE
        )
        assertTrue("expected a measurement, got $analysis", analysis is Loudness.Analysis.Measured)
        val measured = analysis as Loudness.Analysis.Measured
        assertEquals(-19.98, measured.integratedLufs!!, 0.0001)
        assertEquals(5.31, measured.loudnessRangeLu!!, 0.0001)
        assertEquals(-0.01, measured.truePeakDbfs!!, 0.0001)
        assertEquals(5.0, measured.measuredSeconds, 0.0001)
        assertFalse(measured.truncated)
        assertTrue(measured.measurable)
    }

    @Test
    fun silenceAndTooShortSpansAreNotMeasurableRatherThanZero() {
        // The native module answers NaN when no gated block passed; a number here
        // would be a claim the measurement never made.
        for (raw in listOf(
            native(Double.NaN, Double.NaN, Double.NaN, LoudnessNative.STATUS_OK),
            native(Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN, LoudnessNative.STATUS_OK)
        )) {
            val measured = Loudness.interpret(
                raw, 2, 48_000, measuredFrames = 9_600, sourceFrames = 9_600, source = WavPcm.SOURCE
            ) as Loudness.Analysis.Measured
            assertNull(measured.integratedLufs)
            assertNull(measured.loudnessRangeLu)
            assertNull(measured.truePeakDbfs)
            assertFalse(measured.measurable)
            val lines = Loudness.reportLines(measured).joinToString("\n")
            assertTrue(lines.contains("not measurable"))
            assertTrue(lines.contains("no signal"))
            assertTrue("the gate must be explained", lines.contains("−70 LUFS"))
        }
    }

    @Test
    fun aTruncatedSpanSaysWhichPartWasMeasured() {
        val measured = Loudness.interpret(
            native(-14.0, 4.0, -1.0, LoudnessNative.STATUS_OK),
            2, 44_100, measuredFrames = 44_100 * 180, sourceFrames = 44_100 * 300, source = WavPcm.SOURCE
        ) as Loudness.Analysis.Measured
        assertTrue(measured.truncated)
        assertEquals(300.0, measured.sourceSeconds, 0.01)
        val lines = Loudness.reportLines(measured).joinToString("\n")
        assertTrue(lines.contains("Measured the first 3:00 of 5:00"))
        assertTrue(lines.contains("at most 3 minutes"))
    }

    @Test
    fun everyNativeRefusalBecomesItsOwnReason() {
        val statuses = listOf(
            LoudnessNative.STATUS_BAD_PARAMETERS to "refused the format",
            LoudnessNative.STATUS_ADD_FAILED to "could not add those frames",
            LoudnessNative.STATUS_INIT_FAILED to "EBU R 128 measurement state"
        )
        for ((status, expected) in statuses) {
            val analysis = Loudness.interpret(
                native(0.0, 0.0, 0.0, status), 2, 48_000, 48_000, 48_000, WavPcm.SOURCE
            )
            assertTrue("status $status gave $analysis", analysis is Loudness.Analysis.Refused)
            assertTrue((analysis as Loudness.Analysis.Refused).reason.contains(expected))
            assertTrue(Loudness.reportLines(analysis).single().startsWith("Not measured:"))
        }
        val shortReply = Loudness.interpret(doubleArrayOf(1.0), 2, 48_000, 1, 1, WavPcm.SOURCE)
        assertTrue(shortReply is Loudness.Analysis.Refused)
        val nothing = Loudness.interpret(null, 2, 48_000, 1, 1, WavPcm.SOURCE)
        assertTrue(nothing is Loudness.Analysis.Refused)
        assertTrue((nothing as Loudness.Analysis.Refused).reason.contains("returned nothing"))
    }

    // ── What the report says about scope, format and length ──

    @Test
    fun theReportStatesTheScopeSoNothingLiveIsImplied() {
        val measured = Loudness.interpret(
            native(-9.0, 2.0, -0.5, LoudnessNative.STATUS_OK),
            1, 48_000, 48_000, 48_000, WavPcm.SOURCE
        ) as Loudness.Analysis.Measured
        val lines = Loudness.reportLines(measured).joinToString("\n")
        assertTrue(lines.contains("Offline analysis"))
        assertTrue(lines.contains("does not measure live playback"))
        assertTrue(lines.contains("system-wide audio"))
        assertEquals(Loudness.SCOPE, measured.notes.last())
    }

    @Test
    fun theReportNamesItsInputFormatAndSpan() {
        val measured = Loudness.interpret(
            native(-9.0, 2.0, -0.5, LoudnessNative.STATUS_OK),
            2, 44_100, 44_100 * 3 + 44_100 / 2, 44_100 * 3 + 44_100 / 2, WavPcm.SOURCE
        ) as Loudness.Analysis.Measured
        val lines = Loudness.reportLines(measured)
        assertEquals("Input: stereo, 44.1 kHz, 0:03 measured", lines.first())
        assertTrue(lines.any { it == "Integrated loudness: -9.0 LUFS" })
        assertTrue(lines.any { it == "Loudness range: 2.0 LU" })
        assertTrue(lines.any { it == "True peak: -0.5 dBTP" })
    }

    @Test
    fun theFormatLineReadsAsPlainAudioLanguage() {
        assertEquals("mono, 48 kHz, 0:05 of audio", Loudness.formatLine(1, 48_000, 48_000 * 5))
        assertEquals("stereo, 8 kHz, 1:00 of audio", Loudness.formatLine(2, 8_000, 8_000 * 60))
        assertEquals("4 channels, 96 kHz, 0:00 of audio", Loudness.formatLine(4, 96_000, 10))
    }

    // ── The one entry point that touches the native module ──

    @Test
    fun aBufferTheModuleCannotBeGivenIsRefusedBeforeAnythingIsMeasured() {
        val analysis = Loudness.analyze(pcm(3, 48_000, 1_000), WavPcm.SOURCE)
        assertTrue(analysis is Loudness.Analysis.Refused)
        assertTrue((analysis as Loudness.Analysis.Refused).reason.contains("mono or stereo"))
    }

    @Test
    fun theNativeModuleIsUsedWhenItIsThereAndItsAbsenceIsStatedWhenItIsNot() {
        // On the JVM the library is not loadable, so this pins the honest refusal;
        // where it does load (a device), the same call must produce a measurement.
        val analysis = Loudness.analyze(pcm(2, 48_000, 48_000), WavPcm.SOURCE)
        if (LoudnessNative.available) {
            assertTrue("expected a measurement, got $analysis", analysis is Loudness.Analysis.Measured)
        } else {
            assertTrue("expected a refusal, got $analysis", analysis is Loudness.Analysis.Refused)
            assertTrue((analysis as Loudness.Analysis.Refused).reason.contains("unavailable on this device"))
        }
    }
}
