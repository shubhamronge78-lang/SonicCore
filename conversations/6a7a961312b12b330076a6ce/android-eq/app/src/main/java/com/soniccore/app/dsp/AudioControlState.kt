package com.soniccore.app.dsp

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

/** Fixed corner of the native low shelf. */
const val LOW_SHELF_HZ = 200f

/** Fixed corner of the native high shelf. */
const val HIGH_SHELF_HZ = 4000f

/**
 * Modelled shelf response in dB at [freqHz] for the current bass/treble gains.
 *
 * The native chain applies one fixed low shelf and one fixed high shelf, so the
 * combined shape of the two is fully determined by the two gains. This drives
 * the response curve on the Bass & Treble screen: it is derived from the
 * parameters the DSP was actually given, and is a rendering aid — never a
 * measurement of processed audio.
 */
fun shelfResponseDb(state: AudioControlState, freqHz: Float): Float {
    if (!freqHz.isFinite() || freqHz <= 0f) return 0f
    val v = state.validated()
    val f = freqHz
    val low = f / LOW_SHELF_HZ
    val high = f / HIGH_SHELF_HZ
    val lowShare = 1f / (1f + low * low)
    val highShare = (high * high) / (1f + high * high)
    return v.bass * lowShare + v.treble * highShare
}
