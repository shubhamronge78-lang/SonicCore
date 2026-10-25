package com.soniccore.app.ui.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.NeonTheme
import com.soniccore.app.ui.design.GlassIntensityPreference
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.PlatformInfoRow
import com.soniccore.app.ui.design.PlatformSectionLabel
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.StatusPill

private data class SystemCustomization(
    val title: String,
    val detail: String,
    val supported: Boolean
)

/**
 * Customization Center: the app-level appearance controls that actually exist,
 * plus an honest list of the system-level customizations SonicCore cannot make
 * on another app's behalf.
 */
@Composable
fun CustomizationCenterScreen(
    themes: List<NeonTheme>,
    currentThemeId: String,
    onThemeSelect: (NeonTheme) -> Unit,
    onThemeApply: (NeonTheme) -> Unit,
    glassLevel: Int,
    onGlassLevelChange: (Int) -> Unit,
    appearanceMode: String,
    onAppearanceModeChange: (String) -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val systemCustomizations = listOf(
        SystemCustomization(
            "Quick Settings tile",
            "SonicCore declares its own tile service; add it from the notification shade's edit screen.",
            supported = true
        ),
        SystemCustomization(
            "Home screen widget",
            "An app widget is registered and can be placed on the launcher.",
            supported = true
        ),
        SystemCustomization(
            "Notification controls",
            "The foreground service publishes playback controls.",
            supported = true
        ),
        SystemCustomization(
            "Launcher icon pack",
            "Not supported: icon packs are applied by the launcher, not by other apps.",
            supported = false
        ),
        SystemCustomization(
            "System-wide accent color",
            "Not available: Android does not let a third-party app override another app's accent color.",
            supported = false
        ),
        SystemCustomization(
            "Wallpaper-based color extraction",
            "Not implemented: SonicCore has no Material You dynamic-color pipeline.",
            supported = false
        ),
        SystemCustomization(
            "Force dark mode in other apps",
            "Not supported: dark mode here applies to SonicCore's own surfaces only.",
            supported = false
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Appearance mode
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            PlatformSectionLabel("Appearance")
            Spacer(Modifier.height(4.dp))
            Text(
                "Applied immediately and saved with the existing UI preferences.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "SYSTEM", "light" to "LIGHT", "dark" to "DARK").forEach { (value, label) ->
                    val selected = appearanceMode == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) SonicCoreColors.Cyan.copy(alpha = 0.18f)
                                else SonicCoreColors.ElevatedSurface
                            )
                            .border(
                                1.dp,
                                if (selected) SonicCoreColors.Cyan else SonicCoreColors.Border.copy(alpha = 0.3f),
                                RoundedCornerShape(50)
                            )
                            .clickable { onAppearanceModeChange(value) }
                            .semantics { contentDescription = "Appearance $label" }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.PrimaryText
                        )
                    }
                }
            }
        }

        // Theme studio
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            PlatformSectionLabel("Theme studio", color = SonicCoreColors.Violet)
            Spacer(Modifier.height(4.dp))
            Text(
                "Selecting previews the palette; Apply commits it to the saved preference.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { theme ->
                    val selected = theme.id == currentThemeId
                    Column(
                        modifier = Modifier
                            .width(116.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.darkSurfaces.card)
                            .border(
                                1.dp,
                                if (selected) theme.primary else theme.darkSurfaces.borderDim,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onThemeSelect(theme) }
                            .semantics { contentDescription = "Theme ${theme.label}" }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(theme.primary, theme.secondary, theme.accent).forEach { color ->
                                Box(
                                    Modifier
                                        .size(17.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (selected) Text("✓", color = theme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            theme.label,
                            fontSize = 11.sp,
                            color = if (selected) theme.primary else theme.darkSurfaces.text,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            minLines = 2
                        )
                    }
                }
            }
            val active = themes.firstOrNull { it.id == currentThemeId }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { active?.let(onThemeApply) },
                enabled = active != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Apply selected theme" },
                colors = ButtonDefaults.buttonColors(containerColor = SonicCoreColors.Cyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "APPLY ${active?.label?.uppercase() ?: "THEME"}",
                    color = Color(0xFF06121A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Liquid glass intensity
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("Liquid glass intensity")
            Spacer(Modifier.height(4.dp))
            Text(
                "Rendering only. Never changes DSP, capture, buffers or sample rate.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIntensityPreference.LEVELS.forEach { (label, level) ->
                    val selected = glassLevel == level
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) SonicCoreColors.Violet.copy(alpha = 0.18f)
                                else SonicCoreColors.ElevatedSurface
                            )
                            .border(
                                1.dp,
                                if (selected) SonicCoreColors.Violet else SonicCoreColors.Border.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onGlassLevelChange(level) }
                            .semantics { contentDescription = "Glass intensity $label" }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) SonicCoreColors.Violet else SonicCoreColors.PrimaryText
                        )
                    }
                }
            }
        }

        // System customization capabilities
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            PlatformSectionLabel("System customization")
            Spacer(Modifier.height(4.dp))
            Text(
                "What Android lets a third-party app customize, and what it does not.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(8.dp))
            systemCustomizations.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            color = SonicCoreColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            item.detail,
                            fontSize = 10.sp,
                            color = SonicCoreColors.SecondaryText,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        if (item.supported) "Supported" else "Not supported",
                        if (item.supported) SonicCoreColors.Success else SonicCoreColors.SecondaryText
                    )
                }
            }
        }

        // Link to the full settings sheet
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("More preferences")
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Engine & audio", "Performance mode, start on boot, preset restore")
            PlatformInfoRow("Output route", "Sample rate, buffer mode, latency budget")
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenFullSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Open full settings sheet" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                    contentColor = SonicCoreColors.Cyan
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("OPEN FULL SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
