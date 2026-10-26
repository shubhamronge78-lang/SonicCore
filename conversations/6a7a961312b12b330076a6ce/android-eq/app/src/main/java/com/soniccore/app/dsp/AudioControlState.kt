package com.soniccore.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable snapshot of the audio parameters the Audio Center screens edit.
 *
 * The Audio screens are pure consumers: they read this value and hand a new one
 * back through `SonicCoreToolHost`, which merges it into the persisted
 * [DspParams] and applies it with the long-standing `DspParams.applyTo` path.
 * Every value is validated here first, so a slider can never push a NaN or an
 * out-of-range gain into the audio thread.
 *
 * This mirrors the fields of [DspParams] that the native chain actually exposes
 * (preamp, bass/treble shelves, stereo width/balance/swap/mono, compressor and
 * limiter). The parametric slots and the graphic bands stay with the Equalizer
 * and DSP Studio screens that already own them.
 */
data class AudioControlState(
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val width: Float = 1f,
    val balance: Float = 0f,
    val mono: Boolean = false,
    val swap: Boolean = false,
    val compOn: Boolean = false,
    val compThresh: Float = -18f,
    val limiterOn: Boolean = true,
    val limThresh: Float = -1f
) {

    /** Every numeric field inside the range the native engine accepts. */
    fun validated(): AudioControlState = copy(
        preamp = AudioControlRanges.clampTo(AudioControlRanges.PREAMP, preamp),
        bass = AudioControlRanges.clampTo(AudioControlRanges.BASS, bass),
        treble = AudioControlRanges.clampTo(AudioControlRanges.TREBLE, treble),
        width = AudioControlRanges.clampTo(AudioControlRanges.WIDTH, width),
        balance = AudioControlRanges.clampTo(AudioControlRanges.BALANCE, balance),
        compThresh = AudioControlRanges.clampTo(AudioControlRanges.COMP_THRESHOLD, compThresh),
        limThresh = AudioControlRanges.clampTo(AudioControlRanges.LIMITER_THRESHOLD, limThresh)
    )

    /** True while the tonal controls sit at their neutral values. */
    val isToneNeutral: Boolean get() = preamp == 0f && bass == 0f && treble == 0f

    /** True while the stereo controls sit at their neutral values. */
    val isSpatialNeutral: Boolean
        get() = width == 1f && balance == 0f && !mono && !swap

    companion object {
        /** Reads the persisted parameter set. Never throws. */
        fun of(params: DspParams): AudioControlState = AudioControlState(
            preamp = params.preamp,
            bass = params.bass,
            treble = params.treble,
            width = params.width,
            balance = params.balance,
            mono = params.mono,
            swap = params.swap,
            compOn = params.compOn,
            compThresh = params.compThresh,
            limiterOn = params.limiterOn,
            limThresh = params.limThresh
        ).validated()
    }
}

/**
 * Writes the validated values of this snapshot into [params].
 *
 * The remaining [DspParams] fields (parametric slots) are left exactly as they
 * were, so an edit on one Audio screen can never clear another screen's state.
 */
fun AudioControlState.mergeInto(params: DspParams) {
    val v = validated()
    params.preamp = v.preamp
    params.bass = v.bass
    params.treble = v.treble
    params.width = v.width
    params.balance = v.balance
    params.mono = v.mono
    params.swap = v.swap
    params.compOn = v.compOn
    params.compThresh = v.compThresh
    params.limiterOn = v.limiterOn
    params.limThresh = v.limThresh
}

/**
 * Parameter ranges published by the native engine. Shared by every Audio screen
 * so a slider's endpoints are always the engine's own limits, never a guess.
 */
object AudioControlRanges {
    val PREAMP = -30f..30f
    val BASS = -30f..30f
    val TREBLE = -30f..30f
    val WIDTH = 0f..4f
    val BALANCE = -1f..1f
    val COMP_THRESHOLD = -60f..0f
    val LIMITER_THRESHOLD = -60f..0f

    /**
     * Clamps [value] into [range] without ever forwarding a non-finite value:
     * NaN collapses to the range's low bound, infinity to the bound it names.
     * Nothing a UI control can produce reaches the DSP unchecked.
     */
    fun clampTo(range: ClosedFloatingPointRange<Float>, value: Float): Float = when {
        value.isNaN() -> range.start
        value == Float.POSITIVE_INFINITY -> range.endInclusive
        value == Float.NEGATIVE_INFINITY -> range.start
        else -> value.coerceIn(range.start, range.endInclusive)
    }
}

/**
 * Reference sample rate the modelled curve is evaluated at.
 *
 * The native engine runs at the capture rate and defaults to 48 kHz
 * (`neondsp.cpp`: `sr = 48000.0` until `init` sees another rate), so the UI
 * states 48 kHz rather than inventing a device-specific number it cannot read.
 */
const val SHELF_MODEL_SAMPLE_RATE = 48000f

/* ── The one source of truth for the shelf corners ───────────────────────────
 *
 * `neondsp.cpp` builds the low shelf at 100 Hz (`setShelf(&bassSh[..], 100.0, …)`
 * and `setShelfT(&bassSh[..], 100.0, …)`) and the high shelf at 8000 Hz, and
 * `tools/dspharness/harness.cpp` section [14] measures exactly those corners
 * (±20 dB shelf -> ±10 dB at the corner). [LOW_SHELF_HZ] / [HIGH_SHELF_HZ] are
 * the UI's copy of that fact and must stay equal to the native constants; the
 * regression test `AudioControlStateTest.theShelfCornersMatchTheNativeEngine`
 * pins them, and the modelled curve below is built from the *same* RBJ formulas
 * so the label, the curve and the DSP are three views of one value. */

/** Fixed corner of the native low shelf — 100 Hz in `neondsp.cpp`. */
const val LOW_SHELF_HZ = 100f

/** Fixed corner of the native high shelf — 8000 Hz in `neondsp.cpp`. */
const val HIGH_SHELF_HZ = 8000f

/**
 * Modelled shelf response in dB at [freqHz] for the current bass/treble gains.
 *
 * The native chain applies one fixed low shelf and one fixed high shelf, so the
 * combined shape of the two is fully determined by the two gains. This drives
 * the response curve on the Bass & Treble screen: it is derived from the
 * parameters the DSP was actually given, and is a rendering aid — never a
 * measurement of processed audio.
 *
 * Unlike the one-pole sketch this replaced, it evaluates the **real** RBJ shelf
 * the engine builds — the same coefficients, the same `alpha = sin(w0) / 1.4`
 * slope — as a magnitude response, so the curve no longer disagrees with the DSP
 * it describes (the corners used to be stated an octave away from 100 Hz/8 kHz).
 */
fun shelfResponseDb(state: AudioControlState, freqHz: Float): Float {
    if (!freqHz.isFinite() || freqHz <= 0f) return 0f
    val v = state.validated()
    return rbjShelfDb(freqHz, LOW_SHELF_HZ, v.bass, high = false) +
        rbjShelfDb(freqHz, HIGH_SHELF_HZ, v.treble, high = true)
}

/**
 * |H(e^jω)| in dB of one RBJ shelf at [SHELF_MODEL_SAMPLE_RATE].
 *
 * The coefficients mirror `neondsp.cpp`'s `setShelf` / `setShelfT` exactly,
 * including the `sin(w0) / 1.4` slope, so the modelled curve is the engine's own
 * response rather than a lookalike. A zero gain is unity by construction and
 * short-circuits to 0 dB.
 */
private fun rbjShelfDb(freqHz: Float, cornerHz: Float, gainDb: Float, high: Boolean): Float {
    if (gainDb == 0f) return 0f
    val fs = SHELF_MODEL_SAMPLE_RATE
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * cornerHz / fs
    val cw = cos(w0)
    val sw = sin(w0)
    val alpha = sw / 1.4
    val sqA = sqrt(a)

    val b0: Double
    val b1: Double
    val b2: Double
    val a0: Double
    val a1: Double
    val a2: Double
    if (!high) {
        b0 = a * ((a + 1) - (a - 1) * cw + 2 * sqA * alpha)
        b1 = 2 * a * ((a - 1) - (a + 1) * cw)
        b2 = a * ((a + 1) - (a - 1) * cw - 2 * sqA * alpha)
        a0 = (a + 1) + (a - 1) * cw + 2 * sqA * alpha
        a1 = -2 * ((a - 1) + (a + 1) * cw)
        a2 = (a + 1) + (a - 1) * cw - 2 * sqA * alpha
    } else {
        b0 = a * ((a + 1) + (a - 1) * cw + 2 * sqA * alpha)
        b1 = -2 * a * ((a - 1) + (a + 1) * cw)
        b2 = a * ((a + 1) + (a - 1) * cw - 2 * sqA * alpha)
        a0 = (a + 1) - (a - 1) * cw + 2 * sqA * alpha
        a1 = 2 * ((a - 1) - (a + 1) * cw)
        a2 = (a + 1) - (a - 1) * cw - 2 * sqA * alpha
    }

    val w = 2.0 * PI * freqHz / fs
    val nRe = b0 + b1 * cos(w) + b2 * cos(2.0 * w)
    val nIm = -(b1 * sin(w) + b2 * sin(2.0 * w))
    val dRe = a0 + a1 * cos(w) + a2 * cos(2.0 * w)
    val dIm = -(a1 * sin(w) + a2 * sin(2.0 * w))
    val num = nRe * nRe + nIm * nIm
    val den = dRe * dRe + dIm * dIm
    if (den <= 0.0 || num <= 0.0) return 0f
    return (10.0 * log10(num / den)).toFloat()
}
