package com.soniccore.app.ui.navigation

import com.soniccore.app.SonicCoreCategory

/**
 * Navigation destinations within SonicCore categories.
 */
enum class SonicCoreDestination {
    HOME,
    AUDIO_EQ,
    AUDIO_PRESETS,
    AUDIO_CAPTURE,
    DEVICE_INFO,
    DEVICE_BATTERY,
    DEVICE_STORAGE,
    DEVICE_DISPLAY,
    SHIZUKU_CONNECT,
    SHIZUKU_OPS,
    CUSTOMIZE_THEME,
    CUSTOMIZE_ACCENT,
    CUSTOMIZE_GLASS,
    PRIVACY_PERMISSIONS,
    PRIVACY_EXPORT,
    SETTINGS_PREFERENCES,
    SETTINGS_ABOUT,
    SETTINGS_DIAGNOSTICS;

    fun category(): SonicCoreCategory = when (this) {
        HOME, AUDIO_EQ, AUDIO_PRESETS, AUDIO_CAPTURE -> SonicCoreCategory.AUDIO
        DEVICE_INFO, DEVICE_BATTERY, DEVICE_STORAGE, DEVICE_DISPLAY -> SonicCoreCategory.DEVICE
        SHIZUKU_CONNECT, SHIZUKU_OPS -> SonicCoreCategory.SHIZUKU
        CUSTOMIZE_THEME, CUSTOMIZE_ACCENT, CUSTOMIZE_GLASS -> SonicCoreCategory.CUSTOMIZE
        PRIVACY_PERMISSIONS, PRIVACY_EXPORT -> SonicCoreCategory.PRIVACY
        SETTINGS_PREFERENCES, SETTINGS_ABOUT, SETTINGS_DIAGNOSTICS -> SonicCoreCategory.SETTINGS
    }
}
