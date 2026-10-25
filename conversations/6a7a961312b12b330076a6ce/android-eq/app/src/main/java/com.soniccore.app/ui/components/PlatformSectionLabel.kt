package com.soniccore.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun PlatformSectionLabel(
    text: String,
    color: Color = SonicCoreColors.PrimaryText,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        color = color,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
