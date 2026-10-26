package com.soniccore.app.ui.settings

import com.soniccore.app.ui.design.GlassRadius
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.NeonTheme
import com.soniccore.app.SurfaceModes
import com.soniccore.app.appModeState
import com.soniccore.app.capture.CaptureEqService
import com.soniccore.app.diagnostics.OperationLog
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.engine.EqualizerEngine
import com.soniccore.app.permissions.PermissionCenter
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.LimitNote
import com.soniccore.app.ui.category.SettingsGroup
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.customization.ThemeSwatchStrip
import com.soniccore.app.ui.design.AppearanceState
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.navigation.SonicCoreCategory

private val settingsTone = SonicCoreCategory.SETTINGS.tone

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(if (checked) SonicCoreColors.Cyan.copy(alpha = 0.08f) else SonicCoreColors.Background.copy(alpha = 0.35f))
            .border(
                0.8.dp,
                if (checked) SonicCoreColors.Cyan.copy(alpha = 0.30f) else SonicCoreColors.Border.copy(alpha = 0.12f),
                RoundedCornerShape(GlassRadius.tile)
            )
            .clickable { onToggle(!checked) }
            .semantics {
                contentDescription = title
                this.stateDescription = if (checked) "On" else "Off"
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SonicCoreColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            if (checked) "ON" else "OFF",
            color = if (checked) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(6.dp))
}

/** Settings → General Preferences. */
@Composable
fun SettingsGeneralScreen(host: SonicCoreToolHost, themes: List<NeonTheme>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { EqualizerEngine.getInstance(context) }
    var startOnBoot by remember { mutableStateOf(engine.isStartOnBoot()) }
    var autoApply by remember { mutableStateOf(engine.isAutoApplyPreset()) }
    var visualizer by remember { mutableStateOf(engine.isShowVisualizer()) }
    var glow by remember { mutableStateOf(engine.isShowGlow()) }
    var mode by remember { mutableStateOf(appModeState.value) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Every switch writes the same preference the engine itself reads.")
        SettingsGroup("Appearance") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "DARK" to SurfaceModes.DARK,
                    "LIGHT" to SurfaceModes.LIGHT,
                    "SYSTEM" to SurfaceModes.SYSTEM
                ).forEach { (label, palette) ->
                    val selected = mode == palette
                    Text(
                        label,
                        color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) SonicCoreColors.Cyan.copy(alpha = 0.12f) else SonicCoreColors.Background.copy(alpha = 0.4f))
                            .clickable {
                                mode = palette
                                appModeState.value = palette
                                runCatching {
                                    context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                                        .edit().putString(
                                            "mode",
                                            when (palette) {
                                                SurfaceModes.LIGHT -> "light"
                                                SurfaceModes.DARK -> "dark"
                                                else -> "system"
                                            }
                                        ).apply()
                                }
                            }
                            .padding(vertical = 9.dp)
                    )
                }
            }
            Text(
                "THEME",
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 7.dp)
            )
            ThemeSwatchStrip(
                themes = themes,
                currentThemeId = host.currentThemeId,
                onThemeApply = host.onThemeApply
            )
        }
        SettingsGroup("Engine behaviour") {
            ToggleRow("Start on boot", "Restores the engine after a device restart.", startOnBoot) {
                startOnBoot = it
                engine.setStartOnBoot(it)
            }
            ToggleRow("Apply last preset on launch", "Restores the preset that was active when the app closed.", autoApply) {
                autoApply = it
                engine.setAutoApplyPreset(it)
            }
            ToggleRow("Audio visualizer", "Drives the waveform view from captured PCM.", visualizer) {
                visualizer = it
                engine.setShowVisualizer(it)
            }
            ToggleRow("Glow effects", "Adds glow to active audio elements.", glow) {
                glow = it
                engine.setShowGlow(it)
            }
            LimitNote("These are user preferences. Changing one never alters the DSP parameters of the running session.", settingsTone)
        }
        SettingsGroup("Navigation") {
            SonicCoreDataRow("Home categories", SonicCoreCategory.entries.size.toString() + " (fixed)", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Dock", if (AppearanceState.dockVisible) "Visible" else "Hidden", SonicCoreCapability.AVAILABLE)
            Text(
                "CHANGE DOCK VISIBILITY IN NAVIGATION PREFERENCES ›",
                color = SonicCoreColors.Cyan,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { host.onNavigate(com.soniccore.app.ui.navigation.SonicCoreDestination.SETTINGS) }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

/** Settings → Audio Defaults. */
@Composable
fun SettingsAudioDefaultsScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { EqualizerEngine.getInstance(context) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Defaults used when a capture session starts.")
        SettingsGroup("Session defaults") {
            SonicCoreDataRow("Band count", engine.bandCount.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Active preset", host.currentPreset, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Capture buffer mode", engine.getCaptureBufferMode(), SonicCoreCapability.AVAILABLE,
                "Read from the engine's stored capture preference.")
            SonicCoreDataRow("Output volume", "%.2f×".format(engine.getCaptureOutVolume()), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Noise gate", if (engine.isNoiseGate()) "Enabled" else "Disabled", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Limiter", if (engine.isLimiter()) "Enabled" else "Disabled", SonicCoreCapability.AVAILABLE)
        }
        SettingsGroup("Preset library") {
            SonicCoreDataRow("Built-in presets", host.builtinPresetNames.size.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Custom presets", host.customPresetNames.size.toString(), SonicCoreCapability.AVAILABLE)
            Text(
                "MANAGE PRESETS IN AUDIO › PRESETS",
                color = SonicCoreColors.Cyan,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { host.onNavigate(com.soniccore.app.ui.navigation.SonicCoreDestination.EQUALIZER) }
                    .padding(vertical = 6.dp)
            )
        }
        LimitNote(
            "SonicCore has no global audio default that could apply to other apps' output paths — those are controlled by Android.",
            settingsTone
        )
    }
}

/** Settings → Navigation Preferences. */
@Composable
fun SettingsNavigationScreen(context: Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Navigation behaviour for the six categories and their tools.")
        SettingsGroup("Dock") {
            ToggleRow(
                "Show the category dock",
                "Hides the six-category dock on devices where it is not wanted.",
                AppearanceState.dockVisible
            ) { visible ->
                AppearanceState.dockVisible = visible
                AppearanceState.save(context)
            }
        }
        SettingsGroup("Back behaviour") {
            SonicCoreDataRow("Tool → hub", "Back returns to the category hub", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Hub → home", "Back returns to Home", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Home", "Back leaves the app", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("State restoration", "Category, tool and scroll position are restored", SonicCoreCapability.AVAILABLE)
        }
        SettingsGroup("Search") {
            SonicCoreDataRow("Searches", "Every tool in all six categories", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Flattens categories", "No — results always name their category", SonicCoreCapability.AVAILABLE)
        }
    }
}

/** Settings → Notifications. */
@Composable
fun SettingsNotificationsScreen(context: Context, modifier: Modifier = Modifier) {
    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    var tick by remember { mutableStateOf(0) }
    val captureRunning = remember(tick) { CaptureEqService.running }
    val engineEnabled = remember(tick) { EqualizerEngine.getInstance(context).isBoot() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (granted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION,
            "The ongoing notification is how Android shows a running engine; it cannot be removed while the service runs."
        )
        SettingsGroup("Notifications") {
            SonicCoreDataRow("Permission", if (granted) "Granted" else "Not granted",
                if (granted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION)
            SonicCoreDataRow("Capture notification", if (captureRunning) "Visible" else "Not posted",
                if (captureRunning) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
            SonicCoreDataRow("Engine enabled", if (engineEnabled) "Yes" else "No",
                SonicCoreCapability.AVAILABLE, "The engine notification is posted by the running capture session.")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationShortcut(context, "NOTIFICATION SETTINGS", notificationIntent(context))
                NotificationShortcut(
                    context,
                    "REFRESH STATE",
                    null,
                    onFallback = {
                        tick++
                        OperationLog.record(
                            category = "Settings",
                            message = "Notification state re-read",
                            result = com.soniccore.app.diagnostics.OperationResult.SUCCESS
                        )
                    }
                )
            }
        }
        SettingsGroup("Quick access") {
            SonicCoreDataRow("Quick settings tile", "Declared and available to add", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Home screen widget", "Registered app widget", SonicCoreCapability.AVAILABLE)
        }
    }
}

private fun notificationIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else null

@Composable
private fun RowScope.NotificationShortcut(
    context: Context,
    label: String,
    intent: Intent?,
    onFallback: (() -> Unit)? = null
) {
    Text(
        label,
        color = SonicCoreColors.Cyan,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .clickable {
                if (intent != null) {
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } else {
                    onFallback?.invoke()
                }
            }
            .padding(vertical = 9.dp)
            .semantics { contentDescription = label }
    )
}

/** Settings → App Behavior & Background Operation. */
@Composable
fun SettingsBehaviorScreen(context: Context, modifier: Modifier = Modifier) {
    val ignoringBattery = remember { PermissionCenter.isIgnoringBatteryOptimizations(context) }
    val engine = remember { EqualizerEngine.getInstance(context) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (ignoringBattery) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED,
            if (ignoringBattery) "Android will not stop the capture service in the background."
            else "Android may pause background capture; an exemption is needed for reliable long sessions."
        )
        SettingsGroup("Background") {
            SonicCoreDataRow("Battery optimisation", if (ignoringBattery) "Exempt" else "Not exempt",
                if (ignoringBattery) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED)
            SonicCoreDataRow("Start on boot", if (engine.isStartOnBoot()) "Enabled" else "Disabled", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Foreground service type", "mediaPlayback / mediaProjection", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Restricted by Android", "Not reported by the platform", SonicCoreCapability.UNKNOWN,
                "Android does not expose an app's own 'restricted' flag to the app itself.")
        }
        SettingsGroup("Audio isolation") {
            SonicCoreDataRow("DSP runs on", "the capture thread, never the UI thread", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("UI polling", "Meters and counters only while a screen is visible", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Background work", "None beyond the capture session", SonicCoreCapability.AVAILABLE)
        }
        Text(
            "REQUEST BATTERY EXEMPTION",
            color = SonicCoreColors.Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassRadius.tile))
                .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                .clickable(enabled = !ignoringBattery) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.fromParts("package", context.packageName, null)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(vertical = 11.dp)
                .semantics { contentDescription = "Request a battery optimisation exemption" }
        )
    }
}

/** Settings → Storage & Data Management. */
@Composable
fun SettingsStorageScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val storedBytes = remember { host.locallyStoredBytes() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "SonicCore stores preferences and technical session logs only.")
        SettingsGroup("On this device") {
            SonicCoreDataRow("Local storage used", "${storedBytes / 1024} KB", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Recorded session log", "${host.sessionHistorySize()} entries", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Operation log", "${host.operationLogSize()} entries (in memory)", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Recorded audio", "Never written", SonicCoreCapability.AVAILABLE)
        }
        SettingsGroup("Managed elsewhere") {
            SonicCoreDataRow("Clear history", "Privacy → Clear History & Reset Data", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Full app data reset", "Android app info screen", SonicCoreCapability.NOT_IMPLEMENTED,
                "SonicCore deliberately does not wipe its own storage.")
        }
        Text(
            "CLEAR CACHED SESSION LOG",
            color = SonicCoreColors.Error,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassRadius.tile))
                .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                .clickable { host.onClearSessionHistory() }
                .padding(vertical = 11.dp)
                .semantics { contentDescription = "Clear the cached session log" }
        )
    }
}

/** Settings → Diagnostics Preferences. */
@Composable
fun SettingsDiagnosticsScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "How much SonicCore records and what leaves the device.")
        SettingsGroup("Diagnostics") {
            SonicCoreDataRow("Operation logging", "Enabled, in memory", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Log capacity", "${OperationLog.MAX_ENTRIES} entries", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Stored to disk", "Only when you export", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Report redaction", "Device identifiers stripped", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Native DSP counters", if (NeonDsp.available) "Available" else "Unavailable",
                if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "EXPORT REPORT",
                color = SonicCoreColors.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(GlassRadius.tile))
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                    .clickable {
                        val text = runCatching {
                            com.soniccore.app.diagnostics.DeviceDiagnosticsManager.generateRedactedReport(context)
                        }.getOrNull()
                        if (text != null) host.onShareText("SonicCore diagnostics report", text)
                    }
                    .padding(vertical = 11.dp)
            )
            Text(
                "CLEAR LOG",
                color = SonicCoreColors.Error,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(GlassRadius.tile))
                    .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                    .clickable { host.onClearOperationLog() }
                    .padding(vertical = 11.dp)
            )
        }
        LimitNote(
            "SonicCore has no upload path. A report leaves the device only when you share it yourself.",
            settingsTone
        )
    }
}

/** Holds the current Android context for the diagnostics export button. */
object LocalContextHolder {
    @Volatile
    var context: Context? = null
}

/** Settings → About & Licenses. */
@Composable
fun SettingsAboutScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LocalContextHolder.context = context
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Build and licensing information for this installation.")
        SettingsGroup("About") {
            SonicCoreDataRow("App", "SonicCore", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Version", host.appVersion, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Package", context.packageName, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Build type", buildTypeLabel(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Signature", signatureLabel(context), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", "), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Native DSP bridge", if (NeonDsp.available) "Loaded" else "Unavailable",
                if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED)
        }
        SettingsGroup("Open source") {
            SonicCoreDataRow("Jetpack Compose", "Apache License 2.0", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("AndroidX", "Apache License 2.0", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Kotlin", "Apache License 2.0", SonicCoreCapability.AVAILABLE)
            // Shizuku-API is MIT (its repository LICENSE and the published Maven POM
            // both say so), not Apache-2.0 — see docs/repository-integration.md.
            SonicCoreDataRow("Shizuku client (dev.rikka.shizuku)", "MIT License", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("SonicCore DSP", "Part of this repository", SonicCoreCapability.AVAILABLE)
            LimitNote(
                "Full license texts ship with their respective projects; SonicCore itself is distributed under the licence in the repository.",
                settingsTone
            )
        }
    }
}

private fun buildTypeLabel(): String =
    if (Build.TYPE.equals("user", ignoreCase = true)) "Release / user" else "Debug (${Build.TYPE})"

private fun signatureLabel(context: Context): String = try {
    val pm = context.packageManager
    // SigningInfo (API 28+) replaces the deprecated signatures array; minSdk is
    // 26, so the older accessor is still needed below it.
    val signers: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    if (signers.isNullOrEmpty()) "Not available" else "Present (${signers.first().toChars().size} chars)"
} catch (_: Throwable) {
    "Not readable"
}
