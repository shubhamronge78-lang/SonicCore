package com.soniccore.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = androidx.compose.foundation.layout.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = SonicCoreColors.PrimaryText)
            description?.let { sub ->
                Text(text = sub, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = SonicCoreColors.SecondaryText, modifier = androidx.compose.foundation.layout.Padding(top = 4.dp))
            }
        }
        trailing()
    }
}
