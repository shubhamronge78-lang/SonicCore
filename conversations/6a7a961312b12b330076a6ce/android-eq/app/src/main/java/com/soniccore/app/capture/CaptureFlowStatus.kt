package com.soniccore.app.capture

internal object CaptureFlowStatus {
    const val RECENT_DATA_WINDOW_MS = 2500L

    fun hasRecentCaptureData(
        running: Boolean,
        paused: Boolean,
        framesCaptured: Long,
        lastDataAtElapsedMs: Long,
        nowElapsedMs: Long
    ): Boolean =
        running &&
            !paused &&
            framesCaptured > 0L &&
            lastDataAtElapsedMs > 0L &&
            nowElapsedMs >= lastDataAtElapsedMs &&
            nowElapsedMs - lastDataAtElapsedMs < RECENT_DATA_WINDOW_MS

    fun hasRecentOutputData(
        running: Boolean,
        paused: Boolean,
        framesCaptured: Long,
        framesOutput: Long,
        lastDataAtElapsedMs: Long,
        nowElapsedMs: Long
    ): Boolean =
        hasRecentCaptureData(
            running,
            paused,
            framesCaptured,
            lastDataAtElapsedMs,
            nowElapsedMs
        ) && framesOutput > 0L
}
