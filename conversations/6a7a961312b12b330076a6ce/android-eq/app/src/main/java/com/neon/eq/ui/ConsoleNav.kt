package com.neon.eq.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The console's top-level destinations.
 *
 * The v3.7 build was one very long scroll holding every subsystem. This is the
 * replacement hierarchy: five destinations, each owning one job, so a control
 * exists in exactly one place.
 *
 *  - DASHBOARD  what the audio path is doing right now
 *  - EQUALIZER  the EQ workspace and the preset library
 *  - PLAYER     the app's own playback and its visualizer
 *  - STUDIO     the processing rack (chain, Pro FX, per-app profiles)
 *  - SYSTEM     capture, signal path, diagnostics, settings
 */
enum class ConsoleScreen(
    val label: String,
    val caption: String
) {
    DASHBOARD("DASH", "Audio activity"),
    EQUALIZER("EQ", "Equalizer & presets"),
    PLAYER("PLAYER", "Playback & visualizer"),
    STUDIO("STUDIO", "DSP chain & effects"),
    SYSTEM("SYSTEM", "Capture & signal path");

    companion object {
        /** Single source of truth for the nav bars, in display order. */
        val all: List<ConsoleScreen> = listOf(DASHBOARD, EQUALIZER, PLAYER, STUDIO, SYSTEM)
    }
}

/**
 * Navigation icons drawn with Canvas rather than a font or icon pack: the app
 * ships no icon dependency, and vector paths stay crisp on every density.
 */
@Composable
private fun ConsoleTabIcon(screen: ConsoleScreen, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = size.minDimension * 0.11f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        when (screen) {
            ConsoleScreen.DASHBOARD -> {
                drawCircle(tint, radius = w * 0.33f, center = Offset(w / 2f, h / 2f), style = stroke)
                drawCircle(tint, radius = w * 0.085f, center = Offset(w / 2f, h / 2f))
            }
            ConsoleScreen.EQUALIZER -> {
                val xs = listOf(0.17f, 0.39f, 0.61f, 0.83f)
                val heights = listOf(0.42f, 0.80f, 0.58f, 0.30f)
                for (i in xs.indices) {
                    val x = w * xs[i]
                    drawLine(
                        color = tint,
                        start = Offset(x, h * (0.5f - heights[i] / 2f)),
                        end = Offset(x, h * (0.5f + heights[i] / 2f)),
                        strokeWidth = strokeWidth * 1.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
            ConsoleScreen.PLAYER -> {
                val path = Path().apply {
                    moveTo(w * 0.30f, h * 0.20f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.30f, h * 0.80f)
                    close()
                }
                drawPath(path, tint)
            }
            ConsoleScreen.STUDIO -> {
                val ys = listOf(0.25f, 0.50f, 0.75f)
                for (y in ys) {
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.17f, h * y),
                        end = Offset(w * 0.60f, h * y),
                        strokeWidth = strokeWidth * 1.4f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(tint, radius = w * 0.075f, center = Offset(w * 0.83f, h * y))
                }
            }
            ConsoleScreen.SYSTEM -> {
                drawCircle(tint, radius = w * 0.075f, center = Offset(w * 0.5f, h * 0.78f))
                drawArc(
                    color = tint,
                    startAngle = -145f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.24f, h * 0.50f),
                    size = Size(w * 0.52f, h * 0.52f),
                    style = stroke
                )
                drawArc(
                    color = tint,
                    startAngle = -145f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.08f, h * 0.32f),
                    size = Size(w * 0.84f, h * 0.84f),
                    style = stroke
                )
            }
        }
    }
}

@Composable
private fun ConsoleTab(
    screen: ConsoleScreen,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .background(
                if (selected) ConsoleTokens.Cyan.copy(alpha = 0.10f) else Color.Transparent
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConsoleTabIcon(screen, if (selected) ConsoleTokens.Cyan else ConsoleTokens.InkDim)
        Spacer(Modifier.height(4.dp))
        Text(
            text = screen.label,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ConsoleTokens.Ink else ConsoleTokens.InkDim,
            maxLines = 1
        )
    }
}

@Composable
private fun ConsoleBottomBar(screen: ConsoleScreen, onSelect: (ConsoleScreen) -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        ConsoleTokens.Panel.copy(alpha = 0.86f),
                        ConsoleTokens.PanelDeep.copy(alpha = 0.97f)
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
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConsoleScreen.all.forEach { destination ->
            ConsoleTab(
                screen = destination,
                selected = destination == screen,
                modifier = Modifier.weight(1f)
            ) { onSelect(destination) }
        }
    }
}

@Composable
private fun ConsoleRail(screen: ConsoleScreen, onSelect: (ConsoleScreen) -> Unit) {
    val shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(92.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ConsoleTokens.Panel.copy(alpha = 0.88f),
                        ConsoleTokens.PanelDeep.copy(alpha = 0.97f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(ConsoleTokens.Hairline, ConsoleTokens.HairlineSoft)
                ),
                shape = shape
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ConsoleScreen.all.forEach { destination ->
            val selected = destination == screen
            ConsoleTab(
                screen = destination,
                selected = selected,
                modifier = Modifier.fillMaxWidth()
            ) { onSelect(destination) }
        }
    }
}

/**
 * The console shell: aurora backdrop plus responsive navigation.
 *
 * Portrait puts the five destinations in a glass bar along the bottom;
 * landscape moves them into a left rail so the wide workspace is never stolen
 * from the content. Only one destination renders at a time.
 */
@Composable
fun ConsoleScaffold(
    screen: ConsoleScreen,
    onSelect: (ConsoleScreen) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConsoleRail(screen, onSelect)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                ConsoleBottomBar(screen, onSelect)
            }
        }
    }
}
