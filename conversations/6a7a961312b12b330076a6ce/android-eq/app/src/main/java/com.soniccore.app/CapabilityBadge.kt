package com.soniccore.app

import androidx.compose.foundation.layout.SizeModifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.run

/**
 * Capability badge composable previously from com.neon.eq.ui.category.CapabilityBadge.
 * Displays a capability status label next to a capability name.
 */
@Composable
fun CapabilityBadge(
    capability: String,
    compact: Boolean = false
) {
    val text = if (compact) {
        capability.substring(0, minOf(4, capability.length))
    } else {
        capability
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(4.dp)
    )
}
