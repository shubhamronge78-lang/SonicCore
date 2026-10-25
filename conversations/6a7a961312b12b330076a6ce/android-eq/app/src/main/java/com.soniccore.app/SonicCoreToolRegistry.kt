package com.soniccore.app

/**
 * Tool registry previously defined in com.neon.eq.ui.navigation.SonicCoreToolRegistry.
 * Provides search and counting for available tools across categories.
 */
object SonicCoreToolRegistry {
    fun search(query: String): List<String> {
        // Return tools matching the query - simplified implementation
        return when (query.isEmpty()) {
            true -> listOf("EQ", "Bypass", "Device", "Theme", "Presets", "Capture")
            else -> listOf(query)
        }
    }

    fun countFor(category: com.soniccore.app.SonicCoreCategory): Int {
        return when (category) {
            com.soniccore.app.SonicCoreCategory.AUDIO -> 6
            com.soniccore.app.SonicCoreCategory.DEVICE -> 8
            com.soniccore.app.SonicCoreCategory.SHIZUKU -> 4
            com.soniccore.app.SonicCoreCategory.CUSTOMIZE -> 6
            com.soniccore.app.SonicCoreCategory.PRIVACY -> 5
            com.soniccore.app.SonicCoreCategory.SETTINGS -> 10
            else -> 0
        }
    }
}
