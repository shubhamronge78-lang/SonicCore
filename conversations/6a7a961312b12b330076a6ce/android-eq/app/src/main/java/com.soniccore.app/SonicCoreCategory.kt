package com.soniccore.app

import androidx.compose.ui.graphics.Color

import com.soniccore.app.ui.design.SonicCoreColors

/**
 * The six primary navigation categories of SonicCore.
 *
 * Each category exposes a [tone] color used for theming accents and
 * capability badges across the dashboard and hub screens.
 */
enum class SonicCoreCategory(
    val title: String,
    val subtitle: String,
    val icon: String,
    val tone: Color
) {
    AUDIO("Audio", "Equalizer, presets, capture", "🎵", SonicCoreColors.Cyan),
    DEVICE("Device Center", "Hardware, battery, storage", "📱", SonicCoreColors.SecondaryText),
    SHIZUKU("Shizuku Center", "Connection, privileged ops", "🔐", SonicCoreColors.Violet),
    CUSTOMIZE("Customization", "Themes, accents, glass", "🎨", SonicCoreColors.Violet),
    PRIVACY("Permissions & Privacy", "Permission status, export", "🔒", SonicCoreColors.Warning),
    SETTINGS("Settings", "Preferences, about, diagnostics", "⚙", SonicCoreColors.SecondaryText);

    fun color(): Color = tone

    companion object {
        /** All six categories in dashboard order. */
        val entries: List<SonicCoreCategory> = values().toList()
    }
}
