package com.neon.eq.ui.design

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Per-workspace colour identity. Electric cyan stays reserved for active audio. */
enum class SonicCoreTone { CYAN, BLUE, VIOLET, MAGENTA, GREEN, SLATE }

/** Resolved tone colour, read from the live (theme-aware) SonicCore palette. */
fun SonicCoreTone.color(): Color = when (this) {
    SonicCoreTone.CYAN -> SonicCoreColors.Cyan
    SonicCoreTone.BLUE -> Color(0xFF5AA9FF)
    SonicCoreTone.VIOLET -> SonicCoreColors.Violet
    SonicCoreTone.MAGENTA -> Color(0xFFE08CFF)
    SonicCoreTone.GREEN -> SonicCoreColors.Success
    SonicCoreTone.SLATE -> SonicCoreColors.SecondaryText
}

/**
 * Appearance preferences owned by the Customization workspace.
 *
 * Everything here is UI-only presentation state: it never touches the audio
 * path, the DSP engine or any capture behaviour. Values are persisted in the
 * existing `ui_prefs` file under their own keys, so a reset can restore each
 * group independently without disturbing themes, glass or audio settings.
 */
object AppearancePreferenceKeys {
    const val PREFS = "ui_prefs"
    const val ACCENT = "sc_accent"
    const val HOME_COLUMNS = "sc_home_columns"
    const val CARD_DENSITY = "sc_card_density"
    const val CARD_CORNERS = "sc_card_corners"
    const val CARD_BORDER = "sc_card_border"
    const val MOTION = "sc_motion"
    const val DOCK_VISIBLE = "sc_dock_visible"
}

enum class AccentChoice(val id: String, val label: String, val tone: SonicCoreTone) {
    CYAN("cyan", "Electric Cyan", SonicCoreTone.CYAN),
    BLUE("blue", "Signal Blue", SonicCoreTone.BLUE),
    VIOLET("violet", "Studio Violet", SonicCoreTone.VIOLET),
    MAGENTA("magenta", "Creative Magenta", SonicCoreTone.MAGENTA),
    GREEN("green", "Verified Green", SonicCoreTone.GREEN);

    val color: Color get() = tone.color()

    companion object {
        fun byId(id: String?): AccentChoice = entries.firstOrNull { it.id == id } ?: CYAN
    }
}

/** Home grid columns. AUTO adapts to the available width. */
enum class HomeColumnChoice(val id: String, val label: String, val columns: Int) {
    AUTO("auto", "Automatic", 0),
    ONE("one", "Single column", 1),
    TWO("two", "Two columns", 2);

    companion object {
        fun byId(id: String?): HomeColumnChoice = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class CardDensityChoice(val id: String, val label: String, val verticalPadding: Dp) {
    COMFORTABLE("comfortable", "Comfortable", 18.dp),
    COMPACT("compact", "Compact", 12.dp);

    companion object {
        fun byId(id: String?): CardDensityChoice = entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

enum class CardCornerChoice(val id: String, val label: String, val radius: Dp) {
    SHARP("sharp", "Crisp", 10.dp),
    ROUNDED("rounded", "Rounded", 18.dp),
    PILL("pill", "Soft", 26.dp);

    companion object {
        fun byId(id: String?): CardCornerChoice = entries.firstOrNull { it.id == id } ?: ROUNDED
    }
}

enum class CardBorderChoice(val id: String, val label: String, val width: Dp) {
    HAIRLINE("hairline", "Hairline", 0.6.dp),
    STANDARD("standard", "Standard", 1.dp),
    BOLD("bold", "Defined", 1.6.dp);

    companion object {
        fun byId(id: String?): CardBorderChoice = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * Motion preference. [REDUCED] and [OFF] genuinely change behaviour: the UI
 * shortens or skips transitions instead of animating at full length.
 */
enum class MotionChoice(val id: String, val label: String, val scale: Float) {
    FULL("full", "Full motion", 1f),
    REDUCED("reduced", "Reduced motion", 0.45f),
    OFF("off", "Motion off", 0f);

    val reducesMotion: Boolean get() = this != FULL

    companion object {
        fun byId(id: String?): MotionChoice = entries.firstOrNull { it.id == id } ?: FULL
    }
}

/** Composition locals so any screen can honour the motion preference. */
val LocalMotionScale = compositionLocalOf { 1f }
val LocalReducedMotion = compositionLocalOf { false }

/** Applies the motion preference to an animation duration. */
fun motionDurationMillis(baseMillis: Int, scale: Float): Int {
    if (scale <= 0f) return 0
    return (baseMillis * scale).toInt().coerceAtLeast(1)
}

object AppearanceState {

    // Plain Compose state: writing a value recomposes every screen that reads
    // it, and `save()` persists the whole group. No duplicate setter functions
    // (they would collide with the property setters on the JVM).
    var accent by mutableStateOf(AccentChoice.CYAN)
    var homeColumns by mutableStateOf(HomeColumnChoice.AUTO)
    var density by mutableStateOf(CardDensityChoice.COMFORTABLE)
    var corners by mutableStateOf(CardCornerChoice.ROUNDED)
    var border by mutableStateOf(CardBorderChoice.STANDARD)
    var motion by mutableStateOf(MotionChoice.FULL)
    var dockVisible by mutableStateOf(true)

    fun load(context: Context) {
        val prefs = readPrefs(context) ?: return
        accent = AccentChoice.byId(prefs.getString(AppearancePreferenceKeys.ACCENT, null))
        homeColumns = HomeColumnChoice.byId(prefs.getString(AppearancePreferenceKeys.HOME_COLUMNS, null))
        density = CardDensityChoice.byId(prefs.getString(AppearancePreferenceKeys.CARD_DENSITY, null))
        corners = CardCornerChoice.byId(prefs.getString(AppearancePreferenceKeys.CARD_CORNERS, null))
        border = CardBorderChoice.byId(prefs.getString(AppearancePreferenceKeys.CARD_BORDER, null))
        motion = MotionChoice.byId(prefs.getString(AppearancePreferenceKeys.MOTION, null))
        dockVisible = prefs.getBoolean(AppearancePreferenceKeys.DOCK_VISIBLE, true)
    }

    fun save(context: Context) {
        val prefs = readPrefs(context) ?: return
        prefs.edit()
            .putString(AppearancePreferenceKeys.ACCENT, accent.id)
            .putString(AppearancePreferenceKeys.HOME_COLUMNS, homeColumns.id)
            .putString(AppearancePreferenceKeys.CARD_DENSITY, density.id)
            .putString(AppearancePreferenceKeys.CARD_CORNERS, corners.id)
            .putString(AppearancePreferenceKeys.CARD_BORDER, border.id)
            .putString(AppearancePreferenceKeys.MOTION, motion.id)
            .putBoolean(AppearancePreferenceKeys.DOCK_VISIBLE, dockVisible)
            .apply()
    }

    /** Resets only the appearance groups owned by the Customization workspace. */
    fun resetAppearance(context: Context) {
        accent = AccentChoice.CYAN
        homeColumns = HomeColumnChoice.AUTO
        density = CardDensityChoice.COMFORTABLE
        corners = CardCornerChoice.ROUNDED
        border = CardBorderChoice.STANDARD
        save(context)
    }

    fun resetMotion(context: Context) {
        motion = MotionChoice.FULL
        save(context)
    }

    private fun readPrefs(context: Context): SharedPreferences? = try {
        context.getSharedPreferences(AppearancePreferenceKeys.PREFS, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }
}
