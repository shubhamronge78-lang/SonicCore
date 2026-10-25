package com.soniccore.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small status pill shared by the platform screens (Shizuku, permissions, diagnostics). */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Two-column label/value row that keeps long values readable instead of clipping them. */
@Composable
fun PlatformInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = SonicCoreColors.PrimaryText
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.SecondaryText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

/** Uppercase section caption used above grouped content. */
@Composable
fun PlatformSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SonicCoreColors.Cyan
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}
