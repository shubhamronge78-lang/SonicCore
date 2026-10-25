package com.soniccore.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.FloatProp

/**
 * Composition local for motion scale factor (1.0 = normal, 0.0 = reduced motion).
 */
val LocalMotionScale: ProvidableCompositionLocal<Float> = staticCompositionLocalOf { 1.0f }

/**
 * Returns the motion duration in milliseconds for a given base duration and motion scale.
 */
fun motionDurationMillis(baseMillis: Int, motionScale: Float): Int {
    return (baseMillis * motionScale).coerceAtLeast(0).toInt()
}
