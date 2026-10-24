package com.neon.eq.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFlowStatusTest {
    @Test
    fun captureDataRequiresRunningUnpausedSessionWithRecentFrames() {
        assertTrue(recentCapture())
        assertFalse(recentCapture(running = false))
        assertFalse(recentCapture(paused = true))
        assertFalse(recentCapture(framesCaptured = 0L))
        assertFalse(recentCapture(lastDataAtElapsedMs = 0L))
        assertFalse(recentCapture(lastDataAtElapsedMs = 7_499L))
        assertFalse(recentCapture(lastDataAtElapsedMs = 10_001L))
    }

    @Test
    fun outputDataRequiresAcceptedOutputFramesAsWellAsCapturedInput() {
        assertTrue(recentOutput())
        assertFalse(recentOutput(framesCaptured = 0L))
        assertFalse(recentOutput(framesOutput = 0L))
        assertFalse(recentOutput(paused = true))
        assertFalse(recentOutput(running = false))
    }

    private fun recentCapture(
        running: Boolean = true,
        paused: Boolean = false,
        framesCaptured: Long = 480L,
        lastDataAtElapsedMs: Long = 8_000L
    ): Boolean = CaptureFlowStatus.hasRecentCaptureData(
        running = running,
        paused = paused,
        framesCaptured = framesCaptured,
        lastDataAtElapsedMs = lastDataAtElapsedMs,
        nowElapsedMs = 10_000L
    )

    private fun recentOutput(
        running: Boolean = true,
        paused: Boolean = false,
        framesCaptured: Long = 480L,
        framesOutput: Long = 480L
    ): Boolean = CaptureFlowStatus.hasRecentOutputData(
        running = running,
        paused = paused,
        framesCaptured = framesCaptured,
        framesOutput = framesOutput,
        lastDataAtElapsedMs = 8_000L,
        nowElapsedMs = 10_000L
    )
}
