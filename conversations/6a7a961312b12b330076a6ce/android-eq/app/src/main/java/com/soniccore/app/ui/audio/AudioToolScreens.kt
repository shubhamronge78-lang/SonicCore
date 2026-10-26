package com.soniccore.app.ui.audio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.soniccore.app.loudness.Loudness
import com.soniccore.app.loudness.WavPcm
import com.soniccore.app.ui.design.GlassRadius
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
import com.soniccore.app.capture.AudioCapabilityManager
import com.soniccore.app.capture.CapState
import com.soniccore.app.capture.CaptureEqService
import com.soniccore.app.diagnostics.DeviceDiagnosticsManager
import com.soniccore.app.diagnostics.OperationLog
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.engine.AutoEqImport
import com.soniccore.app.engine.AutoEqProfile
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreStatTile
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.navigation.SonicCoreCategory

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
    // AutoEQ's own files carry no model name, so the import takes one.
    var importName by remember { mutableStateOf("") }
    // The reviewed profile, and the reason a review was refused. Both are pure
    // state: while a review is on screen nothing has been stored or applied yet,
    // which is what makes Cancel safe.
    var importReview by remember { mutableStateOf<AutoEqImport.Review?>(null) }
    var importProblem by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    // Read the .txt file AutoEQ publishes, capped at the parser's own limit so an
    // oversized pick is refused by the same rule as an oversized paste.
    val pickProfileFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(AutoEqProfile.MAX_TEXT_CHARS + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(buffer, 0, read, Charsets.UTF_8)
                }
            }.getOrNull()
            if (text == null) {
                importProblem = "That file could not be read. Paste the profile text instead."
            } else {
                importText = text
                if (importName.isBlank()) {
                    // AutoEQ names a profile after the model, with the format as a
                    // suffix: "Sennheiser HD 650 ParametricEQ.txt".
                    importName = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringBeforeLast('.')
                        ?.removeSuffix(" ParametricEQ")
                        ?.removeSuffix(" GraphicEQ")
                        ?.trim()
                        ?.take(64)
                        .orEmpty()
                }
                importProblem = null
                importReview = null
            }
        }
    }

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
        AudioSection(
            title = "Current profile",
            description = "The profile the engine is running right now."
        ) {
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

        AudioSection(
            title = "Profile library",
            description = "Built-in and custom profiles. Applying one goes through the same validated DSP path as everywhere else."
        ) {
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
        // Step 1 pastes or opens a profile and changes nothing; step 2 shows what
        // was parsed and offers the two deliberate actions. Cancelling at either
        // step leaves the active sound and every stored profile exactly as they were.
        val reviewed = importReview
        val plan = (reviewed as? AutoEqImport.Ready)?.plan
        AlertDialog(
            onDismissRequest = {
                showImport = false
                importReview = null
                importProblem = null
            },
            containerColor = SonicCoreColors.Surface,
            title = {
                Text(
                    if (plan == null) "Import profile" else "Review AutoEQ profile",
                    color = SonicCoreColors.PrimaryText
                )
            },
            text = {
                if (plan == null) {
                    Column {
                        Text(
                            "Paste a SonicCore exported bundle (JSON), or paste or open an AutoEQ ParametricEQ / GraphicEQ profile. Nothing is stored or applied until you confirm on the next step.",
                            color = SonicCoreColors.SecondaryText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importProblem = null },
                            label = { Text("Paste JSON or AutoEQ profile") },
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = importName,
                            onValueChange = { importName = it },
                            singleLine = true,
                            label = { Text("Name (AutoEQ profile)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = {
                                pickProfileFile.launch(
                                    arrayOf("text/plain", "application/octet-stream")
                                )
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        ) { Text("Open a .txt profile") }
                        importProblem?.let {
                            Text(
                                it,
                                color = SonicCoreColors.Warning,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            "An AutoEQ curve is stored as a custom profile and applied by the existing equalizer; its preamp is written to the native preamp parameter once, only when you apply.",
                            color = SonicCoreColors.SecondaryText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                } else {
                    Column {
                        AutoEqImport.reviewLines(plan).forEach { line ->
                            Text(
                                "• $line",
                                color = SonicCoreColors.PrimaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            AutoEqImport.scope(plan),
                            color = SonicCoreColors.SecondaryText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (plan == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            val result = runCatching {
                                host.onReviewAutoEqProfile(importName, importText)
                            }.getOrElse {
                                AutoEqImport.Unsupported("That profile could not be read.")
                            }
                            when (result) {
                                is AutoEqImport.Ready -> {
                                    importReview = result
                                    importProblem = null
                                }
                                is AutoEqImport.Unsupported -> {
                                    importReview = null
                                    importProblem = result.reason
                                }
                                AutoEqImport.Empty -> {
                                    importReview = null
                                    importProblem = "Paste an AutoEQ ParametricEQ or GraphicEQ profile (or open its file) first."
                                }
                            }
                        }) { Text("Review") }
                        TextButton(onClick = {
                            val count = runCatching { host.onImportPresets(importText.trim()) }.getOrDefault(0)
                            status = if (count > 0) "Imported $count profile(s)." else "Nothing could be imported from that text."
                            showImport = false
                            importReview = null
                        }) { Text("Import bundle") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            status = host.onApplyAutoEqProfile(reviewed, false)
                            showImport = false
                            importReview = null
                        }) { Text("Save profile") }
                        TextButton(onClick = {
                            status = host.onApplyAutoEqProfile(reviewed, true)
                            showImport = false
                            importReview = null
                        }) { Text("Apply now") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (plan == null) showImport = false
                    importReview = null
                    importProblem = null
                }) { Text(if (plan == null) "Cancel" else "Back") }
            }
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
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(if (active) SonicCoreColors.Cyan.copy(alpha = 0.09f) else SonicCoreColors.Background.copy(alpha = 0.35f))
            .border(
                0.8.dp,
                if (active) SonicCoreColors.Cyan.copy(alpha = 0.35f) else SonicCoreColors.Border.copy(alpha = 0.12f),
                RoundedCornerShape(GlassRadius.tile)
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
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .border(0.8.dp, SonicCoreColors.Cyan.copy(alpha = 0.28f), RoundedCornerShape(GlassRadius.tile))
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
    var testResult by remember { mutableStateOf<String?>(null) }
    // The offline loudness report. Kept as lines so a refusal reads exactly like a
    // measurement: both are sentences this screen shows as they are.
    var loudnessLines by remember { mutableStateOf<List<String>?>(null) }
    val pickWavFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loudnessLines = measureWavFile(context, uri)
    }
    val serviceHealth = remember { runCatching { DeviceDiagnosticsManager.getServiceHealth(context) }.getOrNull() }
    val log = remember { OperationLog.snapshot() }
    // The probes are read once per screen visit: the DSP probe runs the native
    // self-test, so it must never sit in a recomposition path.
    val probes = remember(context) {
        listOf(
            "AudioPlaybackCapture" to runCatching { AudioCapabilityManager.playbackCapture() }.getOrDefault(CapState.UNKNOWN),
            "MediaProjection" to runCatching { AudioCapabilityManager.mediaProjection(context) }.getOrDefault(CapState.UNKNOWN),
            "AudioRecord" to runCatching { AudioCapabilityManager.audioRecord() }.getOrDefault(CapState.UNKNOWN),
            "AudioTrack" to runCatching { AudioCapabilityManager.audioTrack(CaptureEqService.captureSampleRate) }.getOrDefault(CapState.UNKNOWN),
            "Native DSP bridge" to runCatching { AudioCapabilityManager.dsp() }.getOrDefault(CapState.UNKNOWN)
        )
    }

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
        AudioSection(
            title = "Audio session",
            description = "Counters published by the native DSP bridge and the running capture session."
        ) {
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

        AudioSection(
            title = "Recent operations (${log.size})",
            description = "The bounded, in-process operation log."
        ) {
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
                    .clip(RoundedCornerShape(GlassRadius.tile))
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                    .clickable {
                        report = runCatching { DeviceDiagnosticsManager.generateRedactedReport(context) }.getOrNull()
                        OperationLog.record(
                            category = "Diagnostics",
                            message = "Audio report generated",
                            result = if (report != null) com.soniccore.app.diagnostics.OperationResult.SUCCESS
                            else com.soniccore.app.diagnostics.OperationResult.FAILED
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
                        .clip(RoundedCornerShape(GlassRadius.tile))
                        .background(SonicCoreColors.ElevatedSurface.copy(alpha = 0.8f))
                        .clickable { host.onShareText("SonicCore audio diagnostics", text) }
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                )
            }
        }
        AudioSection(
            title = "Compatibility probes",
            description = "Each entry is read from the platform itself — a capability is never presented as a live result."
        ) {
            probes.forEach { (name, state) ->
                SonicCoreDataRow(
                    label = name,
                    value = state.name.replace('_', ' '),
                    capability = state.toAudioCapability()
                )
            }
        }

        AudioSection(
            title = "Test results",
            description = "The project's own compatibility test: platform support, current route, capture and processing state."
        ) {
            AudioActionRow(
                listOf(
                    AudioAction(
                        label = "RUN AUDIO SELF-TEST",
                        onClick = {
                            testResult = runCatching { AudioCapabilityManager.runFullTest(context) }.getOrNull()
                            OperationLog.record(
                                category = "Audio",
                                message = "Audio compatibility self-test run",
                                result = if (testResult != null) com.soniccore.app.diagnostics.OperationResult.SUCCESS
                                else com.soniccore.app.diagnostics.OperationResult.FAILED
                            )
                        }
                    )
                )
            )
            val result = testResult
            if (result == null) {
                Text(
                    "No test run in this session yet.",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text(
                    result,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                AudioActionRow(
                    actions = listOf(
                        AudioAction(
                            label = "SHARE TEST RESULT",
                            onClick = { host.onShareText("SonicCore audio self-test", result) }
                        )
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        AudioSection(
            title = "Loudness (offline)",
            description = "Integrated loudness, loudness range and true peak of one 16-bit PCM WAV file. " +
                "Measured after the fact, in the module's own native library — never during playback."
        ) {
            Text(
                Loudness.SCOPE,
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            AudioActionRow(
                actions = listOf(
                    AudioAction(
                        label = "ANALYZE A .WAV FILE",
                        onClick = {
                            loudnessLines = listOf(
                                "Reading the file you pick (up to ${WavPcm.MAX_FILE_BYTES / (1024 * 1024)} MB)…"
                            )
                            pickWavFile.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                        }
                    )
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            loudnessLines?.forEach { line ->
                Text(
                    "• $line",
                    color = SonicCoreColors.PrimaryText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        LimitNote2("SonicCore reports only counters it measures. Metrics a device does not expose stay “Not readable” rather than being estimated.")
    }
}

/**
 * Reads the picked `.wav` (bounded by [WavPcm.MAX_FILE_BYTES]), measures it and
 * returns the lines to show. Every outcome — a refused format, a truncated file,
 * a module that is not on this device — is a sentence, never a missing row.
 */
private fun measureWavFile(context: Context, uri: Uri): List<String> {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (total < WavPcm.MAX_FILE_BYTES) {
                val read = input.read(chunk, 0, minOf(chunk.size, WavPcm.MAX_FILE_BYTES - total))
                if (read <= 0) break
                out.write(chunk, 0, read)
                total += read
            }
            out.toByteArray()
        }
    }.getOrNull() ?: return listOf("Not measured: that file could not be read.")

    val capNote = if (bytes.size >= WavPcm.MAX_FILE_BYTES) {
        listOf(
            "Read the first ${WavPcm.MAX_FILE_BYTES / (1024 * 1024)} MB of that file — " +
                "the picker's own limit."
        )
    } else {
        emptyList()
    }

    val parsed = runCatching { WavPcm.read(bytes) }.getOrElse {
        return listOf("Not measured: that file could not be read (${it.message ?: it.toString()}).")
    }
    return when (parsed) {
        is WavPcm.Read.Refused -> listOf("Not measured: ${parsed.reason}")
        is WavPcm.Read.Ok -> runCatching {
            Loudness.reportLines(Loudness.analyze(parsed.pcm, WavPcm.SOURCE)) + parsed.notes + capNote
        }.getOrElse {
            listOf("Not measured: the measurement failed (${it.message ?: it.toString()}).")
        }
    }
}

@Composable
private fun LimitNote2(text: String) {
    com.soniccore.app.ui.category.LimitNote(text, SonicCoreCategory.AUDIO.tone)
}

/** Platform capability states expressed in the one vocabulary every screen uses. */
private fun CapState.toAudioCapability(): SonicCoreCapability = when (this) {
    CapState.SUPPORTED -> SonicCoreCapability.AVAILABLE
    CapState.PARTIALLY_SUPPORTED -> SonicCoreCapability.AVAILABLE
    CapState.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
    CapState.BLOCKED_BY_SOURCE_APP -> SonicCoreCapability.BLOCKED
    CapState.UNKNOWN -> SonicCoreCapability.UNKNOWN
}
