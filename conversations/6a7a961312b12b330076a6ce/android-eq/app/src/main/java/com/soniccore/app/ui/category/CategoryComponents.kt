package com.soniccore.app.ui.category

import com.soniccore.app.ui.design.GlassRadius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.capabilities.SupportState
import com.soniccore.app.shizuku.ShizukuAvailability
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.SonicCoreTone
import com.soniccore.app.ui.design.color
import com.soniccore.app.ui.navigation.SonicCoreTool
import com.soniccore.app.ui.navigation.color

/**
 * The single capability vocabulary used by every SonicCore screen.
 *
 * A feature is never described as working unless the value behind it was
 * actually read from the platform; anything else maps to NEEDS_PERMISSION,
 * BLOCKED, UNSUPPORTED, UNKNOWN or NOT_IMPLEMENTED and is labelled as such.
 */
enum class SonicCoreCapability(val label: String, val guidance: String) {
    AVAILABLE("Available", "Readable and usable on this device right now."),
    NEEDS_PERMISSION("Needs permission", "Readable once the user grants the required access."),
    BLOCKED("Blocked", "The platform or another app currently prevents this."),
    UNSUPPORTED("Unsupported", "This device or Android version does not expose it."),
    UNKNOWN("Unknown", "No value could be read, so nothing is claimed."),
    NOT_IMPLEMENTED("Not implemented", "SonicCore has no verified implementation for this yet.");

    val color: Color
        get() = when (this) {
            AVAILABLE -> SonicCoreColors.Success
            NEEDS_PERMISSION -> SonicCoreColors.Cyan
            BLOCKED -> SonicCoreColors.Warning
            UNSUPPORTED -> SonicCoreColors.SecondaryText
            UNKNOWN -> SonicCoreColors.Warning
            NOT_IMPLEMENTED -> SonicCoreColors.Violet
        }

    val glyph: String
        get() = when (this) {
            AVAILABLE -> "✓"
            NEEDS_PERMISSION -> "🔑"
            BLOCKED -> "⛔"
            UNSUPPORTED -> "∅"
            UNKNOWN -> "?"
            NOT_IMPLEMENTED -> "◌"
        }
}

fun SupportState.toSonicCoreCapability(): SonicCoreCapability = when (this) {
    SupportState.SUPPORTED -> SonicCoreCapability.AVAILABLE
    SupportState.PARTIALLY_SUPPORTED -> SonicCoreCapability.BLOCKED
    SupportState.PERMISSION_REQUIRED -> SonicCoreCapability.NEEDS_PERMISSION
    SupportState.RESTRICTED -> SonicCoreCapability.BLOCKED
    SupportState.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
}

fun ShizukuAvailability.toSonicCoreCapability(): SonicCoreCapability = when (this) {
    ShizukuAvailability.AVAILABLE -> SonicCoreCapability.AVAILABLE
    ShizukuAvailability.REQUIRES_PERMISSION -> SonicCoreCapability.NEEDS_PERMISSION
    ShizukuAvailability.REQUIRES_CONNECTION -> SonicCoreCapability.BLOCKED
    ShizukuAvailability.NOT_INSTALLED -> SonicCoreCapability.BLOCKED
    ShizukuAvailability.NOT_SUPPORTED -> SonicCoreCapability.UNSUPPORTED
    ShizukuAvailability.NOT_IMPLEMENTED -> SonicCoreCapability.NOT_IMPLEMENTED
}

@Composable
fun CapabilityBadge(
    capability: SonicCoreCapability,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val tint = capability.color
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 2.dp else 4.dp)
            .semantics { contentDescription = "State: ${capability.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(capability.glyph, color = tint, fontSize = if (compact) 9.sp else 11.sp)
        Text(
            capability.label.uppercase(),
            color = tint,
            fontSize = if (compact) 8.sp else 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Label + value row used by the metric-heavy workspaces. */
@Composable
fun SonicCoreDataRow(
    label: String,
    value: String,
    capability: SonicCoreCapability? = null,
    note: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.2f)
            )
        }
        if (capability != null) {
            CapabilityBadge(capability, modifier = Modifier.padding(top = 3.dp), compact = true)
        }
        if (note != null) {
            Text(
                note,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** Small numeric tile used by Device Center and Audio Diagnostics. */
@Composable
fun SonicCoreStatTile(
    label: String,
    value: String,
    tone: SonicCoreTone,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    val tint = tone.color()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.control))
            .background(SonicCoreColors.Background.copy(alpha = 0.55f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(GlassRadius.control))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(
            label.uppercase(),
            color = SonicCoreColors.SecondaryText,
            fontSize = 9.sp,
            letterSpacing = 0.7.sp
        )
        Text(
            value,
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (footer != null) {
            Text(
                footer,
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Explains what a screen can and cannot do, using the shared vocabulary. */
@Composable
fun CapabilityLegend(capability: SonicCoreCapability, extra: String? = null) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
        shape = RoundedCornerShape(GlassRadius.control)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityBadge(capability)
            Text(
                extra ?: capability.guidance,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Tool row inside a category hub's internal navigation list. */
@Composable
fun SonicCoreToolRow(
    tool: SonicCoreTool,
    capability: SonicCoreCapability?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.control))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${tool.title}. ${tool.summary}" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(GlassRadius.tile))
                .background(tool.category.color().copy(alpha = 0.13f))
                .border(1.dp, tool.category.color().copy(alpha = 0.28f), RoundedCornerShape(GlassRadius.tile)),
            contentAlignment = Alignment.Center
        ) {
            Text(tool.icon, color = tool.category.color(), fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tool.title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                tool.summary,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (capability != null) CapabilityBadge(capability, compact = true)
        Text("›", color = tool.category.color(), style = MaterialTheme.typography.titleMedium)
    }
}

/** Titled group used by the Settings workspace. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** Selectable option row: label, description and a selected marker. */
@Composable
fun SonicCoreChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    swatch: Color? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(
                if (selected) SonicCoreColors.Cyan.copy(alpha = 0.09f) else Color.Transparent
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) SonicCoreColors.Cyan.copy(alpha = 0.35f) else SonicCoreColors.Border.copy(alpha = 0.15f),
                shape = RoundedCornerShape(GlassRadius.tile)
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = title
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (swatch != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(swatch)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(7.dp))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) Text("✓", color = SonicCoreColors.Cyan, fontSize = 14.sp)
    }
    Spacer(Modifier.height(6.dp))
}

/** Card outline honouring the user's card-style preferences. */
@Composable
fun appearanceBorder(selected: Boolean = false): BorderStroke {
    val tint = if (selected) SonicCoreColors.Cyan else SonicCoreColors.Border
    return BorderStroke(
        width = com.soniccore.app.ui.design.AppearanceState.border.width,
        color = tint.copy(alpha = if (selected) 0.45f else 0.16f)
    )
}

@Suppress("UnusedReceiverParameter")
@Composable
fun ColumnScope.SonicCoreInlineNote(text: String) {
    Text(
        text,
        color = SonicCoreColors.SecondaryText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Full-width note strip, used to state limits honestly. */
@Composable
fun LimitNote(text: String, tone: SonicCoreTone = SonicCoreTone.SLATE) {
    val tint = tone.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.20f), RoundedCornerShape(GlassRadius.tile))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ⓘ", color = tint, fontSize = 12.sp)
        Text(
            text,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TileRow(vararg tiles: Pair<String, String>, tone: SonicCoreTone, footers: List<String?> = emptyList()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEachIndexed { index, (label, value) ->
            SonicCoreStatTile(
                label = label,
                value = value,
                tone = tone,
                footer = footers.getOrNull(index),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun HorizontalGap() {
    Spacer(Modifier.width(8.dp))
}
