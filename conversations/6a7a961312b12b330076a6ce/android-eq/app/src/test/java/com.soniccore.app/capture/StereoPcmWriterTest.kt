package com.soniccore.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class StereoPcmWriterTest {
    private val pcm = ShortArray(8) { it.toShort() }

    @Test
    fun fullWriteCountsAllStereoFrames() {
        val calls = mutableListOf<Pair<Int, Int>>()

        val result = writeStereoPcmFully(pcm, pcm.size) { offset, count ->
            calls += offset to count
            count
        }

        assertEquals(4, result.framesWritten)
        assertNull(result.error)
        assertEquals(listOf(0 to 8), calls)
    }

    @Test
    fun partialWritesContinueAtAcceptedSampleOffset() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val writes = listOf(2, 4, 2)

        val result = writeStereoPcmFully(pcm, pcm.size) { offset, count ->
            calls += offset to count
            writes[calls.lastIndex]
        }

        assertEquals(4, result.framesWritten)
        assertNull(result.error)
        assertEquals(listOf(0 to 8, 2 to 6, 6 to 2), calls)
    }

    @Test
    fun zeroWriteReportsNoProgress() {
        val result = writeStereoPcmFully(pcm, pcm.size) { _, _ -> 0 }
        var calls = 0
        val afterPartialWrite = writeStereoPcmFully(pcm, pcm.size) { _, _ ->
            if (calls++ == 0) 4 else 0
        }

        assertEquals(0, result.framesWritten)
        assertNotNull(result.error)
        assertEquals(2, afterPartialWrite.framesWritten)
        assertEquals("write made no progress", afterPartialWrite.error)
    }

    @Test
    fun errorAfterPartialWriteCountsOnlyAcceptedFrames() {
        var calls = 0

        val result = writeStereoPcmFully(pcm, pcm.size) { _, _ ->
            if (calls++ == 0) 4 else -6
        }

        assertEquals(2, result.framesWritten)
        assertEquals("write returned error code -6", result.error)
    }

    @Test
    fun writeExceptionsBecomeReportedErrors() {
        val result = writeStereoPcmFully(pcm, pcm.size) { _, _ ->
            throw IllegalStateException("track is stopped")
        }

        assertEquals(0, result.framesWritten)
        assertEquals("write threw: track is stopped", result.error)
    }

    @Test
    fun fatalErrorsAreNotConvertedIntoWriteResults() {
        try {
            writeStereoPcmFully(pcm, pcm.size) { _, _ ->
                throw AssertionError("fatal")
            }
            fail("fatal JVM errors must propagate")
        } catch (error: AssertionError) {
            assertEquals("fatal", error.message)
        }
    }

    @Test
    fun rejectsUnalignedWriteResultAndSampleCount() {
        val oddResult = writeStereoPcmFully(pcm, pcm.size) { _, _ -> 3 }
        val oddCount = writeStereoPcmFully(pcm, pcm.size - 1) { _, _ -> 0 }

        assertEquals(0, oddResult.framesWritten)
        assertNotNull(oddResult.error)
        assertEquals(0, oddCount.framesWritten)
        assertNotNull(oddCount.error)
    }
}
