package com.neon.eq.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
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
import com.neon.eq.ui.design.LiquidGlassDepth
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors
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

    // Lifecycle-bounded refresh every 3 seconds while screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            memStatus = DeviceDiagnosticsManager.getMemoryStatus(context)
            batStatus = DeviceDiagnosticsManager.getBatteryStatus(context)
            thermStatus = DeviceDiagnosticsManager.getThermalStatus(context)
            srvHealth = DeviceDiagnosticsManager.getServiceHealth(context)
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
            Text(
                "HARDWARE & PLATFORM",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            DiagRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            DiagRow("Android OS", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            DiagRow("Board / Hardware", "${android.os.Build.BOARD} / ${android.os.Build.HARDWARE}")
            DiagRow("CPU ABIs", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        }

        // 2. Memory & Resources Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MEMORY & HEAP HEALTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (memStatus.isLowMemory) {
                    StatusBadge(text = "LOW MEMORY", color = Color(0xFFFF5252))
                } else {
                    StatusBadge(text = "HEALTHY", color = Color(0xFF00E676))
                }
            }
            Spacer(Modifier.height(8.dp))
            DiagRow("Available RAM", "${memStatus.availRamMb} MB / ${memStatus.totalRamMb} MB")
            DiagRow("Low RAM Threshold", "${memStatus.thresholdMb} MB")
            DiagRow("App JVM Heap", "${memStatus.jvmUsedHeapMb} MB used / ${memStatus.jvmMaxHeapMb} MB max")
        }

        // 3. Power, Battery & Thermal Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "POWER & THERMAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (thermStatus.isThrottling) {
                    StatusBadge(text = "THROTTLED", color = Color(0xFFFFAB00))
                } else {
                    StatusBadge(text = thermStatus.statusLabel, color = Color(0xFF00E676))
                }
            }
            Spacer(Modifier.height(8.dp))
            DiagRow("Battery Level", if (batStatus.levelPercent >= 0) "${batStatus.levelPercent}%" else "Unknown")
            DiagRow("Power Source", batStatus.powerSource)
            DiagRow("Battery Temperature", "${batStatus.temperatureCelsius} °C (${batStatus.health})")
            DiagRow("Battery Saver", if (batStatus.isPowerSaveMode) "Active (May limit background)" else "Inactive")
            DiagRow("Thermal Status", "${thermStatus.statusLabel} — ${thermStatus.note}")
        }

        // 4. Audio Engine & Service Health Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AUDIO SERVICE & DSP HEALTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (srvHealth.captureRunning) {
                    StatusBadge(text = if (srvHealth.captureBypassed) "BYPASS" else "PROCESSING", color = Color(0xFF00E5FF))
                } else {
                    StatusBadge(text = "STANDBY", color = Color.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            DiagRow("Capture Service", if (srvHealth.captureRunning) "Active Foreground Service" else "Standby / Idle")
            DiagRow("Frames Captured", "${srvHealth.framesCaptured} frames")
            DiagRow("Buffer Underruns", "${srvHealth.underruns}")
            DiagRow("DSP Load", "${"%.1f".format(srvHealth.dspCpuLoadPct)}%")
            DiagRow("Native C++ DSP", if (srvHealth.dspAvailable) "Loaded & Verified (NEON)" else "Unavailable")
            DiagRow("Peak Clips / NaN", "${srvHealth.clips} clips / ${srvHealth.nanEvents} NaN events")
            DiagRow("Active Route", srvHealth.outputRoute)
        }

        // 5. Actions Card
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            Text(
                "PRIVACY-REDACTED REPORT",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Violet,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Generates an anonymized diagnostic report sanitizing all IP, MAC, serial, and user path identifiers.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val report = DeviceDiagnosticsManager.generateRedactedReport(context)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("SonicCore Diagnostics", report)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Redacted diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Copy privacy-redacted diagnostics report to clipboard" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Cyan.copy(alpha = 0.25f),
                    contentColor = SonicCoreColors.Cyan
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("COPY REDACTED REPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.SecondaryText,
            fontSize = 12.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.PrimaryText,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
