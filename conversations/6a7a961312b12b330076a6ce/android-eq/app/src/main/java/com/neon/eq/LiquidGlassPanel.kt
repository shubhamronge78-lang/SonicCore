package com.neon.eq

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Liquid Glass panel — a translucent frosted-glass container with a subtle
 * vertical gradient, thin luminous border, and soft depth. Used to organize
 * Settings into clear visual sections.
 */
@Composable
fun LiquidGlassPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val a = glassSurfaceAlpha()
    val bA = glassBorderAlpha()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(S.card.copy(alpha = a), S.cardAlt.copy(alpha = a))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(T.primary.copy(alpha = bA), T.secondary.copy(alpha = bA * 0.5f))
                ),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        content = {
            GradientText(title, 12.sp, Brush.horizontalGradient(listOf(T.primary, T.secondary)))
            Spacer(Modifier.height(8.dp))
            content()
        }
    )
}
