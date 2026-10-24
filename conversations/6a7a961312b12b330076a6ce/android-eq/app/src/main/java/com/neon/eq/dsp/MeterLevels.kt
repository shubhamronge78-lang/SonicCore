package com.neon.eq.dsp

/**
 * Measured meter snapshot. Public because the home / category surfaces expose it
 * through [com.neon.eq.ui.home.HomeAudioSummary]; the factory helpers below stay
 * module-internal.
 */
data class MeterLevels(
    val db: FloatArray,
    val rms: FloatArray,
    val peaks: FloatArray
) {
    fun dbForChannel(channel: Int): Float =
        if (channel in 0 until METER_CHANNEL_COUNT) {
            db.getOrNull(channel)?.takeIf { it.isFinite() }?.coerceIn(-60f, 0f) ?: -60f
        } else {
            -60f
        }

    fun rmsForChannel(channel: Int): Float =
        if (channel in 0 until METER_CHANNEL_COUNT) {
            rms.getOrNull(channel)?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        } else {
            0f
        }

    fun peakForChannel(channel: Int): Float =
        if (channel in 0 until METER_CHANNEL_COUNT) {
            peaks.getOrNull(channel + METER_CHANNEL_COUNT)
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 0f
        } else {
            0f
        }

    fun hasSignal(): Boolean = rms.any { it.isFinite() && it > 0.001f }

    companion object {
        fun silent() = MeterLevels(
            db = FloatArray(METER_CHANNEL_COUNT) { -60f },
            rms = FloatArray(METER_CHANNEL_COUNT),
            peaks = FloatArray(METER_SNAPSHOT_SIZE)
        )
    }
}

private const val METER_CHANNEL_COUNT = 4
private const val METER_SNAPSHOT_SIZE = METER_CHANNEL_COUNT * 2
private const val MAX_METER_VALUE = 1000

internal fun computeMeterLevels(
    values: IntArray?,
    previousPeaks: FloatArray?
): MeterLevels {
    if (values == null || values.size != METER_SNAPSHOT_SIZE) {
        return MeterLevels.silent()
    }

    fun msToDb(ms: Int): Float =
        if (ms in 1..MAX_METER_VALUE) (20.0 * Math.log10(ms / MAX_METER_VALUE.toDouble())).toFloat()
        else -60f

    fun normalize(db: Float): Float = ((db + 60f) / 60f).coerceIn(0f, 1f)

    val db = FloatArray(METER_CHANNEL_COUNT) { i -> msToDb(values[i]) }
    val rms = FloatArray(METER_CHANNEL_COUNT) { i -> normalize(db[i]) }
    val validPreviousPeaks = previousPeaks?.takeIf { it.size == METER_SNAPSHOT_SIZE }
    val peaks = FloatArray(METER_SNAPSHOT_SIZE) { slot ->
        val sample = values[slot]
        val current = if (slot < METER_CHANNEL_COUNT) {
            rms[slot]
        } else {
            normalize(msToDb(sample))
        }
        val previous = validPreviousPeaks
            ?.getOrNull(slot)
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f
        maxOf((previous - 0.012f).coerceAtLeast(0f), current)
    }

    return MeterLevels(db, rms, peaks)
}
