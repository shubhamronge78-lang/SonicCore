package com.soniccore.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A tool available inside one of the six SonicCore categories.
 */
data class SonicCoreTool(
    val title: String,
    val icon: String,
    val category: SonicCoreCategory,
    val onClick: () -> Unit = {}
) {
    fun color(): Color = category.tone
}

/**
 * Top-level color value used by design system components.
 */
val color: Color = SonicCoreColors.PrimaryText
