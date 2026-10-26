package com.soniccore.app.dsp

/**
 * SONICCORE — MEASURED DSP TELEMETRY
 *
 * One snapshot of what the native bridge has actually processed. It exists so
 * the effect cards can show a real gain-reduction meter when there is one, and
 * say *which* of "waiting for audio" or "not measurable" applies when there is
 * not — instead of drawing a needle that moves on its own.
 *
 * [available] false means the numbers cannot be read at all (the native library
 * did not load, or the setters/readers are unavailable); [unavailableReason]
 * carries the bridge's own message. [processedFrames] is the bridge's own frame
 * counter: a running service is not the same thing as processed audio, and the
 * cards distinguish the two.
 *
 * Scope note, stated in the UI as well as here: these counters belong to the
 * capture/playback-capture path, which is the pipeline that runs the native
 * chain. The in-app player processes through the app's own software chain, so
 * on that path gain reduction is genuinely not measurable and is reported as
 * such rather than borrowed from the other path.
 */
data class DspTelemetry(
    val available: Boolean,
    val unavailableReason: String?,
    val processedFrames: Long,
    val clipping: Boolean,
    /** Measured compressor gain reduction in dB (0 = no reduction). */
    val compressorReductionDb: Float,
    /** Measured limiter gain reduction in dB (0 = no reduction). */
    val limiterReductionDb: Float,
    /** True while the limiter is actively reducing gain. */
    val limiterActive: Boolean,
    /** Measured output peak in dBFS, or NEGATIVE_INFINITY when not measurable. */
    val outputPeakDbfs: Float
) {
    /** True only when frames really went through the native chain. */
    val hasProcessedAudio: Boolean get() = available && processedFrames > 0

    companion object {
        fun unavailable(reason: String?): DspTelemetry = DspTelemetry(
            available = false,
            unavailableReason = reason,
            processedFrames = 0L,
            clipping = false,
            compressorReductionDb = 0f,
            limiterReductionDb = 0f,
            limiterActive = false,
            outputPeakDbfs = Float.NEGATIVE_INFINITY
        )
    }
}

/**
 * The sentences a dynamics card shows under its controls.
 *
 * Pure text decisions on purpose: "show the measurement, or say honestly that
 * there is none" is exactly the rule that is easy to bend in a composable, so it
 * lives here and `DspTelemetryTextTest` pins it.
 */
object DspTelemetryText {

    const val NO_FRAMES: String =
        "Waiting for audio — the DSP bridge has not processed a frame yet."

    const val COMPRESSOR_NOT_MEASURABLE: String =
        "Not measurable on this path: gain reduction is reported by the capture pipeline, " +
            "and the in-app player runs SonicCore's own software chain."

    private fun reason(reason: String?): String =
        if (reason.isNullOrBlank()) "Not measurable: the native DSP bridge is unavailable."
        else "Not measurable: $reason"

    /** The compressor's own line, or an honest refusal. */
    fun compressorLine(telemetry: DspTelemetry?): String = when {
        telemetry == null || !telemetry.available -> reason(telemetry?.unavailableReason)
        !telemetry.hasProcessedAudio -> NO_FRAMES
        telemetry.compressorReductionDb <= 0f -> "Measured reduction 0.0 dB — below threshold."
        else -> "Measured reduction ${"%.1f".format(telemetry.compressorReductionDb)} dB."
    }

    /** The limiter's own line, or an honest refusal. */
    fun limiterLine(telemetry: DspTelemetry?): String = when {
        telemetry == null || !telemetry.available -> reason(telemetry?.unavailableReason)
        !telemetry.hasProcessedAudio -> NO_FRAMES
        telemetry.limiterActive ->
            "Active — measured reduction ${"%.1f".format(telemetry.limiterReductionDb)} dB."
        else -> "Transparent — 0.0 dB of reduction below the ceiling."
    }

    /** The output-peak line shared by the dynamics cards. */
    fun outputPeakLine(telemetry: DspTelemetry?): String = when {
        telemetry == null || !telemetry.available -> "Output peak: not measured."
        !telemetry.hasProcessedAudio -> "Output peak: waiting for audio."
        !telemetry.outputPeakDbfs.isFinite() -> "Output peak: not measurable."
        else -> "Output peak ${"%.1f".format(telemetry.outputPeakDbfs)} dBFS" +
            if (telemetry.clipping) " · clipping" else ""
    }
}
