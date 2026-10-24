package com.neon.eq.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

enum class ShizukuState(val label: String, val description: String) {
    NOT_INSTALLED("Not Installed", "Shizuku Manager is not installed on this device"),
    NOT_RUNNING("Service Stopped", "Shizuku is installed, but the Shizuku server process is not running"),
    PERMISSION_REQUIRED("Permission Required", "Shizuku server is running, but SonicCore requires user authorization"),
    CONNECTED("Connected", "Connected to Shizuku privileged bridge; elevated diagnostics enabled"),
    UNSUPPORTED("Unsupported", "Shizuku IPC protocol is not supported on this platform")
}

object ShizukuBridge {

    const val SHIZUKU_PACKAGE_V3 = "moe.shizuku.privileged.api"
    const val SHIZUKU_PACKAGE_MANAGER = "moe.shizuku.manager"
    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    private const val PROVIDER_AUTHORITY = "moe.shizuku.privileged.api.provider"

    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, SHIZUKU_PACKAGE_V3) || isPackageInstalled(pm, SHIZUKU_PACKAGE_MANAGER)
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

    fun isRunning(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://$PROVIDER_AUTHORITY")
            val bundle = context.contentResolver.call(uri, "checkService", null, null)
            bundle != null || isProviderAvailable(context, PROVIDER_AUTHORITY)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isProviderAvailable(context: Context, authority: String): Boolean {
        return try {
            val pi = context.packageManager.resolveContentProvider(authority, 0)
            pi != null
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(context: Context): Boolean {
        return try {
            context.checkCallingOrSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun checkState(context: Context): ShizukuState {
        if (!isInstalled(context)) {
            return ShizukuState.NOT_INSTALLED
        }
        if (!isRunning(context)) {
            return ShizukuState.NOT_RUNNING
        }
        if (!hasPermission(context)) {
            return ShizukuState.PERMISSION_REQUIRED
        }
        return ShizukuState.CONNECTED
    }

    /**
     * Narrowly-scoped diagnostic query via Shizuku provider if connected.
     * Always falls back safely to standard unprivileged mode if Shizuku is not running.
     */
    fun queryRestrictedSetting(context: Context, key: String): String? {
        val state = checkState(context)
        if (state != ShizukuState.CONNECTED) {
            return null
        }
        return try {
            val uri = Uri.parse("content://$PROVIDER_AUTHORITY")
            val extras = Bundle().apply { putString("key", key) }
            val result = context.contentResolver.call(uri, "getSetting", null, extras)
            result?.getString("value")
        } catch (_: Throwable) {
            null
        }
    }

    fun getLaunchManagerIntent(context: Context): Intent? {
        val pm = context.packageManager
        val intent1 = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE_MANAGER)
        if (intent1 != null) return intent1
        return pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE_V3)
    }
}
