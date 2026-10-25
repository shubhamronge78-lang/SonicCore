package com.soniccore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun DSPModuleCard(
    name: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(12.dp)
    ) {
        Text(text = name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = SonicCoreColors.PrimaryText)
        Text(text = status, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = SonicCoreColors.SecondaryText)
    }
}
