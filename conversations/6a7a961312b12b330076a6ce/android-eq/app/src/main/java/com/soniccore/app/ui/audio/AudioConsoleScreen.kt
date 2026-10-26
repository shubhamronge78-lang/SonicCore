package com.soniccore.app.ui.audio

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.LiquidToggle
import com.soniccore.app.dsp.AudioControlRanges
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.ChainMove
import com.soniccore.app.dsp.ChainStage
import com.soniccore.app.dsp.DspChain
import com.soniccore.app.dsp.DspChainPresets
import com.soniccore.app.dsp.DspTelemetry
import com.soniccore.app.dsp.DspTelemetryText
import com.soniccore.app.dsp.HIGH_SHELF_HZ
import com.soniccore.app.dsp.LOW_SHELF_HZ
import com.soniccore.app.dsp.shelfResponseDb
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.AdaptiveControlRow
import com.soniccore.app.ui.design.GlassIconButton
import com.soniccore.app.ui.design.GlassInset
import com.soniccore.app.ui.design.GlassSpacing
import com.soniccore.app.ui.design.LiquidGlassButton
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.navigation.SonicCoreDestination
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SONICCORE — THE AUDIO CONSOLE
 *
 * One continuous control surface for every effect the engine really has. Each
 * effect is an inline card: its name and live status, its own enable/bypass
 * toggle on the same row, its parameters side by side where the window has the
 * width for them, and — immediately underneath, never on another screen — that
 * effect's own output.
 *
 * Three rules this file keeps:
 *
 *  - **A toggle is the real control.** A card's switch either moves the native
 *    chain's own stage enable (`NeonDsp.setStageEnabled` — the same flag the
 *    chain editor shows) or the parameter switch the engine already has
 *    (`compOn`, `limiterOn`). Nothing here flips a boolean only the UI sees.
 *  - **Output is measured or it is declined.** Every output block holds a
 *    measurement from the native bridge, or a sentence saying which of "waiting
 *    for audio" / "not measurable" applies. [DspTelemetryText] decides that
 *    wording and is unit-tested, so a card cannot draw a number it lacks.
 *  - **An unavailable effect says so.** The final section names what SonicCore
 *    does not offer on this path, and why.
 *
 * The screen owns no DSP state: it reads the host's snapshots and hands validated
 * ones back through the host's existing apply paths.
 */
@Composable
fun AudioConsoleScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    var status by remember { mutableStateOf<String?>(null) }

    // Measured telemetry is read on a slow tick — a meter can move without the
    // read sitting in the layout path, and never once per recomposition.
    var telemetryTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TELEMETRY_INTERVAL_MS)
            telemetryTick++
        }
    }
    val telemetry: DspTelemetry = remember(telemetryTick, host.audioSummary) {
        runCatching { host.measuredDsp() }
            .getOrElse { DspTelemetry.unavailable(it.message ?: it.toString()) }
    }

    val chain = host.dspChain
    val controls = host.audioControl

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GlassSpacing.gap)
    ) {
        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "Every control below is applied through the app's one validated path — the same one the " +
                "Equalizer and DSP Studio use — and every toggle moves the native stage it names."
        )

        SignalChainEditor(
            chain = chain,
            limiterOn = controls.limiterOn,
            onChainChange = { next -> host.onDspChainChange(next) },
            onStatus = { status = it }
        )
        status?.let { message -> AudioLimitNote(message) }

        PreampEffectCard(chain, controls, { next -> host.onDspChainChange(next) }, { next -> host.onAudioControlChange(next) }) { message ->
            status = message
        }
        ShelvesEffectCard(chain, controls, { next -> host.onDspChainChange(next) }, { next -> host.onAudioControlChange(next) })
        EqualizerEffectCard(
            stage = ChainStage.GRAPHIC_EQ,
            title = "Graphic EQ",
            bands = host.eqBands,
            chain = chain,
            onChainChange = { next -> host.onDspChainChange(next) },
            onOpenEditor = { host.onNavigate(SonicCoreDestination.EQUALIZER) }
        )
        EqualizerEffectCard(
            stage = ChainStage.PARAMETRIC_EQ,
            title = "Parametric EQ",
            bands = host.eqBands,
            chain = chain,
            onChainChange = { next -> host.onDspChainChange(next) },
            onOpenEditor = { host.onNavigate(SonicCoreDestination.EQUALIZER) }
        )
        StereoEffectCard(chain, controls, host)
        CompressorEffectCard(chain, controls, telemetry, { next -> host.onAudioControlChange(next) })
        LimiterEffectCard(chain, controls, telemetry, { next -> host.onAudioControlChange(next) })
        PipelineCard(host, telemetry)
        UnavailableEffectsSection()
        LoudnessAnalysisSection()
    }
}

private const val TELEMETRY_INTERVAL_MS = 700L

// ════════════════════════════════════════════════════════════════════════════
// THE CHAIN EDITOR
// ════════════════════════════════════════════════════════════════════════════

/**
 * The eight stages in the order the engine runs them, with a real reorder.
 *
 * Two ways to move a stage, because a drag is not the only way to use a phone: a
 * long-press drag on the handle, and an earlier/later pair on every row that a
 * screen reader or a keyboard can drive. Both go through the same
 * [DspChain.moved] decision, so both are refused in the same words when an order
 * would break SonicCore's contract — and the chain the host accepts is the chain
 * whose order the engine is really walking.
 */
@Composable
internal fun SignalChainEditor(
    chain: DspChain,
    limiterOn: Boolean,
    onChainChange: (DspChain) -> Boolean,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val rowHeights = remember { mutableStateMapOf<Int, Float>() }
    var dragStageId by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableStateOf(-1) }
    var dragTarget by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var presetName by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(loadChainPresets(context)) }

    /** Applies a move through the engine, or reports why it was refused. */
    fun commitMove(from: Int, to: Int) {
        if (from == to || from !in chain.order.indices || to !in chain.order.indices) return
        when (val move = chain.moved(from, to)) {
            is ChainMove.Applied -> {
                val next = chain.copy(order = move.order)
                onStatus(
                    if (onChainChange(next)) "Chain applied: ${next.orderText}"
                    else "The engine refused that order — the chain it is running is unchanged."
                )
            }
            is ChainMove.Refused -> onStatus(move.reason)
        }
    }

    fun toggleStage(stage: ChainStage, bypass: Boolean) {
        val next = chain.withBypass(stage, bypass)
        if (next == chain) return
        onStatus(
            if (onChainChange(next)) {
                "${stage.title} ${if (bypass) "bypassed" else "enabled"} — its settings are kept."
            } else {
                "The engine refused that change; ${stage.title} is unchanged."
            }
        )
    }

    AudioSection(
        title = "Signal chain",
        modifier = modifier,
        description = "The order the engine really processes: ${chain.orderText}"
    ) {
        // The name comes before the actions that use it: the field used to sit
        // below SAVE CHAIN, so "save" was offered over a name the user had not
        // been asked for yet.
        OutlinedTextField(
            value = presetName,
            onValueChange = { presetName = it },
            singleLine = true,
            label = { Text("Chain name", color = SonicCoreColors.SecondaryText) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
        ) {
            LiquidGlassButton(
                text = "RESET CHAIN",
                onClick = {
                    onStatus(
                        if (onChainChange(DspChain.default())) "Chain reset to the engine's own order."
                        else "The engine refused the reset."
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !chain.isDefault
            )
            LiquidGlassButton(
                text = "SAVE CHAIN",
                onClick = {
                    val safeName = DspChainPresets.sanitizeName(presetName)
                    val next = DspChainPresets.save(presets, safeName, chain)
                    presets = next
                    storeChainPresets(context, next)
                    presetName = ""
                    onStatus("Chain saved as “$safeName”.")
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (presets.isNotEmpty()) {
            Text(
                "SAVED CHAINS (${presets.size}/${DspChainPresets.MAX_PRESETS})",
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            presets.forEach { saved ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            saved.name,
                            color = SonicCoreColors.PrimaryText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            saved.chain.orderText,
                            color = SonicCoreColors.SecondaryText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LiquidGlassButton(
                        text = "LOAD",
                        onClick = {
                            onStatus(
                                if (onChainChange(saved.chain)) "Loaded “${saved.name}”."
                                else "The engine refused “${saved.name}”."
                            )
                        }
                    )
                    LiquidGlassButton(
                        text = "DELETE",
                        secondary = true,
                        onClick = {
                            val next = DspChainPresets.remove(presets, saved.name)
                            presets = next
                            storeChainPresets(context, next)
                            onStatus("Deleted “${saved.name}”.")
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        chain.order.forEachIndexed { index, stage ->
            ChainStageRow(
                stage = stage,
                position = index,
                total = chain.order.size,
                // One source of truth per stage: the bypass set for the optional
                // stages, and the app-level limiter switch for the limiter — the
                // same answer the engine is given, so the row cannot claim the
                // limiter is on while the Limiter card switched it off.
                bypassed = !chain.enableOf(stage, limiterOn),
                dragging = dragStageId == stage.id,
                onToggle = { bypass -> toggleStage(stage, bypass) },
                onMove = { target -> commitMove(index, target) },
                onSize = { height -> rowHeights[stage.id] = height },
                dragHandle = { handleModifier ->
                    val gapPx = with(density) { GlassSpacing.gapSmall.toPx() }
                    Box(
                        modifier = handleModifier.pointerInput(stage, index, chain.order) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragStageId = stage.id
                                    dragFrom = index
                                    dragTarget = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val rowHeight = rowHeights[stage.id] ?: 0f
                                    if (rowHeight > 0f) {
                                        val shift = (dragOffset / (rowHeight + gapPx)).roundToInt()
                                        dragTarget = (index + shift).coerceIn(0, chain.order.lastIndex)
                                    }
                                },
                                onDragEnd = {
                                    commitMove(dragFrom, dragTarget)
                                    dragStageId = null
                                },
                                onDragCancel = { dragStageId = null }
                            )
                        }
                    ) {
                        Text("⠿", color = SonicCoreColors.Cyan, fontSize = 18.sp)
                    }
                }
            )
        }
        Text(
            "Long-press the handle to drag a stage, or use the ‹ › buttons — both move it the same way. " +
                "SonicCore keeps the limiter last.",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ChainStageRow(
    stage: ChainStage,
    position: Int,
    total: Int,
    bypassed: Boolean,
    dragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onSize: (Float) -> Unit,
    dragHandle: @Composable (Modifier) -> Unit
) {
    GlassInset(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .onSizeChanged { size -> onSize(size.height.toFloat()) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Stage ${position + 1} of $total: ${stage.title}"
                    stateDescription = if (bypassed) "Bypassed" else "Enabled"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
        ) {
            dragHandle(Modifier.semantics { contentDescription = "Reorder ${stage.title}" })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${position + 1} · ${stage.title}",
                    color = if (bypassed) SonicCoreColors.SecondaryText else SonicCoreColors.PrimaryText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when {
                        !stage.bypassable -> if (bypassed) {
                            "Last in the chain · switched off by the Limiter card"
                        } else {
                            "Always last — output ceiling"
                        }
                        bypassed -> "Bypassed · ${stage.summary}"
                        dragging -> "Moving…"
                        else -> stage.summary
                    },
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (stage.bypassable) {
                LiquidToggle(
                    checked = !bypassed,
                    modifier = Modifier.semantics {
                        contentDescription = "Enable ${stage.title}"
                        stateDescription = if (bypassed) "Bypassed" else "Enabled"
                    },
                    onCheckedChange = { on -> onToggle(!on) }
                )
            }
            GlassIconButton(
                contentDescription = "Move ${stage.title} earlier",
                onClick = { onMove(position - 1) },
                enabled = position > 0
            ) {
                Text("‹", color = SonicCoreColors.Cyan, fontSize = 18.sp)
            }
            GlassIconButton(
                contentDescription = "Move ${stage.title} later",
                onClick = { onMove(position + 1) },
                enabled = position < total - 1
            ) {
                Text("›", color = SonicCoreColors.Cyan, fontSize = 18.sp)
            }
        }
        if (stage == ChainStage.CONVOLVER) {
            Text(
                "No impulse response can be loaded in this build, so this stage passes audio through unchanged.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EFFECT CARDS
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PreampEffectCard(
    chain: DspChain,
    controls: AudioControlState,
    onChainChange: (DspChain) -> Boolean,
    onControls: (AudioControlState) -> Unit,
    onStatus: (String) -> Unit
) {
    val bypassed = chain.isBypassed(ChainStage.PREAMP)
    AudioEffectCard(
        title = "Preamp",
        status = if (bypassed) "Bypassed — output trim not applied" else "Applied · ${formatDb(controls.preamp)}",
        description = "Headroom before the tone stages. An AutoEQ profile's preamp is written here, once.",
        toggle = AudioEffectToggle(
            checked = !bypassed,
            onCheckedChange = { on ->
                onStatus(
                    if (onChainChange(chain.withBypass(ChainStage.PREAMP, !on))) {
                        "Preamp ${if (on) "enabled" else "bypassed"} — its gain is kept."
                    } else {
                        "The engine refused that change; the preamp stage is unchanged."
                    }
                )
            },
            label = "Enable the preamp stage"
        ),
        output = {
            AudioOutputLine("Trim", formatDb(controls.preamp))
            AudioOutputLine("Stage", if (bypassed) "Bypassed in the chain" else "In the path")
        }
    ) {
        AudioSliderSetting(
            label = "Gain",
            value = controls.preamp,
            valueText = formatDb(controls.preamp),
            valueRange = AudioControlRanges.PREAMP,
            onValueChange = { value -> onControls(controls.copy(preamp = value)) },
            onReset = { onControls(controls.copy(preamp = 0f)) }
        )
    }
}

@Composable
private fun ShelvesEffectCard(
    chain: DspChain,
    controls: AudioControlState,
    onChainChange: (DspChain) -> Boolean,
    onControls: (AudioControlState) -> Unit
) {
    val bypassed = chain.isBypassed(ChainStage.SHELVES)
    AudioEffectCard(
        title = "Bass & Treble",
        status = if (bypassed) {
            "Bypassed"
        } else {
            "Low shelf ${formatDb(controls.bass)} · high shelf ${formatDb(controls.treble)}"
        },
        description = "One low shelf at ${LOW_SHELF_HZ.toInt()} Hz and one high shelf at ${HIGH_SHELF_HZ.toInt()} Hz.",
        toggle = AudioEffectToggle(
            checked = !bypassed,
            onCheckedChange = { on -> onChainChange(chain.withBypass(ChainStage.SHELVES, !on)) },
            label = "Enable the bass and treble shelves"
        ),
        output = {
            AudioOutputLine("Low shelf", "${LOW_SHELF_HZ.toInt()} Hz")
            AudioOutputLine("High shelf", "${HIGH_SHELF_HZ.toInt()} Hz")
            InlineResponseCurve(
                values = List(CURVE_POINTS) { index ->
                    shelfResponseDb(controls, curveFrequency(index, CURVE_POINTS))
                },
                valueRange = -18f..18f,
                tint = SonicCoreColors.Cyan,
                description = "Modelled shelf response, from the gains the engine was given"
            )
        }
    ) {
        // Side by side when the window is wide enough, stacked when it is not.
        val bassControl: @Composable (Modifier) -> Unit = { cell ->
            AudioSliderSetting(
                label = "Bass",
                value = controls.bass,
                valueText = formatDb(controls.bass),
                valueRange = AudioControlRanges.BASS,
                onValueChange = { value -> onControls(controls.copy(bass = value)) },
                onReset = { onControls(controls.copy(bass = 0f)) },
                modifier = cell
            )
        }
        val trebleControl: @Composable (Modifier) -> Unit = { cell ->
            AudioSliderSetting(
                label = "Treble",
                value = controls.treble,
                valueText = formatDb(controls.treble),
                valueRange = AudioControlRanges.TREBLE,
                onValueChange = { value -> onControls(controls.copy(treble = value)) },
                onReset = { onControls(controls.copy(treble = 0f)) },
                modifier = cell
            )
        }
        AdaptiveControlRow(controls = listOf(bassControl, trebleControl))
    }
}

@Composable
private fun EqualizerEffectCard(
    stage: ChainStage,
    title: String,
    bands: List<Float>,
    chain: DspChain,
    onChainChange: (DspChain) -> Boolean,
    onOpenEditor: () -> Unit
) {
    val bypassed = chain.isBypassed(stage)
    val active = bands.count { abs(it) > 0.05f }
    AudioEffectCard(
        title = title,
        status = if (bypassed) {
            "Bypassed"
        } else {
            "In the path · $active of ${bands.size} band values shaping the response"
        },
        description = if (stage == ChainStage.GRAPHIC_EQ) {
            "The band values the engine holds, with the per-band faders on the Equalizer screen."
        } else {
            "Up to eight peaking slots; the slot editor is on the Equalizer screen."
        },
        toggle = AudioEffectToggle(
            checked = !bypassed,
            onCheckedChange = { on -> onChainChange(chain.withBypass(stage, !on)) },
            label = "Enable the $title stage"
        ),
        output = {
            AudioOutputLine("Band values", "$active non-flat of ${bands.size}")
            InlineResponseCurve(
                values = bands,
                valueRange = -18f..18f,
                tint = if (bypassed) SonicCoreColors.SecondaryText else SonicCoreColors.Violet,
                description = "$title response, as the engine holds it"
            )
            LiquidGlassButton(
                text = "OPEN THE EQ EDITOR",
                onClick = onOpenEditor,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    ) {
        Text(
            if (stage == ChainStage.GRAPHIC_EQ) {
                "The 31 band faders stay on the Equalizer screen: 31 separate touch targets do not fit a " +
                    "console this dense without shrinking them below a usable size."
            } else {
                "The frequency, gain and Q of each slot stay on the Equalizer screen for the same reason."
            },
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun StereoEffectCard(
    chain: DspChain,
    controls: AudioControlState,
    host: SonicCoreToolHost
) {
    val bypassed = chain.isBypassed(ChainStage.STEREO)
    val meters = host.audioSummary.meters
    val measured = host.audioSummary.hasMeasuredSignal
    AudioEffectCard(
        title = "Stereo & spatial",
        status = if (bypassed) "Bypassed" else stereoStatus(controls),
        description = "Width, balance, channel swap and mono downmix — the engine's own stereo stage.",
        toggle = AudioEffectToggle(
            checked = !bypassed,
            onCheckedChange = { on -> host.onDspChainChange(chain.withBypass(ChainStage.STEREO, !on)) },
            label = "Enable the stereo stage"
        ),
        output = {
            AudioOutputLine("Channels", if (controls.mono) "Mono (L+R)" else "Stereo L / R")
            AudioOutputLine("Width", "%.2f×".format(controls.width))
            AudioOutputLine("Balance", "%.2f".format(controls.balance))
            AudioOutputLine(
                "Measured L / R",
                if (measured) {
                    "%.1f dB / %.1f dB".format(meters.dbForChannel(0), meters.dbForChannel(1))
                } else {
                    "Waiting for audio — no frames measured yet"
                }
            )
        }
    ) {
        val widthControl: @Composable (Modifier) -> Unit = { cell ->
            AudioSliderSetting(
                label = "Width",
                value = controls.width,
                valueText = "%.2f×".format(controls.width),
                valueRange = AudioControlRanges.WIDTH,
                onValueChange = { value -> host.onAudioControlChange(controls.copy(width = value)) },
                onReset = { host.onAudioControlChange(controls.copy(width = 1f)) },
                modifier = cell
            )
        }
        val balanceControl: @Composable (Modifier) -> Unit = { cell ->
            AudioSliderSetting(
                label = "Balance",
                value = controls.balance,
                valueText = "%.2f".format(controls.balance),
                valueRange = AudioControlRanges.BALANCE,
                onValueChange = { value -> host.onAudioControlChange(controls.copy(balance = value)) },
                onReset = { host.onAudioControlChange(controls.copy(balance = 0f)) },
                modifier = cell
            )
        }
        AdaptiveControlRow(controls = listOf(widthControl, balanceControl))
        AudioSwitchSetting(
            title = "Swap channels",
            checked = controls.swap,
            description = "Exchanges left and right after the width stage.",
            onCheckedChange = { value -> host.onAudioControlChange(controls.copy(swap = value)) }
        )
        AudioSwitchSetting(
            title = "Mono downmix",
            checked = controls.mono,
            description = "Sums left and right into both channels.",
            onCheckedChange = { value -> host.onAudioControlChange(controls.copy(mono = value)) }
        )
    }
}

@Composable
private fun CompressorEffectCard(
    chain: DspChain,
    controls: AudioControlState,
    telemetry: DspTelemetry,
    onControls: (AudioControlState) -> Unit
) {
    val stageBypassed = chain.isBypassed(ChainStage.COMPRESSOR)
    AudioEffectCard(
        title = "Compressor",
        status = when {
            stageBypassed -> "Bypassed in the chain"
            controls.compOn -> "Enabled · threshold ${formatDb(controls.compThresh)}"
            else -> "Switched off"
        },
        description = "Stereo-linked, gain-reduction domain, with the engine's own fixed attack and release.",
        toggle = AudioEffectToggle(
            checked = controls.compOn,
            onCheckedChange = { on -> onControls(controls.copy(compOn = on)) },
            label = "Enable the compressor"
        ),
        output = {
            AudioOutputLine("Stage", if (stageBypassed) "Bypassed in the chain" else "In the path")
            AudioOutputLine("Threshold", formatDb(controls.compThresh))
            Text(
                DspTelemetryText.compressorLine(telemetry),
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                DspTelemetryText.outputPeakLine(telemetry),
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    ) {
        AudioSliderSetting(
            label = "Threshold",
            value = controls.compThresh,
            valueText = formatDb(controls.compThresh),
            valueRange = AudioControlRanges.COMP_THRESHOLD,
            onValueChange = { value -> onControls(controls.copy(compThresh = value)) },
            onReset = { onControls(controls.copy(compThresh = -18f)) }
        )
        Text(
            "Ratio, attack and release are fixed by the engine (4:1, 5 ms, 150 ms). There is no native " +
                "control for them, so this console does not show one.",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LimiterEffectCard(
    chain: DspChain,
    controls: AudioControlState,
    telemetry: DspTelemetry,
    onControls: (AudioControlState) -> Unit
) {
    AudioEffectCard(
        title = "Limiter",
        status = if (controls.limiterOn) {
            "Ceiling ${formatDb(controls.limThresh)} · enabled"
        } else {
            "Switched off · the chain still keeps the stage last"
        },
        description = "The final safety stage. This switch is the limiter's own enable — the same value the " +
            "chain editor shows. The chain itself only fixes its place: always last, nothing runs after the output ceiling.",
        toggle = AudioEffectToggle(
            checked = controls.limiterOn,
            onCheckedChange = { on -> onControls(controls.copy(limiterOn = on)) },
            label = "Enable the output limiter"
        ),
        output = {
            AudioOutputLine("Ceiling", formatDb(controls.limThresh))
            AudioOutputLine(
                "Chain position",
                if (chain.order.lastOrNull() == ChainStage.LIMITER) "Last" else "Not last — the app refuses that order"
            )
            Text(
                DspTelemetryText.limiterLine(telemetry),
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                DspTelemetryText.outputPeakLine(telemetry),
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    ) {
        AudioSliderSetting(
            label = "Threshold",
            value = controls.limThresh,
            valueText = formatDb(controls.limThresh),
            valueRange = AudioControlRanges.LIMITER_THRESHOLD,
            onValueChange = { value -> onControls(controls.copy(limThresh = value)) },
            onReset = { onControls(controls.copy(limThresh = -1f)) }
        )
    }
}

@Composable
private fun PipelineCard(host: SonicCoreToolHost, telemetry: DspTelemetry) {
    val summary = host.audioSummary
    AudioSection(
        title = "Capture & DSP pipeline",
        description = "What is running, and how much audio has actually gone through it."
    ) {
        AudioOutputLine("Session", summary.stateLabel)
        AudioOutputLine("Detail", summary.stateDetail)
        AudioOutputLine(
            "Frames processed",
            if (!telemetry.available) "Not measurable" else "${telemetry.processedFrames}"
        )
        AudioOutputLine("Clipped samples", "${summary.clipCount}")
        AudioOutputLine("Output route", summary.routeName)
        Text(
            when {
                !telemetry.available -> DspTelemetryText.outputPeakLine(telemetry)
                telemetry.processedFrames == 0L ->
                    "The service can be running with no audio going through it — a running service is not " +
                        "the same measurement as processed frames, and this counter is the difference."
                else ->
                    "Frames counted by the native bridge. Android only exposes audio from apps that permit " +
                        "playback capture, so a protected source is never routed through this chain."
            },
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * The honest half of the effects list: what this build does not do on this path,
 * named with the reason. No control is rendered for any of them — a toggle that
 * changed nothing would be worse than saying so.
 */
@Composable
private fun UnavailableEffectsSection() {
    AudioSection(
        title = "Not available on this path",
        description = "Listed so the absence is a statement rather than a missing control."
    ) {
        SonicCoreDataRow(
            label = "Impulse-response convolution",
            value = "No IR can be loaded in this build",
            capability = SonicCoreCapability.NOT_IMPLEMENTED
        )
        SonicCoreDataRow(
            label = "Reverb / virtualizer",
            value = "Not part of the native chain",
            capability = SonicCoreCapability.UNSUPPORTED
        )
        SonicCoreDataRow(
            label = "Haptic bass",
            value = "No API for a third-party app to drive a haptic transducer",
            capability = SonicCoreCapability.UNSUPPORTED
        )
        SonicCoreDataRow(
            label = "System-wide processing",
            value = "Only for sources that permit playback capture",
            capability = SonicCoreCapability.BLOCKED
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SHARED PIECES
// ════════════════════════════════════════════════════════════════════════════

/**
 * An effect's response drawn from the values the engine was given.
 *
 * A rendering aid, never a measurement — the caption says which curve it is, and
 * an all-zero array draws a flat line rather than a graph that looks busy.
 */
@Composable
private fun InlineResponseCurve(
    values: List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    tint: Color,
    description: String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val zeroFraction = ((0f - valueRange.start) / span).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CURVE_HEIGHT)
                .padding(vertical = 2.dp)
                .semantics { contentDescription = description }
        ) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
            val path = Path()
            values.forEachIndexed { index, value ->
                val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
                val x = stepX * index
                val y = size.height * (1f - fraction)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawLine(
                color = SonicCoreColors.SecondaryText.copy(alpha = 0.35f),
                start = Offset(0f, size.height * (1f - zeroFraction)),
                end = Offset(size.width, size.height * (1f - zeroFraction)),
                strokeWidth = 1f
            )
            drawPath(path = path, color = tint, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
        Text(
            description,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** A log-spaced frequency for point [index] of [points] between 20 Hz and 20 kHz. */
private fun curveFrequency(index: Int, points: Int): Float {
    if (points <= 1) return 1000f
    val ratio = index.toFloat() / (points - 1).toFloat()
    return 20f * Math.pow(1000.0, ratio.toDouble()).toFloat()
}

private val CURVE_HEIGHT = 64.dp
private const val CURVE_POINTS = 40

private fun formatDb(value: Float): String = "%+.1f dB".format(value)

private fun stereoStatus(controls: AudioControlState): String = buildString {
    append("%.2f× width".format(controls.width))
    if (controls.balance != 0f) append(" · balance %.2f".format(controls.balance))
    if (controls.swap) append(" · channels swapped")
    if (controls.mono) append(" · mono")
}

// ── Saved chains: one short line per preset, in the app's own preferences ──

private const val CHAIN_PREFS = "dsp_chain_presets"

private fun loadChainPresets(context: Context): List<DspChainPresets.Saved> {
    val prefs = context.getSharedPreferences(CHAIN_PREFS, Context.MODE_PRIVATE)
    return (0 until DspChainPresets.MAX_PRESETS).mapNotNull { index ->
        val raw = prefs.getString("p$index", null) ?: return@mapNotNull null
        val split = raw.indexOf('\n')
        if (split <= 0) null
        else DspChainPresets.decode(raw.substring(0, split), raw.substring(split + 1))
    }
}

private fun storeChainPresets(context: Context, presets: List<DspChainPresets.Saved>) {
    val prefs = context.getSharedPreferences(CHAIN_PREFS, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    for (index in 0 until DspChainPresets.MAX_PRESETS) {
        val preset = presets.getOrNull(index)
        if (preset == null) {
            editor.remove("p$index")
        } else {
            editor.putString("p$index", preset.name + "\n" + DspChainPresets.encode(preset.chain))
        }
    }
    editor.apply()
}
