package com.soniccore.app.engine

import java.util.Locale
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
 *  * AutoEQ's own measurements are *not* bundled — the user supplies the profile
 *    text (or picks the `.txt` file), so no measurement data is redistributed;
 *  * the profile's filters are evaluated at SonicCore's 31 band centres and then
 *    mapped onto the device's real bands by the engine. That is an
 *    **approximation** of the original parametric response, and it is stated as
 *    one wherever the import is described.
 *
 * This file only parses and evaluates; it changes nothing. [AutoEqImport] is what
 * decides to store or apply a parsed profile, so a refusal or a cancellation here
 * can never disturb the active sound.
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

    /** Longest stored profile name. Every name this object produces fits inside it. */
    const val MAX_NAME_CHARS = 64

    /** Caps on what one profile may contain. */
    const val MAX_FILTERS = 64
    const val MAX_GRAPHIC_POINTS = 8192

    /**
     * Ceiling for a computed band level, in dB.
     *
     * The engine's own ceiling for a stored curve is +15 dB (see `Presets`), and
     * a clamp keeps an extreme AutoEQ result inside the range the existing
     * equalizer was validated against instead of asking it for gain it cannot do.
     * A clamp is never silent: [Curve.clamped] reports it and the import review
     * says so in words.
     */
    const val MAX_BAND_DB = 15
    const val MIN_BAND_DB = -15

    /** Range one filter's own gain and Q are normalised into before evaluation. */
    const val MAX_FILTER_GAIN_DB = 40.0
    const val MIN_Q = 0.1
    const val MAX_Q = 10.0

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
     *
     * [preampDeclared] distinguishes "the profile says `Preamp: 0.0 dB`" (apply
     * zero, the profile's own instruction) from "the profile has no `Preamp:`
     * line at all" (leave the user's current preamp alone).
     */
    data class Profile(
        val name: String,
        val preampDb: Double,
        val sourceFormat: String,
        val filters: List<Filter> = emptyList(),
        val graphicCurve: List<Pair<Double, Double>> = emptyList(),
        val preampDeclared: Boolean = false,
        /** Values normalised into a range SonicCore can store, in plain words. */
        val adjustments: List<String> = emptyList()
    )

    /**
     * Parse outcome. [Empty] and [Unsupported] are reported, never guessed at —
     * and [Unsupported] carries the reason, so the import screen can say which
     * filter or value it refused instead of a generic "bad profile".
     */
    sealed interface Result

    /** Nothing to parse. */
    object Empty : Result

    /** Text that is not an AutoEQ profile — or one this app cannot honour. */
    data class Unsupported(val reason: String) : Result

    data class Ok(val profile: Profile) : Result

    /**
     * The profile evaluated at SonicCore's 31 band centres, together with the
     * unclamped extremes it needed — so a stored clamp can be reported instead of
     * happening silently.
     */
    data class Curve(
        val levels: ShortArray,
        val rawMinDb: Double,
        val rawMaxDb: Double
    ) {
        /** True when the requested correction did not fit SonicCore's ±15 dB store. */
        val clamped: Boolean get() = rawMaxDb > MAX_BAND_DB || rawMinDb < MIN_BAND_DB
    }

    private val FILTER_RE = Regex(
        """Filter\s+(\d+)\s*:\s*(ON|OFF)\s+([A-Za-z]+)\s+Fc\s+(-?[\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+(-?[\d.]+)""",
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
        if (body.length > MAX_TEXT_CHARS) {
            return Unsupported(
                "That text is ${body.length} characters; SonicCore reads profiles up to " +
                    "${MAX_TEXT_CHARS / 1024} KB."
            )
        }

        val storedName = sanitizeName(name)
        val nameText = name.trim()

        val graphic = scanGraphic(body)
        when (graphic) {
            is GraphicScan.Refused -> return Unsupported(graphic.reason)
            is GraphicScan.Ready -> {
                if (graphic.points.size < 2) {
                    return Unsupported(
                        "A GraphicEQ profile needs at least two measurement points; this one has " +
                            "${graphic.points.size}."
                    )
                }
                return Ok(
                    Profile(
                        name = storedName,
                        preampDb = parsePreamp(body),
                        sourceFormat = "GraphicEQ",
                        graphicCurve = graphic.points,
                        preampDeclared = PREAMP_RE.containsMatchIn(body),
                        adjustments = nameNote(nameText, storedName) + graphic.adjustments
                    )
                )
            }
            GraphicScan.Absent -> Unit
        }

        return when (val scan = scanFilters(body)) {
            FilterScan.None -> Unsupported(
                "That is not an AutoEQ ParametricEQ or GraphicEQ profile."
            )
            is FilterScan.Refused -> Unsupported(scan.reason)
            is FilterScan.Ready -> Ok(
                Profile(
                    name = storedName,
                    preampDb = parsePreamp(body),
                    sourceFormat = "ParametricEQ",
                    filters = scan.filters,
                    preampDeclared = PREAMP_RE.containsMatchIn(body),
                    adjustments = nameNote(nameText, storedName) + scan.adjustments
                )
            )
        }
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

    /** True when the profile's own preamp had to be clamped to the control range. */
    fun preampClamped(profile: Profile): Boolean =
        profile.preampDeclared && profile.preampDb != preampDb(profile).toDouble()

    /**
     * The profile evaluated at SonicCore's 31 band centres, low → high, in dB,
     * plus the unclamped extremes so the caller can report a clamp.
     *
     * Parametric filters are summed as bell curves in log-frequency space (a
     * peaking filter contributes its gain at its own centre and falls away by its
     * Q; a shelf contributes its gain across its side of the corner), and a
     * graphic curve is interpolated in log-frequency.
     */
    fun toCurve(profile: Profile): Curve {
        val levels = ShortArray(BAND_FREQUENCIES.size)
        var rawMin = Double.MAX_VALUE
        var rawMax = -Double.MAX_VALUE
        for (i in BAND_FREQUENCIES.indices) {
            val freq = BAND_FREQUENCIES[i]
            val db = if (profile.graphicCurve.isNotEmpty()) {
                graphicDbAt(profile.graphicCurve, freq)
            } else {
                profile.filters.sumOf { filterDb(it, freq) }
            }
            val finite = if (db.isFinite()) db else if (db > 0) Double.MAX_VALUE else -Double.MAX_VALUE
            rawMin = minOf(rawMin, finite)
            rawMax = maxOf(rawMax, finite)
            levels[i] = finite.roundToInt().coerceIn(MIN_BAND_DB, MAX_BAND_DB).toShort()
        }
        if (rawMin > rawMax) { // no bands at all — nothing to report
            rawMin = 0.0
            rawMax = 0.0
        }
        return Curve(levels, rawMin, rawMax)
    }

    /** The profile evaluated at SonicCore's 31 band centres, low → high, in dB. */
    fun toBandLevels(profile: Profile): ShortArray = toCurve(profile).levels

    // ── parsing ──

    private fun parsePreamp(body: String): Double =
        PREAMP_RE.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** Notes that the stored name is not the one that was typed, when it differs. */
    private fun nameNote(supplied: String, stored: String): List<String> =
        if (supplied == stored) emptyList() else listOf("Name cleaned up to “$stored”.")

    private sealed interface FilterScan {
        /** No `Filter n:` line at all. */
        object None : FilterScan
        data class Ready(val filters: List<Filter>, val adjustments: List<String>) : FilterScan
        data class Refused(val reason: String) : FilterScan
    }

    private fun scanFilters(body: String): FilterScan {
        if (!FILTER_RE.containsMatchIn(body)) return FilterScan.None
        val filters = mutableListOf<Filter>()
        val adjustments = mutableListOf<String>()
        for (match in FILTER_RE.findAll(body)) {
            val index = match.groupValues[1]
            val enabled = match.groupValues[2].equals("ON", ignoreCase = true)
            // A disabled slot is AutoEQ saying "this band is not used" — skipping
            // it is what the profile means, not a loss. An OFF slot is also
            // skipped before its type is read, so a type this app cannot
            // represent is only refused when the profile actually uses it.
            if (!enabled) continue
            val rawType = match.groupValues[3]
            val type = when (rawType.uppercase()) {
                "PK" -> FilterType.PEAKING
                "LS", "LSC", "LOW_SHELF" -> FilterType.LOW_SHELF
                "HS", "HSC", "HIGH_SHELF" -> FilterType.HIGH_SHELF
                // Low/high-pass and any other type cannot be represented as a band
                // gain, so the profile is refused — never silently dropped or
                // truncated to something it is not.
                else -> return FilterScan.Refused(
                    "Filter $index uses “$rawType”, a filter type SonicCore cannot represent as a band, " +
                        "so the profile was refused rather than approximated."
                )
            }
            val freqText = match.groupValues[4]
            val freq = freqText.toDoubleOrNull()
            val gain = match.groupValues[5].toDoubleOrNull()
            val q = match.groupValues[6].toDoubleOrNull()
            if (freq == null || gain == null || q == null || !gain.isFinite() || !q.isFinite()) {
                return FilterScan.Refused("Filter $index carries a value SonicCore cannot read.")
            }
            if (!freq.isFinite() || freq <= 0.0) {
                return FilterScan.Refused(
                    "Filter $index has an unusable centre frequency ($freqText Hz)."
                )
            }
            var storedGain = gain
            if (gain > MAX_FILTER_GAIN_DB || gain < -MAX_FILTER_GAIN_DB) {
                storedGain = gain.coerceIn(-MAX_FILTER_GAIN_DB, MAX_FILTER_GAIN_DB)
                adjustments += "Filter $index gain ${dbText(gain)} dB is outside ±${MAX_FILTER_GAIN_DB.toInt()} dB; " +
                    "evaluated at ${dbText(storedGain)} dB."
            }
            var storedQ = q
            if (q < MIN_Q || q > MAX_Q) {
                storedQ = q.coerceIn(MIN_Q, MAX_Q)
                adjustments += "Filter $index Q $q is outside $MIN_Q–${MAX_Q.toInt()}; evaluated at $storedQ."
            }
            filters += Filter(type, freq, storedGain, storedQ)
            if (filters.size > MAX_FILTERS) {
                return FilterScan.Refused(
                    "That profile declares more than $MAX_FILTERS filters, which is more than SonicCore reads."
                )
            }
        }
        if (filters.isEmpty()) {
            return FilterScan.Refused(
                "Every filter in that profile is switched off, so it carries no correction to apply."
            )
        }
        return FilterScan.Ready(filters, adjustments)
    }

    private sealed interface GraphicScan {
        object Absent : GraphicScan
        data class Ready(val points: List<Pair<Double, Double>>, val adjustments: List<String>) : GraphicScan
        data class Refused(val reason: String) : GraphicScan
    }

    private fun scanGraphic(body: String): GraphicScan {
        val match = GRAPHIC_RE.find(body) ?: return GraphicScan.Absent
        val points = mutableListOf<Pair<Double, Double>>()
        var outOfRange = 0
        for (token in match.groupValues[1].split(';', '\n', ',')) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 2) {
                return GraphicScan.Refused(
                    "The GraphicEQ line carries “$trimmed”, which is not a “frequency gain” pair."
                )
            }
            val freq = parts[0].toDoubleOrNull()
            val gain = parts[1].toDoubleOrNull()
            if (freq == null || gain == null || !freq.isFinite() || !gain.isFinite()) {
                return GraphicScan.Refused(
                    "The GraphicEQ line carries “$trimmed”, which SonicCore cannot read as a number."
                )
            }
            if (freq <= 0.0) {
                return GraphicScan.Refused("The GraphicEQ line has an unusable frequency ($trimmed Hz).")
            }
            var storedGain = gain
            if (gain > MAX_FILTER_GAIN_DB || gain < -MAX_FILTER_GAIN_DB) {
                storedGain = gain.coerceIn(-MAX_FILTER_GAIN_DB, MAX_FILTER_GAIN_DB)
                outOfRange++
            }
            points += freq to storedGain
            if (points.size > MAX_GRAPHIC_POINTS) {
                return GraphicScan.Refused(
                    "That GraphicEQ line carries more than $MAX_GRAPHIC_POINTS measurement points, " +
                        "which is more than SonicCore reads."
                )
            }
        }
        val adjustments = if (outOfRange > 0) {
            listOf(
                "$outOfRange measurement point(s) lay outside ±${MAX_FILTER_GAIN_DB.toInt()} dB " +
                    "and were evaluated at the limit."
            )
        } else {
            emptyList()
        }
        return GraphicScan.Ready(points.sortedBy { it.first }, adjustments)
    }

    /**
     * Turns a supplied name into something safe to store.
     *
     * The name is untrusted input: it comes from whatever the user pasted, or
     * from the file name their picker handed over. It ends up in the app's
     * persisted preset store and is what a later import matches on, so a control
     * character, a quote, a path separator or a `..` is removed here rather than
     * left for whatever consumes the string next. The result is
     * whitespace-collapsed, length-capped and never blank.
     */
    private fun sanitizeName(name: String): String =
        buildString(name.length) {
            for (ch in name) {
                val unsafe = ch.isISOControl() || ch == '"' || ch == '\'' ||
                    ch == '/' || ch == '\\' || ch == ':'
                append(if (unsafe) ' ' else ch)
            }
        }
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.', ' ')
            .trim()
            .take(MAX_NAME_CHARS)
            .ifBlank { DEFAULT_NAME }

    private fun dbText(value: Double): String =
        String.format(Locale.ROOT, "%+.1f", value)

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
        val bandwidthOctaves = (2.0 / LN2) * asinh(1.0 / (2.0 * q.coerceIn(MIN_Q, MAX_Q)))
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
