package com.soniccore.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.SonicCoreColors

/**
 * Indicator status badge/summary.
 */
@Composable
fun IndicatorStatus(
    label: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.foundation.layout.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = SonicCoreColors.SecondaryText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = SonicCoreColors.PrimaryText
        )
    }
}
