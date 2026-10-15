package com.neon.eq

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text

/**
 * Glass console components — Liquid Glass design language for the SonicCore
 * professional audio console. These enhance the existing NeonCard with
 * console-specific layouts: stat rows, flow nodes, section headers with
 * status indicators, and animated flow connectors.
 */

/**
 * ConsoleCard — the primary Liquid Glass container for console sections.
 * Adds a top-edge highlight and optional status accent line compared to NeonCard.
 */
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val a = glassSurfaceAlpha()
    val bA = glassBorderAlpha()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(S.card.copy(alpha = a), S.cardAlt.copy(alpha = a))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        (accentColor ?: T.primary).copy(alpha = bA),
                        T.secondary.copy(alpha = bA * 0.5f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        content = content
    )
}

/**
 * ConsoleSectionHeader — gradient title with optional status chip on the right.
 */
@Composable
fun ConsoleSectionHeader(
    title: String,
    statusText: String? = null,
    statusColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientText(title, 13.sp, Brush.horizontalGradient(listOf(T.secondary, T.primary)))
        if (statusText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                statusText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor ?: T.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background((statusColor ?: T.primary).copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * ConsoleStatRow — a label + value row with optional status dot.
 * Used for meter readouts, telemetry lines, and diagnostic values.
 */
@Composable
fun ConsoleStatRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    dotColor: Color? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 9.sp, color = T.secondary, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 9.sp,
            color = valueColor ?: T.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * FlowNode — a single node in a signal path or DSP chain flow diagram.
 * Lights up when active, dims when inactive, shows error state in red.
 */
@Composable
fun FlowNode(
    label: String,
    sublabel: String? = null,
    state: FlowState,
    modifier: Modifier = Modifier
) {
    val (bg, border, text) = when (state) {
        FlowState.ACTIVE -> Triple(
            T.primary.copy(alpha = 0.18f),
            T.primary.copy(alpha = 0.6f),
            T.primary
        )
        FlowState.BYPASS -> Triple(
            T.secondary.copy(alpha = 0.10f),
            T.secondary.copy(alpha = 0.3f),
            T.secondary
        )
        FlowState.ERROR -> Triple(
            StatusColors.error.copy(alpha = 0.15f),
            StatusColors.error.copy(alpha = 0.5f),
            StatusColors.error
        )
        FlowState.IDLE -> Triple(
            S.cardDeep.copy(alpha = 0.4f),
            S.borderDim.copy(alpha = 0.5f),
            T.secondary.copy(alpha = 0.6f)
        )
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = text)
        if (sublabel != null) {
            Text(sublabel, fontSize = 7.sp, color = text.copy(alpha = 0.7f))
        }
    }
}

enum class FlowState { ACTIVE, BYPASS, ERROR, IDLE }

/**
 * FlowConnector — animated line between flow nodes.
 * Pulses when data is flowing (active), stays dim when idle.
 */
@Composable
fun FlowConnector(
    active: Boolean,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "flow")
    val pulse by transition.animateFloat(
        initialValue = if (active) 0.3f else 0.1f,
        targetValue = if (active) 0.8f else 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val color = if (active) T.primary.copy(alpha = pulse) else T.secondary.copy(alpha = 0.15f)

    if (vertical) {
        Box(
            modifier = modifier
                .width(2.dp)
                .height(20.dp)
                .background(color)
        )
    } else {
        Box(
            modifier = modifier
                .height(2.dp)
                .width(20.dp)
                .background(color)
        )
    }
}

/**
 * FlowDiagram — renders a vertical sequence of FlowNodes connected by
 * FlowConnectors. Used by Signal Path and DSP Chain sections.
 */
@Composable
fun FlowDiagram(
    nodes: List<Triple<String, String?, FlowState>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        nodes.forEachIndexed { i, (label, sub, state) ->
            FlowNode(label = label, sublabel = sub, state = state)
            if (i < nodes.size - 1) {
                FlowConnector(active = state == FlowState.ACTIVE, vertical = true)
            }
        }
    }
}

/**
 * GlassToggleChip — a selectable chip with glass styling for mode selectors.
 */
@Composable
fun GlassToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) T.primary else T.secondary
    Text(
        label,
        fontSize = 9.sp,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (selected) 0.18f else 0.08f))
            .border(
                1.dp,
                color.copy(alpha = if (selected) 0.4f else 0.15f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/**
 * GlassMetric — large display metric with label underneath.
 * Used for dashboard telemetry displays.
 */
@Composable
fun GlassMetric(
    label: String,
    value: String,
    unit: String = "",
    color: Color = T.primary
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(S.cardDeep.copy(alpha = 0.3f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (unit.isNotEmpty()) {
            Text(unit, fontSize = 8.sp, color = color.copy(alpha = 0.7f))
        }
        Text(label, fontSize = 7.sp, color = T.secondary, modifier = Modifier.padding(top = 2.dp))
    }
}
