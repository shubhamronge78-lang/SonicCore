package com.neon.eq.ui.device

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.capabilities.CapabilityRegistry
import com.neon.eq.capabilities.SupportState
import com.neon.eq.diagnostics.DeviceDiagnosticsManager
import com.neon.eq.dsp.NeonDsp
import com.neon.eq.system.DeviceCapabilityProbe
import com.neon.eq.ui.category.CapabilityBadge
import com.neon.eq.ui.category.CapabilityLegend
import com.neon.eq.ui.category.LimitNote
import com.neon.eq.ui.category.SonicCoreCapability
import com.neon.eq.ui.category.SonicCoreDataRow
import com.neon.eq.ui.category.SonicCoreStatTile
import com.neon.eq.ui.category.SonicCoreToolHost
import com.neon.eq.ui.category.toSonicCoreCapability
import com.neon.eq.ui.design.GlassCard
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors
import com.neon.eq.ui.navigation.SonicCoreCategory

private val deviceTone = SonicCoreCategory.DEVICE.tone

/**
 * Device Center panels.
 *
 * Every panel reports what public Android APIs actually expose to this app and
 * labels anything restricted, unavailable or app-scoped. No value is invented:
 * where a probe returns null the screen says so.
 */

@Composable
fun DeviceOverviewScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember { DeviceCapabilityProbe.storageSummary(context) }
    val display = remember { DeviceCapabilityProbe.displaySummary(context) }
    val cpu = remember { DeviceCapabilityProbe.cpuSummary() }
    val capabilityCount = remember { runCatching { CapabilityRegistry.evaluateAll(context).size }.getOrDefault(0) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Read through public Android APIs, with no privileged access.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL}",
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SonicCoreDataRow("Device", Build.DEVICE, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Hardware", Build.HARDWARE, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Product", Build.PRODUCT, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Build ID", Build.ID, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Security patch", Build.VERSION.SECURITY_PATCH ?: "Not reported",
                if (Build.VERSION.SDK_INT >= 23) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
            SonicCoreDataRow("Fingerprint", "${Build.FINGERPRINT.take(48)}…", SonicCoreCapability.AVAILABLE,
                "Reported for diagnostics only — redacted from exported reports.")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonicCoreStatTile("Cores", cpu.cores.toString(), deviceTone, modifier = Modifier.weight(1f))
            SonicCoreStatTile("Storage free", DeviceCapabilityProbe.formatStorage(storage.dataFreeBytes), deviceTone, modifier = Modifier.weight(1f))
            SonicCoreStatTile("Display", "${display.widthPx}×${display.heightPx}", deviceTone, modifier = Modifier.weight(1f))
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SUMMARY",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            SonicCoreDataRow("Capabilities evaluated", capabilityCount.toString(), SonicCoreCapability.AVAILABLE,
                "Full breakdown lives in the Capabilities registry.")
            SonicCoreDataRow("Native DSP bridge", if (NeonDsp.available) "Loaded" else "Unavailable",
                if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                NeonDsp.loadError)
            SonicCoreDataRow("App version", host.appVersion, SonicCoreCapability.AVAILABLE)
        }
    }
}

@Composable
fun DeviceCpuScreen(modifier: Modifier = Modifier) {
    val cpu = remember { DeviceCapabilityProbe.cpuSummary() }
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (cpu.maxFreqMhz != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN,
            cpu.freqNote
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("PROCESSOR", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Available cores", cpu.cores.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Hardware name", cpu.hardware, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Max core frequency",
                DeviceCapabilityProbe.formatFrequency(cpu.maxFreqMhz),
                if (cpu.maxFreqMhz != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN,
                if (cpu.maxFreqMhz == null) "The kernel does not expose cpufreq to unprivileged apps on this device." else null
            )
            SonicCoreDataRow(
                "Device ABIs",
                cpu.abis.joinToString(", "),
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                "64-bit runtime",
                if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "Yes" else "No",
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                "Native libraries loaded in this process",
                if (NeonDsp.available) "loaded" else "not loaded",
                if (NeonDsp.available) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                NeonDsp.loadError
            )
            SonicCoreDataRow(
                "Low-RAM device class",
                if (am?.isLowRamDevice == true) "Yes" else "No",
                SonicCoreCapability.AVAILABLE
            )
        }
        LimitNote(
            "Per-core live frequencies, thermal zones and kernel governor settings are not readable by a normal app on modern Android.",
            deviceTone
        )
    }
}

@Composable
fun DeviceMemoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val memory = remember { DeviceDiagnosticsManager.getMemoryStatus(context) }
    val am = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val appInfo = remember {
        try {
            val info = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            info
        } catch (_: Throwable) { null }
    }
    val runtime = Runtime.getRuntime()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (memory.isLowMemory) SonicCoreCapability.BLOCKED else SonicCoreCapability.AVAILABLE,
            if (memory.isLowMemory) "The system currently reports a low-memory condition."
            else "Values are read from ActivityManager and the JVM heap at composition time."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SYSTEM MEMORY", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Total RAM", "${memory.totalRamMb} MB", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Available RAM", "${memory.availRamMb} MB", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Low-memory threshold", "${memory.thresholdMb} MB", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Low-memory flag",
                if (memory.isLowMemory) "Set" else "Clear",
                if (memory.isLowMemory) SonicCoreCapability.BLOCKED else SonicCoreCapability.AVAILABLE
            )
            appInfo?.let {
                SonicCoreDataRow("System low-memory signal", if (it.lowMemory) "Reported" else "Not reported", SonicCoreCapability.AVAILABLE)
                SonicCoreDataRow("System memory class", "${it.totalMem / (1024L * 1024L)} MB", SonicCoreCapability.AVAILABLE)
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("THIS APP", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreStatTile(
                "JVM heap in use",
                "${runtime.totalMemory() / (1024L * 1024L)} MB",
                deviceTone,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                footer = "Max heap ${runtime.maxMemory() / (1024L * 1024L)} MB · free ${runtime.freeMemory() / (1024L * 1024L)} MB"
            )
            SonicCoreDataRow(
                "Large heap",
                if (am?.largeMemoryClass != null && am.memoryClass != am.largeMemoryClass) "Available (not requested)" else "Not available",
                SonicCoreCapability.UNKNOWN,
                "Per-app memory of other processes is not readable without privileged access."
            )
            SonicCoreDataRow(
                "Other apps' memory usage",
                "App-scoped: not readable",
                SonicCoreCapability.BLOCKED,
                "Android hides other processes' memory from third-party apps."
            )
        }
    }
}

@Composable
fun DeviceStorageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember { DeviceCapabilityProbe.storageSummary(context) }
    val external = remember {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            stat.availableBytes to stat.totalBytes
        } catch (_: Throwable) { null }
    }
    val cacheBytes = remember { directorySize(context.cacheDir) }
    val filesBytes = remember { directorySize(context.filesDir) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, storage.note)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("DATA PARTITION", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Total", DeviceCapabilityProbe.formatStorage(storage.dataTotalBytes), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Free", DeviceCapabilityProbe.formatStorage(storage.dataFreeBytes), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Used",
                "${DeviceCapabilityProbe.storageUsagePercent(storage.dataTotalBytes, storage.dataFreeBytes)}%",
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreStatTile(
                "Used share",
                "${DeviceCapabilityProbe.storageUsagePercent(storage.dataTotalBytes, storage.dataFreeBytes)}%",
                deviceTone,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("EXTERNAL / SHARED", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            if (external == null) {
                SonicCoreDataRow("Shared storage", "Not readable", SonicCoreCapability.UNKNOWN,
                    "This device did not expose the shared volume through StatFs.")
            } else {
                SonicCoreDataRow("Shared storage free", DeviceCapabilityProbe.formatStorage(external.first), SonicCoreCapability.AVAILABLE)
                SonicCoreDataRow("Shared storage total", DeviceCapabilityProbe.formatStorage(external.second), SonicCoreCapability.AVAILABLE)
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SONICCORE FOOTPRINT", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Cache", DeviceCapabilityProbe.formatStorage(cacheBytes), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Files", DeviceCapabilityProbe.formatStorage(filesBytes), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Cache measured by the storage probe", DeviceCapabilityProbe.formatStorage(storage.appCacheBytes), SonicCoreCapability.AVAILABLE)
        }
    }
}

@Composable
fun DeviceBatteryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val battery = remember { DeviceDiagnosticsManager.getBatteryStatus(context) }
    val thermal = remember { DeviceDiagnosticsManager.getThermalStatus(context) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (battery.levelPercent < 0) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE,
            "Charge, health, temperature and voltage come from the system BATTERY_CHANGED broadcast."
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonicCoreStatTile(
                "Level",
                if (battery.levelPercent >= 0) "${battery.levelPercent}%" else "Not readable",
                deviceTone,
                modifier = Modifier.weight(1f)
            )
            SonicCoreStatTile("Temperature", "%.1f °C".format(battery.temperatureCelsius), deviceTone, modifier = Modifier.weight(1f))
            SonicCoreStatTile("Voltage", if (battery.voltageMv > 0) "${battery.voltageMv} mV" else "Not readable", deviceTone, modifier = Modifier.weight(1f))
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("POWER", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Power source", battery.powerSource, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Charging", if (battery.isCharging) "Yes" else "No", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Battery health", battery.health, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Battery saver",
                if (battery.isPowerSaveMode) "Enabled" else "Disabled",
                if (battery.isPowerSaveMode) SonicCoreCapability.BLOCKED else SonicCoreCapability.AVAILABLE,
                if (battery.isPowerSaveMode) "Battery saver can reduce background service reliability." else null
            )
            SonicCoreDataRow(
                "Thermal status",
                thermal.statusLabel,
                if (thermal.isThrottling) SonicCoreCapability.BLOCKED else SonicCoreCapability.AVAILABLE,
                thermal.note
            )
        }
        LimitNote(
            "Historical drain figures need usage-access statistics, which SonicCore does not request. Only instantaneous values are shown.",
            deviceTone
        )
    }
}

@Composable
fun DeviceDisplayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val display = remember { DeviceCapabilityProbe.displaySummary(context) }
    val config = remember { context.resources.configuration }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, display.note)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("DISPLAY", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Resolution", "${display.widthPx} × ${display.heightPx} px", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Density", "${display.densityDpi} dpi", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Refresh rate",
                display.refreshHz?.let { "%.1f Hz".format(it) } ?: "Not reported",
                if (display.refreshHz != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                "Highest refresh mode",
                if (Build.VERSION.SDK_INT >= 23) {
                    runCatching {
                        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                        val modes = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.supportedModes
                        val best = modes?.maxByOrNull { it.refreshRate }
                        best?.let { "%.0f Hz".format(it.refreshRate) }
                    }.getOrNull() ?: "Not reported"
                } else "Requires Android 6+",
                SonicCoreCapability.AVAILABLE
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("LAYOUT & ACCESSIBILITY", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Font scale", "%.2f×".format(config.fontScale), SonicCoreCapability.AVAILABLE,
                if (config.fontScale > 1.2f) "Large font scaling is active — SonicCore layouts reflow rather than clip." else null)
            SonicCoreDataRow(
                "Screen size class",
                when (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                    Configuration.SCREENLAYOUT_SIZE_SMALL -> "Small"
                    Configuration.SCREENLAYOUT_SIZE_NORMAL -> "Normal"
                    Configuration.SCREENLAYOUT_SIZE_LARGE -> "Large"
                    Configuration.SCREENLAYOUT_SIZE_XLARGE -> "Extra large"
                    else -> "Undefined"
                },
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                "Orientation",
                if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait",
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                "Night mode",
                if ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "On" else "Off",
                SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                "HDR / wide-gamut capabilities",
                "Not readable from the app window",
                SonicCoreCapability.UNKNOWN,
                "Display HDR capabilities require the display's own capability report, which is not exposed to apps."
            )
        }
    }
}

@Composable
fun DeviceSensorsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensors = remember { DeviceCapabilityProbe.sensorSummary(context) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (sensors.total == 0) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE,
            sensors.note
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SENSOR SERVICE", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Sensors reported by the HAL", sensors.total.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Tracked sensors present", sensors.present.joinToString(", ").ifBlank { "None" }, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Tracked sensors missing",
                sensors.missing.joinToString(", ").ifBlank { "None" },
                if (sensors.missing.isEmpty()) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SonicCoreStatTile("Present", sensors.present.size.toString(), deviceTone, modifier = Modifier.weight(1f))
                SonicCoreStatTile("Missing", sensors.missing.size.toString(), deviceTone, modifier = Modifier.weight(1f))
            }
        }
        LimitNote(
            "Live sensor readings are not sampled anywhere in SonicCore — only presence is reported, so nothing runs in the background.",
            deviceTone
        )
    }
}

@Composable
fun DeviceNetworkScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val network = remember { DeviceCapabilityProbe.networkSummary(context) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (network.connected) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED,
            network.note
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CONNECTIVITY", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow(
                "Status",
                DeviceCapabilityProbe.networkStatusLabel(network.connected, network.transport, network.metered),
                if (network.connected) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED
            )
            SonicCoreDataRow("Transport", network.transport, SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Metered",
                when (network.metered) {
                    true -> "Yes"
                    false -> "No"
                    null -> "Not reported"
                },
                if (network.metered != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow("VPN active", if (network.vpnActive) "Yes" else "No", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow(
                "Downstream link estimate",
                network.downloadKbps?.let { "$it kbps" } ?: "Not reported",
                if (network.downloadKbps != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                "Upstream link estimate",
                network.uploadKbps?.let { "$it kbps" } ?: "Not reported",
                if (network.uploadKbps != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("NETWORK USE", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Data sent by SonicCore", "None — the app has no network code", SonicCoreCapability.AVAILABLE,
                "SonicCore performs no uploads; diagnostic exports are shared manually by the user.")
            SonicCoreDataRow(
                "Per-app data usage",
                "App-scoped: not readable",
                SonicCoreCapability.BLOCKED,
                "Traffic statistics for other apps require usage access."
            )
        }
    }
}

@Composable
fun DeviceHealthScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val capabilityItems = remember { runCatching { CapabilityRegistry.evaluateAll(context) }.getOrDefault(emptyList()) }
    val restricted = capabilityItems.filter { it.state != SupportState.SUPPORTED }
    val service = remember { DeviceDiagnosticsManager.getServiceHealth(context) }
    val memory = remember { DeviceDiagnosticsManager.getMemoryStatus(context) }
    val thermal = remember { DeviceDiagnosticsManager.getThermalStatus(context) }
    var lastExport by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (service.dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
            "Consolidated health checks; every limitation is named rather than smoothed over."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("ENGINE HEALTH", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Capture session", if (service.captureRunning) "Running" else "Stopped",
                if (service.captureRunning) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
            SonicCoreDataRow("Bypass", if (service.captureBypassed) "Engaged" else "Off", SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Frames captured", service.framesCaptured.toString(), SonicCoreCapability.AVAILABLE)
            SonicCoreDataRow("Underruns", service.underruns.toString(),
                if (service.underruns == 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED)
            SonicCoreDataRow("Clips / NaN", "${service.clips} / ${service.nanEvents}",
                if (service.clips == 0L && service.nanEvents == 0L) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED)
            SonicCoreDataRow("Output route", service.outputRoute.ifBlank { "Not detected" },
                if (service.outputRoute.isBlank()) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonicCoreStatTile("Memory", if (memory.isLowMemory) "Low" else "Normal", deviceTone, modifier = Modifier.weight(1f))
            SonicCoreStatTile("Thermal", thermal.statusLabel.substringBefore(" ("), deviceTone, modifier = Modifier.weight(1f))
            SonicCoreStatTile("Restricted", restricted.size.toString(), deviceTone, modifier = Modifier.weight(1f))
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("LABELLED LIMITATIONS (${restricted.size})", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            if (restricted.isEmpty()) {
                Text("No restricted capabilities were reported on this device.", color = SonicCoreColors.Success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            } else {
                restricted.take(10).forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, color = SonicCoreColors.PrimaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        CapabilityBadge(item.state.toSonicCoreCapability(), compact = true)
                    }
                }
            }
            Text(
                "GENERATE REDACTED HEALTH REPORT",
                color = SonicCoreColors.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clickable {
                        lastExport = runCatching { DeviceDiagnosticsManager.generateRedactedReport(context) }.getOrNull()
                    }
                    .padding(horizontal = 11.dp, vertical = 10.dp)
            )
            lastExport?.let { text ->
                Text(
                    "REPORT READY · ${text.length} characters · redacted",
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
                        .clickable { host.onShareText("SonicCore device health report", text) }
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                )
            }
        }
    }
}

private fun directorySize(dir: java.io.File?, depth: Int = 0): Long {
    if (dir == null || depth > 3) return 0L
    return try {
        dir.listFiles()?.sumOf { file ->
            if (file.isDirectory) directorySize(file, depth + 1) else file.length()
        } ?: 0L
    } catch (_: Throwable) { 0L }
}
