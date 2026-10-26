package com.soniccore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored impulse response: what survives a restart, and what is refused.
 *
 * The store is untrusted input on the way in — it is a file in the app's own
 * directory — so a damaged or foreign file must decode to `null` rather than to
 * taps the convolver would then convolve with. The file I/O around this codec is
 * thin; these tests cover the encoding rules it depends on.
 */
class IrStoreTest {

    private fun stored(
        name: String = "studio.wav",
        taps: Int = 64,
        rate: Int = 48000
    ) = StoredImpulse(
        name = name,
        left = FloatArray(taps) { if (it == 0) 1f else 0f },
        right = FloatArray(taps) { if (it == 0) 0.5f else 0f },
        sourceSampleRate = rate,
        sourceFrames = taps,
        peak = 1f
    )

    @Test
    fun `a stored response round-trips exactly`() {
        val original = stored()
        val decoded = IrStore.decode(IrStore.encode(original))
        assertNotNull(decoded)
        decoded!!
        assertEquals(original.name, decoded.name)
        assertEquals(original.taps, decoded.taps)
        assertEquals(original.sourceSampleRate, decoded.sourceSampleRate)
        assertEquals(original.sourceFrames, decoded.sourceFrames)
        assertEquals(original.peak, decoded.peak, 0f)
        assertEquals(original.left.toList(), decoded.left.toList())
        assertEquals(original.right.toList(), decoded.right.toList())
    }

    @Test
    fun `a foreign file is refused`() {
        assertNull(IrStore.decode("SCIR".toByteArray() + ByteArray(32)))
        assertNull(IrStore.decode(ByteArray(40)))
    }

    @Test
    fun `a truncated store is refused`() {
        val bytes = IrStore.encode(stored(taps = 256))
        assertNull(IrStore.decode(bytes.copyOf(bytes.size - 64)))
    }

    @Test
    fun `a store claiming more taps than it carries is refused`() {
        val bytes = IrStore.encode(stored(taps = 8)).copyOf()
        // Corrupt the tap count field (magic 4 + version 1 + name length 2 + rate 4 + frames 4 + peak 4).
        val tapOffset = 4 + 1 + 2 + 4 + 4 + 4
        bytes[tapOffset] = (ImpulseResponse.MAX_TAPS + 1).toByte()
        bytes[tapOffset + 1] = ((ImpulseResponse.MAX_TAPS + 1) shr 8).toByte()
        bytes[tapOffset + 2] = 0
        bytes[tapOffset + 3] = 0
        assertNull(IrStore.decode(bytes))
    }

    @Test
    fun `a name is sanitised as untrusted input`() {
        assertEquals("room.wav", IrStore.sanitizeName("  room.wav  "))
        assertEquals("etcpasswd", IrStore.sanitizeName("../../etc/passwd"))
        assertEquals("impulse-response.wav", IrStore.sanitizeName("   "))
        assertEquals(60, IrStore.sanitizeName("x".repeat(200)).length)
    }

    @Test
    fun `the card's description states the response or its absence`() {
        assertTrue(IrStore.describe(null).contains("No impulse response"))
        val text = IrStore.describe(stored(name = "hall.wav", taps = 512, rate = 44100))
        assertTrue("was: $text", text.contains("hall.wav"))
        assertTrue("was: $text", text.contains("512 taps"))
        assertTrue("was: $text", text.contains("44.1 kHz"))
    }

    @Test
    fun `decoding a read keeps the file's own facts`() {
        val read = ImpulseResponse.Read.Ok(
            left = floatArrayOf(1f, 0f, 0f),
            right = floatArrayOf(1f, 0f, 0f),
            sourceSampleRate = 44100,
            sourceFrames = 441,
            peak = 1f,
            notes = emptyList()
        )
        val value = IrStore.from(read, "/tmp/../hall.wav")
        assertEquals("tmp..hall.wav", value.name)
        assertEquals(44100, value.sourceSampleRate)
        assertEquals(441, value.sourceFrames)
        assertEquals(3, value.taps)
    }
}
