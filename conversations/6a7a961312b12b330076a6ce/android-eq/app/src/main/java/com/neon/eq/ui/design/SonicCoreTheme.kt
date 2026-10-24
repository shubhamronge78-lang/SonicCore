package com.neon.eq.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object SonicCoreColors {
    var Background by mutableStateOf(Color(0xFF080B11))
        private set
    var Surface by mutableStateOf(Color(0xFF111722))
        private set
    var ElevatedSurface by mutableStateOf(Color(0xFF171F2C))
        private set
    var PrimaryText by mutableStateOf(Color(0xFFF2F5FA))
        private set
    var SecondaryText by mutableStateOf(Color(0xFF9AA8BA))
        private set
    var Cyan by mutableStateOf(Color(0xFF62D5E5))
        private set
    var Violet by mutableStateOf(Color(0xFF9A8CFF))
        private set
    var Success by mutableStateOf(Color(0xFF6DD6A2))
        private set
    var Warning by mutableStateOf(Color(0xFFF0BD68))
        private set
    var Error by mutableStateOf(Color(0xFFFF7785))
        private set
    var Border by mutableStateOf(Color(0xFFDAE7F7))
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

data class SonicCorePalette(
    val background: Color = Color(0xFF080B11),
    val surface: Color = Color(0xFF111722),
    val elevatedSurface: Color = Color(0xFF171F2C),
    val primaryText: Color = Color(0xFFF2F5FA),
    val secondaryText: Color = Color(0xFF9AA8BA),
    val primary: Color = Color(0xFF62D5E5),
    val secondary: Color = Color(0xFF9A8CFF),
    val success: Color = Color(0xFF6DD6A2),
    val warning: Color = Color(0xFFF0BD68),
    val error: Color = Color(0xFFFF7785),
    val border: Color = Color(0xFFDAE7F7)
)

val LocalSonicCorePalette = compositionLocalOf { SonicCorePalette() }

@Composable
fun SonicCoreTheme(
    glassLevel: Int = LocalGlassIntensity.current,
    palette: SonicCorePalette = LocalSonicCorePalette.current,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGlassIntensity provides glassLevel.coerceIn(0, 2),
        LocalSonicCorePalette provides palette
    ) {
        SideEffect { SonicCoreColors.apply(palette) }
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

val LocalGlassIntensity = compositionLocalOf { 1 }

internal data class LiquidGlassMaterialSpec(
    val surfaceAlpha: Float,
    val tintAlpha: Float,
    val edgeAlpha: Float,
    val reflectionAlpha: Float,
    val shadowElevationDp: Float
)

internal fun liquidGlassMaterialSpec(level: Int): LiquidGlassMaterialSpec = when (level.coerceIn(0, 2)) {
    0 -> LiquidGlassMaterialSpec(
        surfaceAlpha = 0.84f,
        tintAlpha = 0.035f,
        edgeAlpha = 0.10f,
        reflectionAlpha = 0.055f,
        shadowElevationDp = 8f
    )
    2 -> LiquidGlassMaterialSpec(
        surfaceAlpha = 0.58f,
        tintAlpha = 0.12f,
        edgeAlpha = 0.24f,
        reflectionAlpha = 0.16f,
        shadowElevationDp = 18f
    )
    else -> LiquidGlassMaterialSpec(
        surfaceAlpha = 0.70f,
        tintAlpha = 0.075f,
        edgeAlpha = 0.16f,
        reflectionAlpha = 0.105f,
        shadowElevationDp = 13f
    )
}
