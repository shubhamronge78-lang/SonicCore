package com.neon.eq.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neon.eq.SurfaceModes
import com.neon.eq.appGlassState
import com.neon.eq.appModeState
import com.neon.eq.glassSurfaceAlpha

/**
 * SonicCore v3.8 console design system.
 *
 * One place for the Liquid Glass language: the midnight canvas, the cyan/violet
 * aurora light field, translucent panel geometry and the motion policy.
 *
 * Everything in this file is pure UI rendering. Nothing here touches the DSP
 * engine, the capture path, the native bridge or any audio state — the console
 * can be restyled without any risk to audio.
 */
object ConsoleTokens {
    /** Deep midnight canvas — the reference's base, not flat AMOLED black. */
    val Midnight = Color(0xFF05080F)
    val MidnightDeep = Color(0xFF02040A)
    val Midnights: List<Color> = listOf(MidnightDeep, Midnight, Color(0xFF070D1A))

    /** Aurora light — the only two hues the chrome is allowed to use. */
    val Cyan = Color(0xFF00F0FF)
    val Violet = Color(0xFFA554FF)
    val Ice = Color(0xFF9BF3FF)

    /** Panel stock. */
    val Panel = Color(0xFF0C1526)
    val PanelDeep = Color(0xFF0A1020)

    /** Hairline border + inner top highlight that read as glass edges. */
    val Hairline = Color(0x24FFFFFF)
    val HairlineSoft = Color(0x0DFFFFFF)
    val Highlight = Color(0x38FFFFFF)

    /** Ink ramp. */
    val Ink = Color(0xFFEDF4FF)
    val InkSoft = Color(0xFFA9B9D9)
    val InkDim = Color(0xFF6E80A2)

    /** Panel corner geometry — one radius family for the whole console. */
    val PanelRadius: Dp = 20.dp
    val InsetRadius: Dp = 14.dp
}

/**
 * Motion policy for the console.
 *
 * Returns 0f when the platform animator duration scale is off — i.e. the user
 * (or the accessibility setting) asked for reduced motion. Callers use this to
 * swap an animated value for a static one; no animation is ever required to
 * understand or operate a control.
 */
@Composable
fun consoleMotionScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE
            )
        } catch (_: Throwable) {
            1f
        }
        if (scale <= 0.01f) 0f else 1f
    }
}

/**
 * The console backdrop: a midnight gradient plus cyan/violet aurora blooms.
 * Static under reduced motion. Deliberately drawn with radial gradients instead
 * of a real blur — a live blur costs GPU on low-end devices and reads as
 * nothing on a near-black surface.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = appModeState.value == SurfaceModes.DARK
    val intensity = when (appGlassState.value) {
        0 -> 0.055f
        2 -> 0.150f
        else -> 0.100f
    }
    val motion = consoleMotionScale()
    val transition = rememberInfiniteTransition(label = "aurora")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion > 0f) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraPhase"
    )
    val phase = if (motion > 0f) animatedPhase else 0.35f

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(ConsoleTokens.Midnights))
        if (!dark) return@Canvas
        val w = size.width
        val h = size.height

        fun bloom(cx: Float, cy: Float, radius: Float, color: Color, strength: Float) {
            if (strength <= 0.001f) return
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = strength), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Cyan aurora, drifting top-left.
        bloom(
            cx = w * (0.18f + 0.06f * phase),
            cy = h * 0.10f,
            radius = w * 1.05f,
            color = ConsoleTokens.Cyan,
            strength = intensity
        )
        // Violet aurora, drifting right.
        bloom(
            cx = w * (0.92f - 0.08f * phase),
            cy = h * (0.42f + 0.05f * phase),
            radius = w * 1.15f,
            color = ConsoleTokens.Violet,
            strength = intensity * 0.85f
        )
        // A cool floor wash so the lower half never goes dead flat.
        bloom(
            cx = w * 0.5f,
            cy = h * 1.02f,
            radius = w * 1.2f,
            color = ConsoleTokens.Cyan,
            strength = intensity * 0.45f
        )
    }
}

/**
 * The single glass panel primitive for the console.
 *
 * Layered translucency (not blur), a hairline vertical border and a top inner
 * highlight — the three things that make a surface read as glass over a dark
 * canvas. A drop shadow is added only at HIGH glass intensity, where the user
 * has explicitly opted into the extra GPU cost.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    tone: Color = ConsoleTokens.Panel,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(ConsoleTokens.PanelRadius)
    val base = glassSurfaceAlpha().coerceIn(0.55f, 0.97f)
    // Restrained by default, deeper only when the user opted into HIGH glass.
    val shadowed = Modifier.shadow(
        elevation = when (appGlassState.value) {
            0 -> 0.dp
            2 -> 16.dp
            else -> 7.dp
        },
        shape = shape,
        clip = false
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowed)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tone.copy(alpha = base),
                        tone.copy(alpha = (base - 0.07f).coerceAtLeast(0.5f))
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(ConsoleTokens.Hairline, ConsoleTokens.HairlineSoft)
                ),
                shape = shape
            )
    ) {
        // Inner top highlight: the lit edge of a glass sheet.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, ConsoleTokens.Highlight, Color.Transparent)
                    )
                )
        )
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
