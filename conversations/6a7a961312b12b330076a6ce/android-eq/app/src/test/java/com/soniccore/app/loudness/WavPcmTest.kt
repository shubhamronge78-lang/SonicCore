package com.soniccore.app.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the WAV reader that feeds the loudness measurement.
 *
 * Every rule here exists because a wider reader would hand the measurement
 * something other than what the file says: a format it cannot report honestly, a
 * channel count it does not measure, or a byte stream whose framing disagrees
 * with its own header. Truncation is the one thing that is *not* a refusal — a
 * file cut short is measured as far as it goes, and says so.
 */
class WavPcmTest {

    // ── A tiny WAV writer, so a case is one readable expression ──

    private fun le16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun le32(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(), ((value shr 24) and 0xFF).toByte()
    )

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private fun chunk(id: String, body: ByteArray) =
        ascii(id) + le32(body.size) + body + if (body.size % 2 == 1) byteArrayOf(0) else ByteArray(0)

    private fun fmtChunk(
        channels: Int,
        sampleRate: Int,
        bits: Int,
        code: Int = 1,
        blockAlign: Int = channels * bits / 8,
        extra: ByteArray = ByteArray(0)
    ) = chunk(
        "fmt ",
        le16(code) + le16(channels) + le32(sampleRate) +
            le32(sampleRate * blockAlign) + le16(blockAlign) + le16(bits) + extra
    )

    private fun dataChunk(bytes: ByteArray, declaredSize: Int = bytes.size) =
        ascii("data") + le32(declaredSize) + bytes

    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { acc, part -> acc + part }
        return ascii("RIFF") + le32(body.size) + ascii("WAVE") + body
    }

    /** Interleaved 16-bit samples, in the byte order a WAV file uses. */
    private fun samples(vararg values: Int): ByteArray =
        values.fold(ByteArray(0)) { acc, value -> acc + le16(value and 0xFFFF) }

    // ── Readable files ──

    @Test
    fun aStereoFileBecomesInterleavedPcmWithItsOwnFormat() {
        val read = WavPcm.read(
            riff(
                fmtChunk(channels = 2, sampleRate = 48_000, bits = 16),
                dataChunk(samples(0, 0, 32767, -32768, -1, 1))
            )
        )
        assertTrue("expected a buffer, got $read", read is WavPcm.Read.Ok)
        val pcm = (read as WavPcm.Read.Ok).pcm
        assertEquals(2, pcm.channels)
        assertEquals(48_000, pcm.sampleRate)
        assertEquals(3, pcm.frames)
        assertEquals(6, pcm.samples.size)
        assertEquals(0, pcm.samples[0].toInt())
        assertEquals(32767, pcm.samples[2].toInt())
        assertEquals(-32768, pcm.samples[3].toInt())
        assertEquals(-1, pcm.samples[4].toInt())
        assertEquals(1, pcm.samples[5].toInt())
        assertTrue("nothing was truncated, so nothing is reported", read.notes.isEmpty())
    }

    @Test
    fun aMonoFileIsReadAsOneChannelAndAnyChunkOrderIsFollowed() {
        // The audio chunk may come before the format chunk; both are still read.
        val read = WavPcm.read(
            riff(
                dataChunk(samples(100, 200)),
                chunk("LIST", ascii("INFOsomenotes")),
                fmtChunk(channels = 1, sampleRate = 8_000, bits = 16)
            )
        )
        assertTrue("expected a buffer, got $read", read is WavPcm.Read.Ok)
        val pcm = (read as WavPcm.Read.Ok).pcm
        assertEquals(1, pcm.channels)
        assertEquals(8_000, pcm.sampleRate)
        assertEquals(2, pcm.frames)
        assertEquals(100, pcm.samples[0].toInt())
        assertEquals(200, pcm.samples[1].toInt())
    }

    @Test
    fun aWaveFormatExtensiblePcmFileIsAcceptedThroughItsSubformat() {
        // WAVE_FORMAT_EXTENSIBLE (0xFFFE) is what many editors write for plain PCM;
        // the real format is the first two bytes of its GUID.
        val extra = le16(22) + le16(16) + le32(0) + le16(1) + ByteArray(14)
        val read = WavPcm.read(
            riff(
                fmtChunk(channels = 2, sampleRate = 44_100, bits = 16, code = 0xFFFE, extra = extra),
                dataChunk(samples(5, 6))
            )
        )
        assertTrue("expected a buffer, got $read", read is WavPcm.Read.Ok)
        assertEquals(44_100, (read as WavPcm.Read.Ok).pcm.sampleRate)
    }

    // ── Truncation is measured as far as it goes, and said out loud ──

    @Test
    fun aCutShortAudioChunkIsMeasuredUpToItsLastCompleteFrame() {
        val read = WavPcm.read(
            riff(
                fmtChunk(channels = 2, sampleRate = 48_000, bits = 16),
                // Declares three frames but carries one and a half.
                dataChunk(samples(1, 2, 3), declaredSize = 12)
            )
        )
        assertTrue("expected a buffer, got $read", read is WavPcm.Read.Ok)
        val ok = read as WavPcm.Read.Ok
        assertEquals(1, ok.pcm.frames)
        assertTrue(
            "the truncation must be stated: ${ok.notes}",
            ok.notes.any { it.contains("cut short") && it.contains("12 bytes") }
        )
        assertTrue(
            "the skipped partial frame must be stated: ${ok.notes}",
            ok.notes.any { it.contains("partial frame") }
        )
    }

    @Test
    fun aReadThatStopsInsideALaterChunkStillUsesTheAudioItHas() {
        // This is what the picker's size cap looks like: the file continues, the
        // bytes handed in do not.
        val complete = riff(
            fmtChunk(channels = 1, sampleRate = 48_000, bits = 16),
            dataChunk(samples(7, 8)),
            chunk("LIST", ByteArray(4096) { 1 })
        )
        val read = WavPcm.read(complete.copyOf(complete.size - 2000))
        assertTrue("expected a buffer, got $read", read is WavPcm.Read.Ok)
        val ok = read as WavPcm.Read.Ok
        assertEquals(2, ok.pcm.frames)
        assertEquals(7, ok.pcm.samples[0].toInt())
    }

    // ── Refusals, each naming what it refused ──

    @Test
    fun formatsTheMeasurementCannotReportAreRefusedWithTheirOwnDetail() {
        val audio = samples(1, 2)

        val eightBit = WavPcm.read(
            riff(fmtChunk(2, 48_000, 8), dataChunk(audio))
        ) as WavPcm.Read.Refused
        assertTrue(eightBit.reason.contains("8-bit samples"))

        val float = WavPcm.read(
            riff(fmtChunk(2, 48_000, 32, code = 3), dataChunk(audio))
        ) as WavPcm.Read.Refused
        assertTrue("reason was ${float.reason}", float.reason.contains("format code 3"))

        val surround = WavPcm.read(
            riff(fmtChunk(6, 48_000, 16), dataChunk(audio))
        ) as WavPcm.Read.Refused
        assertTrue(surround.reason.contains("6 channels"))

        val oddRate = WavPcm.read(
            riff(fmtChunk(2, 4_000, 16), dataChunk(audio))
        ) as WavPcm.Read.Refused
        assertTrue(oddRate.reason.contains("8–192 kHz"))
    }

    @Test
    fun aFramingThatDisagreesWithItsOwnHeaderIsRefusedRatherThanGuessed() {
        val read = WavPcm.read(
            riff(fmtChunk(2, 48_000, 16, blockAlign = 3), dataChunk(samples(1, 2, 3, 4)))
        ) as WavPcm.Read.Refused
        assertTrue("reason was ${read.reason}", read.reason.contains("block alignment"))
    }

    @Test
    fun filesThatAreNotReadableAudioAreRefusedWithAReason() {
        assertTrue((WavPcm.read(ByteArray(4)) as WavPcm.Read.Refused).reason.contains("too short"))

        val notRiff = ByteArray(64) { 7 }
        assertTrue((WavPcm.read(notRiff) as WavPcm.Read.Refused).reason.contains("RIFF/WAVE"))

        val noFormat = WavPcm.read(riff(dataChunk(samples(1, 2)))) as WavPcm.Read.Refused
        assertTrue(noFormat.reason.contains("no format chunk"))

        val noData = WavPcm.read(riff(fmtChunk(2, 48_000, 16))) as WavPcm.Read.Refused
        assertTrue(noData.reason.contains("no audio data"))

        val emptyAudio = WavPcm.read(
            riff(fmtChunk(2, 48_000, 16), dataChunk(ByteArray(0)))
        ) as WavPcm.Read.Refused
        assertTrue(emptyAudio.reason.contains("no complete 16-bit sample frames"))
    }

    @Test
    fun theReaderReturnsTheFramesTheFileHoldsAndLeavesLengthPolicyToTheMeasurement() {
        // 480 frames of 48 kHz stereo, handed back as exactly that: the reader's
        // job is the file's own framing, while how long a span may be measured is
        // the module's decision (Loudness.MAX_ANALYSIS_SECONDS), stated in the
        // measurement's report rather than silently applied while parsing.
        val frames = 480
        val audio = ByteArray(frames * 4) { (it % 251).toByte() }
        val read = WavPcm.read(riff(fmtChunk(2, 48_000, 16), dataChunk(audio))) as WavPcm.Read.Ok
        assertEquals(frames, read.pcm.frames)
        assertEquals(frames * 2, read.pcm.samples.size)
        assertTrue("no truncation is reported for a file that is whole", read.notes.isEmpty())
        assertEquals(48_000 * 180, Loudness.maxFrames(48_000))
    }
}
