package com.neon.eq.system

import android.app.WallpaperManager
import android.content.Context
import android.os.Build

/** Outcome of looking for the system wallpaper's dominant colour. */
data class WallpaperAccent(val argb: Int, val available: Boolean, val detail: String)

/**
 * Source of the "Dynamic Wallpaper" theme's tint.
 *
 * Android only hands `WallpaperColors` to an app that holds the signature
 * permission `READ_WALLPAPER_INTERNAL`, so on an ordinary install this probe
 * gets a `SecurityException` and reports the tint as unavailable instead of
 * inventing one. The decision logic is pure so the JVM tests cover it without
 * an Android framework.
 */
object WallpaperAccentProbe {
    const val UNAVAILABLE_DETAIL = "Wallpaper colours are not readable by this app on this device"
    const val AVAILABLE_DETAIL = "Tinted from the current system wallpaper"

    /** Pure decision: `null` means the platform would not give us a colour. */
    fun evaluate(rawAccent: Int?, fallback: Int): WallpaperAccent =
        if (rawAccent == null) WallpaperAccent(fallback, available = false, detail = UNAVAILABLE_DETAIL)
        else WallpaperAccent(rawAccent, available = true, detail = AVAILABLE_DETAIL)

    /** API 27+ only: earlier releases have no `WallpaperColors` API at all. */
    fun probe(context: Context, fallback: Int): WallpaperAccent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return evaluate(null, fallback)
        val raw = try {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
        } catch (_: Throwable) {
            null
        }
        return evaluate(raw, fallback)
    }
}
