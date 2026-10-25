package com.soniccore.app.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A glass-styled card with subtle border and background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        color = SonicCoreColors.ElevatedSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        content = content
    )
}
