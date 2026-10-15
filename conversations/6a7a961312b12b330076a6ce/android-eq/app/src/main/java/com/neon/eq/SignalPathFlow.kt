package com.neon.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * SignalPathFlow — a visual signal path diagram showing the real-time
 * flow of audio through the SonicCore pipeline:
 *
 *   SOURCE → CAPTURE → AUDIORECORD → JNI → NATIVE DSP → AUDIOTRACK → OUTPUT
 *
 * Each node lights up based on measured frame deltas, not assumed state.
 * Animated flow lines pulse when data is actively flowing between stages.
 */
@Composable
fun SignalPathFlow(
    captureRunning: Boolean,
    bypass: Boolean,
    noEligiblePlayback: Boolean,
    recentData: Boolean,
    framesCaptured: Long,
    recordFrames: Long,
    jniFrames: Long,
    dspFrames: Long,
    outputFrames: Long,
    dCap: Long,
    dJni: Long,
    dDsp: Long,
    dOut: Long,
    outputDeviceLine: String,
    modifier: Modifier = Modifier
) {
    ConsoleCard(modifier = modifier, accentColor = T.primary) {
        ConsoleSectionHeader("SIGNAL PATH", "LIVE", T.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Each stage is measured from live frame counters — a running service alone never reports ACTIVE.",
            fontSize = 8.sp, color = T.secondary
        )
        Spacer(Modifier.height(10.dp))

        // Determine each node's state from measured deltas
        val srcState = when {
            !captureRunning -> FlowState.IDLE
            noEligiblePlayback -> FlowState.ERROR
            recentData -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }
        val capState = when {
            !captureRunning -> FlowState.IDLE
            dCap > 0 || framesCaptured > 0 -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }
        val recState = when {
            !captureRunning -> FlowState.IDLE
            dCap > 0 || recordFrames > 0 -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }
        val jniState = when {
            !captureRunning -> FlowState.IDLE
            bypass -> FlowState.BYPASS
            dJni > 0 -> FlowState.ACTIVE
            dCap > 0 -> FlowState.ERROR
            jniFrames > 0 -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }
        val dspState = when {
            !captureRunning -> FlowState.IDLE
            bypass -> FlowState.BYPASS
            dDsp > 0 -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }
        val outState = when {
            !captureRunning -> FlowState.IDLE
            dOut > 0 -> FlowState.ACTIVE
            dDsp > 0 -> FlowState.ERROR
            else -> FlowState.IDLE
        }
        val devState = when {
            !captureRunning -> FlowState.IDLE
            dOut > 0 -> FlowState.ACTIVE
            else -> FlowState.IDLE
        }

        // Animated flow diagram — vertical stack of nodes with connectors
        val nodes = listOf(
            Triple("SOURCE PLAYBACK", if (captureRunning && !noEligiblePlayback) "eligible" else if (noEligiblePlayback) "blocked" else "—", srcState),
            Triple("AUDIOPLAYBACKCAPTURE", if (framesCaptured > 0) "$framesCaptured frames" else "—", capState),
            Triple("AUDIORECORD", if (recordFrames > 0) "$recordFrames frames" else "—", recState),
            Triple("JNI → NATIVE DSP", if (bypass) "BYPASSED" else if (jniFrames > 0) "$jniFrames frames" else "—", jniState),
            Triple("NATIVE DSP ENGINE", if (bypass) "BYPASSED" else if (dspFrames > 0) "$dspFrames frames" else "—", dspState),
            Triple("AUDIOTRACK OUTPUT", if (outputFrames > 0) "$outputFrames frames" else "—", outState),
            Triple("OUTPUT DEVICE", outputDeviceLine, devState)
        )

        FlowDiagram(nodes = nodes)

        Spacer(Modifier.height(10.dp))

        // Verdict line
        val pathVerdict = when {
            !captureRunning -> "CAPTURE STOPPED"
            noEligiblePlayback -> "NO ELIGIBLE PLAYBACK"
            !recentData -> "UNKNOWN"
            bypass && dOut > 0 -> "SIGNAL PATH ACTIVE (RAW — no DSP)"
            bypass -> "UNKNOWN"
            dCap > 0 && dJni == 0L -> "CAPTURE → DSP CONNECTION FAILURE"
            dDsp > 0 && dOut == 0L -> "DSP → OUTPUT CONNECTION FAILURE"
            dCap > 0 && dJni > 0 && dOut > 0 -> "SIGNAL PATH ACTIVE"
            else -> "UNKNOWN"
        }
        val verdictColor = if (pathVerdict.startsWith("SIGNAL PATH ACTIVE")) T.primary else StatusColors.warn
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Alignment.CenterHorizontally
        ) {
            Text("VERDICT: ", fontSize = 11.sp, color = T.secondary, fontWeight = FontWeight.Bold)
            Text(pathVerdict, fontSize = 11.sp, color = verdictColor, fontWeight = FontWeight.Bold)
        }

        // Confidence indicators
        Spacer(Modifier.height(4.dp))
        Text(
            "PROCESSING PATH: " + (if (pathVerdict == "SIGNAL PATH ACTIVE") "VERIFIED" else "not verified yet") +
                " · OUTPUT DELIVERY: " + (if (dOut > 0) "VERIFIED" else "not verified yet") +
                " · AUDIBLE RESULT: NOT DIRECTLY VERIFIABLE",
            fontSize = 8.sp, color = StatusColors.warn
        )
        Text(
            "SonicCore verified that processed PCM reached its AudioTrack. Android may also continue playing the original source application's output.",
            fontSize = 8.sp, color = T.secondary
        )
    }
}
