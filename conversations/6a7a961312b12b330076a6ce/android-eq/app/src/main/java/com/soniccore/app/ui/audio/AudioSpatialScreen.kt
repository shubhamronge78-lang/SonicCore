package com.soniccore.app.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soniccore.app.dsp.AudioControlRanges
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreToolHost
import kotlin.math.abs

/**
 * Audio → Stereo & Spatial.
 *
 * The channel operations the native chain really implements: stereo width,
 * balance, a hard channel swap and a mono downmix. Each control writes the
 * single `setStereo` parameter it belongs to, so the four stay independent.
 *
 * Room simulation and HRTF spatialisation are not implemented by SonicCore and
 * are reported as such instead of being faked.
 */
@Composable
fun AudioSpatialScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val state = host.audioControl
    val dspAvailable = NeonDsp.available
    var advanced by remember { mutableStateOf(false) }

    val disabledNote =
        "The native DSP bridge is not loaded on this device, so channel processing cannot be applied."

    fun apply(next: AudioControlState) = host.onAudioControlChange(next)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapabilityLegend(
            if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
            NeonDsp.loadError
                ?: "Width, balance, swap and mono are processed in the native chain, after the shelves and before the limiter."
        )

        AudioActionRow(
            listOf(
                AudioAction(
                    label = "RESET STEREO",
                    onClick = { apply(state.copy(width = 1f, balance = 0f)) },
                    enabled = dspAvailable && (state.width != 1f || state.balance != 0f)
                ),
                AudioAction(
                    label = "RESET CHANNELS",
                    onClick = { apply(state.copy(mono = false, swap = false)) },
                    enabled = dspAvailable && (state.mono || state.swap)
                )
            )
        )

        AudioSection(
            title = "Stereo width",
            description = "0.00 collapses the stereo image toward the centre, 1.00 is the untouched image, 4.00 widens it."
        ) {
            AudioSliderSetting(
                label = "Width",
                value = state.width,
                valueText = "%.2f×".format(state.width),
                onValueChange = { apply(state.copy(width = it)) },
                valueRange = AudioControlRanges.WIDTH,
                steps = 15,
                enabled = dspAvailable,
                capability = if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                disabledNote = disabledNote,
                onReset = { apply(state.copy(width = 1f)) }
            )
        }

        AudioSection(
            title = "Balance",
            description = "Weights the two output channels without changing their content."
        ) {
            AudioSliderSetting(
                label = "Balance",
                value = state.balance,
                valueText = when {
                    state.balance == 0f -> "Centre"
                    state.balance < 0f -> "L %.2f".format(abs(state.balance))
                    else -> "R %.2f".format(state.balance)
                },
                onValueChange = { apply(state.copy(balance = it)) },
                valueRange = AudioControlRanges.BALANCE,
                steps = 7,
                enabled = dspAvailable,
                capability = if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                disabledNote = disabledNote,
                onReset = { apply(state.copy(balance = 0f)) }
            )
        }

        AudioSection(
            title = "Channels",
            description = "Channel operations applied to the processed PCM on its way to the output."
        ) {
            AudioSwitchSetting(
                title = "Mono downmix",
                description = "Sums left and right so both outputs carry the same signal.",
                checked = state.mono,
                onCheckedChange = { apply(state.copy(mono = it)) },
                enabled = dspAvailable,
                disabledNote = disabledNote
            )
            AudioSwitchSetting(
                title = "Swap left / right",
                description = "Exchanges the two output channels.",
                checked = state.swap,
                onCheckedChange = { apply(state.copy(swap = it)) },
                enabled = dspAvailable,
                disabledNote = disabledNote
            )
        }

        AudioAdvancedSection(
            title = "Channel matrix",
            expanded = advanced,
            onExpandedChange = { advanced = it },
            description = "What each output channel receives right now"
        ) {
            SonicCoreDataRow(
                label = "Output L ←",
                value = when {
                    state.mono -> "L + R (mono)"
                    state.swap -> "source R"
                    else -> "source L"
                },
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Output R ←",
                value = when {
                    state.mono -> "L + R (mono)"
                    state.swap -> "source L"
                    else -> "source R"
                },
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Room / HRTF spatialisation",
                value = "Not implemented",
                note = "SonicCore does not simulate rooms or head-related transfer functions. Any device virtualiser the platform " +
                    "exposes keeps its existing control on the Equalizer screen.",
                capability = SonicCoreCapability.NOT_IMPLEMENTED
            )
        }

        AudioLimitNote(
            "Only the four parameters above reach the audio chain. Nothing here claims a system-wide spatial effect: " +
                "the output route decides what a listener actually hears."
        )
    }
}
