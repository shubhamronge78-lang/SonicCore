package com.soniccore.app.ui.customization

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.NeonTheme
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun ThemeSwatchStrip(
    themes: List<NeonTheme>,
    currentThemeId: String,
    onThemeApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        themes.forEach { theme ->
            Text(
                text = theme.name,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (theme.id == currentThemeId) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    .clickable { onThemeApply(theme.id) }
            )
        }
    }
}
