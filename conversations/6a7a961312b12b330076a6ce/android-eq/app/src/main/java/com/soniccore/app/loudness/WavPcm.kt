package com.soniccore.app.loudness

/**
 * SONICCORE — 16-BIT PCM WAV READER (input for offline loudness analysis)
 *
 * The loudness module measures one buffer of PCM; this produces that buffer from
 * a `.wav` file the user picked. It is deliberately narrow, because a wider
 * reader would silently take on formats the measurement cannot honestly report:
 *
 *  * **16-bit PCM only.** Anything else (float, 24-bit, ADPCM, MP3-in-WAV) is
 *    refused by name and format code rather than converted into something that
 *    looks measured but is not.
 *  * **Mono or stereo only** — the module's own channel limits.
 *  * **A bounded read.** The bytes arrive already capped by the caller
 *    ([MAX_FILE_BYTES]); a cut-short file is reported as truncated and only its
 *    complete frames are measured.
 *  * **No interpretation.** A file whose declared block alignment disagrees with
 *    its own format is refused, never guessed at, so the sample stream that
 *    reaches the measurement is exactly the one the file describes.
 *
 * Pure Kotlin over a byte array — no Android and no I/O — so `WavPcmTest` pins
 * every header rule, every refusal and the truncation handling on the JVM.
 */
object WavPcm {

    /**
     * Most bytes a caller may hand in: about three minutes of 48 kHz stereo
     * 16-bit audio, which is also the measurement's own cap at that format. The
     * cap exists so one pathological file cannot exhaust the heap.
     */
    const val MAX_FILE_BYTES = 32 * 1024 * 1024

    /** Where a buffer from this reader came from, in the report's words. */
    const val SOURCE = "The WAV file you picked"

    sealed interface Read {
        /** A buffer ready to measure, plus anything the file itself revealed. */
        data class Ok(val pcm: Loudness.Pcm16, val notes: List<String>) : Read

        /** A file this reader will not turn into a buffer — with the reason. */
        data class Refused(val reason: String) : Read
    }

    /** Reads [bytes] as a WAV file; see the class comment for what that accepts. */
    fun read(bytes: ByteArray): Read {
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
        var dataDeclared = 0L
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
                    dataDeclared = declared
                    notes += "That file is cut short: it declares $declared bytes of audio but carries " +
                        "$bodyAvailable, so the part that is present was measured."
                    break
                }
                // A read that stopped at the caller's cap can end inside a later
                // chunk; the audio that is present is still usable.
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
                    dataDeclared = declared
                }
            }
            offset = body + declared.toInt() + (declared.toInt() and 1)
        }

        if (!sawFormat) return Read.Refused("That file carries no format chunk, so SonicCore cannot read it.")
        if (dataStart < 0) return Read.Refused("That file carries no audio data.")
        if (formatCode != 1) {
            return Read.Refused(
                "That file is not uncompressed PCM audio (format code $formatCode); SonicCore measures " +
                    "16-bit PCM WAV files."
            )
        }
        if (bitsPerSample != 16) {
            return Read.Refused("That file carries $bitsPerSample-bit samples; SonicCore measures 16-bit PCM.")
        }
        if (channels < Loudness.MIN_CHANNELS || channels > Loudness.MAX_CHANNELS) {
            return Read.Refused(
                "SonicCore measures mono or stereo audio; that file has $channels channels."
            )
        }
        if (sampleRate < Loudness.MIN_SAMPLE_RATE || sampleRate > Loudness.MAX_SAMPLE_RATE) {
            return Read.Refused(
                "SonicCore measures audio at ${Loudness.MIN_SAMPLE_RATE / 1000}–" +
                    "${Loudness.MAX_SAMPLE_RATE / 1000} kHz; that file is at $sampleRate Hz."
            )
        }
        val frameBytes = channels * 2
        if (blockAlign != frameBytes) {
            return Read.Refused(
                "That file declares a block alignment of $blockAlign bytes for $channels × 16-bit audio " +
                    "($frameBytes bytes per frame), so its samples cannot be read as they are described."
            )
        }

        val available = minOf(dataDeclared, (bytes.size - dataStart).toLong()).toInt()
        val frames = available / frameBytes
        if (frames <= 0) return Read.Refused("That file carries no complete 16-bit sample frames.")
        val remainder = available % frameBytes
        if (remainder != 0) {
            notes += "A partial frame at the end of the audio chunk was skipped."
        }

        val samples = ShortArray(frames * channels)
        var at = dataStart
        for (i in samples.indices) {
            val raw = unsigned16(bytes, at)
            samples[i] = (if (raw >= 0x8000) raw - 0x10000 else raw).toShort()
            at += 2
        }
        return Read.Ok(
            pcm = Loudness.Pcm16(channels, sampleRate, frames, samples),
            notes = notes
        )
    }

    private fun ascii(bytes: ByteArray, at: Int, text: String): Boolean {
        if (at + text.length > bytes.size) return false
        for (i in text.indices) {
            if (bytes[at + i].toInt().toChar() != text[i]) return false
        }
        return true
    }

    private fun asciiString(bytes: ByteArray, at: Int): String =
        String(bytes, at, 4, Charsets.US_ASCII)

    private fun unsigned16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun unsigned32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)
}
