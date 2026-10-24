package com.neon.eq.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.neon.eq.capture.AudioCapabilityManager
import com.neon.eq.capture.CaptureEqService
import com.neon.eq.dsp.NeonDsp

data class MemoryStatus(
    val totalRamMb: Long,
    val availRamMb: Long,
    val thresholdMb: Long,
    val isLowMemory: Boolean,
    val jvmMaxHeapMb: Long,
    val jvmUsedHeapMb: Long,
    val jvmFreeHeapMb: Long
)

data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
    val powerSource: String,
    val health: String,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val isPowerSaveMode: Boolean
)

data class ThermalStatus(
    val statusCode: Int,
    val statusLabel: String,
    val isThrottling: Boolean,
    val note: String
)

data class ServiceHealth(
    val captureRunning: Boolean,
    val captureBypassed: Boolean,
    val framesCaptured: Long,
    val underruns: Int,
    val dspCpuLoadPct: Float,
    val dspAvailable: Boolean,
    val clips: Long,
    val nanEvents: Long,
    val outputRoute: String
)

object DeviceDiagnosticsManager {

    fun getMemoryStatus(context: Context): MemoryStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)

        val totalRam = if (mi.totalMem > 0) mi.totalMem / (1024L * 1024L) else 0L
        val availRam = mi.availMem / (1024L * 1024L)
        val threshold = mi.threshold / (1024L * 1024L)

        val rt = Runtime.getRuntime()
        val maxHeap = rt.maxMemory() / (1024L * 1024L)
        val totalHeap = rt.totalMemory() / (1024L * 1024L)
        val freeHeap = rt.freeMemory() / (1024L * 1024L)
        val usedHeap = totalHeap - freeHeap

        return MemoryStatus(
            totalRamMb = totalRam,
            availRamMb = availRam,
            thresholdMb = threshold,
            isLowMemory = mi.lowMemory || (availRam <= threshold),
            jvmMaxHeapMb = maxHeap,
            jvmUsedHeapMb = usedHeap,
            jvmFreeHeapMb = freeHeap
        )
    }

    fun getBatteryStatus(context: Context): BatteryStatus {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val bIntent = context.registerReceiver(null, iFilter)

        val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = bIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Power"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "Battery"
        }

        val healthCode = bIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val tempTenths = bIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10.0f
        val voltage = bIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = pm?.isPowerSaveMode ?: false

        return BatteryStatus(
            levelPercent = pct,
            isCharging = isCharging,
            powerSource = powerSource,
            health = health,
            temperatureCelsius = tempC,
            voltageMv = voltage,
            isPowerSaveMode = powerSave
        )
    }

    fun getThermalStatus(context: Context): ThermalStatus {
        if (Build.VERSION.SDK_INT >= 29) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val code = pm?.currentThermalStatus ?: 0
            val (label, throttled) = when (code) {
                0 -> "None (Normal)" to false
                1 -> "Light Throttling" to true
                2 -> "Moderate Throttling" to true
                3 -> "Severe Throttling" to true
                4 -> "Critical Throttling" to true
                5 -> "Emergency Throttling" to true
                6 -> "Shutdown Imminent" to true
                else -> "Status $code" to false
            }
            return ThermalStatus(
                statusCode = code,
                statusLabel = label,
                isThrottling = throttled,
                note = "Queried from PowerManager.currentThermalStatus (API 29+)"
            )
        } else {
            return ThermalStatus(
                statusCode = -1,
                statusLabel = "Not supported (API < 29)",
                isThrottling = false,
                note = "Thermal status API was introduced in Android 10 (API 29)"
            )
        }
    }

    fun getServiceHealth(context: Context): ServiceHealth {
        val dspAvail = NeonDsp.available
        val clips = if (dspAvail) try { NeonDsp.clipCount().toLong() } catch (_: Throwable) { 0L } else 0L
        val nans = if (dspAvail) try { NeonDsp.nanCount() } catch (_: Throwable) { 0L } else 0L
        val route = AudioCapabilityManager.outputDeviceLine(context)

        return ServiceHealth(
            captureRunning = CaptureEqService.running,
            captureBypassed = CaptureEqService.bypass,
            framesCaptured = CaptureEqService.framesCaptured,
            underruns = CaptureEqService.underruns,
            dspCpuLoadPct = CaptureEqService.dspLoadPct.toFloat(),
            dspAvailable = dspAvail,
            clips = clips,
            nanEvents = nans,
            outputRoute = route
        )
    }

    /**
     * Privacy-safe report: sanitizes all personally identifiable information, MAC addresses,
     * serial numbers, local storage user IDs, and network details.
     */
    fun generateRedactedReport(context: Context): String {
        val mem = getMemoryStatus(context)
        val bat = getBatteryStatus(context)
        val therm = getThermalStatus(context)
        val health = getServiceHealth(context)

        val rawSb = StringBuilder()
        rawSb.appendLine("==============================================")
        rawSb.appendLine("       SONICCORE DIAGNOSTICS REPORT           ")
        rawSb.appendLine("==============================================")
        rawSb.appendLine()
        rawSb.appendLine("[PLATFORM & HARDWARE]")
        rawSb.appendLine("  Manufacturer : ${Build.MANUFACTURER}")
        rawSb.appendLine("  Model        : ${Build.MODEL}")
        rawSb.appendLine("  Device / Brd : ${Build.DEVICE} / ${Build.BOARD}")
        rawSb.appendLine("  Hardware     : ${Build.HARDWARE}")
        rawSb.appendLine("  Android OS   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        rawSb.appendLine("  Arch / ABIs  : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        rawSb.appendLine()
        rawSb.appendLine("[MEMORY STATUS]")
        rawSb.appendLine("  Device RAM   : ${mem.availRamMb} MB available / ${mem.totalRamMb} MB total")
        rawSb.appendLine("  Low RAM Warn : ${if (mem.isLowMemory) "ACTIVE WARNING" else "Normal"}")
        rawSb.appendLine("  JVM App Heap : ${mem.jvmUsedHeapMb} MB used / ${mem.jvmMaxHeapMb} MB max")
        rawSb.appendLine()
        rawSb.appendLine("[POWER & BATTERY]")
        rawSb.appendLine("  Battery Pct  : ${if (bat.levelPercent >= 0) "${bat.levelPercent}%" else "Unknown"}")
        rawSb.appendLine("  State/Source : ${if (bat.isCharging) "Charging" else "Discharging"} (${bat.powerSource})")
        rawSb.appendLine("  Health       : ${bat.health}")
        rawSb.appendLine("  Temperature  : ${bat.temperatureCelsius} °C")
        rawSb.appendLine("  Battery Saver: ${if (bat.isPowerSaveMode) "ENABLED (System may throttle)" else "Off"}")
        rawSb.appendLine()
        rawSb.appendLine("[THERMAL STATUS]")
        rawSb.appendLine("  Status       : ${therm.statusLabel}")
        rawSb.appendLine("  Throttling   : ${if (therm.isThrottling) "YES" else "None"}")
        rawSb.appendLine("  Details      : ${therm.note}")
        rawSb.appendLine()
        rawSb.appendLine("[AUDIO SERVICE & ENGINE]")
        rawSb.appendLine("  Capture State: ${if (health.captureRunning) "RUNNING" else "STOPPED"}")
        rawSb.appendLine("  DSP Bypass   : ${if (health.captureBypassed) "BYPASS (Raw path)" else "ACTIVE (DSP processing)"}")
        rawSb.appendLine("  Frames In    : ${health.framesCaptured}")
        rawSb.appendLine("  Underruns    : ${health.underruns}")
        rawSb.appendLine("  DSP CPU Load : ${"%.1f".format(health.dspCpuLoadPct)}%")
        rawSb.appendLine("  Native DSP   : ${if (health.dspAvailable) "LOADED & ACTIVE" else "UNAVAILABLE"}")
        rawSb.appendLine("  Buffer Clips : ${health.clips}")
        rawSb.appendLine("  NaN Events   : ${health.nanEvents}")
        rawSb.appendLine("  Active Route : ${health.outputRoute}")
        rawSb.appendLine()
        rawSb.appendLine("[CAPABILITY SUMMARY]")
        rawSb.appendLine("  Playback Cap : ${AudioCapabilityManager.playbackCapture()}")
        rawSb.appendLine("  AudioRecord  : ${AudioCapabilityManager.audioRecord()}")
        val sr = AudioCapabilityManager.suggestedSampleRate(context)
        rawSb.appendLine("  AudioTrack   : ${AudioCapabilityManager.audioTrack(sr)} (${sr} Hz)")
        rawSb.appendLine("  DSP Self-Test: ${AudioCapabilityManager.dspSelfTest()}")
        rawSb.appendLine("==============================================")

        return redactSensitiveInfo(rawSb.toString())
    }

    fun redactSensitiveInfo(text: String): String {
        var redacted = text
        // Redact Bluetooth device addresses (run before general MAC regex)
        redacted = redacted.replace(Regex("Device\\([0-9A-Fa-f:]{17}\\)"), "Device([REDACTED_BT])")
        // Redact MAC addresses
        redacted = redacted.replace(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"), "[REDACTED_MAC]")
        // Redact IPv4 addresses
        redacted = redacted.replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[REDACTED_IP]")
        // Redact Android data path / user identifiers
        redacted = redacted.replace(Regex("/data/user/\\d+/[a-zA-Z0-9_.-]+"), "[APP_STORAGE_DIR]")
        redacted = redacted.replace(Regex("/data/data/[a-zA-Z0-9_.-]+"), "[APP_STORAGE_DIR]")
        return redacted
    }
}
