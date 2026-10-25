package com.soniccore.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun StatusPill(
    text: String,
    color: Color = SonicCoreColors.PrimaryText,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
