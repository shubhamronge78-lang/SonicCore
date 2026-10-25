package com.soniccore.app.permissions

/**
 * Target of a permission check or request.
 */
enum class PermissionTarget {
    MICROPHONE,
    STORAGE,
    CAMERA,
    LOCATION,
    BLUETOOTH,
    NOTIFICATIONS,
    SYSTEM_SETTINGS,
    AUDIO_SETTINGS,
    APP_DETAILS,
    APP_NOTIFICATIONS,
    USAGE_ACCESS,
    BATTERY_OPTIMIZATION,
    NONE,
    OTHER
}
