package com.soniccore.app.ui.design

internal object GlassIntensityPreference {
    const val KEY = "glass"
    const val DEFAULT_LEVEL = 1
    val LEVELS = listOf("Subtle" to 0, "Liquid" to 1, "Crystal" to 2)

    fun parse(storedValue: String?): Int =
        storedValue?.toIntOrNull()?.coerceIn(0, 2) ?: DEFAULT_LEVEL

    fun serialize(level: Int): String = level.coerceIn(0, 2).toString()
}
