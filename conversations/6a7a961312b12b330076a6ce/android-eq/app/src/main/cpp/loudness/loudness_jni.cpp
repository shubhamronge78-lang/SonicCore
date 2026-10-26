/*
 * SONICCORE — OFFLINE LOUDNESS MEASUREMENT (JNI facade over libebur128)
 *
 * WHAT THIS IS
 *   One measurement of one already-captured buffer of 16-bit PCM: the
 *   integrated loudness (LUFS, ITU-R BS.1770-4 / EBU R 128 with gating), the
 *   loudness range (LRA) and the true peak (4x oversampled), using the vendored
 *   libebur128 in ../third_party/ebur128 (MIT, pinned in that directory's
 *   README).
 *
 * WHAT THIS IS NOT
 *   It is not part of the real-time chain and it is not called from it. Nothing
 *   in neondsp.cpp references this file, it lives in its own shared library
 *   (libloudness.so), and it allocates while it runs — so it can never add
 *   latency to playback or to the capture path. Analysis is deliberately
 *   offline: a file (or a finished buffer) is measured once, after the fact.
 *   The module therefore only ever reports on PCM it was actually handed, and
 *   the Kotlin side says so in the report; SonicCore does not claim to measure
 *   live or system-wide audio, because the system route supplies PCM only while
 *   a capture session is running and that path is not measured here.
 *
 * CONTRACT (kept identical in Kotlin's LoudnessNative)
 *   measure(samples, channels, sampleRate, frames) ->
 *       [integratedLufs, loudnessRangeLu, truePeakDbfs, status]
 *   status: 0 = measured, 1 = parameters refused, 2 = frames could not be added,
 *           3 = the EBU R 128 state could not be created.
 *   integratedLufs / loudnessRangeLu / truePeakDbfs are NaN when the signal
 *   carried no gated block (digital silence, or shorter than one 400 ms block) —
 *   "not measurable" is reported as such, never as -inf LUFS or 0 dBTP.
 *
 * The measurement core below is a plain C++ function so the host harness
 * (tools/dspharness/loudness_harness.cpp) can drive it directly under
 * ASan+UBSan, exactly as the DSP harness drives neondsp.cpp.
 */

#include <jni.h>

#include <cmath>
#include <cstdint>
#include <cstddef>

#include "ebur128.h"

namespace {

constexpr int kStatusOk = 0;
constexpr int kStatusBadParameters = 1;
constexpr int kStatusAddFailed = 2;
constexpr int kStatusInitFailed = 3;

/* The Kotlin layer enforces the app's own limits; these are the hard ones the
 * native side refuses regardless of what a caller asks for. */
constexpr int kMinChannels = 1;
constexpr int kMaxChannels = 2;
constexpr int kMinSampleRate = 8000;
constexpr int kMaxSampleRate = 192000;

struct Measurement {
    double integratedLufs = NAN;
    double loudnessRangeLu = NAN;
    double truePeakDbfs = NAN;
    int status = kStatusOk;
};

}  // namespace

/*
 * Measures [frames] interleaved 16-bit frames of [channels] channels.
 *
 * @return a Measurement whose status says whether the numbers are meaningful.
 *   Every field is NaN unless the matching value was actually produced.
 */
static Measurement soniccore_loudness_measure(const int16_t* interleaved,
                                              int channels,
                                              int sampleRate,
                                              int frames) {
    Measurement out;
    if (interleaved == nullptr || channels < kMinChannels || channels > kMaxChannels ||
        sampleRate < kMinSampleRate || sampleRate > kMaxSampleRate || frames <= 0) {
        out.status = kStatusBadParameters;
        return out;
    }

    ebur128_state* state = ebur128_init(static_cast<unsigned int>(channels),
                                       static_cast<unsigned long>(sampleRate),
                                       EBUR128_MODE_I | EBUR128_MODE_LRA | EBUR128_MODE_TRUE_PEAK);
    if (state == nullptr) {
        out.status = kStatusInitFailed;
        return out;
    }

    if (ebur128_add_frames_short(state, interleaved, static_cast<size_t>(frames)) != EBUR128_SUCCESS) {
        ebur128_destroy(&state);
        out.status = kStatusAddFailed;
        return out;
    }

    double value = 0.0;
    if (ebur128_loudness_global(state, &value) == EBUR128_SUCCESS) out.integratedLufs = value;
    if (ebur128_loudness_range(state, &value) == EBUR128_SUCCESS) out.loudnessRangeLu = value;

    double peak = 0.0;
    for (int channel = 0; channel < channels; ++channel) {
        double channelPeak = 0.0;
        if (ebur128_true_peak(state, static_cast<unsigned int>(channel), &channelPeak) != EBUR128_SUCCESS) {
            peak = NAN;
            break;
        }
        if (channelPeak > peak) peak = channelPeak;
    }
    if (!std::isnan(peak)) {
        /* Linear full-scale peak -> dBTP. A silent buffer has no peak at all. */
        out.truePeakDbfs = peak > 0.0 ? 20.0 * std::log10(peak) : NAN;
    }

    ebur128_destroy(&state);

    /* -inf/NaN from the library means "no gated block passed", which is not a
     * measurement; the caller reports it as not measurable instead of showing a
     * number that would be wrong to trust. */
    if (!std::isfinite(out.integratedLufs)) out.integratedLufs = NAN;
    if (!std::isfinite(out.loudnessRangeLu)) out.loudnessRangeLu = NAN;
    return out;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_soniccore_app_loudness_LoudnessNative_measure(JNIEnv* env, jobject /* this */,
                                                       jshortArray samples, jint channels,
                                                       jint sampleRate, jint frames) {
    Measurement refused;
    refused.status = kStatusBadParameters;

    auto reply = [env](const Measurement& m) -> jdoubleArray {
        jdoubleArray result = env->NewDoubleArray(4);
        if (result == nullptr) return nullptr;
        const jdouble values[4] = { m.integratedLufs, m.loudnessRangeLu, m.truePeakDbfs,
                                    static_cast<jdouble>(m.status) };
        env->SetDoubleArrayRegion(result, 0, 4, values);
        return result;
    };

    if (samples == nullptr || channels < kMinChannels || channels > kMaxChannels ||
        sampleRate < kMinSampleRate || sampleRate > kMaxSampleRate || frames <= 0) {
        return reply(refused);
    }

    const jsize available = env->GetArrayLength(samples);
    if (available < frames * channels) return reply(refused);

    jshort* pcm = static_cast<jshort*>(env->GetPrimitiveArrayCritical(samples, nullptr));
    if (pcm == nullptr) {
        Measurement failed;
        failed.status = kStatusAddFailed;
        return reply(failed);
    }
    const Measurement measured =
        soniccore_loudness_measure(reinterpret_cast<const int16_t*>(pcm), channels, sampleRate, frames);
    env->ReleasePrimitiveArrayCritical(samples, pcm, JNI_ABORT);
    return reply(measured);
}
