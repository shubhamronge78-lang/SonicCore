package com.neon.eq.ui.design

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val SPLASH_HOLD_MS = 1050L
private const val SPLASH_ENTER_MS = 420
private const val SPLASH_EXIT_MS = 320

/**
 * First-frame SonicCore splash.
 *
 * The monogram scales in over the dark glass backdrop while a cyan→violet
 * waveform sweeps through it, then the overlay fades out and removes itself.
 * Purely presentational: it loads no resource, reports no progress, swallows
 * taps while it covers the console, and hides instantly when the system
 * animation scale is off. The visible flag is saveable, so a rotation does not
 * replay it.
 */
@Composable
fun SonicCoreSplashOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        } catch (_: Throwable) {
            true
        }
    }
    var visible by rememberSaveable { mutableStateOf(true) }
    val markProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!animationsEnabled) {
            visible = false
            return@LaunchedEffect
        }
        markProgress.animateTo(1f, tween(SPLASH_ENTER_MS))
        delay(SPLASH_HOLD_MS)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(SPLASH_EXIT_MS)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SonicCoreColors.Background,
                            SonicCoreColors.Surface,
                            SonicCoreColors.Background
                        )
                    )
                )
                // The splash covers the console, so it also swallows taps until
                // it goes away instead of letting them land on hidden controls.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val progress = markProgress.value
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 0.88f + 0.12f * progress
                            scaleY = 0.88f + 0.12f * progress
                            alpha = progress
                        }
                        .size(94.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    SonicCoreColors.Cyan.copy(alpha = 0.60f),
                                    SonicCoreColors.Violet.copy(alpha = 0.55f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.38f),
                                    SonicCoreColors.Cyan.copy(alpha = 0.32f)
                                )
                            ),
                            RoundedCornerShape(26.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SC",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                }

                Spacer(Modifier.height(18.dp))
                SplashWaveform(alpha = progress)
                Spacer(Modifier.height(14.dp))
                Text(
                    "SonicCore",
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.graphicsLayer { alpha = progress }
                )
                Text(
                    "PRO AUDIO CONSOLE",
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.graphicsLayer { alpha = progress }
                )
            }
        }
    }
}

/** Travelling sine through the brand cyan→violet, drawn with the live palette. */
@Composable
private fun SplashWaveform(alpha: Float) {
    val transition = rememberInfiniteTransition(label = "splashWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splashWavePhase"
    )
    val sweep by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashWaveSweep"
    )
    Canvas(
        modifier = Modifier
            .width(168.dp)
            .height(26.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        val steps = 64
        val amplitude = size.height / 2f - 2f
        val mid = size.height / 2f
        val path = Path()
        for (index in 0..steps) {
            val fraction = index / steps.toFloat()
            val y = mid - amplitude * sin(2f * PI.toFloat() * (fraction * 1.5f + phase))
            if (index == 0) {
                path.moveTo(fraction * size.width, y)
            } else {
                path.lineTo(fraction * size.width, y)
            }
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    SonicCoreColors.Cyan.copy(alpha = 0.20f),
                    SonicCoreColors.Cyan,
                    SonicCoreColors.Violet,
                    SonicCoreColors.Violet.copy(alpha = 0.20f)
                )
            ),
            style = Stroke(width = 2.4f)
        )
        drawCircle(
            color = SonicCoreColors.Cyan,
            radius = 3.2f,
            center = Offset(sweep * size.width, mid)
        )
    }
}
