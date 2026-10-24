package com.neon.eq.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.neon.eq.capture.CaptureEqService

/**
 * Honest permission reporting for the Permissions & Privacy Center.
 *
 * The model is split in two on purpose:
 *  * [PermissionCenter.evaluate] takes plain data and performs no Android calls,
 *    so every status mapping is unit-testable on the JVM;
 *  * [PermissionCenter.probe] collects that data from the platform.
 *
 * A permission that the app never declares is reported as NOT_DECLARED rather
 * than DENIED — there is nothing for the user to grant, and pretending
 * otherwise would send them to a settings screen that cannot help.
 */
enum class PermissionState(val label: String) {
    GRANTED("Granted"),
    DENIED("Denied"),
    NOT_DECLARED("Not declared"),
    NOT_SUPPORTED("Not supported"),
    SESSION_PROMPTED("Per-session consent"),
    SESSION_ACTIVE("Consent active")
}

/** Where the user can act on an entry, resolved to a real Intent by the screen. */
enum class PermissionTarget {
    APP_DETAILS,
    APP_NOTIFICATIONS,
    BLUETOOTH,
    USAGE_ACCESS,
    BATTERY_OPTIMIZATION,
    NONE
}

data class PermissionEntry(
    val id: String,
    val title: String,
    val reason: String,
    val state: PermissionState,
    val detail: String,
    val recovery: String? = null,
    val target: PermissionTarget = PermissionTarget.NONE,
    val optional: Boolean = true
)

/** Raw, already-collected facts. No Android types so tests can build one directly. */
data class PermissionProbeResult(
    val apiLevel: Int,
    val declared: Set<String>,
    val granted: Set<String>,
    val batteryOptimizationIgnored: Boolean,
    val usageAccessGranted: Boolean,
    val captureSessionActive: Boolean
)

object PermissionCenter {

    val RUNTIME_PERMISSIONS: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    /**
     * Accessibility and notification-listener access are only grantable when the
     * app ships the corresponding service. SonicCore ships neither, so both are
     * reported as NOT_DECLARED and are never advertised as a working toggle.
     */
    fun evaluate(probe: PermissionProbeResult): List<PermissionEntry> {
        val entries = mutableListOf<PermissionEntry>()

        entries += runtime(
            probe = probe,
            id = Manifest.permission.RECORD_AUDIO,
            title = "Record audio",
            reason = "Required by the Android framework to read captured playback audio.",
            requiredApi = Build.VERSION_CODES.O,
            target = PermissionTarget.APP_DETAILS,
            optional = false
        )
        entries += runtime(
            probe = probe,
            id = Manifest.permission.MODIFY_AUDIO_SETTINGS,
            title = "Modify audio settings",
            reason = "Normal permission used for audio routing and volume inspection.",
            requiredApi = Build.VERSION_CODES.O,
            target = PermissionTarget.APP_DETAILS,
            optional = false
        )
        entries += runtime(
            probe = probe,
            id = Manifest.permission.POST_NOTIFICATIONS,
            title = "Notifications",
            reason = "Shows the foreground service notification for the running audio engine.",
            requiredApi = Build.VERSION_CODES.TIRAMISU,
            target = PermissionTarget.APP_NOTIFICATIONS,
            optional = false
        )
        entries += runtime(
            probe = probe,
            id = Manifest.permission.BLUETOOTH_CONNECT,
            title = "Nearby Bluetooth devices",
            reason = "Needed to read Bluetooth output device names on Android 12 and newer.",
            requiredApi = Build.VERSION_CODES.S,
            target = PermissionTarget.BLUETOOTH,
            optional = true
        )

        entries += PermissionEntry(
            id = "svc_accessibility",
            title = "Accessibility service",
            reason = "SonicCore ships no accessibility service, so there is nothing to enable.",
            state = PermissionState.NOT_DECLARED,
            detail = "Elevated input control is intentionally out of scope. Audio processing never depends on accessibility access.",
            optional = true
        )
        entries += PermissionEntry(
            id = "svc_notification_listener",
            title = "Notification listener",
            reason = "SonicCore ships no notification listener service.",
            state = PermissionState.NOT_DECLARED,
            detail = "Notification content is not read. Per-app audio profiles use the public audio session callback instead.",
            optional = true
        )

        entries += PermissionEntry(
            id = "usage_access",
            title = "Usage access",
            reason = "Optional: lets SonicCore see which app owns the active audio session.",
            state = if (probe.usageAccessGranted) PermissionState.GRANTED else PermissionState.DENIED,
            detail = if (probe.usageAccessGranted) {
                "Granted through app-ops. Used only to label the current audio owner."
            } else {
                "Not granted. The app manager falls back to the public audio session, which omits some package names."
            },
            recovery = if (probe.usageAccessGranted) null else "Grant usage access in system settings if you want audio-owner labels.",
            target = PermissionTarget.USAGE_ACCESS,
            optional = true
        )

        entries += PermissionEntry(
            id = "battery_optimization",
            title = "Battery optimization exemption",
            reason = "Optional: keeps the background audio service alive on aggressive OEM power managers.",
            state = if (probe.batteryOptimizationIgnored) PermissionState.GRANTED else PermissionState.DENIED,
            detail = if (probe.batteryOptimizationIgnored) {
                "Exempt. The system will not throttle the background audio service."
            } else {
                "Optimization is active. The system may stop the background service; foreground playback keeps running."
            },
            recovery = if (probe.batteryOptimizationIgnored) null else "Request an exemption from the system dialog.",
            target = PermissionTarget.BATTERY_OPTIMIZATION,
            optional = true
        )

        entries += PermissionEntry(
            id = "capture_consent",
            title = "System audio capture consent",
            reason = "MediaProjection consent is granted by the system per capture session and cannot be pre-approved.",
            state = if (probe.captureSessionActive) PermissionState.SESSION_ACTIVE else PermissionState.SESSION_PROMPTED,
            detail = if (probe.captureSessionActive) {
                "A capture session is running with a valid consent token."
            } else {
                "No active session. Android shows its own consent prompt every time capture starts; SonicCore cannot bypass or persist it."
            },
            recovery = if (probe.captureSessionActive) null else "Start System Capture to receive the system consent prompt.",
            target = PermissionTarget.NONE,
            optional = false
        )

        return entries
    }

    private fun runtime(
        probe: PermissionProbeResult,
        id: String,
        title: String,
        reason: String,
        requiredApi: Int,
        target: PermissionTarget,
        optional: Boolean
    ): PermissionEntry {
        val state = when {
            probe.apiLevel < requiredApi -> PermissionState.NOT_SUPPORTED
            id !in probe.declared -> PermissionState.NOT_DECLARED
            id in probe.granted -> PermissionState.GRANTED
            else -> PermissionState.DENIED
        }
        val detail = when (state) {
            PermissionState.GRANTED -> "Granted to SonicCore."
            PermissionState.DENIED -> "Not granted. Features that depend on it stay disabled."
            PermissionState.NOT_DECLARED -> "SonicCore does not request this permission."
            PermissionState.NOT_SUPPORTED -> "This Android version (API ${probe.apiLevel}) does not expose this permission."
            PermissionState.SESSION_PROMPTED, PermissionState.SESSION_ACTIVE -> "Prompted by the system per session."
        }
        val recovery = when (state) {
            PermissionState.DENIED -> "Open settings to grant it."
            PermissionState.NOT_SUPPORTED -> "No action available on this Android version."
            else -> null
        }
        return PermissionEntry(
            id = id,
            title = title,
            reason = reason,
            state = state,
            detail = detail,
            recovery = recovery,
            target = if (state == PermissionState.DENIED) target else PermissionTarget.NONE,
            optional = optional
        )
    }

    fun probe(context: Context): List<PermissionEntry> {
        val declared = try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toSet()
                ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
        // Check every declared permission: normal permissions (MODIFY_AUDIO_SETTINGS)
        // are granted at install time and must not be reported as denied.
        val granted = declared.filter { permission ->
            try {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        }.toSet()

        return evaluate(
            PermissionProbeResult(
                apiLevel = Build.VERSION.SDK_INT,
                declared = declared,
                granted = granted,
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
                usageAccessGranted = hasUsageAccess(context),
                captureSessionActive = CaptureEqService.running
            )
        )
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (_: Throwable) {
        false
    }

    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }
}
