package com.soniccore.app.permissions

import android.content.Context

/**
 * Probe the current permission status on the device.
 */
object PermissionCenter {
    fun probe(context: Context): List<PermissionEntry> {
        return emptyList()
    }
}

/**
 * A single permission entry.
 */
data class PermissionEntry(
    val title: String,
    val target: PermissionTarget,
    val state: PermissionState,
    val label: String,
    val optional: Boolean = false,
    val reason: String = "",
    val detail: String = "",
    val recovery: String? = null
)
