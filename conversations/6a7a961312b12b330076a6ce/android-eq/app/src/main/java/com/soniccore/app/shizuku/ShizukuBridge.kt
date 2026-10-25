package com.soniccore.app.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

enum class ShizukuState(val label: String, val description: String) {
    NOT_INSTALLED("Not Installed", "Shizuku Manager is not installed on this device"),
    NOT_RUNNING("Service Stopped", "Shizuku is installed, but the Shizuku server process is not running"),
    PERMISSION_REQUIRED("Permission Required", "Shizuku server is running, but SonicCore requires user authorization"),
    CONNECTED("Connected", "Connected to Shizuku privileged bridge; elevated diagnostics enabled"),
    DISCONNECTED("Disconnected", "The Shizuku connection was lost after it had been established"),
    UNSUPPORTED("Unsupported", "Shizuku IPC protocol is not supported on this platform")
}

/**
 * Real Shizuku client integration, built on the official `dev.rikka.shizuku:api`
 * client (v13). This object owns the whole binder lifecycle:
 *
 *  * install detection (with the manifest `<queries>` entries),
 *  * binder liveness through `Shizuku.pingBinder()`,
 *  * authorization through `Shizuku.checkSelfPermission()` /
 *    `Shizuku.requestPermission(...)` with a result listener,
 *  * binder received / dead listeners, so a restart or a dropped connection is
 *    reported as such instead of being guessed.
 *
 * It performs no privileged work itself — that lives in [ShizukuDiagnostics] — and
 * it never throws into the UI: every client call is guarded, and an unavailable
 * client is reported as a [ShizukuState], never as a success.
 */
object ShizukuBridge {

    const val SHIZUKU_PACKAGE_V3 = "moe.shizuku.privileged.api"
    const val SHIZUKU_PACKAGE_MANAGER = "moe.shizuku.manager"

    /** Legacy (<v11) permission, still declared in the manifest for old clients. */
    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

    /** Arbitrary app-local request code for [rikka.shizuku.Shizuku.requestPermission]. */
    const val PERMISSION_REQUEST_CODE = 0x5343

    /**
     * True once a binder was observed in this process. Used to tell "the service was
     * never started" from "the connection we had was lost".
     */
    @Volatile
    private var hasSeenBinder = false

    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, SHIZUKU_PACKAGE_V3) || isPackageInstalled(pm, SHIZUKU_PACKAGE_MANAGER)
    }

    /** Which Shizuku package is present, if any — used for honest status reporting. */
    fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return when {
            isPackageInstalled(pm, SHIZUKU_PACKAGE_V3) -> SHIZUKU_PACKAGE_V3
            isPackageInstalled(pm, SHIZUKU_PACKAGE_MANAGER) -> SHIZUKU_PACKAGE_MANAGER
            else -> null
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /** True when the Shizuku service is running and its binder is reachable. */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** True when the user has authorized SonicCore in Shizuku Manager. */
    fun hasPermission(): Boolean = runCatching {
        if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    /** True when the user chose "deny and don't ask again" — asking again is pointless. */
    fun shouldShowRequestPermissionRationale(): Boolean =
        runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    /** False when the installed Shizuku server predates the v11 API this client needs. */
    fun isSupportedClient(): Boolean = runCatching { !Shizuku.isPreV11() }.getOrDefault(false)

    /**
     * Asks Shizuku Manager to show its authorization dialog.
     *
     * @return false when the request could not be dispatched (binder gone), so the
     *         caller can tell the user to start the service instead of waiting.
     */
    fun requestPermission(): Boolean = runCatching {
        if (!isRunning() || Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }
    }.getOrDefault(false)

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(listener)
        }
    }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.removeBinderReceivedListener(listener) }
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.addBinderDeadListener(listener) }
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.removeBinderDeadListener(listener) }
    }

    /**
     * Probes the real client and reports the first unmet prerequisite. Once a binder
     * has been seen in this process, losing it is reported as [ShizukuState.DISCONNECTED]
     * rather than as "never started".
     */
    fun checkState(context: Context): ShizukuState {
        if (Build.VERSION.SDK_INT < ShizukuOperations.MIN_API) return ShizukuState.UNSUPPORTED
        if (!isInstalled(context)) return ShizukuState.NOT_INSTALLED
        if (!isRunning()) {
            return if (hasSeenBinder) ShizukuState.DISCONNECTED else ShizukuState.NOT_RUNNING
        }
        if (!isSupportedClient()) return ShizukuState.UNSUPPORTED
        hasSeenBinder = true
        return if (hasPermission()) ShizukuState.CONNECTED else ShizukuState.PERMISSION_REQUIRED
    }

    /** Live snapshot for the eligibility engine. Only reads cached server facts. */
    fun environment(context: Context): ShizukuEnvironment {
        val state = checkState(context)
        val base = ShizukuEnvironment(state = state, deviceSdk = Build.VERSION.SDK_INT)
        return when (state) {
            ShizukuState.CONNECTED -> base.copy(
                serverVersion = runCatching { Shizuku.getVersion() }
                    .getOrDefault(ShizukuEnvironment.UNKNOWN_VALUE),
                serverPatchVersion = runCatching { Shizuku.getServerPatchVersion() }
                    .getOrDefault(ShizukuEnvironment.UNKNOWN_VALUE),
                serverUid = runCatching { Shizuku.getUid() }
                    .getOrDefault(ShizukuEnvironment.UNKNOWN_VALUE),
                selinuxContext = runCatching { Shizuku.getSELinuxContext() }.getOrNull(),
                permissionGranted = true,
                binderAlive = true
            )

            ShizukuState.PERMISSION_REQUIRED -> base.copy(
                binderAlive = true,
                permissionDeniedPermanently = shouldShowRequestPermissionRationale()
            )

            else -> base
        }
    }

    fun getLaunchManagerIntent(context: Context): Intent? {
        val pm = context.packageManager
        val intent1 = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE_MANAGER)
        if (intent1 != null) return intent1
        return pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE_V3)
    }
}
