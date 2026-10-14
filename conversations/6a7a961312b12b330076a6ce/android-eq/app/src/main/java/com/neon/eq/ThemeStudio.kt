package com.neon.eq

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── v147 THEME STUDIO ───────────────────────────────────────────────
// Unified theme system that combines the existing NeonTheme (accent colors),
// SurfacePalette (surface colors), and glass intensity into four named
// console themes. Selecting a ConsoleTheme sets all three existing state
// vars (appThemeState / appModeState / appGlassState) so every existing
// composable that reads T.* or S.* picks up the new look instantly — no
// refactor of the 6000-line UI needed.

data class ConsoleTheme(
    val id: String,
    val label: String,
    val subtitle: String,
    val accent: NeonTheme,
    val surface: SurfacePalette,
    val glass: Int,       // 0 LOW · 1 MEDIUM · 2 HIGH
    val isDark: Boolean,
    val aurora: Boolean = false
)

object ConsoleThemes {
    // 1. Liquid Glass — default, translucent and luminous.
    //    Cyan + violet accents on layered dark navy, medium glass.
    val LIQUID_GLASS = ConsoleTheme(
        "liquid_glass", "Liquid Glass", "Translucent and luminous",
        Themes.CLASSIC, SurfaceModes.DARK, 1, true
    )

    // 2. AMOLED Pro — minimal, high contrast, dark black.
    //    Pure black surfaces, restrained cyan accent, low glass (no blur cost).
    val AMOLED_PRO = ConsoleTheme(
        "amoled_pro", "AMOLED Pro", "Minimal high contrast",
        NeonTheme("amoled_pro", "AMOLED", Color(0xFF00D9FF), Color(0xFF00BFA5), Color(0xFFE0E0E0)),
        SurfacePalette(
            bg = Color(0xFF000000), surface = Color(0xFF080808),
            card = Color(0xFF0E0E0E), cardAlt = Color(0xFF0A0A0A),
            cardDeep = Color(0xFF161616), borderDim = Color(0xFF222222),
            text = Color.White, textSoft = Color(0xFFCCCCCC)
        ),
        0, true
    )

    // 3. Crystal Light — clean, bright glass panels.
    //    Light surfaces, blue-cyan accents, medium glass.
    val CRYSTAL_LIGHT = ConsoleTheme(
        "crystal_light", "Crystal Light", "Clean bright glass",
        NeonTheme("crystal_light", "Crystal", Color(0xFF0099CC), Color(0xFF7C3AED), Color(0xFF2563EB)),
        SurfaceModes.LIGHT, 1, false
    )

    // 4. Aurora — subtle multicolor gradients.
    //    Dark purple-tinted surfaces, multicolor cyan/violet/green accents,
    //    high glass, animated aurora gradient background.
    val AURORA = ConsoleTheme(
        "aurora", "Aurora", "Subtle multicolor gradients",
        NeonTheme("aurora", "Aurora", Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFF69F0AE)),
        SurfacePalette(
            bg = Color(0xFF080812), surface = Color(0xFF0C0C1A),
            card = Color(0xFF121225), cardAlt = Color(0xFF0E0E1E),
            cardDeep = Color(0xFF1A1A30), borderDim = Color(0xFF242448),
            text = Color.White, textSoft = Color(0xFFC8D0F0)
        ),
        2, true, aurora = true
    )

    val ALL = listOf(LIQUID_GLASS, AMOLED_PRO, CRYSTAL_LIGHT, AURORA)

    fun byId(id: String?): ConsoleTheme = ALL.firstOrNull { it.id == id } ?: LIQUID_GLASS
}

// ── State ──
val appConsoleThemeState = mutableStateOf(ConsoleThemes.LIQUID_GLASS)
val appReducedMotionState = mutableStateOf(false)

// Apply a console theme: sets accent + surface + glass in one shot.
// Existing composables reading T / S / appGlassState pick up the change
// immediately — no playback restart needed (UI-only, DSP untouched).
fun applyConsoleTheme(ctx: Context, theme: ConsoleTheme) {
    appThemeState.value = theme.accent
    appModeState.value = theme.surface
    appGlassState.value = theme.glass
    appConsoleThemeState.value = theme
    try {
        ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("console_theme", theme.id)
            .putString("theme", theme.accent.id)
            .putString("mode", if (theme.isDark) "dark" else "light")
            .putInt("glass", theme.glass)
            .apply()
    } catch (_: Throwable) { }
}

// Restore the persisted console theme at app start (called from onCreate).
fun restoreConsoleTheme(ctx: Context) {
    val prefs = ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    val themeId = prefs.getString("console_theme", null)
    if (themeId != null) {
        val theme = ConsoleThemes.byId(themeId)
        appThemeState.value = theme.accent
        appModeState.value = theme.surface
        appGlassState.value = theme.glass
        appConsoleThemeState.value = theme
    }
    // Reduced-motion preference
    appReducedMotionState.value = prefs.getBoolean("reduced_motion", false)
}

// Blur fallback: auto-reduce glass intensity on low-RAM devices so the
// glass UI stays smooth without real-time blur overhead.
fun applyBlurFallback(ctx: Context) {
    if (appReducedMotionState.value) return
    val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE)
        as? android.app.ActivityManager
    val memClass = activityManager?.memoryClass ?: 256
    if (memClass <= 128 && appGlassState.value > 0) {
        appGlassState.value = 0
    }
}

// Animation duration helper: returns 0 (instant) when reduced-motion is on.
fun animMs(base: Int): Int = if (appReducedMotionState.value) 0 else base

// ── Theme Studio grid: 2×2 cards with live previews ──
@Composable
fun ThemeStudioGrid() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Theme Studio", fontSize = 15.sp, color = S.text, fontWeight = FontWeight.Bold)
        Text("Switch instantly — playback continues, no restart needed",
             fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        ConsoleThemes.ALL.chunked(2).forEach { rowThemes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()) {
                rowThemes.forEach { theme ->
                    val selected = appConsoleThemeState.value.id == theme.id
                    ThemeCard(theme, selected) { applyConsoleTheme(ctx, theme) }
                }
                // Fill the row if odd number of themes (keeps cards same width)
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // Reduced-motion toggle
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reduced Motion", fontSize = 13.sp, color = S.text)
                Text("Disable animations for accessibility", fontSize = 11.sp, color = Color.Gray)
            }
            androidx.compose.material3.Switch(
                checked = appReducedMotionState.value,
                onCheckedChange = { v ->
                    appReducedMotionState.value = v
                    try {
                        ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                            .edit().putBoolean("reduced_motion", v).apply()
                    } catch (_: Throwable) { }
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = T.primary,
                    checkedTrackColor = T.primary.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun ThemeCard(theme: ConsoleTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(theme.surface.card)
            .border(
                1.dp,
                if (selected) theme.accent.primary else theme.surface.borderDim,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Mini preview: background sample + accent gradient bar + color dots
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surface.bg)
        ) {
            // Glass glow preview
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(theme.accent.primary.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
            )
            Row(
                Modifier.fillMaxSize().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(theme.accent.primary, theme.accent.secondary, theme.accent.accent).forEach { c ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(theme.label, fontSize = 13.sp, color = theme.surface.text,
             fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(theme.subtitle, fontSize = 10.sp, color = theme.surface.textSoft)
        if (selected) {
            Spacer(Modifier.height(4.dp))
            Text("✓ Active", fontSize = 10.sp, color = theme.accent.primary)
        }
    }
}
