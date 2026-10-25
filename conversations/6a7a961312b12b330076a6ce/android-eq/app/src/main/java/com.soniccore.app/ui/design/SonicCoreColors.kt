package com.soniccore.app.ui.design

import androidx.compose.ui.graphics.Color

/**
 * Solid Liquid Glass design system color palette.
 *
 * All colors are defined as [Color] values. Use these consistently across
 * all category screens and components to maintain a cohesive dark theme.
 */
object SonicCoreColors {

    // Backgrounds
    val Background = Color(0xFF080D18)        // #080D18
    val SecondaryBackground = Color(0xFF0D1422) // #0D1422
    val Surface = Color(0xFF121C2C)            // #121C2C (solid cards)
    val ElevatedSurface = Color(0xFF182438)    // #182438

    // Text
    val PrimaryText = Color(0xFFF3F7FF)        // #F3F7FF
    val SecondaryText = Color(0xFF9EACC2)      // #9EACC2

    // Accents
    val Cyan = Color(0xFF48DDF5)               // #48DDF5 (audio accent)
    val Violet = Color(0xFFA78BFA)             // #A78BFA (customization accent)

    // Status
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)

    // Borders
    val Border = Color(0xFF1E2A3A).copy(alpha = 0.5f)

    // Deprecated aliases for migration compatibility
    val PrimaryBackground = Background
    val SecondaryTextColor = SecondaryText
    val AudioAccent = Cyan
    val CustomizationAccent = Violet
    val SolidCard = Surface
}
