package com.neon.eq.ui

import com.neon.eq.NeonTheme
import com.neon.eq.Themes
import com.neon.eq.SurfacePalette
import com.neon.eq.SurfaceModes

// ── v3.7: Appearance presets — alternate styles for the redesigned interface ──
// Each preset is a curated combination of color theme, surface palette,
// glass intensity, and background treatment. Switching presets updates the
// whole app consistently. Glass intensity visibly changes surface treatment.
enum class AppearancePreset(
    val id: String,
    val label: String,
    val themeId: String,
    val modeId: String,
    val glass: Int,         // 0 LOW · 1 MEDIUM · 2 HIGH
    val background: String   // "glass" · "amoled" · "crystal" · "aurora"
) {
    LIQUID_GLASS(
        "liquid_glass", "Liquid Glass",
        "classic", "dark", 1, "glass"
    ),
    AMOLED_PRO(
        "amoled_pro", "AMOLED Pro",
        "classic", "dark", 0, "amoled"
    ),
    CRYSTAL_LIGHT(
        "crystal_light", "Crystal Light",
        "glacier", "light", 2, "crystal"
    ),
    AURORA(
        "aurora", "Aurora",
        "synthwave", "dark", 2, "aurora"
    );

    companion object {
        val DEFAULT = LIQUID_GLASS
        fun byId(id: String?): AppearancePreset =
            values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
