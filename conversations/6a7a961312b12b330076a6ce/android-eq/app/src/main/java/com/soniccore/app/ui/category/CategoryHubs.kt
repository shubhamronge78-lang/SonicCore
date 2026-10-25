package com.soniccore.app.ui.category

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.diagnostics.DeviceDiagnosticsManager
import com.soniccore.app.permissions.PermissionCenter
import com.soniccore.app.shizuku.ShizukuBridge
import com.soniccore.app.shizuku.ShizukuState
import com.soniccore.app.system.DeviceCapabilityProbe
import com.soniccore.app.ui.customization.ThemeSwatchStrip
import com.soniccore.app.ui.design.AppearanceState
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.GlassSlider
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.color
import com.soniccore.app.ui.audio.AudioGroupHeader
import com.soniccore.app.ui.audio.AudioGroupTabs
import com.soniccore.app.ui.home.HomeAudioSummary
import com.soniccore.app.ui.navigation.SonicCoreAudioGroup
import com.soniccore.app.ui.navigation.SonicCoreCategory
import com.soniccore.app.ui.navigation.SonicCoreTool
import com.soniccore.app.ui.navigation.SonicCoreToolRegistry
import com.soniccore.app.ui.navigation.color

/** Shared data the category hubs need from the host screen. */
data class CategoryHubHost(
    val audioSummary: HomeAudioSummary,
    val appVersion: String,
    val glassLevel: Int,
    val onGlassLevelChange: (Int) -> Unit,
    val themes: List<com.soniccore.app.NeonTheme>,
    val currentThemeId: String,
    val onThemeApply: (String) -> Unit,
    val onToggleEngine: () -> Unit,
    val bypassActive: Boolean,
    val onToggleBypass: () -> Unit,
    val onOpenFullSettings: () -> Unit
)

/**
 * Each category renders its own hub layout — audio leads with a live waveform,
 * Device Center with structured metric tiles, Shizuku with a connection card,
 * Customization with a live preview, Privacy with status rows and Settings with
 * compact grouped preferences. They share components, never one template.
 */
@Composable
fun SonicCoreCategoryHub(
    category: SonicCoreCategory,
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier = Modifier
) {
    when (category) {
        SonicCoreCategory.AUDIO -> AudioCategoryHub(host, onToolClick, modifier)
        SonicCoreCategory.DEVICE -> DeviceCategoryHub(host, onToolClick, modifier)
        SonicCoreCategory.SHIZUKU -> ShizukuCategoryHub(host, onToolClick, modifier)
        SonicCoreCategory.CUSTOMIZE -> CustomizationCategoryHub(host, onToolClick, modifier)
        SonicCoreCategory.PRIVACY -> PrivacyCategoryHub(host, onToolClick, modifier)
        SonicCoreCategory.SETTINGS -> SettingsCategoryHub(host, onToolClick, modifier)
    }
}

/** Capability label shown next to a tool on its category hub. */
@Composable
fun ToolCapabilityResolver(category: SonicCoreCategory): (SonicCoreTool) -> SonicCoreCapability {
    val context = LocalContext.current
    return remember(category) {
        val shizukuState = if (category == SonicCoreCategory.SHIZUKU) {
            runCatching { ShizukuBridge.checkState(context) }.getOrDefault(ShizukuState.NOT_INSTALLED)
        } else ShizukuState.CONNECTED
        val permissionEntries = if (category == SonicCoreCategory.PRIVACY) {
            runCatching { PermissionCenter.probe(context) }.getOrDefault(emptyList())
        } else emptyList()
        val deniedCount = permissionEntries.count { it.state == com.soniccore.app.permissions.PermissionState.DENIED }
        val resolve: (SonicCoreTool) -> SonicCoreCapability = { tool ->
            when {
                tool.id.startsWith("shizuku.") -> when (shizukuState) {
                    ShizukuState.CONNECTED -> SonicCoreCapability.AVAILABLE
                    ShizukuState.NOT_INSTALLED -> SonicCoreCapability.BLOCKED
                    ShizukuState.NOT_RUNNING -> SonicCoreCapability.BLOCKED
                    ShizukuState.PERMISSION_REQUIRED -> SonicCoreCapability.NEEDS_PERMISSION
                    ShizukuState.DISCONNECTED -> SonicCoreCapability.BLOCKED
                    ShizukuState.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
                }
                tool.id == "privacy.runtime" && deniedCount > 0 -> SonicCoreCapability.NEEDS_PERMISSION
                else -> SonicCoreCapability.AVAILABLE
            }
        }
        resolve
    }
}

@Composable
private fun ToolList(
    category: SonicCoreCategory,
    onToolClick: (SonicCoreTool) -> Unit,
    capabilityFor: (SonicCoreTool) -> SonicCoreCapability,
    title: String
) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
        Text(
            title,
            color = SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        SonicCoreToolRegistry.forCategory(category).forEach { tool ->
            SonicCoreToolRow(
                tool = tool,
                capability = capabilityFor(tool),
                onClick = { onToolClick(tool) }
            )
        }
    }
}

// ── AUDIO: the Audio Center, with its own six-group sub-navigation ──
@Composable
private fun AudioCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.AUDIO)
    val summary = host.audioSummary
    // Phones get one group at a time behind compact chips; a tablet has the room
    // for every group as a labelled list, so nothing needs a second tap.
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 840
    var selectedGroup by remember { mutableStateOf(SonicCoreAudioGroup.OVERVIEW) }
    val grouped = remember { SonicCoreToolRegistry.audioToolsByGroup() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Status and quick controls ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CURRENT AUDIO STATUS",
                        color = SonicCoreColors.SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        summary.stateLabel,
                        color = when {
                            summary.toneIsError -> SonicCoreColors.Error
                            summary.toneIsActive -> SonicCoreColors.Success
                            else -> SonicCoreColors.Warning
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        summary.routeName.ifBlank { "Output route not detected" },
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CategoryActionPill(
                    label = if (summary.engineEnabled) "ENGINE ON" else "ENGINE OFF",
                    tone = SonicCoreCategory.AUDIO,
                    active = summary.engineEnabled,
                    onClick = host.onToggleEngine
                )
            }
            WaveformPanel(summary)
            Text(
                "Bars render the measured RMS of the live capture session; a running service alone never draws a signal. " +
                    "Active processing: " + when {
                        !summary.engineEnabled -> "engine disabled"
                        host.bypassActive -> "capture session in bypass — DSP is not processing"
                        summary.captureRunning -> "native DSP chain on the capture session"
                        else -> "no capture session — nothing is being processed"
                    },
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            CategoryActionPill(
                label = if (host.bypassActive) "BYPASS ON" else "BYPASS OFF",
                tone = SonicCoreCategory.AUDIO,
                active = host.bypassActive,
                onClick = host.onToggleBypass
            )
        }

        // ── The Audio Center's own sub-navigation ──
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 14.dp,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "AUDIO CENTER",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                "${SonicCoreAudioGroup.entries.size} groups · ${SonicCoreToolRegistry.audioTools().size} screens · " +
                    "every existing Audio feature stays reachable",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            if (wideLayout) {
                grouped.forEach { (group, tools) ->
                    AudioGroupHeader(group, modifier = Modifier.fillMaxWidth())
                    tools.forEach { tool ->
                        SonicCoreToolRow(
                            tool = tool,
                            capability = capabilityFor(tool),
                            onClick = { onToolClick(tool) }
                        )
                    }
                }
            } else {
                AudioGroupTabs(
                    selected = selectedGroup,
                    onSelect = { selectedGroup = it }
                )
                Spacer(Modifier.height(10.dp))
                AudioGroupHeader(selectedGroup, modifier = Modifier.fillMaxWidth())
                SonicCoreToolRegistry.forAudioGroup(selectedGroup).forEach { tool ->
                    SonicCoreToolRow(
                        tool = tool,
                        capability = capabilityFor(tool),
                        onClick = { onToolClick(tool) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformPanel(summary: HomeAudioSummary) {
    val tone = SonicCoreCategory.AUDIO.color()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(top = 12.dp)
            .semantics { contentDescription = "Measured waveform of the live capture session" }
    ) {
        val bars = 48
        val barWidth = size.width / bars
        for (index in 0 until bars) {
            val channel = index % 4
            val level = if (summary.hasMeasuredSignal) {
                summary.meters.rmsForChannel(channel).coerceIn(0f, 1f)
            } else 0f
            // A deterministic envelope so the panel stays readable without signal.
            val envelope = 0.18f + 0.82f * kotlin.math.abs(
                kotlin.math.sin(index * 0.42f)
            )
            val height = (size.height * (level * envelope)).coerceAtLeast(2f)
            drawRoundRect(
                color = if (level > 0f) tone.copy(alpha = 0.85f) else SonicCoreColors.Border.copy(alpha = 0.16f),
                topLeft = androidx.compose.ui.geometry.Offset(index * barWidth + 1f, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth - 2f, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
            )
        }
    }
}

// ── DEVICE CENTER: structured metric panels ──
@Composable
private fun DeviceCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.DEVICE)
    val cpu = remember { DeviceCapabilityProbe.cpuSummary() }
    val storage = remember { DeviceCapabilityProbe.storageSummary(context) }
    val display = remember { DeviceCapabilityProbe.displaySummary(context) }
    var memory by remember { mutableStateOf(DeviceDiagnosticsManager.getMemoryStatus(context)) }
    var battery by remember { mutableStateOf(DeviceDiagnosticsManager.getBatteryStatus(context)) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "HARDWARE SNAPSHOT",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SonicCoreStatTile(
                    label = "Cores",
                    value = cpu.cores.toString(),
                    tone = SonicCoreCategory.DEVICE.tone,
                    footer = cpu.abis.firstOrNull() ?: "ABI unknown",
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    label = "Storage free",
                    value = DeviceCapabilityProbe.formatStorage(storage.dataFreeBytes),
                    tone = SonicCoreCategory.DEVICE.tone,
                    footer = "${DeviceCapabilityProbe.storageUsagePercent(storage.dataTotalBytes, storage.dataFreeBytes)}% used",
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    label = "Battery",
                    value = if (battery.levelPercent >= 0) "${battery.levelPercent}%" else "Not readable",
                    tone = SonicCoreCategory.DEVICE.tone,
                    footer = if (battery.isCharging) "Charging · ${battery.powerSource}" else "${battery.powerSource} · ${battery.health}",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SonicCoreStatTile(
                    label = "Available RAM",
                    value = DeviceCapabilityProbe.formatStorage(memory.availRamMb * 1024L * 1024L),
                    tone = SonicCoreCategory.DEVICE.tone,
                    footer = "of ${DeviceCapabilityProbe.formatStorage(memory.totalRamMb * 1024L * 1024L)}",
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    label = "Display",
                    value = "${display.widthPx}×${display.heightPx}",
                    tone = SonicCoreCategory.DEVICE.tone,
                    footer = display.refreshHz?.let { "%.0f Hz".format(it) } ?: "${display.densityDpi} dpi",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        ToolList(SonicCoreCategory.DEVICE, onToolClick, capabilityFor, "DEVICE PANELS")
    }
}

// ── SHIZUKU CENTER: connection-status first ──
@Composable
private fun ShizukuCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.SHIZUKU)
    var state by remember { mutableStateOf(runCatching { ShizukuBridge.checkState(context) }.getOrDefault(ShizukuState.NOT_INSTALLED)) }
    var environment by remember {
        mutableStateOf<com.soniccore.app.shizuku.ShizukuEnvironment?>(
            runCatching { ShizukuBridge.environment(context) }.getOrNull()
        )
    }

    DisposableEffect(Unit) {
        val received = rikka.shizuku.Shizuku.OnBinderReceivedListener {
            state = runCatching { ShizukuBridge.checkState(context) }.getOrDefault(state)
            environment = runCatching { ShizukuBridge.environment(context) }.getOrNull()
        }
        val dead = rikka.shizuku.Shizuku.OnBinderDeadListener {
            state = runCatching { ShizukuBridge.checkState(context) }.getOrDefault(state)
            environment = runCatching { ShizukuBridge.environment(context) }.getOrNull()
        }
        runCatching {
            ShizukuBridge.addBinderReceivedListener(received)
            ShizukuBridge.addBinderDeadListener(dead)
        }
        onDispose {
            runCatching {
                ShizukuBridge.removeBinderReceivedListener(received)
                ShizukuBridge.removeBinderDeadListener(dead)
            }
        }
    }

    val tone = SonicCoreCategory.SHIZUKU
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(tone.color().copy(alpha = 0.14f))
                        .border(1.dp, tone.color().copy(alpha = 0.32f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⇄", color = tone.color(), fontSize = 22.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CONNECTION",
                        color = SonicCoreColors.SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        state.label,
                        color = SonicCoreColors.PrimaryText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.description,
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CapabilityBadge(
                    when (state) {
                        ShizukuState.CONNECTED -> SonicCoreCapability.AVAILABLE
                        ShizukuState.PERMISSION_REQUIRED -> SonicCoreCapability.NEEDS_PERMISSION
                        ShizukuState.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
                        else -> SonicCoreCapability.BLOCKED
                    },
                    compact = true
                )
            }
            environment?.let { env ->
                SonicCoreDataRow(
                    label = "Server",
                    value = if (env.serverVersion > 0) "v${env.serverVersion} (api ${env.serverPatchVersion})" else "Not reported",
                    capability = if (env.serverVersion > 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
                )
                SonicCoreDataRow(
                    label = "Server identity",
                    value = env.privilege.label,
                    capability = if (env.serverUid >= 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
                )
                SonicCoreDataRow(
                    label = "SELinux context",
                    value = env.selinuxContext ?: "Not reported",
                    capability = if (env.selinuxContext != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
                )
            }
            LimitNote(
                "SonicCore performs read-only elevated diagnostics only. It never writes secure settings, grants permissions or reroutes audio through Shizuku.",
                tone.tone
            )
        }
        ToolList(SonicCoreCategory.SHIZUKU, onToolClick, capabilityFor, "SHIZUKU WORKSPACE")
    }
}

// ── CUSTOMIZATION: live preview ──
@Composable
private fun CustomizationCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.CUSTOMIZE)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "LIVE PREVIEW",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            ThemeSwatchStrip(
                themes = host.themes,
                currentThemeId = host.currentThemeId,
                onThemeApply = host.onThemeApply,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("GLASS", color = SonicCoreColors.SecondaryText, fontSize = 9.sp)
                GlassSlider(
                    value = host.glassLevel.toFloat(),
                    onValueChange = { host.onGlassLevelChange(it.toInt().coerceIn(0, 2)) },
                    valueRange = 0f..2f,
                    modifier = Modifier.weight(1f).height(30.dp)
                )
                Text(
                    listOf("Subtle", "Liquid", "Crystal").getOrElse(host.glassLevel.coerceIn(0, 2)) { "Liquid" },
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 9.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACCENT", color = SonicCoreColors.SecondaryText, fontSize = 9.sp)
                Spacer(Modifier.weight(1f))
                com.soniccore.app.ui.design.AccentChoice.entries.forEach { accent ->
                    val selected = AppearanceState.accent == accent
                    Box(
                        modifier = Modifier
                            .size(if (selected) 24.dp else 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) SonicCoreColors.PrimaryText else SonicCoreColors.Border.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                AppearanceState.accent = accent
                                AppearanceState.save(context)
                            }
                            .semantics { contentDescription = "Accent ${accent.label}" }
                    )
                }
            }
            LimitNote(
                "Theme, accent, glass, layout and motion apply inside SonicCore. Android does not let an app restyle the system UI, so system-wide appearance changes are unsupported.",
                SonicCoreCategory.CUSTOMIZE.tone
            )
        }
        ToolList(SonicCoreCategory.CUSTOMIZE, onToolClick, capabilityFor, "APPEARANCE WORKSPACE")
    }
}

// ── PRIVACY: status list ──
@Composable
private fun PrivacyCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.PRIVACY)
    val entries = remember { runCatching { PermissionCenter.probe(context) }.getOrDefault(emptyList()) }
    val granted = entries.count { it.state == com.soniccore.app.permissions.PermissionState.GRANTED }
    val denied = entries.count { it.state == com.soniccore.app.permissions.PermissionState.DENIED }
    val tone = SonicCoreCategory.PRIVACY

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "ACCESS STATE",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SonicCoreStatTile(
                    label = "Granted",
                    value = granted.toString(),
                    tone = SonicCoreToneFor(granted > 0),
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    label = "Needs access",
                    value = denied.toString(),
                    tone = if (denied > 0) com.soniccore.app.ui.design.SonicCoreTone.BLUE else SonicCoreToneFor(true),
                    modifier = Modifier.weight(1f)
                )
                SonicCoreStatTile(
                    label = "Declared",
                    value = entries.size.toString(),
                    tone = tone.tone,
                    modifier = Modifier.weight(1f)
                )
            }
            entries.take(4).forEach { entry ->
                SonicCoreDataRow(
                    label = entry.title,
                    value = entry.state.label,
                    capability = when (entry.state) {
                        com.soniccore.app.permissions.PermissionState.GRANTED -> SonicCoreCapability.AVAILABLE
                        com.soniccore.app.permissions.PermissionState.DENIED -> SonicCoreCapability.NEEDS_PERMISSION
                        else -> SonicCoreCapability.UNKNOWN
                    }
                )
            }
            LimitNote(
                "SonicCore reads its own permission state. It never reports a permission as granted when the platform says otherwise.",
                tone.tone
            )
        }
        ToolList(SonicCoreCategory.PRIVACY, onToolClick, capabilityFor, "PRIVACY WORKSPACE")
    }
}

private fun SonicCoreToneFor(ok: Boolean) =
    if (ok) com.soniccore.app.ui.design.SonicCoreTone.GREEN else com.soniccore.app.ui.design.SonicCoreTone.BLUE

// ── SETTINGS: compact grouped preferences ──
@Composable
private fun SettingsCategoryHub(
    host: CategoryHubHost,
    onToolClick: (SonicCoreTool) -> Unit,
    modifier: Modifier
) {
    val capabilityFor = ToolCapabilityResolver(SonicCoreCategory.SETTINGS)
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
            Text(
                "APP",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            SettingsGroup("Session") {
                SonicCoreDataRow(label = "Version", value = host.appVersion)
                SonicCoreDataRow(label = "Engine", value = if (host.audioSummary.engineEnabled) "Enabled" else "Disabled")
                SonicCoreDataRow(label = "Capture session", value = if (host.audioSummary.captureRunning) "Running" else "Stopped")
                SonicCoreDataRow(label = "Android", value = "API ${Build.VERSION.SDK_INT}")
            }
            CategoryActionPill(
                label = "OPEN FULL SETTINGS SHEET",
                tone = SonicCoreCategory.SETTINGS,
                active = false,
                onClick = host.onOpenFullSettings
            )
        }
        ToolList(SonicCoreCategory.SETTINGS, onToolClick, capabilityFor, "PREFERENCE GROUPS")
    }
}

@Composable
private fun CategoryActionPill(
    label: String,
    tone: SonicCoreCategory,
    active: Boolean,
    onClick: () -> Unit
) {
    val tint = tone.color()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        tint.copy(alpha = if (active) 0.20f else 0.12f),
                        SonicCoreColors.ElevatedSurface.copy(alpha = 0.7f)
                    )
                )
            )
            .border(1.dp, tint.copy(alpha = 0.34f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
