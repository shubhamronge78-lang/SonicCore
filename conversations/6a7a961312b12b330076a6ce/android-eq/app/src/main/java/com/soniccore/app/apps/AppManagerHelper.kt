package com.soniccore.app.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

data class InstalledAudioApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val isCurrentlyPlaying: Boolean = false,
    val assignedPreset: String? = null
)

object AppManagerHelper {

    fun getInstalledAudioApps(
        context: Context,
        currentlyPlayingPackage: String? = null,
        perAppProfiles: Map<String, String> = emptyMap()
    ): List<InstalledAudioApp> {
        val pm = context.packageManager
        val result = mutableListOf<InstalledAudioApp>()
        val seen = mutableSetOf<String>()

        // 1. Add currently playing package if available
        if (!currentlyPlayingPackage.isNullOrBlank()) {
            val app = createFromPackage(pm, currentlyPlayingPackage, true, perAppProfiles[currentlyPlayingPackage])
            if (app != null) {
                result.add(app)
                seen.add(currentlyPlayingPackage)
            }
        }

        // 2. Add packages that have assigned profiles
        for ((pkg, preset) in perAppProfiles) {
            if (pkg !in seen) {
                val app = createFromPackage(pm, pkg, false, preset)
                if (app != null) {
                    result.add(app)
                    seen.add(pkg)
                }
            }
        }

        // 3. Query all launchable apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Throwable) {
            emptyList()
        }

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue // Don't list ourselves
            if (pkg in seen) continue

            val app = createFromPackage(pm, pkg, false, perAppProfiles[pkg])
            if (app != null) {
                result.add(app)
                seen.add(pkg)
            }
        }

        // Sort: Playing first, then assigned profiles, then alphabetical by appName
        return result.sortedWith(
            compareByDescending<InstalledAudioApp> { it.isCurrentlyPlaying }
                .thenByDescending { it.assignedPreset != null }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun createFromPackage(
        pm: PackageManager,
        packageName: String,
        isPlaying: Boolean,
        assignedPreset: String?
    ): InstalledAudioApp? {
        return try {
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val appInfo = pkgInfo.applicationInfo
            val label = appInfo?.loadLabel(pm)?.toString() ?: packageName
            val isSystem = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } else false

            InstalledAudioApp(
                packageName = packageName,
                appName = label,
                versionName = pkgInfo.versionName ?: "Unknown",
                targetSdk = appInfo?.targetSdkVersion ?: 0,
                isSystemApp = isSystem,
                isCurrentlyPlaying = isPlaying,
                assignedPreset = assignedPreset
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openAppSettings(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
