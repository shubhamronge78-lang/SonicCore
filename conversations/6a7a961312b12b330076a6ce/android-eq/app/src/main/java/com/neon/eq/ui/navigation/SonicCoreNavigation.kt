package com.neon.eq.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.ui.design.GlassIconButton
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.liquidGlassMaterialSpec
import com.neon.eq.ui.design.LocalGlassIntensity
import com.neon.eq.ui.design.SonicCoreColors

enum class SonicCoreDestination(
    val title: String,
    val label: String,
    val subtitle: String,
    val icon: String,
    val isPrimary: Boolean = true
) {
    DASHBOARD("Home", "HOME", "Live engine status and audio overview", "◉"),
    EQUALIZER("Equalizer", "EQ", "Graphic and parametric equalization", "∿"),
    PLAYER("Player", "PLAYER", "Play music through the SonicCore engine", "▶"),
    DSP_FX("DSP Studio", "DSP", "Processing chain and supported effects", "⌘"),
    SYSTEM_CAPTURE("System Capture", "CAPTURE", "Capture controls and measured status", "◎", false),
    SESSIONS("Sessions", "SESSIONS", "Recorded sessions and saved configurations", "▤", false),
    SIGNAL_PATH("Signal Path", "PATH", "Measured capture-to-output diagnostics", "↗", false),
    PRO_FX("Pro FX", "PRO FX", "Noise gate and limiter controls", "✦", false),
    DIAGNOSTICS("Diagnostics", "DIAG", "Device info, battery, thermal, and health", "⚙", false),
    CAPABILITIES("Capabilities", "CAPS", "Capability registry and system restrictions", "✓", false),
    DEVICE_TOOLS("Device Tools", "TOOLS", "System audio shortcuts and settings", "🛠", false),
    APP_MANAGER("App Manager", "APPS", "App profiles, package visibility, and launch", "㗊", false);

    companion object {
        val primaryDestinations = listOf(DASHBOARD, EQUALIZER, PLAYER, DSP_FX)
        val consoleDestinations = listOf(
            DASHBOARD,
            EQUALIZER,
            PLAYER,
            DSP_FX,
            SYSTEM_CAPTURE,
            SESSIONS,
            SIGNAL_PATH,
            PRO_FX,
            DIAGNOSTICS,
            CAPABILITIES,
            DEVICE_TOOLS,
            APP_MANAGER
        )
    }
}

@Composable
fun SonicCoreScreenHeader(
    destination: SonicCoreDestination,
    onSettingsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                destination.title.uppercase(),
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                destination.subtitle,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (onBackClick != null) {
            GlassIconButton(
                contentDescription = "Back to previous screen",
                onClick = onBackClick
            ) {
                Text("‹", color = SonicCoreColors.Cyan, fontSize = 28.sp)
            }
        } else {
            GlassIconButton(
                contentDescription = "Open settings",
                onClick = onSettingsClick
            ) {
                Text("⚙", color = SonicCoreColors.Cyan, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SonicCoreBottomNavigation(
    selected: SonicCoreDestination,
    onSelect: (SonicCoreDestination) -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = if (landscape) 28.dp else 24.dp, vertical = 8.dp),
        contentPadding = 7.dp,
        shape = RoundedCornerShape(30.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SonicCoreDestination.consoleDestinations.forEach { destination ->
                val active = selected == destination
                val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
                val activeSurface by animateColorAsState(
                    targetValue = if (active) SonicCoreColors.Cyan.copy(alpha = material.tintAlpha + 0.10f)
                    else Color.Transparent,
                    label = "liquidDockSelection"
                )
                Surface(
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .width(72.dp)
                        .height(56.dp)
                        .semantics {
                            contentDescription = destination.title
                            stateDescription = if (active) "Selected" else "Not selected"
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = activeSurface,
                    contentColor = if (active) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                    border = BorderStroke(
                        0.8.dp,
                        (if (active) SonicCoreColors.Cyan else SonicCoreColors.Border)
                            .copy(alpha = if (active) material.edgeAlpha else 0f)
                    ),
                    shadowElevation = if (active) 4.dp else 0.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            destination.icon,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = if (active) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText
                        )
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            color = if (active) SonicCoreColors.PrimaryText else SonicCoreColors.SecondaryText
                        )
                    }
                }
            }
        }
    }
}
