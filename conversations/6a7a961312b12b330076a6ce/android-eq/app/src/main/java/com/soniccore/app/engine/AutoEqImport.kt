package com.soniccore.app.engine

import java.util.Locale

/**
 * SONICCORE — AUTOEQ IMPORT WIRING (repository integration, Phase 4)
 *
 * [AutoEqProfile] parses a profile; this is what a user actually does with one.
 * It is split in two on purpose:
 *
 *  * [review] **changes nothing at all.** It parses the text and returns either
 *    a refusal with its reason or a [Plan] the user can look at. Cancelling an
 *    import therefore cannot disturb the active sound, and a malformed profile
 *    cannot half-apply.
 *  * [apply] is the deliberate step. It stores the curve on the engine's own
 *    preset store ([Presets.CustomPreset], the same model "save current profile"
 *    writes) and, only when asked to apply, makes it the active curve and writes
 *    the profile's preamp **exactly once** through the caller's native preamp
 *    sink. There is no second equalizer and no band is applied twice.
 *
 * What an import replaces and what it merges — stated here once, and said in the
 * import screen's own words by [scope]:
 *
 *  * **replaced:** a custom profile with the same name (the engine's
 *    `saveCustomPreset` keeps one profile per name) and, on apply, the active EQ
 *    curve and the preamp value;
 *  * **left alone:** every other stored profile, and the bass / virtualizer /
 *    loudness effect strengths (the stored preset carries 0 for them, which the
 *    engine's apply path reads as "keep what the user has");
 *  * **untouched when the profile has no `Preamp:` line:** the user's current
 *    preamp — a GraphicEQ file without a preamp is not a licence to reset it.
 *
 * Failure is never silent and never partial: if storing the profile fails,
 * nothing is applied and the previous preset is still there; if activation fails,
 * the profile is reported as saved but not applied; and if the preamp write does
 * not land, the active curve is put back to the profile it had, because an EQ
 * curve running without its headroom compensation is a state the user would hear
 * and not understand. That last step is a **rollback, not a transaction** — the
 * native preamp parameter and the preset store are two separate writes and the
 * engine offers no way to commit them together, so the order is chosen so that
 * every failure leaves either the previous sound or a state the screen describes
 * in words.
 *
 * Pure Kotlin with the side effects passed in as lambdas, so the whole flow is
 * pinned on the JVM by `AutoEqImportTest` without a device.
 */
object AutoEqImport {

    /**
     * Appended when an import's name is already a built-in profile's name.
     *
     * A stored custom preset is activated *by name*, and the engine resolves a
     * built-in first — so an import left under a built-in's name would activate
     * the built-in curve while reporting the imported one. Renaming it here is
     * what keeps "apply activates the profile you reviewed" true.
     */
    const val BUILTIN_COLLISION_SUFFIX = " (AutoEQ)"

    /** What a review produced. Only [Ready] may be applied. */
    sealed interface Review

    /** Nothing usable in the box. */
    object Empty : Review

    /** Text that is not a profile — or one carrying something this app refuses. */
    data class Unsupported(val reason: String) : Review

    /** A profile ready to save or apply. Pure data: nothing has been changed yet. */
    data class Ready(val plan: Plan) : Review

    /**
     * Everything the UI needs to describe one parsed profile and to apply it.
     * Built only by [review]; holding a [Plan] never means anything was changed.
     */
    data class Plan(
        val name: String,
        val sourceFormat: String,
        val bandLevels: ShortArray,
        val preampDb: Float,
        /** True when the profile carries a `Preamp:` line, so writing it means something. */
        val appliesPreamp: Boolean,
        val filterCount: Int,
        val measurementPoints: Int,
        val curveMinDb: Short,
        val curveMaxDb: Short,
        val curveClamped: Boolean,
        val preampClamped: Boolean,
        /** Adjustments, in plain words, so nothing is clipped silently. */
        val notes: List<String>
    ) {
        val isGraphic: Boolean get() = measurementPoints > 0

        val shape: String
            get() = if (isGraphic) "$measurementPoints measurement points" else "$filterCount filters"

        val bandCount: Int get() = bandLevels.size
    }

    /** Reviews one profile. Never touches the engine, the preamp or the preset store. */
    fun review(text: String, name: String): Review =
        when (val parsed = AutoEqProfile.parse(text, name)) {
            is AutoEqProfile.Empty -> Empty
            is AutoEqProfile.Unsupported -> Unsupported(parsed.reason)
            is AutoEqProfile.Ok -> {
                val profile = parsed.profile
                val curve = AutoEqProfile.toCurve(profile)
                val preamp = AutoEqProfile.preampDb(profile)
                val preampWasClamped = AutoEqProfile.preampClamped(profile)
                val storedName = availableName(profile.name)
                val notes = buildList {
                    addAll(profile.adjustments)
                    if (storedName != profile.name) {
                        add(
                            "A built-in profile is already named “${profile.name}”, so this one is stored as " +
                                "“$storedName” — applying it activates this curve, not the built-in profile."
                        )
                    }
                    if (preampWasClamped) {
                        add(
                            "Preamp ${db(profile.preampDb)} is outside the ±30 dB preamp control; " +
                                "applied at ${db(preamp)}."
                        )
                    }
                    if (curve.clamped) {
                        add(
                            "SonicCore stores band gains within ±${AutoEqProfile.MAX_BAND_DB} dB; this profile " +
                                "asks for ${db(curve.rawMaxDb)} to ${db(curve.rawMinDb)}, so the stored curve is clamped."
                        )
                    }
                }
                Ready(
                    Plan(
                        name = storedName,
                        sourceFormat = profile.sourceFormat,
                        bandLevels = curve.levels,
                        preampDb = preamp,
                        appliesPreamp = profile.preampDeclared,
                        filterCount = profile.filters.size,
                        measurementPoints = profile.graphicCurve.size,
                        curveMinDb = curve.levels.minOrNull() ?: 0,
                        curveMaxDb = curve.levels.maxOrNull() ?: 0,
                        curveClamped = curve.clamped,
                        preampClamped = preampWasClamped,
                        notes = notes
                    )
                )
            }
        }

    /**
     * The name [name] is stored under: itself, unless a built-in profile already
     * owns it — in which case it carries [BUILTIN_COLLISION_SUFFIX] so that
     * applying the import activates the reviewed curve and not the built-in one.
     *
     * Pure: it only reads the built-in catalogue's names.
     */
    fun availableName(name: String): String {
        if (Presets.presets.none { it.name == name }) return name
        val room = (AutoEqProfile.MAX_NAME_CHARS - BUILTIN_COLLISION_SUFFIX.length).coerceAtLeast(1)
        return name.take(room).trimEnd() + BUILTIN_COLLISION_SUFFIX
    }

    /** The lines the import screen shows for [plan], in order. */
    fun reviewLines(plan: Plan): List<String> = buildList {
        add("Format: ${plan.sourceFormat} — ${plan.shape}")
        add(
            if (plan.appliesPreamp) {
                "Preamp: ${db(plan.preampDb)} — written once to the native preamp parameter when applied"
            } else {
                "Preamp: none in this profile — your current preamp is left unchanged"
            }
        )
        add("Curve: ${plan.curveMinDb} to ${plan.curveMaxDb} dB across ${plan.bandCount} bands")
        add("Stored as the custom profile “${plan.name}”")
        addAll(plan.notes)
    }

    /**
     * Exactly what the import replaces and what it leaves alone, in the screen's
     * own words. Never contradicts [apply] — that is the point of it.
     */
    fun scope(plan: Plan): String = buildString {
        append("Saving stores this curve as the custom profile “${plan.name}”; ")
        append("a custom profile with that name is replaced and every other profile is left alone. ")
        if (plan.appliesPreamp) {
            append("Applying also makes it the active curve and replaces the current preamp with ")
            append("${db(plan.preampDb)}. ")
        } else {
            append("Applying also makes it the active curve, and keeps your current preamp because ")
            append("this profile carries no Preamp line. ")
        }
        append("Bass, virtualizer and loudness keep the values you have set.")
    }

    /**
     * Runs one deliberate import.
     *
     * @param savePreset stores the curve on the engine's own preset store. Throwing
     *   means the profile was NOT stored, and then nothing else is touched either.
     * @param applyCurve makes the named profile the active curve; returning false
     *   means it could not be activated. The same function restores the previous
     *   profile when the preamp write fails.
     * @param setPreamp the app's one native preamp write, **returning whether it
     *   actually landed** — the app's preamp path swallows its own exceptions, so
     *   a write that throws and a write that lands are only distinguishable if the
     *   caller reports it.
     * @param previousCurve the profile that was active before this import, so a
     *   failed preamp write can be rolled back to it. Null when the caller cannot
     *   name it, and the report then says the rollback did not happen.
     * @return the sentence the import screen shows.
     */
    fun apply(
        review: Review,
        applyNow: Boolean,
        savePreset: (Presets.CustomPreset) -> Unit,
        applyCurve: (String) -> Boolean,
        setPreamp: (Float) -> Boolean,
        previousCurve: String? = null
    ): String {
        val plan = when (review) {
            is Empty ->
                return "Paste an AutoEQ ParametricEQ or GraphicEQ profile (or pick its file) first. Nothing was changed."
            is Unsupported -> return "Not imported: ${review.reason} Nothing was changed."
            is Ready -> review.plan
        }

        try {
            savePreset(preset(plan))
        } catch (t: Throwable) {
            return "“${plan.name}” could not be saved (${t.message ?: "storage error"}); nothing was changed " +
                "and your profiles are untouched."
        }

        if (!applyNow) {
            return "Saved “${plan.name}” as a custom profile (${plan.shape} from ${plan.sourceFormat}). " +
                "The active sound is unchanged — apply it from CUSTOM when you want it."
        }

        val activated = try {
            applyCurve(plan.name)
        } catch (_: Throwable) {
            false
        }
        if (!activated) {
            return "Saved “${plan.name}”, but it could not be made the active curve — the active sound is unchanged."
        }

        val preampWritten = try {
            applyPreamp(plan, setPreamp)
        } catch (_: Throwable) {
            false
        }

        // The curve is active but the profile's preamp never landed — the one
        // partial state this flow can reach, and the one the user would hear as
        // clipping. Put the previous profile back rather than describing it.
        if (plan.appliesPreamp && !preampWritten) {
            val reverted = previousCurve != null && previousCurve != plan.name &&
                try {
                    applyCurve(previousCurve)
                } catch (_: Throwable) {
                    false
                }
            return if (reverted) {
                "Saved “${plan.name}”, but its preamp could not be set, so the active profile was put back to " +
                    "“$previousCurve”. The active sound is unchanged."
            } else {
                "Saved “${plan.name}”, but its preamp could not be set and the active profile could not be put " +
                    "back — the curve is applied without its preamp, so set the preamp yourself or apply " +
                    "another profile."
            }
        }

        return buildString {
            append("Applied “${plan.name}” — ${plan.shape} from ${plan.sourceFormat}. ")
            append("It replaces your active EQ curve; bass, virtualizer and loudness keep their values. ")
            append(
                when {
                    preampWritten ->
                        "Preamp ${db(plan.preampDb)} was written once to the native preamp parameter. "
                    plan.appliesPreamp ->
                        "The preamp could not be set, so your previous preamp value still stands. "
                    else ->
                        "This profile has no Preamp line, so your current preamp is unchanged. "
                }
            )
            append("The curve is an approximation at SonicCore's ${plan.bandCount} band centres.")
        }
    }

    /**
     * Writes the profile's preamp through [sink] **exactly once**, and only when
     * the profile actually declares one. Nothing else in this object applies a
     * preamp, and this is called at most once per import — the native parameter is
     * never set twice for one profile.
     *
     * @param sink the one native preamp write. It returns whether the value
     *   actually reached the parameter, so a write that did not land is reported
     *   instead of assumed.
     * @return true when the profile declares a preamp **and** that write landed.
     */
    fun applyPreamp(plan: Plan, sink: (Float) -> Boolean): Boolean {
        if (!plan.appliesPreamp) return false
        return sink(plan.preampDb)
    }

    /**
     * The preamp the plan leaves behind. A profile without a `Preamp:` line keeps
     * [current]; one with a line replaces it with the profile's own clamped value.
     */
    fun preampAfter(plan: Plan, current: Float): Float =
        if (plan.appliesPreamp) plan.preampDb else current

    /** The custom preset an import stores — the engine's existing preset model. */
    fun preset(plan: Plan): Presets.CustomPreset =
        Presets.CustomPreset(plan.name, plan.bandLevels, 0, 0, 0)

    private fun db(value: Float): String = String.format(Locale.ROOT, "%+.1f dB", value)

    private fun db(value: Double): String = String.format(Locale.ROOT, "%+.1f dB", value)
}
