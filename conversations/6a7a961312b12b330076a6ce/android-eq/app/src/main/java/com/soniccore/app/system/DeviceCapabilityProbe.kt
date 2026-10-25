package com.soniccore.app.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Display

/**
 * Read-only device capability probes built exclusively on public Android APIs.
 *
 * Every probe reports what the platform actually exposes to an unprivileged
 * third-party app. Where a value is restricted by the platform or by the OEM
 * kernel, the probe returns null and the UI shows an explicit "not readable"
 * note instead of inventing a value.
 */
data class CpuSummary(
    val cores: Int,
    val abis: List<String>,
    val hardware: String,
    val maxFreqMhz: Long?,
    val freqNote: String
)

data class StorageSummary(
    val dataTotalBytes: Long,
    val dataFreeBytes: Long,
    val appCacheBytes: Long,
    val note: String
)

data class DisplaySummary(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshHz: Float?,
    val note: String
)

data class NetworkSummary(
    val connected: Boolean,
    val transport: String,
    val metered: Boolean?,
    val downloadKbps: Int?,
    val uploadKbps: Int?,
    val vpnActive: Boolean,
    val note: String
)

data class SensorSummary(
    val total: Int,
    val present: List<String>,
    val missing: List<String>,
    val note: String
)

object DeviceCapabilityProbe {

    /** Sensors the dashboard reports on, by the display label shown in the UI. */
    val TRACKED_SENSORS: List<Pair<String, Int>> = listOf(
        "Accelerometer" to Sensor.TYPE_ACCELEROMETER,
        "Gyroscope" to Sensor.TYPE_GYROSCOPE,
        "Magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
        "Light" to Sensor.TYPE_LIGHT,
        "Proximity" to Sensor.TYPE_PROXIMITY,
        "Step counter" to Sensor.TYPE_STEP_COUNTER,
        "Heart rate" to Sensor.TYPE_HEART_RATE
    )

    // Locale.US keeps the decimal separator stable, so the UI and the exported
    // report read the same on every device locale.
    fun formatStorage(bytes: Long): String {
        if (bytes <= 0L) return "Unavailable"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
        else String.format(java.util.Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
    }

    fun formatFrequency(mhz: Long?): String = when {
        mhz == null || mhz <= 0L -> "Not readable"
        mhz >= 1000L -> String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000.0)
        else -> "$mhz MHz"
    }

    /** Used share of a storage volume, clamped to 0..100. */
    fun storageUsagePercent(totalBytes: Long, freeBytes: Long): Int {
        if (totalBytes <= 0L || freeBytes < 0L) return 0
        val used = (totalBytes - freeBytes).coerceAtLeast(0L)
        return ((used * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    fun networkStatusLabel(connected: Boolean, transport: String, metered: Boolean?): String = when {
        !connected -> "Offline"
        metered == true -> "$transport · metered"
        metered == false -> "$transport · unmetered"
        else -> transport
    }

    /** Splits the tracked sensor list into present / missing, preserving order. */
    fun sensorCoverage(presentLabels: Collection<String>): Pair<List<String>, List<String>> {
        val present = TRACKED_SENSORS.map { it.first }.filter { presentLabels.contains(it) }
        val missing = TRACKED_SENSORS.map { it.first }.filterNot { presentLabels.contains(it) }
        return present to missing
    }

    fun cpuSummary(): CpuSummary {
        val maxFreq = readCpuMaxFreqMhz()
        return CpuSummary(
            cores = Runtime.getRuntime().availableProcessors(),
            abis = Build.SUPPORTED_ABIS.toList(),
            hardware = Build.HARDWARE ?: "Unknown",
            maxFreqMhz = maxFreq,
            freqNote = if (maxFreq == null) {
                "Core count and ABI are public; per-core frequencies are not exposed by this device's kernel to unprivileged apps."
            } else {
                "Maximum core frequency read from the kernel cpufreq interface."
            }
        )
    }

    private fun readCpuMaxFreqMhz(): Long? = try {
        val file = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
        if (!file.canRead()) null else {
            val khz = file.readText().trim().toLongOrNull()
            if (khz == null || khz <= 0L) null else khz / 1000L
        }
    } catch (_: Throwable) {
        null
    }

    fun storageSummary(context: Context): StorageSummary {
        var total = 0L
        var free = 0L
        try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            total = stat.blockCountLong * stat.blockSizeLong
            free = stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Throwable) {
            total = 0L
            free = 0L
        }
        return StorageSummary(
            dataTotalBytes = total,
            dataFreeBytes = free,
            appCacheBytes = directorySize(context.cacheDir),
            note = "Reports the shared data partition as visible to this app. Android 11+ may report an app-visible quota rather than the full physical disk."
        )
    }

    private fun directorySize(dir: java.io.File?, depth: Int = 0): Long {
        if (dir == null || depth > 4) return 0L
        return try {
            dir.listFiles()?.sumOf { file ->
                if (file.isDirectory) directorySize(file, depth + 1) else file.length()
            } ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun displaySummary(context: Context): DisplaySummary {
        val metrics = context.resources.displayMetrics
        val refresh = try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        } catch (_: Throwable) {
            null
        }
        return DisplaySummary(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshHz = refresh?.takeIf { it > 0f },
            note = if (refresh == null) {
                "Resolution and density are public; refresh rate is not reported by this display."
            } else {
                "Refresh rate is the display's current mode, which the system may change at runtime."
            }
        )
    }

    fun networkSummary(context: Context): NetworkSummary {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkSummary(false, "Unknown", null, null, null, false, "ConnectivityManager unavailable")

        return try {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            if (caps == null) {
                NetworkSummary(
                    connected = false,
                    transport = "None",
                    metered = null,
                    downloadKbps = null,
                    uploadKbps = null,
                    vpnActive = false,
                    note = "No active network. Status reads the system's current default network only."
                )
            } else {
                val transports = buildList {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                }
                NetworkSummary(
                    connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    transport = if (transports.isEmpty()) "Unknown" else transports.joinToString(" + "),
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    downloadKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
                    uploadKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
                    vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    note = "Reports link estimate for the default network, not measured throughput. Requires the ACCESS_NETWORK_STATE permission."
                )
            }
        } catch (_: Throwable) {
            NetworkSummary(false, "Unknown", null, null, null, false, "Network status could not be read on this device.")
        }
    }

    fun sensorSummary(context: Context): SensorSummary {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return SensorSummary(0, emptyList(), TRACKED_SENSORS.map { it.first }, "SensorManager unavailable")
        val all = try {
            sm.getSensorList(Sensor.TYPE_ALL).mapNotNull { it?.name }
        } catch (_: Throwable) {
            emptyList()
        }
        val presentLabels = TRACKED_SENSORS.mapNotNull { (label, type) ->
            val available = try {
                sm.getDefaultSensor(type) != null
            } catch (_: Throwable) {
                false
            }
            if (available) label else null
        }
        val (present, missing) = sensorCoverage(presentLabels)
        return SensorSummary(
            total = all.size,
            present = present,
            missing = missing,
            note = "Sensor availability is reported by the device HAL; virtual or emulated sensors may be listed as present."
        )
    }
}
