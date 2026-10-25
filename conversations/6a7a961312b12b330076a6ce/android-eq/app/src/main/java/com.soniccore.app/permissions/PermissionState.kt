package com.soniccore.app.permissions

/**
 * State of a permission.
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    DENIED_FOREVER,
    SESSION_ACTIVE,
    SESSION_PROMPTED,
    NOT_DECLARED,
    NOT_SUPPORTED,
    UNKNOWN;

    val label: String get = when (this) {
        GRANTED -> "Granted"
        DENIED -> "Denied"
        DENIED_FOREVER -> "Denied forever"
        SESSION_ACTIVE -> "Session active"
        SESSION_PROMPTED -> "Prompted"
        NOT_DECLARED -> "Not declared"
        NOT_SUPPORTED -> "Not supported"
        UNKNOWN -> "Unknown"
    }
}
