package com.soniccore.app.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.dsp.AudioControlRanges
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.HIGH_SHELF_HZ
import com.soniccore.app.dsp.LOW_SHELF_HZ
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.dsp.shelfResponseDb
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.SonicCoreColors
import kotlin.math.pow

/**
 * Audio → Bass & Treble.
 *
 * The two independent shelves of the native chain. Each control is a single
 * parameter the engine already exposes (`setShelves`), so moving bass never
 * touches treble and neither one disturbs the graphic bands the Equalizer owns.
 * The curve below the controls is the modelled shelf response of exactly these
 * two gains — a rendering aid, never a measurement of processed audio.
 */
@Composable
fun AudioBassTrebleScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    val state = host.audioControl
    val dspAvailable = NeonDsp.available
    var advanced by remember { mutableStateOf(false) }

    val disabledNote =
        "The native DSP bridge is not loaded on this device, so shelf gains cannot reach the audio chain."

    fun apply(next: AudioControlState) = host.onAudioControlChange(next)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapabilityLegend(
            if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
            NeonDsp.loadError
                ?: "Bass and treble are the native engine's fixed low and high shelves, applied inside the DSP chain."
        )

        AudioActionRow(
            listOf(
                AudioAction(
                    label = "FLATTEN SHELVES",
                    onClick = { apply(state.copy(bass = 0f, treble = 0f)) },
                    enabled = dspAvailable && !(state.bass == 0f && state.treble == 0f)
                ),
                AudioAction(
                    label = "RESET ALL TONE",
                    onClick = { apply(state.copy(preamp = 0f, bass = 0f, treble = 0f)) },
                    enabled = dspAvailable && !state.isToneNeutral
                )
            )
        )

        AudioSection(
            title = "Bass & Treble",
            description = "Independent shelf gains. Each control writes its own parameter."
        ) {
            AudioSliderSetting(
                label = "Bass",
                value = state.bass,
                valueText = "%+.1f dB".format(state.bass),
                onValueChange = { apply(state.copy(bass = it)) },
                valueRange = AudioControlRanges.BASS,
                steps = 59,
                enabled = dspAvailable,
                capability = if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                disabledNote = disabledNote,
                onReset = { apply(state.copy(bass = 0f)) }
            )
            AudioSliderSetting(
                label = "Treble",
                value = state.treble,
                valueText = "%+.1f dB".format(state.treble),
                onValueChange = { apply(state.copy(treble = it)) },
                valueRange = AudioControlRanges.TREBLE,
                steps = 59,
                enabled = dspAvailable,
                capability = if (dspAvailable) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED,
                disabledNote = disabledNote,
                onReset = { apply(state.copy(treble = 0f)) }
            )
        }

        AudioSection(
            title = "Frequency response",
            description = "Modelled response of the two shelves at their current gains (20 Hz – 20 kHz, ±30 dB)."
        ) {
            ShelfResponseCurve(state, modifier = Modifier.fillMaxWidth())
            Text(
                "Low shelf corner ${LOW_SHELF_HZ.toInt()} Hz · high shelf corner ${HIGH_SHELF_HZ.toInt()} Hz · " +
                    "both corners are fixed by the native chain.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall
            )
        }

        AudioAdvancedSection(
            title = "Response detail",
            description = "Modelled gain at reference frequencies",
            expanded = advanced,
            onExpandedChange = { advanced = it }
        ) {
            listOf(60f, 250f, 1000f, 4000f, 12000f).forEach { frequency ->
                SonicCoreDataRow(
                    label = if (frequency >= 1000f) "${(frequency / 1000f).toInt()} kHz" else "${frequency.toInt()} Hz",
                    value = "%+.1f dB".format(shelfResponseDb(state, frequency)),
                    capability = SonicCoreCapability.AVAILABLE
                )
            }
            SonicCoreDataRow(
                label = "Preamp (chain input gain)",
                value = "%+.1f dB".format(state.preamp),
                note = "Shared with DSP Studio — the same single parameter, shown here for headroom context.",
                capability = SonicCoreCapability.AVAILABLE
            )
        }

        AudioLimitNote(
            "The curve is derived from the shelf parameters the DSP received. SonicCore does not claim a " +
                "measured response, and no processing is reported as applied while the engine is bypassed."
        )
    }
}

/**
 * Draws the modelled shelf response as a log-frequency curve. The 0 dB line is
 * the flat reference the shelves move away from.
 */
@Composable
private fun ShelfResponseCurve(state: AudioControlState, modifier: Modifier = Modifier) {
    val tone = SonicCoreColors.Cyan
    val axis = SonicCoreColors.Border.copy(alpha = 0.35f)
    val reference = SonicCoreColors.SecondaryText.copy(alpha = 0.30f)
    Canvas(
        modifier = modifier
            .height(140.dp)
            .semantics {
                contentDescription = "Modelled frequency response of the bass and treble shelves"
            }
    ) {
        val minHz = 20f
        val maxHz = 20000f
        val zeroY = size.height / 2f
        val dbScale = (size.height / 2f) / 30f

        // ±30 dB frame and the flat 0 dB reference.
        drawLine(axis, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f)
        drawLine(axis, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1f)
        drawLine(reference, androidx.compose.ui.geometry.Offset(0f, zeroY), androidx.compose.ui.geometry.Offset(size.width, zeroY), 1.5f)

        val path = Path()
        val points = 128
        for (index in 0..points) {
            val t = index.toFloat() / points
            val frequency = minHz * (maxHz / minHz).pow(t)
            val db = shelfResponseDb(state, frequency).coerceIn(-30f, 30f)
            val x = size.width * t
            val y = zeroY - db * dbScale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = tone, style = Stroke(width = 2.5f))
    }
}
