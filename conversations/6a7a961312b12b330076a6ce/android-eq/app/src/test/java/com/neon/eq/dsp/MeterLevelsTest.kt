package com.neon.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MeterLevelsTest {

    @Test
    fun mapsRmsAndPeakSlotsWithoutReadingPastTheEightValueSnapshot() {
        val values = intArrayOf(100, 200, 300, 400, 500, 600, 700, 800)
        val levels = computeMeterLevels(values, FloatArray(8))

        assertEquals(4, levels.db.size)
        assertEquals(4, levels.rms.size)
        assertEquals(8, levels.peaks.size)
        for (slot in 0..3) {
            val db = 20.0 * Math.log10(values[slot] / 1000.0)
            val expected = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
            assertEquals("RMS slot $slot must use native meter value $slot", expected, levels.rms[slot], 0.000001f)
            assertEquals(db.toFloat(), levels.db[slot], 0.000001f)
        }
        for (slot in 4..7) {
            val db = 20.0 * Math.log10(values[slot] / 1000.0)
            val expected = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
            assertEquals("peak slot $slot must use native meter value $slot", expected, levels.peaks[slot], 0.000001f)
            assertEquals(expected, levels.peakForChannel(slot - 4), 0.000001f)
        }
        assertEquals(0f, levels.peakForChannel(-1), 0f)
        assertEquals(0f, levels.peakForChannel(4), 0f)
    }

    @Test
    fun nullAndEveryShortSnapshotReturnSilentDefaults() {
        assertSilentDefaults(computeMeterLevels(null, FloatArray(8)))
        for (size in 0..7) {
            assertSilentDefaults(computeMeterLevels(IntArray(size) { 1000 }, FloatArray(8)))
        }
        assertSilentDefaults(computeMeterLevels(IntArray(9) { 1000 }, FloatArray(8)))
    }

    @Test
    fun invalidMeterSamplesAreTreatedAsSilence() {
        val levels = computeMeterLevels(
            intArrayOf(-1, 0, 1000, 1001, -20, 250, 1000, 2000),
            null
        )

        assertArrayEquals(floatArrayOf(-60f, -60f, 0f, -60f), levels.db, 0.000001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f), levels.rms, 0.000001f)
        assertEquals(0f, levels.peaks[4], 0f)
        assertEquals(0f, levels.peaks[7], 0f)
        assertTrueAllFinite(levels)
    }

    @Test
    fun malformedPeakHoldStateUsesSafeClampedDefaults() {
        val levels = computeMeterLevels(
            IntArray(8),
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)
        )

        assertArrayEquals(FloatArray(4), levels.rms, 0f)
        assertArrayEquals(FloatArray(8), levels.peaks, 0f)
        assertTrueAllFinite(levels)
    }

    @Test
    fun exactSizedPeakHoldStateSanitizesInvalidValues() {
        val levels = computeMeterLevels(
            IntArray(8) { if (it < 4) 1000 else 0 },
            floatArrayOf(0f, 0f, 0f, 0f, Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)
        )

        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f, 0f, 0f, 0f, 0.988f), levels.peaks, 0.000001f)
        assertTrueAllFinite(levels)
    }

    @Test
    fun accessorsSafelyHandleMalformedLevelArrays() {
        val levels = MeterLevels(
            db = floatArrayOf(Float.NaN),
            rms = floatArrayOf(Float.POSITIVE_INFINITY),
            peaks = floatArrayOf(0f, 0f, Float.NaN)
        )

        assertEquals(-60f, levels.dbForChannel(0), 0f)
        assertEquals(-60f, levels.dbForChannel(1), 0f)
        assertEquals(0f, levels.rmsForChannel(0), 0f)
        assertEquals(0f, levels.rmsForChannel(1), 0f)
        assertEquals(0f, levels.peakForChannel(0), 0f)
        assertEquals(0f, levels.peakForChannel(3), 0f)
        assertEquals(0f, levels.peakForChannel(4), 0f)
    }

    private fun assertSilentDefaults(levels: MeterLevels) {
        assertArrayEquals(FloatArray(4) { -60f }, levels.db, 0f)
        assertArrayEquals(FloatArray(4), levels.rms, 0f)
        assertArrayEquals(FloatArray(8), levels.peaks, 0f)
    }

    private fun assertTrueAllFinite(levels: MeterLevels) {
        (levels.db.asList() + levels.rms.asList() + levels.peaks.asList()).forEach {
            org.junit.Assert.assertTrue(it.isFinite())
        }
    }
}
