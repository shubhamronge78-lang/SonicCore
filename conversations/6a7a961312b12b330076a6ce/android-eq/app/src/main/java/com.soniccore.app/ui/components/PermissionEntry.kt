package com.soniccore.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun PermissionEntry(
    name: String,
    state: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
        verticalAlignment = androidx.compose.foundation.layout.Alignment.CenterVertically
    ) {
        Text(text = name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = SonicCoreColors.PrimaryText, modifier = Modifier.weight(1f))
        Text(text = state, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = SonicCoreColors.SecondaryText)
    }
}
