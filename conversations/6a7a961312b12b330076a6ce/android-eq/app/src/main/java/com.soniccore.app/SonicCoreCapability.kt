package com.soniccore.app

/**
 * Capability status for SonicCore category/dashboard integration.
 * Mirrors the constants previously defined in com.neon.eq.ui.category.SonicCoreCapability.
 */
object SonicCoreCapability {
    val AVAILABLE = "AVAILABLE"
    val NEEDS_PERMISSION = "NEEDS_PERMISSION"
    val UNSUPPORTED = "UNSUPPORTED"
    val UNKNOWN = "UNKNOWN"
    val BLOCKED = "BLOCKED"
}
