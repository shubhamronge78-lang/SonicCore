package com.soniccore.app.loudness

import java.util.Locale

/**
 * SONICCORE — LOUDNESS ANALYSIS (repository integration: libebur128)
 *
 * Offline measurement of one buffer of PCM that already exists: integrated
 * loudness (LUFS, EBU R 128 / ITU-R BS.1770-4 with gating), loudness range (LU)
 * and true peak (dBTP), from the vendored libebur128 in `cpp/third_party/ebur128`
 * (MIT — provenance and pinned commit in that directory's README).
 *
 * Boundaries, stated so nothing is implied:
 *
 *  * **Offline, not live.** A buffer that has already been captured is measured
 *    once, after the fact. Nothing here runs on the audio thread, inside the
 *    capture path or inside the DSP chain ([LoudnessNative] loads its own shared
 *    library), so playback latency is untouched. SonicCore does not measure live
 *    playback, and cannot measure system-wide audio, because the system route
 *    supplies PCM only while a capture session is running — a fact the report
 *    repeats in its own words ([SCOPE]).
 *  * **Only what it is handed.** The result carries the format, the channel
 *    count and the span it measured, and `Measured` is only produced for a buffer
 *    that was actually analysed.
 *  * **Not measurable is a result.** Digital silence, or a span shorter than one
 *    gating block, produces `null` for the values that could not be produced —
 *    never `-inf LUFS` or `0 dBTP` dressed up as a measurement.
 *
 * The decisions are pure Kotlin ([validate], [interpret], [reportLines]) so
 * `LoudnessTest` pins them on the JVM; only [analyze] calls the native module.
 */
object Loudness {

    /** Channels this module measures: mono and stereo. */
    const val MIN_CHANNELS = 1
    const val MAX_CHANNELS = 2

    /** Sample rates this module measures, inclusive. */
    const val MIN_SAMPLE_RATE = 8_000
    const val MAX_SAMPLE_RATE = 192_000

    /** Longest span measured out of one buffer, in seconds. */
    const val MAX_ANALYSIS_SECONDS = 180

    /** EBU R 128 gating works on 400 ms blocks, so nothing shorter can be gated. */
    const val GATING_BLOCK_SECONDS = 0.4

    /** What this module measures, and what it deliberately does not. */
    const val SCOPE: String =
        "Offline analysis: one already-captured buffer is measured once, after the fact. " +
            "SonicCore does not measure live playback, and cannot measure system-wide audio, " +
            "because the system route supplies PCM only while a capture session is running."

    /** Interleaved 16-bit PCM with the format it claims. */
    data class Pcm16(
        val channels: Int,
        val sampleRate: Int,
        val frames: Int,
        val samples: ShortArray
    )

    sealed interface Analysis {

        /** A buffer that was actually measured. */
        data class Measured(
            /** Integrated loudness in LUFS, or null when no gated block passed. */
            val integratedLufs: Double?,
            /** Loudness range in LU, or null when it cannot be derived. */
            val loudnessRangeLu: Double?,
            /** True peak in dBTP, or null when the buffer carries no signal. */
            val truePeakDbfs: Double?,
            val channels: Int,
            val sampleRate: Int,
            /** The span the numbers above belong to. */
            val measuredFrames: Int,
            /** The span the buffer carried before the module's own length cap. */
            val sourceFrames: Int,
            /** Everything the user needs to read the numbers correctly. */
            val notes: List<String>
        ) : Analysis {
            val measuredSeconds: Double get() = measuredFrames.toDouble() / sampleRate
            val sourceSeconds: Double get() = sourceFrames.toDouble() / sampleRate
            val truncated: Boolean get() = measuredFrames < sourceFrames
            val measurable: Boolean get() = integratedLufs != null
        }

        /** A buffer, file or module this app will not measure — with the reason. */
        data class Refused(val reason: String) : Analysis
    }

    /** Frames of one buffer this module will measure at [sampleRate]. */
    fun maxFrames(sampleRate: Int): Int =
        sampleRate.coerceAtLeast(0) * MAX_ANALYSIS_SECONDS

    /** The input description the report opens with. */
    fun formatLine(channels: Int, sampleRate: Int, frames: Int): String =
        "${channelText(channels)}, ${rateText(sampleRate)}, ${clock(frames.toDouble() / sampleRate.coerceAtLeast(1))} of audio"

    /**
     * Refuses a buffer this module cannot measure, in plain words; null when it
     * can. Pure, so every refusal is pinned by a JVM test.
     */
    fun validate(channels: Int, sampleRate: Int, frames: Int, samples: ShortArray): String? = when {
        frames <= 0 -> "That buffer carries no frames to measure."
        channels < MIN_CHANNELS || channels > MAX_CHANNELS ->
            "SonicCore measures mono or stereo audio; that buffer has $channels channels."
        sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE ->
            "SonicCore measures audio at ${MIN_SAMPLE_RATE / 1000}–${MAX_SAMPLE_RATE / 1000} kHz; " +
                "that buffer is at $sampleRate Hz."
        samples.size < frames * channels ->
            "That buffer is shorter than its own format claims (${samples.size} samples for " +
                "$frames frames of $channels channels)."
        else -> null
    }

    /**
     * Turns the native reply into an [Analysis]. Pure: the JVM tests drive it
     * with the exact arrays the native module returns, including the refusal
     * statuses and the NaN cases.
     */
    fun interpret(
        raw: DoubleArray?,
        channels: Int,
        sampleRate: Int,
        measuredFrames: Int,
        sourceFrames: Int,
        source: String
    ): Analysis {
        if (raw == null || raw.size < 4) {
            return Analysis.Refused("The native measurement returned nothing.")
        }
        if (raw[3] != LoudnessNative.STATUS_OK.toDouble()) {
            return Analysis.Refused(nativeRefusal(raw[3]))
        }
        val integrated = raw[0].takeIf { it.isFinite() }
        val range = raw[1].takeIf { it.isFinite() }
        val peak = raw[2].takeIf { it.isFinite() }
        val measuredSeconds = measuredFrames.toDouble() / sampleRate.coerceAtLeast(1)
        val sourceSeconds = sourceFrames.toDouble() / sampleRate.coerceAtLeast(1)
        val notes = buildList {
            add("$source — ${formatLine(channels, sampleRate, measuredFrames)}.")
            if (sourceFrames > measuredFrames) {
                add(
                    "Measured the first ${clock(measuredSeconds)} of ${clock(sourceSeconds)}: " +
                        "SonicCore analyses at most ${MAX_ANALYSIS_SECONDS / 60} minutes of one buffer."
                )
            }
            if (integrated == null) {
                add(
                    "No integrated loudness: no 400 ms block passed the −70 LUFS gate, so this span is " +
                        "digital silence — or shorter than one block."
                )
            }
            if (range == null) {
                add("No loudness range: it needs at least two gated blocks that differ.")
            }
            if (peak == null) {
                add("No true peak: the span carries no signal at all.")
            }
            add(SCOPE)
        }
        return Analysis.Measured(
            integratedLufs = integrated,
            loudnessRangeLu = range,
            truePeakDbfs = peak,
            channels = channels,
            sampleRate = sampleRate,
            measuredFrames = measuredFrames,
            sourceFrames = sourceFrames,
            notes = notes
        )
    }

    /**
     * Measures [pcm]. The only function in this object that touches the native
     * module — and it never runs on an audio thread.
     */
    fun analyze(pcm: Pcm16, source: String): Analysis {
        validate(pcm.channels, pcm.sampleRate, pcm.frames, pcm.samples)?.let { return Analysis.Refused(it) }
        if (!LoudnessNative.available) {
            return Analysis.Refused(
                "Loudness measurement is unavailable on this device — the native module did not load " +
                    "(${LoudnessNative.loadError ?: "unknown reason"})."
            )
        }
        val measuredFrames = pcm.frames.coerceAtMost(maxFrames(pcm.sampleRate))
        val raw = try {
            LoudnessNative.measure(pcm.samples, pcm.channels, pcm.sampleRate, measuredFrames)
        } catch (t: Throwable) {
            return Analysis.Refused("The measurement failed: ${t.message ?: t.toString()}")
        }
        return interpret(raw, pcm.channels, pcm.sampleRate, measuredFrames, pcm.frames, source)
    }

    /** The lines the Audio → Diagnostics screen shows, in order. */
    fun reportLines(analysis: Analysis): List<String> = when (analysis) {
        is Analysis.Refused -> listOf("Not measured: ${analysis.reason}")
        is Analysis.Measured -> buildList {
            add(
                "Input: ${channelText(analysis.channels)}, ${rateText(analysis.sampleRate)}, " +
                    "${clock(analysis.measuredSeconds)} measured"
            )
            add(
                if (analysis.integratedLufs != null) {
                    "Integrated loudness: ${number(analysis.integratedLufs, 1)} LUFS"
                } else {
                    "Integrated loudness: not measurable from this span"
                }
            )
            add(
                if (analysis.loudnessRangeLu != null) {
                    "Loudness range: ${number(analysis.loudnessRangeLu, 1)} LU"
                } else {
                    "Loudness range: not measurable from this span"
                }
            )
            add(
                if (analysis.truePeakDbfs != null) {
                    "True peak: ${number(analysis.truePeakDbfs, 1)} dBTP"
                } else {
                    "True peak: no signal"
                }
            )
            addAll(analysis.notes)
        }
    }

    private fun nativeRefusal(status: Double): String = when (status) {
        LoudnessNative.STATUS_BAD_PARAMETERS.toDouble() ->
            "the native module refused the format (channel count, sample rate or buffer length)."
        LoudnessNative.STATUS_ADD_FAILED.toDouble() ->
            "the native module could not add those frames to the measurement."
        LoudnessNative.STATUS_INIT_FAILED.toDouble() ->
            "the native module could not create its EBU R 128 measurement state."
        else -> "the native measurement failed with status ${status.toInt()}."
    }

    private fun channelText(channels: Int): String = when (channels) {
        1 -> "mono"
        2 -> "stereo"
        else -> "$channels channels"
    }

    private fun rateText(sampleRate: Int): String =
        if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz"
        else String.format(Locale.ROOT, "%.1f kHz", sampleRate / 1000.0)

    private fun number(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)

    /** m:ss, so a span is readable next to the numbers it belongs to. */
    private fun clock(seconds: Double): String {
        val safe = if (seconds.isFinite() && seconds > 0) seconds else 0.0
        val total = safe.toInt()
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }
}
