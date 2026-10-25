package com.soniccore.app.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A liquid glass surface container.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    shape: androidx.compose.foundation.shape.CornerShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        color = SonicCoreColors.SecondaryBackground,
        shape = shape,
        content = content
    )
}
