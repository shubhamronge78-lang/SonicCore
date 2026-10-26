/* SonicCore offline loudness harness — host self-test (runs in CI, ASan+UBSan).
 *
 * Drives the REAL measurement core of app/src/main/cpp/loudness/loudness_jni.cpp
 * (the same single translation unit the Android build compiles, linked against
 * the vendored libebur128) so every gate the app reports is pinned here:
 *
 *   [1] the EBU R 128 calibration anchors — a 1 kHz sine at -23 dBFS RMS is
 *       -23.0 LUFS in mono and -20.0 LUFS with the same signal in both channels
 *       (the +3.01 dB the channel sum adds), and its true peak is -20.0 dBTP;
 *   [2] silence and a span shorter than one 400 ms gating block produce NO
 *       integrated loudness (NaN, never -inf or a number to trust);
 *   [3] the loudness range of a two-level signal is the ~5 LU difference, and
 *       the true peak of a full-scale sine reaches 0 dBTP through oversampling;
 *   [4] every refused input (channels, sample rate, frame count, buffer length,
 *       null data) is refused with the status the Kotlin layer maps to a reason;
 *   [5] no measurement leaks memory or reads out of bounds — that is what the
 *       sanitizers running alongside this file are for.
 *
 * The numbers are the reference implementation's own output for these signals,
 * measured once and asserted with a tolerance, so a re-vendored or modified
 * libebur128 that shifts them fails here instead of silently changing what the
 * app tells the user about their audio. */
#include "jni.h"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <algorithm>
#include <vector>

/* The real measurement core, compiled in exactly as the Android build compiles it. */
#include "app/src/main/cpp/loudness/loudness_jni.cpp"

/* The stub's host-array registry, as the DSP harness defines it. */
HostArr g_hostArrays[512];
int g_hostArrCount = 0;

static int fails = 0;

#define CHECK(cond, msg)                       \
    do {                                       \
        if (!(cond)) {                         \
            printf("  FAIL: %s\n", msg);       \
            fails++;                           \
        } else {                               \
            printf("  ok: %s\n", msg);         \
        }                                      \
    } while (0)

/* The core is file-static, so it is reached the way the JNI wrapper reaches it:
 * through the exported entry point, with the harness's stub JNIEnv. */
static Measurement measure(const std::vector<int16_t>& pcm, int channels, int sampleRate, int frames) {
    jshortArray handle = (jshortArray) &g_hostArrays[hostReg((void*) pcm.data(),
                                                             (jsize) pcm.size(), 0)];
    JNIEnv env;
    JNIEXPORT jdoubleArray result =
        Java_com_soniccore_app_loudness_LoudnessNative_measure(&env, nullptr, handle, channels,
                                                               sampleRate, frames);
    Measurement out;
    const double* values = (const double*) result;
    if (values == nullptr) {
        out.status = 99;
        return out;
    }
    out.integratedLufs = values[0];
    out.loudnessRangeLu = values[1];
    out.truePeakDbfs = values[2];
    out.status = (int) values[3];
    return out;
}

/* A sine whose RMS is `rmsDb` dBFS (peak = sqrt(2) x RMS), clamped to the
   16-bit range: a generator that wrapped at full scale would hand the
   measurement a signal with a sign-flipped peak and every number it produced
   would be about that artefact instead of the signal. */
static std::vector<int16_t> sine(int channels, int frames, double rmsDb, double freqHz = 1000.0) {
    const double peak = std::sqrt(2.0) * std::pow(10.0, rmsDb / 20.0);
    std::vector<int16_t> pcm((size_t) frames * channels);
    for (int i = 0; i < frames; i++) {
        const double t = (double) i / 48000.0;
        const long sample = llround(peak * 32767.0 * std::sin(2.0 * M_PI * freqHz * t));
        const int16_t v = (int16_t) std::max(-32767L, std::min(32767L, sample));
        for (int c = 0; c < channels; c++) pcm[(size_t) i * channels + c] = v;
    }
    return pcm;
}

/* A sine at fs/4 with a 45 degree offset: every sample lands at +-0.707 FS, so
   the sample peak is -3.01 dBFS while the true inter-sample peak is 0 dBFS.
   That difference is the only evidence that the true peak is really oversampled
   rather than a sample maximum in disguise. */
static std::vector<int16_t> quarterRateSine(int channels, int frames) {
    std::vector<int16_t> pcm((size_t) frames * channels);
    for (int i = 0; i < frames; i++) {
        const double x = std::sin(2.0 * M_PI * 0.25 * i + M_PI / 4.0);
        const int16_t v = (int16_t) llround(32767.0 * x);
        for (int c = 0; c < channels; c++) pcm[(size_t) i * channels + c] = v;
    }
    return pcm;
}

static bool near(double value, double want, double tolerance) {
    return std::isfinite(value) && std::fabs(value - want) <= tolerance;
}

int main() {
    const int SR = 48000;
    printf("[1] EBU R 128 calibration anchors\n");

    const auto mono = sine(1, SR * 5, -23.0);
    const Measurement monoResult = measure(mono, 1, SR, SR * 5);
    CHECK(monoResult.status == 0, "a normal buffer is measured (status 0)");
    CHECK(near(monoResult.integratedLufs, -22.99, 0.15),
          "mono 1 kHz at -23 dBFS RMS reads -23.0 LUFS");
    CHECK(near(monoResult.truePeakDbfs, -19.99, 0.15), "its true peak reads -20.0 dBTP");
    CHECK(std::isfinite(monoResult.loudnessRangeLu), "a steady tone still yields a range figure");

    const auto stereo = sine(2, SR * 5, -23.0);
    const Measurement stereoResult = measure(stereo, 2, SR, SR * 5);
    CHECK(near(stereoResult.integratedLufs, -19.98, 0.15),
          "the same signal in both channels reads +3.01 dB louder (channel sum)");

    printf("[2] nothing to gate: silence and a too-short span\n");
    std::vector<int16_t> silence((size_t) SR * 5 * 2, 0);
    const Measurement silenceResult = measure(silence, 2, SR, SR * 5);
    CHECK(silenceResult.status == 0, "digital silence is still a successful call");
    CHECK(std::isnan(silenceResult.integratedLufs),
          "silence reports NO integrated loudness (NaN, not -inf)");
    CHECK(std::isnan(silenceResult.truePeakDbfs), "silence reports no true peak");

    const auto shortSpan = sine(2, SR / 3, -23.0);
    const Measurement shortResult = measure(shortSpan, 2, SR, SR / 3);
    CHECK(std::isnan(shortResult.integratedLufs),
          "a span shorter than one 400 ms block reports no integrated loudness");

    const auto justMeasurable = sine(2, SR / 2, -23.0);
    const Measurement justResult = measure(justMeasurable, 2, SR, SR / 2);
    CHECK(near(justResult.integratedLufs, -19.98, 0.15), "a 0.5 s span is measurable");

    printf("[3] loudness range and true peak\n");
    std::vector<int16_t> twoLevel = sine(2, SR * 5, -30.0);
    {
        const auto loudHalf = sine(2, SR * 5, -20.0);
        for (size_t i = 0; i < twoLevel.size() / 2; i++) twoLevel[i] = loudHalf[i];
    }
    const Measurement rangeResult = measure(twoLevel, 2, SR, SR * 5);
    CHECK(near(rangeResult.loudnessRangeLu, 5.31, 0.5),
          "a signal that steps from -20 to -30 dBFS reports an LRA of about 5 LU");

    const auto fullScale = sine(2, SR * 5, -3.0103, 997.0);
    const Measurement peakResult = measure(fullScale, 2, SR, SR * 5);
    char peakMsg[160];
    snprintf(peakMsg, sizeof peakMsg,
             "a full-scale sine reaches 0 dBTP through 4x oversampling (measured %.3f dBTP)",
             peakResult.truePeakDbfs);
    CHECK(std::isfinite(peakResult.truePeakDbfs) && peakResult.truePeakDbfs > -0.5 &&
              peakResult.truePeakDbfs < 0.6,
          peakMsg);

    const auto interSample = quarterRateSine(2, SR * 3);
    long samplePeak = 0;
    for (int16_t sample : interSample) samplePeak = std::max(samplePeak, (long) std::labs(sample));
    const double samplePeakDb = 20.0 * std::log10((double) samplePeak / 32767.0);
    const Measurement interSampleResult = measure(interSample, 2, SR, SR * 3);
    char interMsg[200];
    snprintf(interMsg, sizeof interMsg,
             "an fs/4 sine whose samples peak at %.2f dBFS still reads %.2f dBTP (true peak, not a sample peak)",
             samplePeakDb, interSampleResult.truePeakDbfs);
    CHECK(samplePeakDb < -2.5 && near(interSampleResult.truePeakDbfs, 0.0, 0.3), interMsg);

    printf("[4] refused inputs\n");
    const auto small = sine(2, SR, -20.0);
    CHECK(measure(small, 0, SR, SR).status == 1, "zero channels is refused");
    CHECK(measure(small, 3, SR, SR).status == 1, "three channels is refused");
    CHECK(measure(small, 2, 4000, SR).status == 1, "a 4 kHz sample rate is refused");
    CHECK(measure(small, 2, 400000, SR).status == 1, "a 400 kHz sample rate is refused");
    CHECK(measure(small, 2, SR, 0).status == 1, "an empty frame count is refused");
    CHECK(measure(small, 2, SR, SR * 4).status == 1,
          "a frame count longer than the buffer is refused");
    CHECK(measure(small, 2, SR, -1).status == 1, "a negative frame count is refused");

    JNIEnv env;
    jshortArray none = nullptr;
    const double* nullReply = (const double*) Java_com_soniccore_app_loudness_LoudnessNative_measure(
        &env, nullptr, none, 2, SR, 100);
    CHECK(nullReply != nullptr && nullReply[3] == 1.0,
          "a null buffer is refused, never dereferenced");

    printf("[5] repeated measurement stays stable\n");
    for (int i = 0; i < 50; i++) {
        const Measurement repeated = measure(stereo, 2, SR, SR * 5);
        if (!near(repeated.integratedLufs, -19.98, 0.15)) {
            CHECK(false, "the 50th measurement reads what the first one did");
            break;
        }
        if (i == 49) CHECK(true, "50 measurements of the same buffer agree");
    }

    printf("\n%s (%d failures)\n", fails ? "LOUDNESS HARNESS FAIL" : "LOUDNESS HARNESS PASS", fails);
    return fails ? 1 : 0;
}
