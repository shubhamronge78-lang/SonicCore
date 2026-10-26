package com.soniccore.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SONICCORE — SHARED GLASS WIDGETS
 *
 * The controls and read-out blocks that sit *inside* a [GlassSurface]. They are
 * the second half of the design system: `GlassComponents.kt` owns the surfaces
 * and the chrome, this file owns the widgets.
 *
 * Every value here comes from `GlassTokens.kt`. Nothing in this file touches
 * DSP, capture, audio buffers, latency or the sample rate.
 */

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    depth: LiquidGlassDepth = LiquidGlassDepth.Floating,
    shape: Shape = RoundedCornerShape(GlassRadius.surface),
    contentPadding: Dp = GlassSpacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        depth = depth,
        shape = shape,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassSurface(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
fun LiquidGlassInset(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassInset(modifier = modifier, content = content)
}

/**
 * Glass button.
 *
 * Gains the states the old button was missing: pressed, focused, hovered and
 * disabled are all visible, and the target is at least [GlassSpacing.touchTarget]
 * tall so it stays reachable with large font scaling and on a small phone.
 */
@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    selected: Boolean = false,
    accentColor: Color? = null
) {
    val accent = accentColor ?: if (secondary) SonicCoreColors.Violet else SonicCoreColors.Cyan
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> SonicCoreColors.ElevatedSurface.copy(alpha = 0.45f)
            selected -> accent.copy(alpha = material.tintAlpha + 0.14f)
            pressed -> SonicCoreColors.ElevatedSurface.copy(alpha = 0.95f)
            hovered -> SonicCoreColors.ElevatedSurface.copy(alpha = 0.85f)
            else -> SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.54f)
        },
        label = "glassButtonContainer"
    )
    val contentColor = when {
        !enabled -> SonicCoreColors.SecondaryText.copy(alpha = 0.45f)
        else -> accent
    }
    val ringColor by animateColorAsState(
        targetValue = when {
            focused -> accent.copy(alpha = 0.85f)
            selected -> accent.copy(alpha = material.edgeAlpha * 1.3f)
            else -> SonicCoreColors.Border.copy(alpha = material.edgeAlpha * 0.6f)
        },
        label = "glassButtonRing"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = GlassSpacing.touchTarget),
        shape = RoundedCornerShape(GlassRadius.control),
        color = container,
        contentColor = contentColor,
        border = BorderStroke(1.dp, ringColor),
        shadowElevation = if (selected) GlassElevation.control else GlassElevation.flat
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false
) {
    LiquidGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        secondary = secondary,
        selected = secondary
    )
}

/**
 * Square glass icon button.
 *
 * Slightly wider than the stock 48dp target when it carries a glyph, and it
 * keeps its content description so TalkBack and Switch Access name it.
 */
@Composable
fun GlassIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> SonicCoreColors.ElevatedSurface.copy(alpha = 0.40f)
            pressed -> SonicCoreColors.ElevatedSurface.copy(alpha = 0.98f)
            else -> SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.72f)
        },
        label = "glassIconContainer"
    )
    val ringColor by animateColorAsState(
        targetValue = if (focused) SonicCoreColors.Cyan.copy(alpha = 0.85f)
        else SonicCoreColors.Border.copy(alpha = material.edgeAlpha * 0.6f),
        label = "glassIconRing"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .size(GlassSpacing.touchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(GlassRadius.control),
        color = container,
        contentColor = SonicCoreColors.PrimaryText,
        border = BorderStroke(1.dp, ringColor),
        shadowElevation = GlassElevation.control
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun LiquidGlassChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        selected = selected
    )
}

@Composable
fun LiquidGlassValueChip(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val accent = if (active) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(
                SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.82f)
            )
            .border(
                1.dp,
                accent.copy(alpha = material.edgeAlpha * 0.75f),
                RoundedCornerShape(GlassRadius.tile)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(label, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = if (active) SonicCoreColors.PrimaryText else SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Glass slider.
 *
 * The track is thick enough to read against a busy backdrop, the thumb has a
 * defined edge so it stays visible at the extremes of the range, and the
 * disabled state is genuinely greyed rather than merely motionless.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = GlassSpacing.touchTarget),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = SonicCoreColors.Cyan,
            activeTrackColor = SonicCoreColors.Cyan,
            inactiveTrackColor = SonicCoreColors.Border.copy(alpha = 0.28f),
            activeTickColor = SonicCoreColors.Background.copy(alpha = 0.55f),
            inactiveTickColor = SonicCoreColors.SecondaryText.copy(alpha = 0.45f),
            disabledThumbColor = SonicCoreColors.SecondaryText.copy(alpha = 0.5f),
            disabledActiveTrackColor = SonicCoreColors.SecondaryText.copy(alpha = 0.35f),
            disabledInactiveTrackColor = SonicCoreColors.Border.copy(alpha = 0.16f)
        )
    )
}

enum class IndicatorStatus {
    Active,
    Inactive,
    Warning,
    Error
}

@Composable
fun StatusIndicator(
    label: String,
    status: IndicatorStatus,
    modifier: Modifier = Modifier
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val color = when (status) {
        IndicatorStatus.Active -> SonicCoreColors.Success
        IndicatorStatus.Inactive -> SonicCoreColors.SecondaryText
        IndicatorStatus.Warning -> SonicCoreColors.Warning
        IndicatorStatus.Error -> SonicCoreColors.Error
    }
    val animatedColor by animateColorAsState(color, label = "statusIndicatorColor")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.pill))
            .background(
                SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.88f)
            )
            .border(1.dp, animatedColor.copy(alpha = material.edgeAlpha + 0.10f), RoundedCornerShape(GlassRadius.pill))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            // Never colour-only: the dot is decorative, the label carries the state.
            .semantics { contentDescription = "Status: $label" },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(animatedColor)
        )
        Text(
            text = label,
            color = SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Section header. Delegates to [GlassSectionHeader] so a header looks the same
 * wherever it is written — the app used to have two of these with different
 * type scales and no shared accent.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    GlassSectionHeader(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        action = action
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    accent: Color = SonicCoreColors.Cyan
) {
    GlassCard(modifier = modifier) {
        Text(
            text = label,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = accent,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DSPModuleCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SonicCoreColors.PrimaryText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            GlassSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
    }
}

@Composable
fun PresetCard(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val borderColor by animateColorAsState(
        if (selected) SonicCoreColors.Cyan.copy(alpha = material.edgeAlpha + 0.20f)
        else SonicCoreColors.Border.copy(alpha = material.edgeAlpha * 0.7f),
        label = "presetCardBorder"
    )
    GlassSurface(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = 15.dp,
        shape = RoundedCornerShape(GlassRadius.card),
        ringColor = borderColor,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.PrimaryText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                // Selection is stated, not only tinted.
                if (selected) Text("✓", color = SonicCoreColors.Cyan, fontSize = 14.sp)
            }
        }
    )
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val rowModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description != null) {
                Text(
                    text = description,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        trailing()
    }
}

@Composable
fun AudioMeterContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassInset(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(12.dp))
        }
        content()
    }
}
