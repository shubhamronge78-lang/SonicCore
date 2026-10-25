package com.soniccore.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the audio parameters the Audio Center screens edit.
 *
 * The screens hand a snapshot to the host, which validates it before anything
 * reaches the engine. These assertions are what keeps a slider from pushing a
 * NaN or an out-of-range gain into the audio thread, and what keeps one Audio
 * screen from clearing a parameter another one owns.
 */
class AudioControlStateTest {

    @Test
    fun theNeutralSnapshotIsTheEnginesUnprocessedState() {
        val neutral = AudioControlState()
        assertEquals(0f, neutral.preamp, 0f)
        assertEquals(0f, neutral.bass, 0f)
        assertEquals(0f, neutral.treble, 0f)
        assertEquals(1f, neutral.width, 0f)
        assertEquals(0f, neutral.balance, 0f)
        assertFalse(neutral.mono)
        assertFalse(neutral.swap)
        assertTrue(neutral.isToneNeutral)
        assertTrue(neutral.isSpatialNeutral)
        assertEquals(neutral, neutral.validated())
    }

    @Test
    fun everyNumericFieldIsClampedToTheEnginesOwnRange() {
        val wild = AudioControlState(
            preamp = 500f,
            bass = -500f,
            treble = 500f,
            width = 99f,
            balance = -99f,
            compThresh = 40f,
            limThresh = -900f
        ).validated()

        assertEquals(AudioControlRanges.PREAMP.endInclusive, wild.preamp, 0f)
        assertEquals(AudioControlRanges.BASS.start, wild.bass, 0f)
        assertEquals(AudioControlRanges.TREBLE.endInclusive, wild.treble, 0f)
        assertEquals(AudioControlRanges.WIDTH.endInclusive, wild.width, 0f)
        assertEquals(AudioControlRanges.BALANCE.start, wild.balance, 0f)
        assertEquals(AudioControlRanges.COMP_THRESHOLD.endInclusive, wild.compThresh, 0f)
        assertEquals(AudioControlRanges.LIMITER_THRESHOLD.start, wild.limThresh, 0f)
    }

    @Test
    fun NaNAndInfinityNeverReachTheDsp() {
        val broken = AudioControlState(
            preamp = Float.NaN,
            bass = Float.POSITIVE_INFINITY,
            treble = Float.NEGATIVE_INFINITY,
            width = Float.NaN,
            balance = Float.NaN,
            compThresh = Float.NaN,
            limThresh = Float.POSITIVE_INFINITY
        ).validated()

        listOf(broken.preamp, broken.bass, broken.treble, broken.width, broken.balance, broken.compThresh, broken.limThresh)
            .forEach { assertTrue("a non-finite value survived validation", it.isFinite()) }
        assertEquals(AudioControlRanges.PREAMP.start, broken.preamp, 0f)
        assertEquals(AudioControlRanges.BASS.endInclusive, broken.bass, 0f)
        assertEquals(AudioControlRanges.TREBLE.start, broken.treble, 0f)
    }

    @Test
    fun mergingWritesTheAudioControlsAndLeavesTheParametricSlotsAlone() {
        val params = DspParams()
        params.slots = params.slots.toMutableList().also { slots ->
            slots[0] = PeqSlot(on = true, freq = 120f, gain = 6f, q = 1.4f)
            slots[3] = PeqSlot(on = true, freq = 3200f, gain = -4f, q = 2.5f)
        }

        AudioControlState(bass = 7.5f, treble = -3f, width = 1.8f, mono = true)
            .mergeInto(params)

        assertEquals(7.5f, params.bass, 0f)
        assertEquals(-3f, params.treble, 0f)
        assertEquals(1.8f, params.width, 0f)
        assertTrue(params.mono)
        // The Equalizer's parametric slots are a different screen's state.
        assertTrue(params.slots[0].on)
        assertEquals(120f, params.slots[0].freq, 0f)
        assertEquals(6f, params.slots[0].gain, 0f)
        assertEquals(1.4f, params.slots[0].q, 0f)
        assertEquals(3200f, params.slots[3].freq, 0f)
        assertEquals(-4f, params.slots[3].gain, 0f)
    }

    @Test
    fun aSnapshotReadsBackExactlyWhatWasMerged() {
        val params = DspParams()
        val expected = AudioControlState(
            preamp = -6f,
            bass = 4f,
            treble = 2.5f,
            width = 0.75f,
            balance = -0.25f,
            mono = true,
            swap = true,
            compOn = true,
            compThresh = -12f,
            limiterOn = false,
            limThresh = -3f
        )
        expected.mergeInto(params)

        assertEquals(expected, AudioControlState.of(params))
    }

    @Test
    fun readingAPersistedOutOfRangeValueStillYieldsAValidSnapshot() {
        val params = DspParams()
        params.bass = 999f
        params.width = -4f
        params.limThresh = Float.NaN

        val read = AudioControlState.of(params)

        assertEquals(AudioControlRanges.BASS.endInclusive, read.bass, 0f)
        assertEquals(AudioControlRanges.WIDTH.start, read.width, 0f)
        assertEquals(AudioControlRanges.LIMITER_THRESHOLD.start, read.limThresh, 0f)
    }

    @Test
    fun theShelfCurveIsFlatWhileBothGainsAreZero() {
        val flat = AudioControlState(bass = 0f, treble = 0f)
        listOf(20f, 60f, 200f, 1000f, 4000f, 12000f, 20000f).forEach { frequency ->
            assertEquals("$frequency Hz is not flat", 0f, shelfResponseDb(flat, frequency), 0.0001f)
        }
    }

    @Test
    fun bassAndTrebleShapeOppositeEndsOfTheSpectrumIndependently() {
        val bassOnly = AudioControlState(bass = 9f, treble = 0f)
        assertTrue(shelfResponseDb(bassOnly, 20f) > 8.5f)
        assertTrue(shelfResponseDb(bassOnly, 20000f) < 0.5f)

        val trebleOnly = AudioControlState(bass = 0f, treble = 9f)
        assertTrue(shelfResponseDb(trebleOnly, 20000f) > 8f)
        assertTrue(shelfResponseDb(trebleOnly, 20f) < 0.5f)

        // The fixed corners are where each shelf is half applied.
        assertEquals(4.5f, shelfResponseDb(bassOnly, LOW_SHELF_HZ), 0.01f)
        assertEquals(4.5f, shelfResponseDb(trebleOnly, HIGH_SHELF_HZ), 0.01f)
    }

    @Test
    fun theCurveStaysInsideTheEngineRangeAcrossTheAudibleBand() {
        val extreme = AudioControlState(bass = 30f, treble = 30f)
        var frequency = 20f
        while (frequency <= 20000f) {
            val db = shelfResponseDb(extreme, frequency)
            assertTrue("$frequency Hz produced $db dB", db.isFinite())
            assertTrue("$frequency Hz produced $db dB", db >= 0f && db <= 30f)
            frequency *= 1.15f
        }
        // A negative response never leaves the frame either.
        val cut = AudioControlState(bass = -30f, treble = -30f)
        assertEquals(0f, shelfResponseDb(cut, 0f), 0f)
        assertTrue(shelfResponseDb(cut, 1000f) <= 0f)
        assertTrue(shelfResponseDb(cut, Float.NaN).isFinite())
    }
}
