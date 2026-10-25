package com.neon.eq.ui.design

/**
 * Persisted accent swatch behind the "Custom Accent" theme.
 *
 * This is presentation only: it drives the palette of one theme and never
 * touches the DSP, capture path, buffers or sample rate. Parsing and
 * serialisation are pure functions so the JVM tests can exercise them without
 * an Android framework.
 */
object ThemeAccentPreference {
    /** SharedPreferences key inside the existing `ui_prefs` file. */
    const val KEY = "custom_accent"

    /** Default swatch — SonicCore cyan, so the theme looks sane before a pick. */
    val DEFAULT: Int = 0xFF00D9FF.toInt()

    /** Selectable accents offered by the Customization Center. */
    val SWATCHES: List<Pair<String, Int>> = listOf(
        "Cyan" to 0xFF00D9FF.toInt(),
        "Violet" to 0xFF8B5CF6.toInt(),
        "Magenta" to 0xFFFF4FD8.toInt(),
        "Amber" to 0xFFFFB347.toInt(),
        "Emerald" to 0xFF2BE8A5.toInt(),
        "Ice" to 0xFF9FD8FF.toInt()
    )

    /** `#AARRGGBB` for storage; never locale-dependent. */
    fun serialize(argb: Int): String = "#%08X".format(argb)

    /** Accepts `#AARRGGBB`, `AARRGGBB` or a bare `#RRGGBB`; invalid input -> DEFAULT. */
    fun parse(raw: String?): Int {
        if (raw.isNullOrBlank()) return DEFAULT
        val hex = raw.trim().removePrefix("#")
        if (hex.isEmpty() || hex.length > 8 || !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return DEFAULT
        }
        val value = hex.toLongOrNull(16) ?: return DEFAULT
        return when (hex.length) {
            8 -> value.toInt()
            6 -> (0xFF000000L or value).toInt()
            else -> DEFAULT
        }
    }

    /** Human label for the current swatch — "Custom" for a value we did not offer. */
    fun label(argb: Int): String =
        SWATCHES.firstOrNull { it.second == argb }?.first ?: "Custom"
}
