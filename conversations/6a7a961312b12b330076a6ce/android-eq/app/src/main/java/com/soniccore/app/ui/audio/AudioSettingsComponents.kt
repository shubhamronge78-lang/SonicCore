package com.soniccore.app.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.GlassChipKind
import com.soniccore.app.LiquidChip
import com.soniccore.app.LiquidToggle
import com.soniccore.app.ui.category.CapabilityBadge
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.GlassSlider
import com.soniccore.app.ui.design.LiquidGlassButton
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.navigation.SonicCoreAudioGroup

/**
 * The shared settings-style vocabulary of every Audio Center screen.
 *
 * One card shape, one heading treatment, one slider row, one switch row, one
 * reset affordance and one expandable "advanced" group — so the thirteen Audio
 * screens read as one workspace instead of thirteen different pages, while each
 * screen stays a short list of clearly labelled groups.
 *
 * Everything here is presentation only: these composables never read or write
 * the engine. A screen hands a new value back through its host callbacks.
 */

/** Titled, optionally described group of settings — the Audio card shape. */
@Composable
fun AudioSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = SonicCoreColors.Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        if (description != null) {
            Text(
                description,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Section heading used inside a grouped, multi-section Audio screen. */
@Composable
fun AudioGroupHeader(
    group: SonicCoreAudioGroup,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(
            "${group.icon}  ${group.title.uppercase()}",
            color = SonicCoreColors.Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            group.summary,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Slider with a value label, a screen-reader description and an optional
 * per-control reset. A disabled control says why instead of looking broken.
 */
@Composable
fun AudioSliderSetting(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    capability: SonicCoreCapability? = null,
    disabledNote: String? = null,
    onReset: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (onReset != null) {
                Text(
                    "RESET",
                    color = if (enabled) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = enabled, onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Reset $label" }
                )
            }
            Text(
                valueText,
                color = if (enabled) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.55f)
                .semantics { contentDescription = "$label, $valueText" },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled
        )
        if (capability != null) {
            CapabilityBadge(capability, modifier = Modifier.padding(top = 2.dp), compact = true)
        }
        if (disabledNote != null && !enabled) {
            Text(
                disabledNote,
                color = SonicCoreColors.Warning,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Switch row with a description and an explicit unavailable state. */
@Composable
fun AudioSwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    disabledNote: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    description,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (disabledNote != null && !enabled) {
                Text(
                    disabledNote,
                    color = SonicCoreColors.Warning,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        LiquidToggle(
            checked = checked,
            modifier = Modifier.semantics {
                this.contentDescription = title
                stateDescription = if (checked) "On" else "Off"
            },
            onCheckedChange = { next -> if (enabled) onCheckedChange(next) }
        )
    }
}

/**
 * Collapsed group for controls a user rarely needs. State is hoisted so a
 * screen can decide what "advanced" means for itself.
 */
@Composable
fun AudioAdvancedSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable { onExpandedChange(!expanded) }
                .semantics {
                    contentDescription = "Advanced settings: $title"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (description != null) {
                    Text(
                        description,
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                if (expanded) "▴" else "▾",
                color = SonicCoreColors.Cyan,
                fontSize = 13.sp
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** One screen-level action: save, reset, share or open. */
data class AudioAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val active: Boolean = false
)

/** Compact row of screen-level actions with proper 48 dp touch targets. */
@Composable
fun AudioActionRow(
    actions: List<AudioAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            LiquidGlassButton(
                text = action.label,
                onClick = action.onClick,
                modifier = Modifier.weight(1f),
                enabled = action.enabled,
                selected = action.active
            )
        }
    }
}

/**
 * The Audio Center sub-navigation: the six groups as compact chips.
 *
 * Phone layouts show one group's tools at a time behind these chips; wider
 * layouts show every group as a list instead (see `AudioCategoryHub`).
 */
@Composable
fun AudioGroupTabs(
    selected: SonicCoreAudioGroup,
    onSelect: (SonicCoreAudioGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SonicCoreAudioGroup.entries.forEach { group ->
            LiquidChip(
                label = group.label,
                glyph = group.icon,
                kind = if (group == selected) GlassChipKind.Selected else GlassChipKind.Neutral,
                modifier = Modifier.semantics {
                    contentDescription = "${group.title}. ${group.summary}"
                    stateDescription = if (group == selected) "Selected" else "Not selected"
                },
                onClick = { onSelect(group) }
            )
        }
    }
}

/** Full-width notice strip for a limit the screen cannot work around. */
@Composable
fun AudioLimitNote(text: String) {
    com.soniccore.app.ui.category.LimitNote(
        text = text,
        tone = com.soniccore.app.ui.design.SonicCoreTone.CYAN
    )
}
