package com.neon.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DspChainFlow — visual DSP processing chain diagram.
 * Shows the canonical signal order:
 *   PREAMP → PARAMETRIC → GRAPHIC → BASS/TREBLE → STEREO → COMPRESSOR → CROSSFEED → LIMITER
 *
 * Each stage is a node that lights up when active and dims when bypassed.
 * Tapping a node toggles its bypass state (delegated to caller).
 */
@Composable
fun DspChainFlow(
    stages: List<DspStageInfo>,
    onToggleStage: (String) -> Unit,
    onEditStage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ConsoleCard(modifier = modifier, accentColor = T.accent) {
        ConsoleSectionHeader("DSP CHAIN", "PROCESSING ORDER", T.accent)
        Spacer(Modifier.height(4.dp))
        Text(
            "Canonical signal order — each stage is measured from the native DSP. Tap EDIT to adjust, BYPASS/ENABLE to toggle.",
            fontSize = 8.sp, color = T.secondary
        )
        Spacer(Modifier.height(10.dp))

        stages.forEachIndexed { i, stage ->
            DspStageNode(
                stage = stage,
                onToggle = { onToggleStage(stage.key) },
                onEdit = { onEditStage(stage.key) }
            )
            if (i < stages.size - 1) {
                FlowConnector(active = stage.on, vertical = true)
            }
        }
    }
}

data class DspStageInfo(
    val key: String,
    val label: String,
    val on: Boolean,
    val moving: Boolean = false,
    val isPlaceholder: Boolean = false
)

@Composable
private fun DspStageNode(
    stage: DspStageInfo,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val state = when {
        stage.isPlaceholder -> FlowState.IDLE
        stage.on && stage.moving -> FlowState.ACTIVE
        stage.on -> FlowState.BYPASS
        else -> FlowState.IDLE
    }
    val (bg, borderC, textC) = when (state) {
        FlowState.ACTIVE -> Triple(T.primary.copy(alpha = 0.15f), T.primary.copy(alpha = 0.5f), T.primary)
        FlowState.BYPASS -> Triple(T.secondary.copy(alpha = 0.08f), T.secondary.copy(alpha = 0.25f), T.secondary)
        FlowState.ERROR -> Triple(StatusColors.error.copy(alpha = 0.12f), StatusColors.error.copy(alpha = 0.4f), StatusColors.error)
        FlowState.IDLE -> Triple(S.cardDeep.copy(alpha = 0.3f), S.borderDim.copy(alpha = 0.4f), T.secondary.copy(alpha = 0.5f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderC, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (stage.on && stage.moving) T.primary else if (stage.on) T.secondary.copy(alpha = 0.5f) else S.borderDim)
        )
        Spacer(Modifier.width(8.dp))
        Text(stage.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = textC, modifier = Modifier.weight(1f))

        if (stage.isPlaceholder) {
            Text("API READY", fontSize = 8.sp, color = T.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(T.secondary.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
        } else {
            // EDIT button
            Text("EDIT", fontSize = 8.sp, color = if (stage.on) T.primary else T.secondary.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (stage.on) T.primary else T.secondary).copy(alpha = if (stage.on) 0.12f else 0.06f)
                    .clickable { if (stage.on) onEdit() }
                    .padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(Modifier.width(6.dp))
            // BYPASS/ENABLE toggle
            Text(
                if (stage.on) "BYPASS" else "ENABLE",
                fontSize = 8.sp,
                color = if (stage.on) StatusColors.warn else T.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (stage.on) StatusColors.warn else T.primary
                    ).copy(alpha = 0.12f)
                    .clickable { onToggle() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
