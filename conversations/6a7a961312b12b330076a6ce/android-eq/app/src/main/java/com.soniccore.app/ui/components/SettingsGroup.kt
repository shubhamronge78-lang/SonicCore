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
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = SonicCoreColors.PrimaryText, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
