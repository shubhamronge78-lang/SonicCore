package com.soniccore.app.ui.design

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The live SonicCore palette.
 *
 * These are Compose state values read directly by the shared components, so a
 * theme change repaints every reader. They are written by [SonicCoreTheme]
 * *during* composition (see the comment there) and never by a screen.
 */
object SonicCoreColors {
    var Background by mutableStateOf(SonicCoreGlassDark.background)
        private set
    var Surface by mutableStateOf(SonicCoreGlassDark.backgroundRaised)
        private set
    var ElevatedSurface by mutableStateOf(SonicCoreGlassDark.elevated)
        private set
    var PrimaryText by mutableStateOf(SonicCoreGlassDark.textPrimary)
        private set
    var SecondaryText by mutableStateOf(SonicCoreGlassDark.textSecondary)
        private set
    var Cyan by mutableStateOf(SonicCoreGlassDark.audioAccent)
        private set
    var Violet by mutableStateOf(SonicCoreGlassDark.customizationAccent)
        private set
    var Success by mutableStateOf(Color(0xFF6DD6A2))
        private set
    var Warning by mutableStateOf(Color(0xFFF0BD68))
        private set
    var Error by mutableStateOf(Color(0xFFFF7785))
        private set
    var Border by mutableStateOf(SonicCoreGlassDark.border)
        private set

    fun apply(palette: SonicCorePalette) {
        Background = palette.background
        Surface = palette.surface
        ElevatedSurface = palette.elevatedSurface
        PrimaryText = palette.primaryText
        SecondaryText = palette.secondaryText
        Cyan = palette.primary
        Violet = palette.secondary
        Success = palette.success
        Warning = palette.warning
        Error = palette.error
        Border = palette.border
    }
}

/**
 * A resolved appearance. The surface ramp is what a theme owns; the glass
 * *behaviour* (opacity, edge, radius, elevation) always comes from the tokens
 * in `GlassTokens.kt`, so the twelve themes stay visually coherent.
 */
data class SonicCorePalette(
    val background: Color = SonicCoreGlassDark.background,
    val surface: Color = SonicCoreGlassDark.backgroundRaised,
    val elevatedSurface: Color = SonicCoreGlassDark.elevated,
    val primaryText: Color = SonicCoreGlassDark.textPrimary,
    val secondaryText: Color = SonicCoreGlassDark.textSecondary,
    val primary: Color = SonicCoreGlassDark.audioAccent,
    val secondary: Color = SonicCoreGlassDark.customizationAccent,
    val success: Color = Color(0xFF6DD6A2),
    val warning: Color = Color(0xFFF0BD68),
    val error: Color = Color(0xFFFF7785),
    val border: Color = SonicCoreGlassDark.border
)

/** The active palette, for screens that need the whole record (not one token). */
val LocalSonicCorePalette = compositionLocalOf { SonicCorePalette() }

/**
 * The active palette as a [GlassPalette], so the shared glass components read
 * the same source as the tokens file's defaults.
 */
fun SonicCorePalette.toGlassPalette(): GlassPalette = GlassPalette(
    background = background,
    backgroundRaised = surface,
    card = surface,
    elevated = elevatedSurface,
    textPrimary = primaryText,
    textSecondary = secondaryText,
    audioAccent = primary,
    customizationAccent = secondary,
    border = border
)

@Composable
fun SonicCoreTheme(
    glassLevel: Int = LocalGlassIntensity.current,
    palette: SonicCorePalette = LocalSonicCorePalette.current,
    content: @Composable () -> Unit
) {
    // GL-05 (stale colours): this used to run in a `SideEffect`, which fires
    // *after* composition and layout — so the first frame after a theme, accent
    // or appearance-mode change still painted the previous palette, and any
    // screen whose read was skipped kept the old colours until something else
    // invalidated it. Applying it inside a `remember(palette)` runs during
    // composition, before `content()` composes, so every reader in this frame
    // already sees the new palette. `remember(palette)` keeps it to one apply
    // per palette value instead of one per recomposition.
    remember(palette) { SonicCoreColors.apply(palette) }

    val glassPalette = remember(palette) { palette.toGlassPalette() }
    val nativeBlur = remember { glassNativeBlurSupported(Build.VERSION.SDK_INT) }

    CompositionLocalProvider(
        LocalGlassIntensity provides glassLevel.coerceIn(0, 2),
        LocalSonicCorePalette provides palette,
        LocalGlassPalette provides glassPalette,
        LocalGlassNativeBlur provides nativeBlur
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.primary,
                onPrimary = palette.background,
                secondary = palette.secondary,
                onSecondary = palette.primaryText,
                tertiary = palette.success,
                background = palette.background,
                onBackground = palette.primaryText,
                surface = palette.surface,
                onSurface = palette.primaryText,
                surfaceVariant = palette.elevatedSurface,
                onSurfaceVariant = palette.secondaryText,
                error = palette.error,
                onError = palette.background
            ),
            content = content
        )
    }
}

/**
 * Glass intensity: 0 Subtle · 1 Liquid · 2 Crystal. Rendering only — it never
 * reaches DSP, capture, buffers, latency or the sample rate.
 */
val LocalGlassIntensity = compositionLocalOf { 1 }
