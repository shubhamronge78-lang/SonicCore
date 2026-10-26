package com.soniccore.app.loudness

/**
 * JNI bridge to the offline loudness measurement in
 * `cpp/loudness/loudness_jni.cpp` (vendored libebur128, MIT).
 *
 * It loads its **own** shared library (`libloudness.so`), separate from the DSP
 * engine's, so the measurement code cannot be reached from the real-time chain
 * and cannot change how audio is processed. [available] is false when the module
 * is not present on this device (a build without the library, or a device where
 * loading failed), and callers must report that instead of pretending a
 * measurement happened.
 */
object LoudnessNative {

    @Volatile
    var loadError: String? = null
        private set

    val available: Boolean get() = loadError == null

    init {
        loadError = try {
            System.loadLibrary("loudness")
            null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }
    }

    /** The measurement ran; the numbers may still be NaN when nothing was gated. */
    const val STATUS_OK = 0

    /** The channel count, sample rate, frame count or buffer length was refused. */
    const val STATUS_BAD_PARAMETERS = 1

    /** The frames could not be added to the measurement. */
    const val STATUS_ADD_FAILED = 2

    /** The EBU R 128 measurement state could not be created. */
    const val STATUS_INIT_FAILED = 3

    /**
     * Measures one interleaved 16-bit PCM buffer.
     *
     * @param samples the interleaved samples; must hold at least `frames * channels`
     * @return `[integratedLufs, loudnessRangeLu, truePeakDbfs, status]`, where the
     *   first three are NaN when they were not produced (silence, or a span
     *   shorter than one 400 ms gating block) and [status] says whether the call
     *   itself succeeded.
     */
    external fun measure(samples: ShortArray, channels: Int, sampleRate: Int, frames: Int): DoubleArray?
}
