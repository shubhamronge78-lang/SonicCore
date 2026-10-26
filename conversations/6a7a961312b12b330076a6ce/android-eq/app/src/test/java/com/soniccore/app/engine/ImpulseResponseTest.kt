package com.soniccore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The impulse-response reader, pinned on the JVM.
 *
 * The convolver itself is measured by the native harness; what these tests cover
 * is the part that decides *what the engine is handed* — the formats accepted,
 * every refusal, and the two transformations (resampling and truncation) that
 * must never happen silently.
 */
class ImpulseResponseTest {

    /** Builds a WAV file the way a real one is laid out: fmt, then data. */
    private fun wav(
        frameValues: List<FloatArray>,
        sampleRate: Int = 48000,
        bits: Int = 16,
        format: Int = 1,
        channels: Int = frameValues.first().size,
        dataBytesOverride: Int? = null
    ): ByteArray {
        val bytesPerSample = bits / 8
        val frameBytes = bytesPerSample * channels
        val data = ByteBuffer.allocate(frameValues.size * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in frameValues) {
            for (c in 0 until channels) {
                val v = frame[c]
                when {
                    format == 3 -> data.putFloat(v)
                    bits == 16 -> data.putShort((v * 32767f).toInt().toShort())
                    bits == 8 -> data.put((v * 127f).toInt().toByte())
                    bits == 24 -> {
                        val i = (v * 8388607f).toInt()
                        data.put((i and 0xFF).toByte())
                        data.put(((i shr 8) and 0xFF).toByte())
                        data.put(((i shr 16) and 0xFF).toByte())
                    }
                    else -> data.putInt((v * 2147483647f).toInt())
                }
            }
        }
        val dataBytes = data.array()

        val out = ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(value: Int) {
            out.write(value and 0xFF); out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF); out.write((value shr 24) and 0xFF)
        }
        fun le16(value: Int) {
            out.write(value and 0xFF); out.write((value shr 8) and 0xFF)
        }
        ascii("RIFF"); le32(36 + dataBytes.size); ascii("WAVE")
        ascii("fmt "); le32(16); le16(format); le16(channels); le32(sampleRate)
        le32(sampleRate * frameBytes); le16(frameBytes); le16(bits)
        ascii("data"); le32(dataBytesOverride ?: dataBytes.size)
        out.write(dataBytes)
        return out.toByteArray()
    }

    /** One unit impulse followed by silence — a valid, if plain, response. */
    private fun impulse(frames: Int = 200, channels: Int = 2): List<FloatArray> =
        List(frames) { i ->
            FloatArray(channels) { c -> if (i == 0) (if (c == 0) 1f else 0.5f) else 0f }
        }

    private fun ok(read: ImpulseResponse.Read): ImpulseResponse.Read.Ok {
        assertTrue("expected the file to be accepted, was $read", read is ImpulseResponse.Read.Ok)
        return read as ImpulseResponse.Read.Ok
    }

    private fun refused(read: ImpulseResponse.Read): String {
        assertTrue("expected a refusal, was $read", read is ImpulseResponse.Read.Refused)
        return (read as ImpulseResponse.Read.Refused).reason
    }

    @Test
    fun `a stereo 16-bit response becomes the engine's taps`() {
        val read = ok(ImpulseResponse.read(wav(impulse(frames = 200))))
        assertEquals(200, read.taps)
        assertEquals(1f, read.left[0], 0.001f)
        assertEquals(0.5f, read.right[0], 0.001f)
        assertEquals(0f, read.left[1], 0.0001f)
        assertEquals(48000, read.sourceSampleRate)
        assertEquals(200, read.sourceFrames)
        assertTrue("nothing was resampled or truncated", read.notes.isEmpty())
    }

    @Test
    fun `a mono response feeds both output channels and says it is mono`() {
        val read = ok(ImpulseResponse.read(wav(impulse(frames = 100, channels = 1))))
        assertEquals(read.left.toList(), read.right.toList())
        assertTrue(
            "the mono duplication must be reported, notes were ${read.notes}",
            read.notes.any { it.contains("mono", ignoreCase = true) }
        )
    }

    @Test
    fun `float samples are read`() {
        val read = ok(ImpulseResponse.read(wav(impulse(frames = 64), bits = 32, format = 3)))
        assertEquals(64, read.taps)
        assertEquals(1f, read.left[0], 0.001f)
    }

    @Test
    fun `an unsupported sample format is refused by name`() {
        val reason = refused(ImpulseResponse.read(wav(impulse(frames = 64), bits = 8)))
        assertTrue("the refusal has to name what it cannot read, was: $reason", reason.contains("8-bit"))
    }

    @Test
    fun `more than two channels is refused`() {
        val frames = List(64) { FloatArray(3) { c -> if (c == 0) 1f else 0f } }
        val reason = refused(ImpulseResponse.read(wav(frames, channels = 3)))
        assertTrue("was: $reason", reason.contains("3 channels"))
    }

    @Test
    fun `a silent file is refused rather than loaded as pass-through`() {
        val frames = List(128) { FloatArray(2) { 0f } }
        val reason = refused(ImpulseResponse.read(wav(frames)))
        assertTrue("was: $reason", reason.contains("silent"))
    }

    @Test
    fun `a response too short to be a response is refused`() {
        val reason = refused(ImpulseResponse.read(wav(impulse(frames = 4))))
        assertTrue("was: $reason", reason.contains("too short"))
    }

    @Test
    fun `a long file is truncated to the engine's limit and says how many taps it kept`() {
        val frames = List(3000) { i -> FloatArray(2) { if (i == 0) 1f else 0f } }
        val read = ok(ImpulseResponse.read(wav(frames)))
        assertEquals(ImpulseResponse.MAX_TAPS, read.taps)
        assertEquals(3000, read.sourceFrames)
        assertTrue(
            "the truncation must be reported, notes were ${read.notes}",
            read.notes.any { it.contains("2048") }
        )
    }

    @Test
    fun `a different file rate is resampled and the approximation is reported`() {
        val frames = List(441) { i -> FloatArray(2) { if (i == 0) 1f else 0f } }
        val read = ok(ImpulseResponse.read(wav(frames, sampleRate = 44100)))
        assertEquals(44100, read.sourceSampleRate)
        assertEquals(480, read.taps)
        assertTrue(
            "resampling has to be stated, notes were ${read.notes}",
            read.notes.any { it.contains("Resampled") }
        )
    }

    @Test
    fun `a peak above full scale is reported instead of normalised`() {
        val frames = List(64) { i -> FloatArray(2) { if (i == 0) 1.6f else 0f } }
        val read = ok(ImpulseResponse.read(wav(frames, bits = 32, format = 3)))
        assertEquals(1.6f, read.peak, 0.01f)
        assertTrue(
            "the level has to be stated, notes were ${read.notes}",
            read.notes.any { it.contains("full scale") }
        )
    }

    @Test
    fun `a file that is not a WAV is refused`() {
        val reason = refused(ImpulseResponse.read("not a wav file at all".toByteArray()))
        assertTrue("was: $reason", reason.contains("RIFF/WAVE"))
    }

    @Test
    fun `a cut-short file uses what is present and says so`() {
        val read = ok(ImpulseResponse.read(wav(impulse(frames = 200), dataBytesOverride = 100_000)))
        assertEquals(200, read.taps)
        assertTrue(
            "the short read must be reported, notes were ${read.notes}",
            read.notes.any { it.contains("cut short") }
        )
    }

    @Test
    fun `a rate is spelled the way the rest of the app spells it`() {
        assertEquals("48 kHz", ImpulseResponse.kilo(48000))
        assertEquals("44.1 kHz", ImpulseResponse.kilo(44100))
    }
}
