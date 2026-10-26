package com.soniccore.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * SONICCORE — IMPULSE RESPONSE READER (input for the Convolver stage)
 *
 * The native chain has had a real FIR convolver for a long time
 * (`neondsp.cpp`, `MAXIR` taps, `NeonDsp.loadIr`) but nothing in the app could
 * hand it an impulse response, so the stage was listed as unusable. This reads
 * one `.wav` file into the two float arrays that stage takes.
 *
 * It is deliberately narrow, for the same reason [com.soniccore.app.loudness.WavPcm]
 * is: a wider reader would quietly accept formats the engine cannot honestly
 * convolve.
 *
 *  * **PCM 16/24/32-bit or 32-bit float only.** Anything else (8-bit, ADPCM,
 *    MP3-in-WAV) is refused by name instead of being resampled into a guess.
 *  * **Mono or stereo only** — the engine's convolver is stereo.
 *  * **Bounded.** The engine keeps at most [MAX_TAPS]; a longer file is
 *    truncated and *says so*, and the caller caps the bytes it reads at
 *    [MAX_FILE_BYTES].
 *  * **No silent normalisation.** The convolver applies no makeup gain, so the
 *    taps are passed through at the level the file states; a peak above 1.0 is
 *    reported rather than quietly scaled, and a silent file is refused because
 *    it would change nothing.
 *
 * Pure Kotlin over a byte array — no Android and no I/O — so
 * `ImpulseResponseTest` pins every header rule, every refusal and the
 * resampling/truncation decisions on the JVM.
 */
object ImpulseResponse {

    /**
     * The engine's own limit: `#define MAXIR` in `neondsp.cpp`. The native
     * side truncates too, but doing it here is what lets the screen say how
     * many taps the engine actually got.
     */
    const val MAX_TAPS = 2048

    /** The engine's default rate — the capture pipeline runs stereo at this rate. */
    const val ENGINE_SAMPLE_RATE = 48000

    /** Shortest useful response; below this it is a click, not a space. */
    const val MIN_TAPS = 32

    /** Most bytes a caller may hand in. An IR is a few hundred kB at most. */
    const val MAX_FILE_BYTES = 8 * 1024 * 1024

    sealed interface Read {
        data class Ok(
            val left: FloatArray,
            val right: FloatArray,
            val sourceSampleRate: Int,
            val sourceFrames: Int,
            val peak: Float,
            val notes: List<String>
        ) : Read {
            val taps: Int get() = left.size
        }

        /** A file this reader will not turn into taps — with the reason. */
        data class Refused(val reason: String) : Read
    }

    /**
     * Reads [bytes] as an impulse-response WAV, resampling to [engineRate].
     *
     * Resampling is linear interpolation and says so: an impulse response
     * resampled here is an approximation of the file, never a measurement of it.
     */
    fun read(bytes: ByteArray, engineRate: Int = ENGINE_SAMPLE_RATE): Read {
        if (bytes.size < 12) return Read.Refused("That file is too short to be a WAV file.")
        if (!ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) {
            return Read.Refused("That file is not a RIFF/WAVE file.")
        }

        var formatCode = -1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var dataStart = -1
        var dataAvailable = 0
        var sawFormat = false
        val notes = mutableListOf<String>()

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = asciiString(bytes, offset)
            val declared = unsigned32(bytes, offset + 4)
            val body = offset + 8
            val bodyAvailable = bytes.size - body
            if (declared > bodyAvailable) {
                if (id == "data") {
                    dataStart = body
                    dataAvailable = bodyAvailable
                    notes += "That file is cut short: it declares $declared bytes of audio but carries " +
                        "$bodyAvailable, so the part that is present was used."
                    break
                }
                if (dataStart >= 0) break
                return Read.Refused("That file's “$id” chunk is cut short, so its audio cannot be read.")
            }
            when (id) {
                "fmt " -> {
                    if (declared < 16) return Read.Refused("That file's format chunk is too small to read.")
                    formatCode = unsigned16(bytes, body)
                    channels = unsigned16(bytes, body + 2)
                    sampleRate = unsigned32(bytes, body + 4).toInt()
                    blockAlign = unsigned16(bytes, body + 12)
                    bitsPerSample = unsigned16(bytes, body + 14)
                    // WAVE_FORMAT_EXTENSIBLE carries the real format in its GUID.
                    if (formatCode == 0xFFFE && declared >= 26) {
                        formatCode = unsigned16(bytes, body + 24)
                    }
                    sawFormat = true
                }
                "data" -> {
                    dataStart = body
                    dataAvailable = declared
                }
            }
            offset = body + declared + (declared and 1)
        }

        if (!sawFormat) return Read.Refused("That file carries no format chunk, so SonicCore cannot read it.")
        if (dataStart < 0) return Read.Refused("That file carries no audio data, so it cannot be an impulse response.")
        if (sampleRate <= 0) return Read.Refused("That file declares no sample rate, so it cannot be resampled.")
        if (channels != 1 && channels != 2) {
            return Read.Refused("That file has $channels channels — the convolver takes mono or stereo.")
        }

        val bytesPerSample = when {
            formatCode == 1 && bitsPerSample == 16 -> 2
            formatCode == 1 && bitsPerSample == 24 -> 3
            formatCode == 1 && bitsPerSample == 32 -> 4
            formatCode == 3 && bitsPerSample == 32 -> 4
            else -> return Read.Refused(
                "That file uses format $formatCode with $bitsPerSample-bit samples — SonicCore's convolver " +
                    "reads 16/24/32-bit PCM or 32-bit float WAV files."
            )
        }
        val frameBytes = bytesPerSample * channels
        if (blockAlign != 0 && blockAlign != frameBytes) {
            return Read.Refused(
                "That file's block alignment ($blockAlign) disagrees with its own format " +
                    "(${frameBytes} bytes per frame), so it is refused rather than guessed at."
            )
        }

        val usableBytes = min(dataAvailable, bytes.size - dataStart)
        val sourceFrames = usableBytes / frameBytes
        if (sourceFrames <= 0) return Read.Refused("That file carries no complete audio frames.")
        if (sourceFrames < MIN_TAPS) {
            return Read.Refused(
                "That file is only $sourceFrames frames long — too short to act as an impulse response " +
                    "(the shortest SonicCore accepts is $MIN_TAPS)."
            )
        }

        var leftSrc = FloatArray(sourceFrames)
        var rightSrc = if (channels == 2) FloatArray(sourceFrames) else leftSrc
        for (i in 0 until sourceFrames) {
            val base = dataStart + i * frameBytes
            val l = decodeSample(bytes, base, bytesPerSample, formatCode == 3)
            leftSrc[i] = l
            if (channels == 2) rightSrc[i] = decodeSample(bytes, base + bytesPerSample, bytesPerSample, formatCode == 3)
        }
        if (channels == 1) {
            notes += "That file is mono; both output channels use the same impulse response."
        }

        val safeEngineRate = if (engineRate > 0) engineRate else ENGINE_SAMPLE_RATE
        if (sampleRate != safeEngineRate) {
            val ratio = safeEngineRate.toDouble() / sampleRate
            val resampledFrames = max(1, (sourceFrames * ratio).roundToInt())
            leftSrc = resample(leftSrc, resampledFrames, ratio)
            rightSrc = resample(rightSrc, resampledFrames, ratio)
            notes += "Resampled from ${kilo(sampleRate)} to ${kilo(safeEngineRate)} by linear interpolation — " +
                "an approximation of the file, not a measurement of it."
        }

        if (leftSrc.size > MAX_TAPS) {
            notes += "The file holds ${leftSrc.size} frames; the engine keeps the first $MAX_TAPS taps."
            leftSrc = leftSrc.copyOf(MAX_TAPS)
            rightSrc = rightSrc.copyOf(MAX_TAPS)
        }

        var peak = 0f
        for (i in leftSrc.indices) peak = max(peak, abs(leftSrc[i]))
        for (i in rightSrc.indices) peak = max(peak, abs(rightSrc[i]))
        if (peak < SILENCE_FLOOR) {
            return Read.Refused(
                "That file is silent (peak ${"%.3f".format(peak)}) — loading it would pass audio through " +
                    "with no audible change."
            )
        }
        if (peak > 1f) {
            notes += "The loudest tap is ${"%.2f".format(peak)}× full scale and the convolver adds no makeup " +
                "gain, so keep the limiter enabled after the convolver."
        }

        return Read.Ok(
            left = leftSrc,
            right = rightSrc,
            sourceSampleRate = sampleRate,
            sourceFrames = sourceFrames,
            peak = peak,
            notes = notes
        )
    }

    /** "48 kHz" / "44.1 kHz" — the way the rest of the app states a rate. */
    fun kilo(rate: Int): String {
        if (rate <= 0) return "unknown rate"
        val tenths = (rate / 100f).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
    }

    /** Level below which an IR is treated as silent rather than very quiet. */
    private const val SILENCE_FLOOR = 1e-4f

    private fun resample(source: FloatArray, outFrames: Int, ratio: Double): FloatArray {
        if (outFrames == source.size && ratio == 1.0) return source
        val out = FloatArray(outFrames)
        for (i in 0 until outFrames) {
            val position = i / ratio
            val index = position.toInt()
            if (index >= source.size - 1) {
                out[i] = source.last()
            } else {
                val fraction = (position - index).toFloat()
                out[i] = source[index] * (1f - fraction) + source[index + 1] * fraction
            }
        }
        return out
    }

    private fun decodeSample(bytes: ByteArray, offset: Int, bytesPerSample: Int, isFloat: Boolean): Float {
        val raw = when (bytesPerSample) {
            2 -> unsigned16(bytes, offset).toShort().toInt().toFloat() / 32768f
            3 -> {
                val v = ((bytes[offset].toInt() and 0xFF)) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16)
                // Sign-extend 24 bits, then scale.
                ((v shl 8) shr 8).toFloat() / 8388608f
            }
            else -> {
                val v = (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                if (isFloat) Float.fromBits(v) else v.toFloat() / 2147483648f
            }
        }
        // A malformed float sample must not reach the audio thread as a NaN.
        return if (raw.isFinite()) raw else 0f
    }

    private fun ascii(bytes: ByteArray, offset: Int, expected: String): Boolean =
        asciiString(bytes, offset, expected.length) == expected

    private fun asciiString(bytes: ByteArray, offset: Int, length: Int = 4): String {
        if (offset < 0 || offset + length > bytes.size) return ""
        val sb = StringBuilder(length)
        for (i in 0 until length) sb.append((bytes[offset + i].toInt() and 0xFF).toChar())
        return sb.toString()
    }

    private fun unsigned16(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    /** A 32-bit WAV size field: unsigned in the file, kept as Long so it cannot wrap. */
    private fun unsigned32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        val v = (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        return if (v < 0) Int.MAX_VALUE else v
    }
}
