package com.neon.eq.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.neon.eq.permissions.PermissionCenter
import com.neon.eq.shizuku.ShizukuBridge
import com.neon.eq.shizuku.ShizukuOperations
import com.neon.eq.system.DeviceCapabilityProbe
import java.io.File

/**
 * Builds the exportable diagnostic report and shares it as a plain-text file.
 *
 * The report is composed of the existing privacy-redacted audio report plus the
 * platform sections (device, permissions, Shizuku, operation log). Everything
 * passes through the existing redaction routine before it leaves the app.
 */
object DiagnosticReportExporter {

    fun buildReport(context: Context): String {
        val shizukuState = ShizukuBridge.checkState(context)
        val operations = ShizukuOperations.evaluate(shizukuState, android.os.Build.VERSION.SDK_INT)
        val permissions = PermissionCenter.probe(context)
        val cpu = DeviceCapabilityProbe.cpuSummary()
        val storage = DeviceCapabilityProbe.storageSummary(context)
        val display = DeviceCapabilityProbe.displaySummary(context)
        val network = DeviceCapabilityProbe.networkSummary(context)
        val sensors = DeviceCapabilityProbe.sensorSummary(context)

        val sb = StringBuilder()
        sb.appendLine(DeviceDiagnosticsManager.generateRedactedReport(context))
        sb.appendLine()
        sb.appendLine("[CPU]")
        sb.appendLine("  Cores        : ${cpu.cores}")
        sb.appendLine("  ABIs         : ${cpu.abis.joinToString(", ")}")
        sb.appendLine("  Max Freq     : ${DeviceCapabilityProbe.formatFrequency(cpu.maxFreqMhz)}")
        sb.appendLine()
        sb.appendLine("[STORAGE]")
        sb.appendLine("  Data Total   : ${DeviceCapabilityProbe.formatStorage(storage.dataTotalBytes)}")
        sb.appendLine("  Data Free    : ${DeviceCapabilityProbe.formatStorage(storage.dataFreeBytes)}")
        sb.appendLine("  App Cache    : ${DeviceCapabilityProbe.formatStorage(storage.appCacheBytes)}")
        sb.appendLine()
        sb.appendLine("[DISPLAY]")
        sb.appendLine("  Resolution   : ${display.widthPx} x ${display.heightPx} px")
        sb.appendLine("  Density      : ${display.densityDpi} dpi")
        sb.appendLine("  Refresh      : ${display.refreshHz?.let { "%.1f Hz".format(it) } ?: "Not readable"}")
        sb.appendLine()
        sb.appendLine("[NETWORK]")
        sb.appendLine("  State        : ${DeviceCapabilityProbe.networkStatusLabel(network.connected, network.transport, network.metered)}")
        sb.appendLine("  Downstream   : ${network.downloadKbps?.let { "$it kbps" } ?: "Not reported"}")
        sb.appendLine("  Upstream     : ${network.uploadKbps?.let { "$it kbps" } ?: "Not reported"}")
        sb.appendLine()
        sb.appendLine("[SENSORS]")
        sb.appendLine("  Present      : ${sensors.present.joinToString(", ").ifEmpty { "None reported" }}")
        sb.appendLine("  Missing      : ${sensors.missing.joinToString(", ").ifEmpty { "None" }}")
        sb.appendLine()
        sb.appendLine("[PERMISSIONS]")
        permissions.forEach { entry ->
            sb.appendLine("  ${entry.title.padEnd(26)}: ${entry.state.label}")
        }
        sb.appendLine()
        sb.appendLine("[SHIZUKU]")
        sb.appendLine("  State        : ${shizukuState.label} — ${shizukuState.description}")
        operations.forEach { status ->
            sb.appendLine(
                "  ${status.operation.title.padEnd(26)}: ${status.outcome.label} " +
                    "(${status.operation.access.label}) — ${status.reason}"
            )
        }
        sb.appendLine()
        sb.appendLine("[OPERATION LOG — CURRENT SESSION]")
        sb.appendLine(OperationLog.format(OperationLog.snapshot()))
        sb.appendLine("==============================================")

        return DeviceDiagnosticsManager.redactSensitiveInfo(sb.toString())
    }

    fun writeToCache(context: Context, report: String): File {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "soniccore-diagnostics-${System.currentTimeMillis()}.txt")
        file.writeText(report)
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SonicCore diagnostic report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
