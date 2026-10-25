package com.soniccore.app.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.components.LiquidGlassDepth

/**
 * A liquid glass surface container.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    depth: LiquidGlassDepth = LiquidGlassDepth.NONE,
    shape: androidx.compose.foundation.shape.CornerShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    content: @Composable () -> Unit
) {
    val effectivePadding = if (depth != LiquidGlassDepth.NONE) depth.dp else contentPadding
    Surface(
        modifier = modifier.fillMaxWidth().padding(effectivePadding),
        color = SonicCoreColors.SecondaryBackground,
        shape = shape,
        content = content
    )
}
