package com.neon.eq.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.ui.design.DSPModuleCard
import com.neon.eq.ui.design.GlassCard
import com.neon.eq.ui.design.SectionHeader
import com.neon.eq.ui.design.SonicCoreColors

@Composable
fun HomeCaptureStatusCard(
    hasRecentPcmData: Boolean,
    modifier: Modifier = Modifier
) {
    var explanationExpanded by remember { mutableStateOf(false) }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "CAPTURE STATUS",
            subtitle = if (hasRecentPcmData) "Recent PCM data observed" else "No current PCM data observed"
        )
        Text(
            if (explanationExpanded) "HOW CAPTURE WORKS ▴" else "HOW CAPTURE WORKS ▾",
            color = SonicCoreColors.Cyan,
            modifier = Modifier
                .padding(top = 9.dp)
                .clip(RoundedCornerShape(50))
                .clickable { explanationExpanded = !explanationExpanded }
                .padding(vertical = 3.dp)
                .semantics { contentDescription = "Expand capture explanation" },
            style = MaterialTheme.typography.labelMedium
        )
        if (explanationExpanded) {
            Text(
                "SonicCore captures eligible Android playback, processes captured PCM, and sends the processed copy to its own AudioTrack. Android may also continue playing the source application's original output.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

data class HomeQuickDspSettings(
    val eqEnabled: Boolean,
    val bassEnabled: Boolean,
    val trebleEnabled: Boolean,
    val stereoEnabled: Boolean,
    val limiterEnabled: Boolean
)

@Composable
fun HomeQuickDspCard(
    settings: HomeQuickDspSettings?,
    dspEnabled: Boolean,
    onDspEnabledChange: (Boolean) -> Unit,
    onProcessingSettingsChange: (HomeQuickDspSettings) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onExpandedChange(!expanded)
                    expanded = !expanded
                }
        ) {
            SectionHeader(title = "QUICK DSP", subtitle = "Native chain controls")
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "▲" else "▼", color = SonicCoreColors.SecondaryText)
        }
        if (expanded && settings != null) {
            Text(
                "Toggles map to the existing native chain. Values are restored from the current configuration.",
                fontSize = 9.sp,
                color = SonicCoreColors.SecondaryText,
                modifier = Modifier.padding(top = 5.dp)
            )
            DSPModuleCard(
                title = "DSP",
                description = "Captured-output bypass",
                enabled = dspEnabled,
                onEnabledChange = onDspEnabledChange,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
            )
            Row(modifier = Modifier.padding(top = 6.dp)) {
                HomeControlChip(if (settings.eqEnabled) "EQ ON" else "EQ OFF") {
                    onProcessingSettingsChange(settings.copy(eqEnabled = !settings.eqEnabled))
                }
                HomeControlChip(if (settings.bassEnabled) "BASS ON" else "BASS OFF") {
                    onProcessingSettingsChange(settings.copy(bassEnabled = !settings.bassEnabled))
                }
                HomeControlChip(if (settings.trebleEnabled) "TREBLE ON" else "TREBLE OFF") {
                    onProcessingSettingsChange(settings.copy(trebleEnabled = !settings.trebleEnabled))
                }
            }
            Row(modifier = Modifier.padding(top = 6.dp)) {
                HomeControlChip(if (settings.stereoEnabled) "STEREO ON" else "STEREO OFF") {
                    onProcessingSettingsChange(settings.copy(stereoEnabled = !settings.stereoEnabled))
                }
                HomeControlChip(if (settings.limiterEnabled) "LIMITER ON" else "LIMITER OFF") {
                    onProcessingSettingsChange(settings.copy(limiterEnabled = !settings.limiterEnabled))
                }
            }
        }
    }
}
