package com.neon.eq.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.capture.CaptureEqService
import com.neon.eq.capture.AudioCapabilityManager
import com.neon.eq.diagnostics.OperationLog
import com.neon.eq.diagnostics.OperationResult
import com.neon.eq.permissions.PermissionCenter
import com.neon.eq.permissions.PermissionEntry
import com.neon.eq.permissions.PermissionState
import com.neon.eq.permissions.PermissionTarget
import com.neon.eq.ui.category.CapabilityBadge
import com.neon.eq.ui.category.CapabilityLegend
import com.neon.eq.ui.category.LimitNote
import com.neon.eq.ui.category.SonicCoreCapability
import com.neon.eq.ui.category.SonicCoreDataRow
import com.neon.eq.ui.category.SonicCoreToolHost
import com.neon.eq.ui.design.GlassCard
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors
import com.neon.eq.ui.navigation.SonicCoreCategory

private val privacyTone = SonicCoreCategory.PRIVACY.tone

private fun intentFor(context: Context, target: PermissionTarget): Intent? = when (target) {
    PermissionTarget.APP_DETAILS -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    PermissionTarget.APP_NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else null
    PermissionTarget.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    PermissionTarget.BATTERY_OPTIMIZATION ->
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))
    PermissionTarget.BLUETOOTH -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    PermissionTarget.NONE -> null
}

@Composable
private fun SettingsShortcut(context: Context, target: PermissionTarget, label: String, modifier: Modifier = Modifier) {
    val intent = remember(target) { intentFor(context, target) }
    Text(
        label,
        color = if (intent != null) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .clickable(enabled = intent != null) {
                runCatching { context.startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(vertical = 9.dp, horizontal = 8.dp)
            .semantics { contentDescription = label }
    )
}

private fun entryCapability(entry: PermissionEntry): SonicCoreCapability = when (entry.state) {
    PermissionState.GRANTED, PermissionState.SESSION_ACTIVE -> SonicCoreCapability.AVAILABLE
    PermissionState.DENIED, PermissionState.SESSION_PROMPTED -> SonicCoreCapability.NEEDS_PERMISSION
    PermissionState.NOT_SUPPORTED -> SonicCoreCapability.UNSUPPORTED
    PermissionState.NOT_DECLARED -> SonicCoreCapability.UNKNOWN
}

/** Permissions & Privacy → Special App Access. */
@Composable
fun SpecialAppAccessScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = remember {
        runCatching { PermissionCenter.probe(context) }.getOrDefault(emptyList())
            .filter { it.target == PermissionTarget.BATTERY_OPTIMIZATION || it.target == PermissionTarget.USAGE_ACCESS }
    }
    val ignoringBattery = remember { PermissionCenter.isIgnoringBatteryOptimizations(context) }
    val usageAccess = remember { PermissionCenter.hasUsageAccess(context) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "Special app access is granted outside the runtime-permission dialog, so each one is shown with its real state."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SPECIAL ACCESS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow(
                "Battery optimisation exemption",
                if (ignoringBattery) "Exempt" else "Not exempt",
                if (ignoringBattery) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED,
                "Without an exemption Android may delay or stop the capture service in the background."
            )
            SonicCoreDataRow(
                "Usage access",
                if (usageAccess) "Granted" else "Not granted",
                if (usageAccess) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION,
                "SonicCore does not need usage access and does not request it; it is listed only to report the real state."
            )
            entries.forEach { entry ->
                SonicCoreDataRow(
                    label = entry.title,
                    value = entry.state.label,
                    capability = entryCapability(entry),
                    note = entry.reason
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsShortcut(context, PermissionTarget.BATTERY_OPTIMIZATION, "BATTERY SETTINGS", Modifier.weight(1f))
                SettingsShortcut(context, PermissionTarget.APP_DETAILS, "APP INFO", Modifier.weight(1f))
            }
        }
        LimitNote(
            "SonicCore cannot grant itself a special access. Each button opens the Android screen where you decide.",
            privacyTone
        )
    }
}

/** Permissions & Privacy → Audio Capture Consent. */
@Composable
fun AudioCaptureConsentScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val projection = remember { runCatching { AudioCapabilityManager.mediaProjection(context) }.getOrNull() }
    val running = CaptureEqService.running
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (running) SonicCoreCapability.AVAILABLE
            else if (projection?.toString()?.contains("UNSUPPORTED") == true) SonicCoreCapability.UNSUPPORTED
            else SonicCoreCapability.NEEDS_PERMISSION,
            if (running) "A capture session is active with the consent you granted for this session."
            else "Capture starts only after you approve the screen-recording consent dialog."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CAPTURE CONSENT", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow(
                "Media projection support",
                projection?.toString() ?: "Not readable",
                if (projection != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                "Session state",
                if (running) "Running (audio-only capture)" else "Not running",
                if (running) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED
            )
            SonicCoreDataRow(
                "Audio recording permission",
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied",
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION
            )
            SonicCoreDataRow(
                "Playback capture support",
                runCatching { AudioCapabilityManager.playbackCapture().toString() }.getOrDefault("Not readable"),
                SonicCoreCapability.AVAILABLE,
                "Android 10+ playback capture requires the source app to permit it; some apps block capture entirely."
            )
            Text(
                "Consent is asked per capture session. Revoking it is done from the system notification while the session runs, or by stopping capture here.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        LimitNote(
            "SonicCore captures audio only. The consent dialog is Android's own screen-recording prompt; video is never recorded.",
            privacyTone
        )
    }
}

/** Permissions & Privacy → Notification & Background Access. */
@Composable
fun NotificationBackgroundScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val ignoringBattery = remember { PermissionCenter.isIgnoringBatteryOptimizations(context) }
    var tick by remember { mutableStateOf(0) }
    val captureRunning = remember(tick) { CaptureEqService.running }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (notificationGranted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION,
            "Foreground services need a visible notification; without the permission Android hides it."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("BACKGROUND ACCESS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow(
                "Notification permission",
                if (notificationGranted) "Granted" else "Not granted",
                if (notificationGranted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Required on Android 13+ to show the engine notification." else "Granted at install on this Android version."
            )
            SonicCoreDataRow(
                "Capture foreground service",
                if (captureRunning) "Running" else "Stopped",
                if (captureRunning) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                "Power save mode",
                if (powerManager?.isPowerSaveMode == true) "Active" else "Inactive",
                if (powerManager?.isPowerSaveMode == true) SonicCoreCapability.BLOCKED else SonicCoreCapability.AVAILABLE,
                if (powerManager?.isPowerSaveMode == true) "Power save can pause background capture." else null
            )
            SonicCoreDataRow(
                "Battery optimisation exemption",
                if (ignoringBattery) "Exempt" else "Not exempt",
                if (ignoringBattery) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsShortcut(context, PermissionTarget.APP_NOTIFICATIONS, "NOTIFICATION SETTINGS", Modifier.weight(1f))
                SettingsShortcut(context, PermissionTarget.BATTERY_OPTIMIZATION, "BATTERY SETTINGS", Modifier.weight(1f))
            }
            Text(
                "REFRESH",
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { tick++ }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

/** Permissions & Privacy → Privacy Information. */
@Composable
fun PrivacyInformationScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "A plain statement of what SonicCore reads, stores and never does.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("WHAT SONICCORE READS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Audio", "Eligible Android playback, processed locally", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Device metrics", "Read-only, only while a panel is open", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Permission state", "Your own app's grant state", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Shizuku", "Read-only binder diagnostics when authorized", SonicCoreCapability.AVAILABLE)
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("WHAT IT NEVER DOES", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Network", "No uploads, no telemetry, no analytics", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Storage of audio", "Captured audio is never written to disk by default", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Other apps' data", "Not read; per-app data is not requested", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Secure settings", "Never modified, even with Shizuku authorized", SonicCoreCapability.AVAILABLE)
            LimitNote(
                "Diagnostic reports are redacted before you share them: device identifiers are stripped from the exported text.",
                privacyTone
            )
        }
    }
}

/** Permissions & Privacy → Local Data & Diagnostic Exports. */
@Composable
fun LocalDataExportsScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logSize = remember { host.operationLogSize() }
    val sessionSize = remember { host.sessionHistorySize() }
    val storedBytes = remember { host.locallyStoredBytes() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Everything SonicCore keeps on this device, and what an export contains.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("LOCAL DATA", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Local storage used", "${storedBytes / 1024} KB", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Recorded sessions", sessionSize.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Operation log entries", logSize.toString(), SonicCoreCapability.AVAILABLE, "In-memory for this process; cleared when the app is killed.")
            SonicCoreDataRow("Exported reports", "Generated on request, shared manually", SonicCoreCapability.AVAILABLE)
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("EXPORT CONTENTS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Device model and Android version", "Included", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Engine counters and last error", "Included", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Serial numbers and build fingerprint", "Redacted", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Audio samples", "Never included", SonicCoreCapability.AVAILABLE)
            Text(
                "SHARE LOCAL DATA SUMMARY",
                color = SonicCoreColors.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                    .clickable {
                        host.onShareText(
                            "SonicCore local data summary",
                            buildString {
                                appendLine("SonicCore local data summary")
                                appendLine("Local storage: ${storedBytes / 1024} KB")
                                appendLine("Recorded sessions: $sessionSize")
                                appendLine("Operation log entries: $logSize")
                                appendLine("Package: ${context.packageName}")
                            }
                        )
                    }
                    .padding(vertical = 11.dp)
                    .semantics { contentDescription = "Share a local data summary" }
            )
        }
    }
}

/** Permissions & Privacy → Clear History & Reset Data. */
@Composable
fun ClearHistoryScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var logSize by remember { mutableStateOf(host.operationLogSize()) }
    var sessionSize by remember { mutableStateOf(host.sessionHistorySize()) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Clearing local history never changes your audio settings, presets or permissions.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CLEAR", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Operation log", "$logSize entries", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Recorded sessions", "$sessionSize entries", SonicCoreCapability.AVAILABLE)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CLEAR OPERATION LOG",
                    color = SonicCoreColors.Error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                        .clickable {
                            host.onClearOperationLog()
                            logSize = host.operationLogSize()
                            status = "Operation log cleared."
                        }
                        .padding(vertical = 11.dp)
                        .semantics { contentDescription = "Clear the operation log" }
                )
                Text(
                    "CLEAR SESSION HISTORY",
                    color = SonicCoreColors.Error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SonicCoreColors.Error.copy(alpha = 0.10f))
                        .clickable {
                            val ok = host.onClearSessionHistory()
                            OperationLog.record(
                                category = "Privacy",
                                message = if (ok) "Session history cleared" else "Session history clear failed",
                                result = if (ok) OperationResult.SUCCESS else OperationResult.FAILED
                            )
                            sessionSize = host.sessionHistorySize()
                            status = if (ok) "Session history cleared." else "Session history could not be cleared."
                        }
                        .padding(vertical = 11.dp)
                        .semantics { contentDescription = "Clear recorded session history" }
                )
            }
            status?.let {
                Text(it, color = SonicCoreColors.Success, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
            Text("NOT CLEARED BY THIS SCREEN", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Custom presets", "Kept", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Appearance settings", "Kept", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Permissions", "Kept — change them in Android settings", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Package data reset",
                "Not available in-app",
                SonicCoreCapability.NOT_IMPLEMENTED,
                "A full data wipe is done from Android's app info screen; SonicCore does not clear its own storage silently."
            )
            SettingsShortcut(context, PermissionTarget.APP_DETAILS, "OPEN APP INFO", Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
