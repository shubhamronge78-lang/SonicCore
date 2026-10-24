package com.neon.eq.capture

internal data class StereoPcmWriteResult(
    val framesWritten: Int,
    val error: String? = null
)

/**
 * Writes interleaved stereo PCM, where both the requested count and write
 * return value are measured in 16-bit samples (not frames).
 */
internal fun writeStereoPcmFully(
    samples: ShortArray,
    sampleCount: Int,
    write: (offsetInSamples: Int, sampleCount: Int) -> Int
): StereoPcmWriteResult {
    if (sampleCount < 0 || sampleCount > samples.size || sampleCount % 2 != 0) {
        return StereoPcmWriteResult(0, "invalid stereo sample count: $sampleCount")
    }

    var offset = 0
    while (offset < sampleCount) {
        val remaining = sampleCount - offset
        val written = try {
            write(offset, remaining)
        } catch (t: Exception) {
            return StereoPcmWriteResult(offset / 2, "write threw: ${t.message ?: t}")
        }
        when {
            written < 0 ->
                return StereoPcmWriteResult(offset / 2, "write returned error code $written")
            written == 0 ->
                return StereoPcmWriteResult(offset / 2, "write made no progress")
            written > remaining ->
                return StereoPcmWriteResult(offset / 2, "write returned $written for $remaining samples")
            written % 2 != 0 ->
                return StereoPcmWriteResult(offset / 2, "write broke stereo frame alignment: $written samples")
            else -> offset += written
        }
    }

    return StereoPcmWriteResult(offset / 2)
}
