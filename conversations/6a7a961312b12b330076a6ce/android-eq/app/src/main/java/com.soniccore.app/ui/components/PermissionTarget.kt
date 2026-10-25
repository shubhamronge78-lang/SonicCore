package com.soniccore.app.ui.components

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
    OTHER
}
