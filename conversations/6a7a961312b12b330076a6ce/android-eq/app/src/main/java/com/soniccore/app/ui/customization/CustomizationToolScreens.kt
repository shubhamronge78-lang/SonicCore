package com.soniccore.app.ui.customization

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.NeonTheme
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.LimitNote
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreChoiceRow
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.AccentChoice
import com.soniccore.app.ui.design.AppearanceState
import com.soniccore.app.ui.design.CardBorderChoice
import com.soniccore.app.ui.design.CardCornerChoice
import com.soniccore.app.ui.design.CardDensityChoice
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.GlassSlider
import com.soniccore.app.ui.design.HomeColumnChoice
import com.soniccore.app.ui.design.MotionChoice
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.color
import com.soniccore.app.ui.navigation.SonicCoreCategory
import androidx.compose.ui.semantics.stateDescription

private val customTone = SonicCoreCategory.CUSTOMIZE.tone

/** Horizontal theme gallery used by the Customization hub and the gallery tool. */
@Composable
fun ThemeSwatchStrip(
    themes: List<NeonTheme>,
    currentThemeId: String,
    onThemeApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        themes.forEach { theme ->
            val selected = theme.id == currentThemeId
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.darkSurfaces.card.copy(alpha = 0.92f))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) theme.primary else SonicCoreColors.Border.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onThemeApply(theme.id) }
                    .semantics {
                        contentDescription = "Theme ${theme.label}"
                        stateDescription = if (selected) "Applied" else "Not applied"
                    }
                    .padding(9.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(theme.primary, theme.secondary, theme.accent).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color)
                        )
                    }
                }
                Text(
                    theme.label,
                    color = theme.darkSurfaces.text,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    if (selected) "APPLIED" else "TAP TO APPLY",
                    color = theme.primary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

/** Customization → Color & Accent. */
@Composable
fun CustomizationAccentScreen(context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "The accent is applied to active audio elements and controls throughout SonicCore."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "ACCENT",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            AccentChoice.entries.forEach { accent ->
                SonicCoreChoiceRow(
                    title = accent.label,
                    description = "Applies to active controls, badges and highlights.",
                    selected = AppearanceState.accent == accent,
                    swatch = accent.color,
                    onClick = {
                        AppearanceState.accent = accent
                        AppearanceState.save(context)
                    }
                )
            }
            LimitNote(
                "Electric cyan is reserved for measured audio activity so an active signal always stays visually distinct from a themed control.",
                customTone
            )
        }
        AppearancePreviewCard(context)
    }
}

/** Customization → Glass Appearance. */
@Composable
fun CustomizationGlassScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "Glass is rendered with translucency and gradient edges — no live blur, so low-end devices keep their frame rate."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "GLASS INTENSITY",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSlider(
                    value = host.glassLevel.toFloat(),
                    onValueChange = { host.onGlassLevelChange(it.toInt().coerceIn(0, 2)) },
                    valueRange = 0f..2f,
                    modifier = Modifier.weight(1f).height(32.dp)
                )
                Text(
                    listOf("SUBTLE", "LIQUID", "CRYSTAL").getOrElse(host.glassLevel.coerceIn(0, 2)) { "LIQUID" },
                    color = SonicCoreColors.Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Subtle" to 0, "Liquid" to 1, "Crystal" to 2).forEach { (label, level) ->
                    val selected = host.glassLevel == level
                    Text(
                        label.uppercase(),
                        color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) SonicCoreColors.Cyan.copy(alpha = 0.12f) else SonicCoreColors.Background.copy(alpha = 0.4f))
                            .clickable { host.onGlassLevelChange(level) }
                            .padding(vertical = 9.dp)
                    )
                }
            }
            LimitNote(
                "Higher glass settings cost GPU time. SonicCore never blurs the live meters or the waveform, whatever the setting.",
                customTone
            )
        }
        AppearancePreviewCard(context)
    }
}

/** Customization → Home Screen Layout. */
@Composable
fun CustomizationLayoutScreen(context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Applies to the six category cards on Home.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "HOME GRID",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            HomeColumnChoice.entries.forEach { choice ->
                SonicCoreChoiceRow(
                    title = choice.label,
                    description = when (choice) {
                        HomeColumnChoice.AUTO -> "Two columns on wide phones, one on narrow screens."
                        HomeColumnChoice.ONE -> "Always a single column — easier to read one-handed."
                        HomeColumnChoice.TWO -> "Always two columns."
                    },
                    selected = AppearanceState.homeColumns == choice,
                    onClick = {
                        AppearanceState.homeColumns = choice
                        AppearanceState.save(context)
                    }
                )
            }
        }
        AppearancePreviewCard(context)
    }
}

/** Customization → Card Style & Density. */
@Composable
fun CustomizationCardScreen(context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Card geometry and information density across SonicCore.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CORNERS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            CardCornerChoice.entries.forEach { choice ->
                SonicCoreChoiceRow(
                    title = choice.label,
                    description = "${choice.radius.value.toInt()} dp corner radius",
                    selected = AppearanceState.corners == choice,
                    onClick = { AppearanceState.corners = choice; AppearanceState.save(context) }
                )
            }
            Text("BORDER", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            CardBorderChoice.entries.forEach { choice ->
                SonicCoreChoiceRow(
                    title = choice.label,
                    description = "%.1f dp outline".format(choice.width.value),
                    selected = AppearanceState.border == choice,
                    onClick = { AppearanceState.border = choice; AppearanceState.save(context) }
                )
            }
            Text("DENSITY", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            CardDensityChoice.entries.forEach { choice ->
                SonicCoreChoiceRow(
                    title = choice.label,
                    description = "${choice.verticalPadding.value.toInt()} dp vertical padding per card",
                    selected = AppearanceState.density == choice,
                    onClick = { AppearanceState.density = choice; AppearanceState.save(context) }
                )
            }
        }
        AppearancePreviewCard(context)
    }
}

/** Customization → Animation & Motion. */
@Composable
fun CustomizationMotionScreen(context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            when (AppearanceState.motion) {
                MotionChoice.FULL -> SonicCoreCapability.AVAILABLE
                else -> SonicCoreCapability.BLOCKED
            },
            if (AppearanceState.motion == MotionChoice.FULL) "Transitions run at full length."
            else "Transitions are shortened to ${(AppearanceState.motion.scale * 100).toInt()}% of their normal duration."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("MOTION", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            MotionChoice.entries.forEach { choice ->
                SonicCoreChoiceRow(
                    title = choice.label,
                    description = when (choice) {
                        MotionChoice.FULL -> "Meters, meters' peak decay and screen transitions animate normally."
                        MotionChoice.REDUCED -> "Animations run at under half speed length; meters update without smoothing."
                        MotionChoice.OFF -> "No animated transitions at all; values change immediately."
                    },
                    selected = AppearanceState.motion == choice,
                    onClick = { AppearanceState.motion = choice; AppearanceState.save(context) }
                )
            }
            LimitNote(
                "Reduced motion removes SonicCore's own transition animations. Android's system-wide animation settings still apply on top.",
                customTone
            )
        }
    }
}

/** Customization → Preview & Reset. */
@Composable
fun CustomizationPreviewScreen(host: SonicCoreToolHost, context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "A live sample card rendered with the current appearance settings.")
        AppearancePreviewCard(context)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CURRENT SETTINGS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Theme", host.themes.firstOrNull { it.id == host.currentThemeId }?.label ?: host.currentThemeId)
            SonicCoreDataRow("Accent", AppearanceState.accent.label)
            SonicCoreDataRow("Glass", listOf("Subtle", "Liquid", "Crystal").getOrElse(host.glassLevel.coerceIn(0, 2)) { "Liquid" })
            SonicCoreDataRow("Home grid", AppearanceState.homeColumns.label)
            SonicCoreDataRow("Corners", AppearanceState.corners.label)
            SonicCoreDataRow("Border", AppearanceState.border.label)
            SonicCoreDataRow("Density", AppearanceState.density.label)
            SonicCoreDataRow("Motion", AppearanceState.motion.label)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "RESET APPEARANCE",
                    color = SonicCoreColors.Error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                        .clickable { AppearanceState.resetAppearance(context) }
                        .padding(vertical = 11.dp)
                        .semantics { contentDescription = "Reset appearance settings" }
                )
                Text(
                    "RESET MOTION",
                    color = SonicCoreColors.Error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                        .clickable { AppearanceState.resetMotion(context) }
                        .padding(vertical = 11.dp)
                        .semantics { contentDescription = "Reset motion settings" }
                )
            }
        }
        LimitNote(
            "Resetting appearance never touches audio settings, presets, permissions or diagnostics data.",
            customTone
        )
    }
}

/** Renders one sample card with the current appearance preferences. */
@Composable
fun AppearancePreviewCard(context: Context, modifier: Modifier = Modifier) {
    val accent = AppearanceState.accent.color
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppearanceState.corners.radius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SonicCoreColors.ElevatedSurface.copy(alpha = 0.94f),
                        SonicCoreColors.Surface.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                AppearanceState.border.width,
                accent.copy(alpha = 0.30f),
                RoundedCornerShape(AppearanceState.corners.radius)
            )
            .padding(horizontal = 14.dp, vertical = AppearanceState.density.verticalPadding)
    ) {
        Text(
            "PREVIEW",
            color = SonicCoreColors.SecondaryText,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            "Sample card",
            color = SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "Accent ${AppearanceState.accent.label} · glass " +
                listOf("Subtle", "Liquid", "Crystal").getOrElse(com.soniccore.app.appGlassState.value.coerceIn(0, 2)) { "Liquid" },
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

