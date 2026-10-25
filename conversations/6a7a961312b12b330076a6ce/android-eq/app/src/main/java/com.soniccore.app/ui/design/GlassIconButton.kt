package com.soniccore.app.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A glass-styled icon button.
 */
@Composable
fun GlassIconButton(
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        modifier = modifier.size(44.dp).clickable(onClick = onClick),
        onClick = onClick
    ) {
        content()
    }
}
