package com.soniccore.app.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperAccentProbeTest {
    private val fallback = 0xFF55CFE1.toInt()

    @Test
    fun reportsUnavailableAndKeepsTheFallbackWhenNoColourIsReadable() {
        val result = WallpaperAccentProbe.evaluate(null, fallback)
        assertEquals(fallback, result.argb)
        assertFalse(result.available)
        assertEquals(WallpaperAccentProbe.UNAVAILABLE_DETAIL, result.detail)
    }

    @Test
    fun reportsAvailableAndUsesTheWallpaperColourWhenThereIsOne() {
        val result = WallpaperAccentProbe.evaluate(0xFFFF4FD8.toInt(), fallback)
        assertEquals(0xFFFF4FD8.toInt(), result.argb)
        assertTrue(result.available)
        assertEquals(WallpaperAccentProbe.AVAILABLE_DETAIL, result.detail)
    }

    @Test
    fun aBlackWallpaperIsStillAColourAndNotTreatedAsMissing() {
        val result = WallpaperAccentProbe.evaluate(0x00000000, fallback)
        assertEquals(0x00000000, result.argb)
        assertTrue(result.available)
    }
}
