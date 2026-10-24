package com.neon.eq.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.capture.CaptureEqService
import com.neon.eq.diagnostics.DeviceDiagnosticsManager
import com.neon.eq.diagnostics.OperationLog
import com.neon.eq.dsp.NeonDsp
import com.neon.eq.ui.category.CapabilityBadge
import com.neon.eq.ui.category.CapabilityLegend
import com.neon.eq.ui.category.SonicCoreCapability
import com.neon.eq.ui.category.SonicCoreDataRow
import com.neon.eq.ui.category.SonicCoreStatTile
import com.neon.eq.ui.category.SonicCoreToolHost
import com.neon.eq.ui.design.GlassCard
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors
import com.neon.eq.ui.navigation.SonicCoreCategory

/**
 * Audio → Presets.
 *
 * Built-in and custom profiles, one-tap apply, save, delete, import and export.
 * Every action goes through the host callbacks, which run the same atomic
 * engine paths the rest of the app uses — this screen never writes DSP state
 * directly.
 */
@Composable
fun AudioPresetsScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }

    val builtin = host.builtinPresetNames
    val custom = host.customPresetNames
    val matches = { name: String -> query.isBlank() || name.contains(query, ignoreCase = true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "Presets are applied through the same validated DSP path used everywhere else in SonicCore."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "CURRENT PROFILE",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                host.currentPreset,
                color = SonicCoreColors.Cyan,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetAction("SAVE CURRENT", Modifier.weight(1f)) {
                    saveName = host.currentPreset.takeUnless { it == "Custom" }.orEmpty()
                    showSave = true
                }
                PresetAction("IMPORT", Modifier.weight(1f)) { showImport = true }
                PresetAction("EXPORT", Modifier.weight(1f)) {
                    exportText = runCatching { host.presetsExportJson() }.getOrNull()
                }
            }
            status?.let {
                Text(
                    it,
                    color = SonicCoreColors.Success,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            exportText?.let { json ->
                Text(
                    "EXPORTED BUNDLE (${json.length} characters) — use SHARE to hand it to another device.",
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                PresetAction("SHARE EXPORTED BUNDLE", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    host.onShareText("SonicCore preset bundle", json)
                }
            }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
            Text(
                "PROFILE LIBRARY",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search profiles", color = SonicCoreColors.SecondaryText) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "BUILT-IN (${builtin.count(matches)})",
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
            builtin.filter(matches).forEach { name ->
                PresetRow(
                    name = name,
                    subtitle = if (name == host.currentPreset) "Currently active" else "Built-in profile",
                    active = name == host.currentPreset,
                    onApply = {
                        status = if (host.onApplyPreset(name)) "Applied “$name”." else "“$name” could not be applied."
                    }
                )
            }
            Text(
                "CUSTOM (${custom.count(matches)})",
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (custom.isEmpty()) {
                Text(
                    "No custom profiles yet — use SAVE CURRENT to store the live configuration.",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            custom.filter(matches).forEach { name ->
                PresetRow(
                    name = name,
                    subtitle = if (name == host.currentPreset) "Currently active" else "Custom profile",
                    active = name == host.currentPreset,
                    onApply = {
                        status = if (host.onApplyPreset(name)) "Applied “$name”." else "“$name” could not be applied."
                    },
                    onDelete = {
                        host.onDeleteCustomPreset(name)
                        status = "Deleted “$name”."
                    }
                )
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = SonicCoreColors.Surface,
            title = { Text("Save current profile", color = SonicCoreColors.PrimaryText) },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    singleLine = true,
                    label = { Text("Profile name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = saveName.trim()
                    if (name.isNotEmpty()) {
                        host.onSaveCustomPreset(name)
                        status = "Saved “$name”."
                    }
                    showSave = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = SonicCoreColors.Surface,
            title = { Text("Import profile bundle", color = SonicCoreColors.PrimaryText) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Paste exported JSON") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = runCatching { host.onImportPresets(importText.trim()) }.getOrDefault(0)
                    status = if (count > 0) "Imported $count profile(s)." else "Nothing could be imported from that text."
                    showImport = false
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PresetRow(
    name: String,
    subtitle: String,
    active: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) SonicCoreColors.Cyan.copy(alpha = 0.09f) else SonicCoreColors.Background.copy(alpha = 0.35f))
            .border(
                0.8.dp,
                if (active) SonicCoreColors.Cyan.copy(alpha = 0.35f) else SonicCoreColors.Border.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = SonicCoreColors.SecondaryText, fontSize = 9.sp)
        }
        if (onDelete != null) {
            Text(
                "DELETE",
                color = SonicCoreColors.Error,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Delete $name" }
            )
        }
        Text(
            if (active) "ACTIVE" else "APPLY",
            color = if (active) SonicCoreColors.Success else SonicCoreColors.Cyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !active, onClick = onApply)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = "Apply $name" }
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PresetAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = SonicCoreColors.Cyan,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .border(0.8.dp, SonicCoreColors.Cyan.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label }
    )
}

/**
 * Audio → Audio Diagnostics.
 *
 * Session state, only the counters the engine actually publishes, and the last
 * error. Nothing here is inferred or estimated.
 */
@Composable
fun AudioDiagnosticsScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    val serviceHealth = remember { runCatching { DeviceDiagnosticsManager.getServiceHealth(context) }.getOrNull() }
    val log = remember { OperationLog.snapshot() }

    val sessionState = when {
        !NeonDsp.available -> "DSP UNAVAILABLE"
        CaptureEqService.running && CaptureEqService.bypass -> "BYPASS"
        CaptureEqService.running && CaptureEqService.paused -> "PAUSED"
        CaptureEqService.running -> "RUNNING"
        else -> "IDLE"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapabilityLegend(
            if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
            NeonDsp.loadError ?: "Counters come from the native DSP bridge and the running capture session."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "AUDIO SESSION",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SonicCoreStatTile("State", sessionState, SonicCoreCategory.AUDIO.tone, modifier = Modifier.weight(1f))
                SonicCoreStatTile(
                    "Sample rate",
                    if (CaptureEqService.running && CaptureEqService.captureSampleRate > 0)
                        "${CaptureEqService.captureSampleRate / 1000} kHz" else "Idle",
                    SonicCoreCategory.AUDIO.tone,
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    "Buffer",
                    CaptureEqService.bufferMode,
                    SonicCoreCategory.AUDIO.tone,
                    modifier = Modifier.weight(1f)
                )
            }
            SonicCoreDataRow(
                label = "Frames captured",
                value = CaptureEqService.framesCaptured.toString(),
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Frames output",
                value = CaptureEqService.framesDone.toString(),
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Underruns",
                value = CaptureEqService.underruns.toString(),
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Clips / NaN events",
                value = runCatching { "${NeonDsp.clipCount()} / ${NeonDsp.nanCount()}" }.getOrDefault("Not readable"),
                capability = if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED
            )
            SonicCoreDataRow(
                label = "DSP CPU",
                value = if (CaptureEqService.running && CaptureEqService.dspLoadPct.isFinite())
                    "%.1f%%".format(CaptureEqService.dspLoadPct) else "Not running",
                capability = if (CaptureEqService.running) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                label = "Total latency",
                value = if (CaptureEqService.totalLatencyMs > 0)
                    "~%.1f ms".format(CaptureEqService.totalLatencyMs) else "Not measurable",
                capability = if (CaptureEqService.running) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            val lastError = CaptureEqService.lastError
            SonicCoreDataRow(
                label = "Last error",
                value = lastError ?: "None reported",
                capability = if (lastError == null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED
            )
            serviceHealth?.let { health ->
                SonicCoreDataRow(
                    label = "Output route",
                    value = health.outputRoute.ifBlank { "Not detected" },
                    capability = if (health.outputRoute.isBlank()) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE
                )
                SonicCoreDataRow(
                    label = "DSP bridge",
                    value = if (health.dspAvailable) "Loaded" else "Unavailable",
                    capability = if (health.dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "RECENT OPERATIONS (${log.size})",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            if (log.isEmpty()) {
                Text(
                    "No operations recorded yet in this process.",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                log.take(8).forEach { entry ->
                    SonicCoreDataRow(
                        label = "${entry.timeLabel} · ${entry.category}",
                        value = entry.message,
                        note = entry.result.label
                    )
                }
            }
            Text(
                "EXPORT REDACTED AUDIO REPORT",
                color = SonicCoreColors.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                    .clickable {
                        report = runCatching { DeviceDiagnosticsManager.generateRedactedReport(context) }.getOrNull()
                        OperationLog.record(
                            category = "Diagnostics",
                            message = "Audio report generated",
                            result = if (report != null) com.neon.eq.diagnostics.OperationResult.SUCCESS
                            else com.neon.eq.diagnostics.OperationResult.FAILED
                        )
                    }
                    .padding(horizontal = 11.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Generate a redacted audio report" }
            )
            report?.let { text ->
                Text(
                    "REPORT READY · ${text.length} characters · sensitive values redacted",
                    color = SonicCoreColors.Success,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "SHARE REPORT",
                    color = SonicCoreColors.PrimaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SonicCoreColors.ElevatedSurface.copy(alpha = 0.8f))
                        .clickable { host.onShareText("SonicCore audio diagnostics", text) }
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                )
            }
        }
        LimitNote2("SonicCore reports only counters it measures. Metrics a device does not expose stay “Not readable” rather than being estimated.")
    }
}

@Composable
private fun LimitNote2(text: String) {
    com.neon.eq.ui.category.LimitNote(text, SonicCoreCategory.AUDIO.tone)
}
