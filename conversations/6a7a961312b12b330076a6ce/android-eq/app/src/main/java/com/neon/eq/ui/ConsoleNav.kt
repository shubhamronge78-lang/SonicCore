package com.neon.eq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.NeonTheme
import com.neon.eq.glassSurfaceAlpha

// ── v3.7: Console navigation — 9 compact sections ──
// Replaces the single-scroll layout with a navigable console.
// The bottom bar is horizontally scrollable to fit all 9 items on a phone.
enum class ConsoleSection(val label: String, val icon: String) {
    DASHBOARD("Home", "▣"),
    EQUALIZER("EQ", "〰"),
    PLAYER("Player", "♪"),
    DSP("DSP", "⚙"),
    CAPTURE("Capture", "◉"),
    SIGNAL("Signal", "→"),
    SESSIONS("Sessions", "◷"),
    PROFX("Pro FX", "✦"),
    PRESETS("Presets", "★");

    companion object {
        val DEFAULT = EQUALIZER  // EQ is the visual centerpiece
    }
}

@Composable
fun ConsoleNavBar(
    current: ConsoleSection,
    onSelect: (ConsoleSection) -> Unit,
    theme: NeonTheme,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textSoftColor: Color
) {
    val a = glassSurfaceAlpha()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor.copy(alpha = a * 0.92f))
            .border(
                1.dp,
                borderColor.copy(alpha = 0.18f),
                RoundedCornerShape(0.dp)
            )
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ConsoleSection.values().toList()) { section ->
                val selected = section == current
                val col = if (selected) theme.primary else textSoftColor
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected)
                                theme.primary.copy(alpha = 0.14f)
                            else
                                Color.Transparent
                        )
                        .clickable { onSelect(section) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        section.icon,
                        fontSize = 16.sp,
                        color = col
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        section.label,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = col
                    )
                }
            }
        }
    }
}
