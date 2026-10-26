package com.soniccore.app.engine

import kotlin.math.asinh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * SONICCORE — AUTOEQ PROFILE IMPORT (repository integration, Phase 4)
 *
 * Reads the two text formats the AutoEQ project publishes for a headphone model
 * and turns them into the curve SonicCore's existing equalizer already applies:
 *
 *  * `ParametricEQ.txt` — a `Preamp:` line plus `Filter n: ON <type> Fc .. Hz Gain .. dB Q ..`;
 *  * `GraphicEQ.txt` — one `GraphicEQ:` line of `frequency gain;` measurement points.
 *
 * Where the result goes: a **custom preset** (the engine's own 31-slot curve,
 * the same model `Presets.CustomPreset` uses) plus the profile's preamp, applied
 * through the app's existing native preamp parameter ([com.soniccore.app.dsp.DspParams.preamp]).
 * Nothing here touches the DSP chain, the capture path or the JNI bridge — no
 * second equalizer is created and no band is applied twice.
 *
 * Integration boundary, stated plainly:
 *  * the correction is applied by the **existing** engine, to whatever route the
 *    user has already selected (SonicCore's own player, captured playback, or a
 *    supported system route);
 *  * AutoEQ's own measurements are *not* bundled — the user pastes the profile
 *    text (or a future file picker does), so no measurement data is redistributed;
 *  * the profile's filters are evaluated at SonicCore's 31 band centres and then
 *    mapped onto the device's real bands by the engine. That is an
 *    **approximation** of the original parametric response, and it is stated as
 *    one wherever the import is described.
 *
 * Licensing: AutoEQ (`jaakkopasanen/AutoEq`) is MIT. Only the *file format* is
 * read here; no AutoEQ source or data is copied into this repository.
 *
 * Pure Kotlin, no Android imports, so `AutoEqProfileTest` pins every rule on the
 * JVM.
 */
object AutoEqProfile {

    /** Name used when the caller supplies none. */
    const val DEFAULT_NAME = "AutoEQ profile"

    /** Largest profile text accepted, so a pathological paste cannot be parsed. */
    const val MAX_TEXT_CHARS = 128 * 1024

    /** Caps on what one profile may contain. */
    private const val MAX_FILTERS = 64
    private const val MAX_GRAPHIC_POINTS = 8192

    /**
     * Ceiling for a computed band level, in dB.
     *
     * The engine's own ceiling for a stored curve is +15 dB (see `Presets`), and
     * a clamp keeps an extreme AutoEQ result inside the range the existing
     * equalizer was validated against instead of asking it for gain it cannot do.
     */
    const val MAX_BAND_DB = 15
    const val MIN_BAND_DB = -15

    /**
     * SonicCore's curve resolution: the ISO 1/3-octave centres from 20 Hz to
     * 20 kHz. This is the same low→high convention `Presets` uses, and the
     * engine samples it onto whatever bands the device actually exposes.
     */
    val BAND_FREQUENCIES: DoubleArray = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0,
        250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0,
        2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )

    enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF }

    /** One AutoEQ parametric filter, already normalised. */
    data class Filter(
        val type: FilterType,
        val freqHz: Double,
        val gainDb: Double,
        val q: Double
    )

    /**
     * A parsed profile. Either [filters] (parametric) or [graphicCurve]
     * (measurement points) carries the correction — never both.
     */
    data class Profile(
        val name: String,
        val preampDb: Double,
        val sourceFormat: String,
        val filters: List<Filter> = emptyList(),
        val graphicCurve: List<Pair<Double, Double>> = emptyList()
    )

    /** Parse outcome. [Empty] and [Unsupported] are reported, never guessed at. */
    sealed interface Result

    /** Nothing to parse. */
    object Empty : Result

    /** Text that is not an AutoEQ profile — or one using a filter this app cannot honour. */
    object Unsupported : Result

    data class Ok(val profile: Profile) : Result

    private val FILTER_RE = Regex(
        """Filter\s+\d+\s*:\s*(ON|OFF)\s+([A-Za-z]+)\s+Fc\s+(-?[\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+(-?[\d.]+)""",
        RegexOption.IGNORE_CASE
    )
    private val PREAMP_RE = Regex("""Preamp\s*:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
    private val GRAPHIC_RE = Regex("""GraphicEQ\s*:\s*(.+)""", RegexOption.IGNORE_CASE)

    /** True when [text] carries AutoEQ content, so a caller can pick the right import path. */
    fun looksLikeAutoEq(text: String): Boolean =
        text.length <= MAX_TEXT_CHARS &&
            (GRAPHIC_RE.containsMatchIn(text) || FILTER_RE.containsMatchIn(text))

    /** True when the parsed profile carries a graphic (measurement-point) curve. */
    fun isGraphic(profile: Profile): Boolean = profile.graphicCurve.isNotEmpty()

    /**
     * Parses an AutoEQ profile.
     *
     * @param name the name the profile is stored under (AutoEQ's text carries no
     *   model name, so the caller supplies one — typically the headphone model).
     */
    fun parse(text: String, name: String = DEFAULT_NAME): Result {
        val body = text.trim()
        if (body.isEmpty()) return Empty
        if (body.length > MAX_TEXT_CHARS) return Unsupported

        val storedName = sanitizeName(name)

        val graphic = parseGraphic(body)
        if (graphic != null) {
            if (graphic.size < 2) return Unsupported
            return Ok(
                Profile(
                    name = storedName,
                    preampDb = parsePreamp(body),
                    sourceFormat = "GraphicEQ",
                    graphicCurve = graphic
                )
            )
        }

        val filters = parseParametric(body) ?: return Unsupported
        if (filters.isEmpty()) return Unsupported
        return Ok(
            Profile(
                name = storedName,
                preampDb = parsePreamp(body),
                sourceFormat = "ParametricEQ",
                filters = filters
            )
        )
    }

    /**
     * The profile's preamp in dB, clamped to the range the app's preamp control
     * accepts (`AudioControlRanges.PREAMP`, ±30 dB). AutoEQ publishes this value
     * so the corrected curve does not clip; SonicCore applies it through the
     * native preamp parameter, on top of the engine's own per-curve
     * compensation, exactly as the profile asks.
     */
    fun preampDb(profile: Profile): Float =
        profile.preampDb.coerceIn(-30.0, 30.0).toFloat()

    /**
     * The profile evaluated at SonicCore's 31 band centres, low → high, in dB.
     *
     * Parametric filters are summed as bell curves in log-frequency space (a
     * peaking filter contributes its gain at its own centre and falls away by its
     * Q; a shelf contributes its gain across its side of the corner), and a
     * graphic curve is interpolated in log-frequency. The result is clamped to
     * ±[MAX_BAND_DB] before it is stored.
     */
    fun toBandLevels(profile: Profile): ShortArray {
        val levels = ShortArray(BAND_FREQUENCIES.size)
        for (i in BAND_FREQUENCIES.indices) {
            val freq = BAND_FREQUENCIES[i]
            val db = if (profile.graphicCurve.isNotEmpty()) {
                graphicDbAt(profile.graphicCurve, freq)
            } else {
                profile.filters.sumOf { filterDb(it, freq) }
            }
            levels[i] = db.roundToInt().coerceIn(MIN_BAND_DB, MAX_BAND_DB).toShort()
        }
        return levels
    }

    // ── parsing ──

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[\"\\n\\r\\t]"), " ").trim().take(64).ifBlank { DEFAULT_NAME }

    private fun parsePreamp(body: String): Double =
        PREAMP_RE.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private fun parseParametric(body: String): List<Filter>? {
        if (!FILTER_RE.containsMatchIn(body)) return null
        val filters = mutableListOf<Filter>()
        for (match in FILTER_RE.findAll(body)) {
            val enabled = match.groupValues[1].equals("ON", ignoreCase = true)
            // A disabled slot is AutoEQ saying "this band is not used" — skipping
            // it is what the profile means, not a loss.
            if (!enabled) continue
            val type = when (match.groupValues[2].uppercase()) {
                "PK" -> FilterType.PEAKING
                "LS", "LSC", "LOW_SHELF" -> FilterType.LOW_SHELF
                "HS", "HSC", "HIGH_SHELF" -> FilterType.HIGH_SHELF
                // Low/high-pass and any other type cannot be represented as a band
                // gain, so the profile is refused rather than silently mangled.
                else -> return null
            }
            val freq = match.groupValues[3].toDoubleOrNull() ?: return null
            val gain = match.groupValues[4].toDoubleOrNull() ?: return null
            val q = match.groupValues[5].toDoubleOrNull() ?: return null
            if (freq <= 0.0 || !freq.isFinite() || !gain.isFinite()) return null
            filters += Filter(type, freq, gain.coerceIn(-40.0, 40.0), q.coerceIn(0.1, 10.0))
            if (filters.size > MAX_FILTERS) return null
        }
        return filters
    }

    private fun parseGraphic(body: String): List<Pair<Double, Double>>? {
        val match = GRAPHIC_RE.find(body) ?: return null
        val raw = match.groupValues[1]
        val points = mutableListOf<Pair<Double, Double>>()
        for (token in raw.split(';', '\n', ',')) {
            val parts = token.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val freq = parts[0].toDoubleOrNull() ?: continue
            val gain = parts[1].toDoubleOrNull() ?: continue
            if (freq <= 0.0 || !freq.isFinite() || !gain.isFinite()) continue
            points += freq to gain
            if (points.size > MAX_GRAPHIC_POINTS) return null
        }
        return points.sortedBy { it.first }
    }

    // ── response evaluation ──

    /**
     * One filter's contribution at [freq]. An approximation of the exact biquad
     * magnitude, deliberately: it needs no sample rate, stays finite for any Q,
     * and is documented as an approximation wherever the import is described.
     *
     *  * a peaking filter is a Gaussian in log-frequency centred on its own
     *    frequency, so it returns to zero on **both** sides;
     *  * a shelf is a smooth transition in log-frequency, so it holds its gain on
     *    its own side of the corner and reaches zero on the other — the shape a
     *    low shelf actually has.
     */
    private fun filterDb(filter: Filter, freq: Double): Double {
        if (filter.freqHz <= 0.0 || freq <= 0.0) return 0.0
        val octaves = log2(freq / filter.freqHz)
        val sigma = sigmaOctaves(filter.q)
        return when (filter.type) {
            FilterType.PEAKING -> filter.gainDb * bellAt(octaves, sigma)
            FilterType.LOW_SHELF -> filter.gainDb * (1.0 - transitionAt(octaves, sigma))
            FilterType.HIGH_SHELF -> filter.gainDb * transitionAt(octaves, sigma)
        }
    }

    /** 1.0 at the filter's centre, falling off with distance in octaves. */
    private fun bellAt(octaves: Double, sigma: Double): Double {
        val x = octaves / sigma
        return exp(-0.5 * x * x)
    }

    /**
     * 0 well below the corner, 1 well above it, 0.5 at the corner itself — the
     * shape of a shelf, with the Q-derived slope in between.
     */
    private fun transitionAt(octaves: Double, sigma: Double): Double {
        val x = octaves / sigma
        return when {
            x > 40.0 -> 1.0
            x < -40.0 -> 0.0
            else -> 1.0 / (1.0 + exp(-x))
        }
    }

    /**
     * The filter's width in octaves, derived from Q: the -3 dB bandwidth of a
     * peaking biquad is `(2 / ln2) * asinh(1 / 2Q)`, and σ is that bandwidth
     * divided by the Gaussian's full-width constant `2 * sqrt(2 ln 2)`.
     */
    private fun sigmaOctaves(q: Double): Double {
        val bandwidthOctaves = (2.0 / LN2) * asinh(1.0 / (2.0 * q.coerceIn(0.1, 10.0)))
        return (bandwidthOctaves / 2.3548).coerceAtLeast(0.05)
    }

    /** Linear interpolation of the measurement points in log-frequency. */
    private fun graphicDbAt(points: List<Pair<Double, Double>>, freq: Double): Double {
        if (points.isEmpty()) return 0.0
        if (freq <= points.first().first) return points.first().second
        if (freq >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val f0 = points[i].first
            val f1 = points[i + 1].first
            if (freq in f0..f1) {
                val span = log2(f1 / f0)
                if (span <= 0.0) return points[i + 1].second
                val t = log2(freq / f0) / span
                return points[i].second + (points[i + 1].second - points[i].second) * t
            }
        }
        return 0.0
    }

    private val LN2 = ln(2.0)
}
