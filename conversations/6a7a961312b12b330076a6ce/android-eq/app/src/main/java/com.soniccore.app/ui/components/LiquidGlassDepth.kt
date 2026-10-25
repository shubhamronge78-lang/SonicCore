package com.soniccore.app.ui.components

import androidx.compose.ui.unit.Dp

/**
 * Depth level for liquid glass surfaces.
 */
enum class LiquidGlassDepth(val dp: Dp) {
    NONE(0.dp),
    SUBTLE(4.dp),
    MODERATE(8.dp),
    DEEP(12.dp),
    Floating(8.dp),
    Inset(4.dp)
}
