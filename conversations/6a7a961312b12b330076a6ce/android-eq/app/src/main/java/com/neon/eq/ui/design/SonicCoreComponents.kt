package com.neon.eq.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(20.dp)
private val CapsuleShape = RoundedCornerShape(50)

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    depth: LiquidGlassDepth = LiquidGlassDepth.Floating,
    shape: Shape = CardShape,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    val isInset = depth == LiquidGlassDepth.Inset
    val surfaceAlpha = if (isInset) (material.surfaceAlpha + 0.14f).coerceAtMost(0.94f)
        else material.surfaceAlpha
    val shadowElevation = if (isInset) 1.dp else material.shadowElevationDp.dp
    val surface = SonicCoreColors.Surface
    val tint = SonicCoreColors.Cyan
    val edge = SonicCoreColors.Border
    Column(
        modifier = modifier
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = surfaceAlpha),
                        SonicCoreColors.ElevatedSurface.copy(alpha = surfaceAlpha * 0.76f),
                        surface.copy(alpha = surfaceAlpha * 0.94f)
                    )
                )
            )
            .drawWithCache {
                val reflectionHeight = 26.dp.toPx().coerceAtMost(size.height)
                val reflection = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = material.reflectionAlpha),
                        tint.copy(alpha = material.tintAlpha),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = reflectionHeight
                )
                val topEdge = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = material.reflectionAlpha * 0.9f),
                        tint.copy(alpha = material.edgeAlpha),
                        Color.White.copy(alpha = material.reflectionAlpha * 0.55f),
                        Color.Transparent
                    )
                )
                val sideEdge = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = material.edgeAlpha),
                        edge.copy(alpha = material.edgeAlpha * 0.48f),
                        Color.Transparent
                    )
                )
                val glow = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = material.tintAlpha),
                        SonicCoreColors.Violet.copy(alpha = material.tintAlpha * 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.width * 0.78f
                )
                onDrawWithContent {
                    drawRect(reflection, size = Size(size.width, reflectionHeight))
                    drawRect(glow)
                    drawContent()
                    drawRoundRect(
                        brush = sideEdge,
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    drawLine(
                        brush = topEdge,
                        start = Offset(20.dp.toPx(), 0.8.dp.toPx()),
                        end = Offset(size.width - 20.dp.toPx(), 0.8.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .padding(contentPadding),
        content = content
    )
}

enum class LiquidGlassDepth {
    Floating,
    Inset
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
    LiquidGlassSurface(
        modifier = modifier,
        depth = LiquidGlassDepth.Inset,
        contentPadding = 12.dp,
        shape = RoundedCornerShape(16.dp),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassPanel(modifier = modifier, content = content)
}

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
    val container by animateColorAsState(
        targetValue = if (enabled && selected) accent.copy(alpha = material.tintAlpha + 0.08f)
        else SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.54f),
        label = "glassButtonContainer"
    )
    val buttonContentColor = if (enabled) accent else SonicCoreColors.SecondaryText
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        shape = CapsuleShape,
        color = container,
        contentColor = buttonContentColor,
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = material.reflectionAlpha * if (selected) 1.2f else 0.62f),
                    accent.copy(alpha = material.edgeAlpha * if (selected) 1.3f else 0.48f)
                )
            )
        ),
        shadowElevation = if (selected) 5.dp else 1.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = buttonContentColor
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

@Composable
fun GlassIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(17.dp),
        color = SonicCoreColors.ElevatedSurface.copy(
            alpha = material.surfaceAlpha * 0.72f
        ),
        contentColor = SonicCoreColors.PrimaryText,
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = material.reflectionAlpha),
                    SonicCoreColors.Border.copy(alpha = material.edgeAlpha * 0.6f)
                )
            )
        ),
        shadowElevation = 3.dp
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
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.82f),
                        SonicCoreColors.Surface.copy(alpha = material.surfaceAlpha * 0.72f)
                    )
                )
            )
            .border(
                BorderStroke(0.8.dp, accent.copy(alpha = material.edgeAlpha * 0.65f)),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(label, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = if (active) SonicCoreColors.PrimaryText else SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
        }
}

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
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = SonicCoreColors.Cyan,
            activeTrackColor = SonicCoreColors.Cyan,
            inactiveTrackColor = SonicCoreColors.Border.copy(alpha = 0.17f),
            activeTickColor = SonicCoreColors.Background.copy(alpha = 0.45f),
            inactiveTickColor = SonicCoreColors.SecondaryText.copy(alpha = 0.45f)
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
            .clip(RoundedCornerShape(50))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SonicCoreColors.ElevatedSurface.copy(alpha = material.surfaceAlpha * 0.88f),
                        SonicCoreColors.Surface.copy(alpha = material.surfaceAlpha * 0.80f)
                    )
                )
            )
            .border(0.8.dp, animatedColor.copy(alpha = material.edgeAlpha), RoundedCornerShape(50))
            .drawWithCache {
                val reflection = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = material.reflectionAlpha * 0.8f),
                        Color.Transparent
                    )
                )
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        reflection,
                        Offset(12.dp.toPx(), 0.5.dp.toPx()),
                        Offset(size.width - 12.dp.toPx(), 0.5.dp.toPx()),
                        0.8.dp.toPx()
                    )
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
            color = animatedColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (action != null) action()
    }
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
            verticalAlignment = Alignment.CenterVertically
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
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SonicCoreColors.Background,
                    checkedTrackColor = SonicCoreColors.Cyan,
                    uncheckedThumbColor = SonicCoreColors.SecondaryText,
                    uncheckedTrackColor = SonicCoreColors.ElevatedSurface,
                    uncheckedBorderColor = SonicCoreColors.Border.copy(alpha = 0.17f)
                )
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
        if (selected) SonicCoreColors.Cyan.copy(alpha = material.edgeAlpha * 1.8f)
        else SonicCoreColors.Border.copy(alpha = material.edgeAlpha * 0.65f),
        label = "presetCardBorder"
    )
    LiquidGlassSurface(
        modifier = modifier
            .shadow(if (selected) 8.dp else 2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .border(0.8.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentPadding = 15.dp,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = name,
            color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
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
                style = MaterialTheme.typography.bodyLarge
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
    LiquidGlassInset(modifier = modifier.fillMaxWidth()) {
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
