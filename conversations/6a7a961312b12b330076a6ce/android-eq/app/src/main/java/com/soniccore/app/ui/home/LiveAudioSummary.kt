package com.soniccore.app.ui.home

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soniccore.app.capture.CaptureEqService
import com.soniccore.app.capture.CaptureFlowStatus
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.dsp.computeMeterLevels
import kotlinx.coroutines.delay

/**
 * Live audio summary for the SonicCore Home header and the category hubs.
 *
 * Every value is measured: the meters come from the native DSP's own readback,
 * the capture counters from the running service, and a state is only reported
 * as active when captured frames are actually reaching the output. A running
 * service alone never draws a signal.
 *
 * Polling stops whenever [active] is false or the screen is not resumed, so the
 * Home screen costs nothing while it is off-screen.
 */
@Composable
fun rememberLiveAudioSummary(
    engineEnabled: Boolean,
    limitedMode: Boolean,
    active: Boolean,
    routeName: String
): HomeAudioSummary {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tick by remember { mutableStateOf(0) }
    var summary by remember {
        mutableStateOf(
            HomeAudioSummary(
                engineEnabled = engineEnabled,
                stateLabel = "READY",
                stateDetail = "No capture session reported yet",
                toneIsActive = false,
                toneIsError = false,
                routeName = routeName,
                meters = MeterLevels.silent(),
                hasMeasuredSignal = false,
                clipCount = 0L,
                cpuPercent = null,
                latencyMs = null,
                captureRunning = false,
                bypassActive = CaptureEqService.bypass
            )
        )
    }

    val polling = active && isResumed
    LaunchedEffect(polling) {
        if (!polling) return@LaunchedEffect
        while (true) {
            delay(120)
            tick++
        }
    }

    LaunchedEffect(tick, polling, engineEnabled, limitedMode, routeName) {
        if (tick == 0 && !polling) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        val running = CaptureEqService.running
        val paused = CaptureEqService.paused
        val recentCaptureData = CaptureFlowStatus.hasRecentCaptureData(
            running = running,
            paused = paused,
            framesCaptured = CaptureEqService.framesCaptured,
            lastDataAtElapsedMs = CaptureEqService.lastDataAt,
            nowElapsedMs = now
        )
        val hasOutputFrames = CaptureFlowStatus.hasRecentOutputData(
            running = running,
            paused = paused,
            framesCaptured = CaptureEqService.framesCaptured,
            framesOutput = CaptureEqService.framesDone,
            lastDataAtElapsedMs = CaptureEqService.lastDataAt,
            nowElapsedMs = now
        )
        val dspAvailable = NeonDsp.available
        val previous = summary.meters
        val levels = if (polling && hasOutputFrames && dspAvailable) {
            val values = try {
                intArrayOf(
                    NeonDsp.inLRmsMs(), NeonDsp.inRRmsMs(), NeonDsp.outLRmsMs(), NeonDsp.outRRmsMs(),
                    NeonDsp.inLPkMs(), NeonDsp.inRPkMs(), NeonDsp.outLPkMs(), NeonDsp.outRPkMs()
                )
            } catch (_: RuntimeException) {
                null
            } catch (_: LinkageError) {
                null
            }
            computeMeterLevels(values, previous.peaks)
        } else {
            MeterLevels.silent()
        }
        val state = when {
            !dspAvailable || CaptureEqService.lastError != null -> "ERROR"
            limitedMode || (running && CaptureEqService.noEligiblePlayback) -> "LIMITED"
            running && paused -> "PAUSED"
            running && CaptureEqService.bypass -> "BYPASS"
            hasOutputFrames -> "ACTIVE"
            running -> "WAITING"
            else -> "READY"
        }
        summary = HomeAudioSummary(
            engineEnabled = engineEnabled,
            stateLabel = state,
            stateDetail = when {
                !dspAvailable -> "Native DSP unavailable · ${NeonDsp.loadError ?: "library load failed"}"
                CaptureEqService.lastError != null -> "Engine error · ${CaptureEqService.lastError}"
                limitedMode -> "Limited mode · this device restricts one or more audio effects"
                running && CaptureEqService.noEligiblePlayback ->
                    "Capture is unavailable for the current source application"
                running && paused -> "Capture is paused · no live audio is being processed"
                running && CaptureEqService.bypass ->
                    "Capture session is in bypass · DSP processing is not active"
                hasOutputFrames -> "Captured input and output frames are present · " +
                    CaptureEqService.captureSampleRate + " Hz · stereo"
                running -> "Capture session is running · waiting for live PCM frames"
                recentCaptureData -> "Frames captured · no processed output reported yet"
                else -> "No capture session · system EQ ${if (engineEnabled) "enabled" else "disabled"}"
            },
            toneIsActive = hasOutputFrames,
            toneIsError = !dspAvailable || CaptureEqService.lastError != null,
            routeName = routeName,
            meters = levels,
            hasMeasuredSignal = hasOutputFrames && levels.hasSignal(),
            clipCount = if (dspAvailable) runCatching { NeonDsp.clipCount() }.getOrDefault(0L) else 0L,
            cpuPercent = if (running && recentCaptureData && CaptureEqService.dspLoadPct.isFinite())
                CaptureEqService.dspLoadPct else null,
            latencyMs = CaptureEqService.totalLatencyMs.takeIf { running && it > 0.0 && it.isFinite() },
            captureRunning = running,
            // Read directly from the service flag the bypass switch itself
            // writes — never inferred from "a session is running".
            bypassActive = CaptureEqService.bypass
        )
    }

    return summary
}
