package com.soniccore.app.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.system.DeviceCapabilityProbe
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.PlatformInfoRow
import com.soniccore.app.ui.design.PlatformSectionLabel
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.StatusPill
import kotlinx.coroutines.delay

@Composable
fun DeviceDiagnosticsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var memStatus by remember { mutableStateOf(DeviceDiagnosticsManager.getMemoryStatus(context)) }
    var batStatus by remember { mutableStateOf(DeviceDiagnosticsManager.getBatteryStatus(context)) }
    var thermStatus by remember { mutableStateOf(DeviceDiagnosticsManager.getThermalStatus(context)) }
    var srvHealth by remember { mutableStateOf(DeviceDiagnosticsManager.getServiceHealth(context)) }
    var storage by remember { mutableStateOf(DeviceCapabilityProbe.storageSummary(context)) }
    var network by remember { mutableStateOf(DeviceCapabilityProbe.networkSummary(context)) }
    val cpu = remember { DeviceCapabilityProbe.cpuSummary() }
    val display = remember { DeviceCapabilityProbe.displaySummary(context) }
    val sensors = remember { DeviceCapabilityProbe.sensorSummary(context) }
    var logTick by remember { mutableStateOf(0) }

    // Lifecycle-bounded refresh every 3 seconds while the screen is visible.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            memStatus = DeviceDiagnosticsManager.getMemoryStatus(context)
            batStatus = DeviceDiagnosticsManager.getBatteryStatus(context)
            thermStatus = DeviceDiagnosticsManager.getThermalStatus(context)
            srvHealth = DeviceDiagnosticsManager.getServiceHealth(context)
            storage = DeviceCapabilityProbe.storageSummary(context)
            network = DeviceCapabilityProbe.networkSummary(context)
            logTick++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hardware & Platform Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            PlatformSectionLabel("Hardware & platform")
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            PlatformInfoRow("Android OS", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            PlatformInfoRow("Board / Hardware", "${android.os.Build.BOARD} / ${android.os.Build.HARDWARE}")
            PlatformInfoRow("CPU ABIs", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        }

        // 2. CPU Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            PlatformSectionLabel("Processor")
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Logical cores", cpu.cores.toString())
            PlatformInfoRow("Hardware", cpu.hardware)
            PlatformInfoRow("Max frequency", DeviceCapabilityProbe.formatFrequency(cpu.maxFreqMhz))
            Text(
                cpu.freqNote,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 3. Memory & Resources Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Memory & heap")
                if (memStatus.isLowMemory) {
                    StatusPill("LOW MEMORY", SonicCoreColors.Error)
                } else {
                    StatusPill("HEALTHY", SonicCoreColors.Success)
                }
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Available RAM", "${memStatus.availRamMb} MB / ${memStatus.totalRamMb} MB")
            PlatformInfoRow("Low RAM threshold", "${memStatus.thresholdMb} MB")
            PlatformInfoRow("App JVM heap", "${memStatus.jvmUsedHeapMb} MB used / ${memStatus.jvmMaxHeapMb} MB max")
        }

        // 4. Storage Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Storage")
                StatusPill(
                    "${DeviceCapabilityProbe.storageUsagePercent(storage.dataTotalBytes, storage.dataFreeBytes)}% used",
                    SonicCoreColors.Cyan
                )
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Data partition", DeviceCapabilityProbe.formatStorage(storage.dataTotalBytes))
            PlatformInfoRow("Free", DeviceCapabilityProbe.formatStorage(storage.dataFreeBytes))
            PlatformInfoRow("App cache", DeviceCapabilityProbe.formatStorage(storage.appCacheBytes))
            Text(
                storage.note,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 5. Display Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            PlatformSectionLabel("Display")
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Resolution", "${display.widthPx} × ${display.heightPx} px")
            PlatformInfoRow("Density", "${display.densityDpi} dpi")
            PlatformInfoRow(
                "Refresh rate",
                display.refreshHz?.let { "%.1f Hz".format(it) } ?: "Not readable"
            )
            Text(
                display.note,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 6. Network Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Network")
                StatusPill(
                    if (network.connected) "ONLINE" else "OFFLINE",
                    if (network.connected) SonicCoreColors.Success else SonicCoreColors.SecondaryText
                )
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow(
                "Status",
                DeviceCapabilityProbe.networkStatusLabel(network.connected, network.transport, network.metered)
            )
            PlatformInfoRow("Downstream", network.downloadKbps?.let { "$it kbps" } ?: "Not reported")
            PlatformInfoRow("Upstream", network.uploadKbps?.let { "$it kbps" } ?: "Not reported")
            PlatformInfoRow("VPN", if (network.vpnActive) "Active" else "Inactive")
            Text(
                network.note,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 7. Sensors Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Sensors")
                StatusPill("${sensors.total} total", SonicCoreColors.Cyan)
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow(
                "Present",
                sensors.present.joinToString(", ").ifEmpty { "None reported" }
            )
            PlatformInfoRow(
                "Missing",
                sensors.missing.joinToString(", ").ifEmpty { "None" }
            )
            Text(
                sensors.note,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 8. Power, Battery & Thermal Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Power & thermal")
                if (thermStatus.isThrottling) {
                    StatusPill("THROTTLED", SonicCoreColors.Warning)
                } else {
                    StatusPill(thermStatus.statusLabel, SonicCoreColors.Success)
                }
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Battery level", if (batStatus.levelPercent >= 0) "${batStatus.levelPercent}%" else "Unknown")
            PlatformInfoRow("Power source", batStatus.powerSource)
            PlatformInfoRow("Temperature", "${batStatus.temperatureCelsius} °C (${batStatus.health})")
            PlatformInfoRow("Battery saver", if (batStatus.isPowerSaveMode) "Active (may limit background)" else "Inactive")
            PlatformInfoRow("Thermal status", "${thermStatus.statusLabel} — ${thermStatus.note}")
        }

        // 9. Audio Engine & Service Health Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Audio service & DSP")
                if (srvHealth.captureRunning) {
                    StatusPill(if (srvHealth.captureBypassed) "BYPASS" else "PROCESSING", SonicCoreColors.Cyan)
                } else {
                    StatusPill("STANDBY", SonicCoreColors.SecondaryText)
                }
            }
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Capture service", if (srvHealth.captureRunning) "Active foreground service" else "Standby / idle")
            PlatformInfoRow("Frames captured", srvHealth.framesCaptured.toString())
            PlatformInfoRow("Buffer underruns", srvHealth.underruns.toString())
            PlatformInfoRow("DSP load", "${"%.1f".format(srvHealth.dspCpuLoadPct)}%")
            PlatformInfoRow("Native C++ DSP", if (srvHealth.dspAvailable) "Loaded & verified" else "Unavailable")
            PlatformInfoRow("Peak clips / NaN", "${srvHealth.clips} / ${srvHealth.nanEvents}")
            PlatformInfoRow("Active route", srvHealth.outputRoute)
        }

        // 10. Operation log
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Operation log", color = SonicCoreColors.Violet)
                StatusPill("session", SonicCoreColors.SecondaryText)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Bounded to ${OperationLog.MAX_ENTRIES} entries and held in memory for this session only.",
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(8.dp))
            // logTick participates so the list refreshes with the 3s loop.
            val entries = remember(logTick) { OperationLog.snapshot().take(10) }
            if (entries.isEmpty()) {
                Text(
                    "Nothing recorded yet. Probing capabilities or refreshing Shizuku adds entries.",
                    fontSize = 11.sp,
                    color = SonicCoreColors.SecondaryText
                )
            } else {
                entries.forEach { entry ->
                    Text(
                        "${entry.timeLabel} · ${entry.result.label} · ${entry.category} · ${entry.message}",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = when (entry.result) {
                            OperationResult.SUCCESS -> SonicCoreColors.Success
                            OperationResult.FAILED -> SonicCoreColors.Error
                            OperationResult.UNAVAILABLE -> SonicCoreColors.Warning
                            OperationResult.INFO -> SonicCoreColors.SecondaryText
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    OperationLog.clear()
                    logTick++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Clear the session operation log" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.ElevatedSurface,
                    contentColor = SonicCoreColors.SecondaryText
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CLEAR LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 11. Export
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            PlatformSectionLabel("Export report", color = SonicCoreColors.Violet)
            Spacer(Modifier.height(4.dp))
            Text(
                "Composes platform, permission, Shizuku and operation-log sections, then sanitises MAC addresses, IP addresses and app storage paths.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val report = DiagnosticReportExporter.buildReport(context)
                    val shared = try {
                        val file = DiagnosticReportExporter.writeToCache(context, report)
                        context.startActivity(
                            Intent.createChooser(
                                DiagnosticReportExporter.shareIntent(context, file),
                                "Share SonicCore diagnostics"
                            )
                        )
                        true
                    } catch (_: Throwable) {
                        false
                    }
                    OperationLog.record(
                        category = "Diagnostics",
                        message = if (shared) "Report file shared" else "Report share failed",
                        result = if (shared) OperationResult.SUCCESS else OperationResult.FAILED
                    )
                    if (!shared) {
                        Toast.makeText(context, "Sharing is unavailable — use copy instead", Toast.LENGTH_SHORT).show()
                    }
                    logTick++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Build and share the diagnostic report file" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Cyan.copy(alpha = 0.25f),
                    contentColor = SonicCoreColors.Cyan
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SHARE REPORT FILE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val report = DiagnosticReportExporter.buildReport(context)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("SonicCore Diagnostics", report))
                    OperationLog.record("Diagnostics", "Report copied to clipboard", OperationResult.SUCCESS)
                    logTick++
                    Toast.makeText(context, "Redacted diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Copy privacy-redacted diagnostics report to clipboard" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Violet.copy(alpha = 0.20f),
                    contentColor = SonicCoreColors.Violet
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("COPY REDACTED REPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
