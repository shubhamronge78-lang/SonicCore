package com.soniccore.app.ui.home

import com.soniccore.app.ui.design.GlassRadius
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.ui.design.AudioMeterContainer
import com.soniccore.app.ui.design.GlassButton
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.GlassIconButton
import com.soniccore.app.ui.design.GlassSlider
import com.soniccore.app.ui.design.IndicatorStatus
import com.soniccore.app.ui.design.LiquidGlassChoiceChip
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.LiquidGlassValueChip
import com.soniccore.app.ui.design.MetricCard
import com.soniccore.app.ui.design.PresetCard
import com.soniccore.app.ui.design.SectionHeader
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.StatusIndicator

@Composable
fun HomeResponsiveColumns(
    landscape: Boolean,
    modifier: Modifier = Modifier,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val twoColumns = homeUsesTwoColumns(landscape, maxWidth)
        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = secondary
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = primary)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = secondary)
            }
        }

    }
}

internal fun homeUsesTwoColumns(landscape: Boolean, availableWidth: Dp): Boolean =
    landscape || availableWidth >= 840.dp

@Composable
fun HomeTopBar(
    status: String,
    statusDetail: String,
    statusKind: IndicatorStatus,
    route: String,
    onRouteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modeLabel: String,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SonicCore",
                    color = SonicCoreColors.PrimaryText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "PRO AUDIO CONSOLE",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            StatusIndicator(status, statusKind)
            GlassIconButton(
                contentDescription = "Open settings",
                onClick = onSettingsClick
            ) {
                Text("⚙", color = SonicCoreColors.SecondaryText, fontSize = 18.sp)
            }
            GlassIconButton(
                contentDescription = "Toggle color mode",
                onClick = onModeClick
            ) {
                Text(modeLabel, color = SonicCoreColors.Violet, fontSize = 17.sp)
            }
        }

        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRouteClick),
            contentPadding = 13.dp,
            shape = RoundedCornerShape(GlassRadius.card)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OUTPUT", color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = route,
                    color = SonicCoreColors.PrimaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = SonicCoreColors.Cyan, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            text = statusDetail,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HomeEngineCard(
    enabled: Boolean,
    stateLabel: String,
    isSignalRecent: Boolean,
    cpuPercent: Double?,
    latencyMs: Double?,
    meters: HomeMeterPresentation,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MASTER ENGINE",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = stateLabel,
                    color = when (stateLabel) {
                        "ACTIVE" -> SonicCoreColors.Success
                        "BYPASS" -> SonicCoreColors.Warning
                        "ERROR" -> SonicCoreColors.Error
                        "LIMITED" -> SonicCoreColors.Warning
                        else -> SonicCoreColors.PrimaryText
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    text = if (isSignalRecent) "Live PCM frames are reaching the engine"
                    else "Waiting for live captured audio",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (enabled) SonicCoreColors.Cyan.copy(alpha = 0.26f)
                                else SonicCoreColors.ElevatedSurface.copy(alpha = 0.85f),
                                SonicCoreColors.ElevatedSurface.copy(alpha = 0.64f),
                                SonicCoreColors.Surface.copy(alpha = 0.94f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                if (enabled) SonicCoreColors.Cyan.copy(alpha = 0.42f)
                                else SonicCoreColors.Border.copy(alpha = 0.16f)
                            )
                        ),
                        CircleShape
                    )
                    .clickable(onClick = onPowerClick)
                    .semantics { contentDescription = if (enabled) "Turn master engine off" else "Turn master engine on" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⏻",
                    color = if (enabled) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (cpuPercent != null) {
                CompactMetric("DSP CPU", "%.1f%%".format(cpuPercent), Modifier.weight(1f))
            }
            if (latencyMs != null) {
                CompactMetric("PIPELINE", "~%.0f ms".format(latencyMs), Modifier.weight(1f))
            }
            if (cpuPercent == null && latencyMs == null) {
                CompactMetric("SIGNAL", if (isSignalRecent) "MEASURED" else "NO FRAMES", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(13.dp))
        HomeAudioMeters(meters)
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(SonicCoreColors.Background.copy(alpha = 0.50f))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(label, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = SonicCoreColors.Cyan,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

internal data class HomeMeterPresentation(
    val levels: MeterLevels,
    val hasMeasuredSignal: Boolean,
    val clipCount: Long,
    val outputPeakMs: Int?
)

@Composable
private fun HomeAudioMeters(meters: HomeMeterPresentation) {
    AudioMeterContainer(title = "INPUT / OUTPUT · MEASURED") {
        if (!meters.hasMeasuredSignal) {
            Text(
                "No measured signal yet — play audio to populate the meters.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        listOf("INPUT L", "INPUT R", "OUTPUT L", "OUTPUT R").forEachIndexed { index, label ->
            val rmsTarget = meters.levels.rmsForChannel(index).coerceIn(0f, 1f)
            val peakTarget = meters.levels.peakForChannel(index).coerceIn(0f, 1f)
            val rmsLevel by animateFloatAsState(rmsTarget, tween(90), label = "meterRms$index")
            val peakLevel by animateFloatAsState(peakTarget, tween(70), label = "meterPeak$index")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(60.dp)
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .semantics { contentDescription = "$label measured level" }
                ) {
                    val barHeight = size.height
                    drawRoundRect(
                        color = SonicCoreColors.Border.copy(alpha = 0.11f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                    )
                    if (rmsLevel > 0f) {
                        drawRoundRect(
                            color = if (index < 2) SonicCoreColors.Cyan else SonicCoreColors.Violet,
                            size = androidx.compose.ui.geometry.Size(size.width * rmsLevel, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                        )
                    }
                    val peakX = size.width * peakLevel
                    drawLine(
                        color = SonicCoreColors.PrimaryText.copy(alpha = 0.85f),
                        start = androidx.compose.ui.geometry.Offset(peakX, 0f),
                        end = androidx.compose.ui.geometry.Offset(peakX, barHeight),
                        strokeWidth = 1.5f
                    )
                }
                Text(
                    meters.levels.dbForChannel(index).let { db ->
                        if (db <= -60f) "-∞" else "%.1f".format(db)
                    },
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(42.dp)
                )
            }
        }
        if (meters.clipCount > 0L) {
            Text(
                "Clipping detected (${meters.clipCount} events). Consider reducing preamp or EQ gain; settings are never changed automatically.",
                color = SonicCoreColors.Warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
        if (meters.hasMeasuredSignal && meters.outputPeakMs != null) {
            val outputPeak = meters.outputPeakMs
            val headroomDb = if (outputPeak > 0) 20.0 * Math.log10(outputPeak / 1000.0) else -60.0
            Text(
                "GAIN HEADROOM: " + when {
                    headroomDb > -3.0 -> "LOW"
                    headroomDb > -8.0 -> "MODERATE"
                    else -> "GOOD"
                } + " · output peak " +
                    (if (outputPeak > 0) "%.1f dBFS".format(headroomDb) else "silent") +
                    " — measured, not a loudness prediction.",
                color = if (headroomDb > -3.0) SonicCoreColors.Warning else SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
        Text(
            "dBFS · native DSP RMS with peak hold",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
fun HomeNowProcessing(
    preset: String,
    eqEnabled: Boolean,
    preampDb: Float,
    bassDb: Float,
    trebleDb: Float,
    stereoWidth: Float,
    compressorEnabled: Boolean,
    limiterEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "NOW PROCESSING", subtitle = preset)
        LazyRow(
            modifier = Modifier.padding(top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(
                listOf(
                    "EQ" to if (eqEnabled) "ON" else "OFF",
                    "PREAMP" to "%+.1f dB".format(preampDb),
                    "BASS" to "%+.1f dB".format(bassDb),
                    "TREBLE" to "%+.1f dB".format(trebleDb),
                    "STEREO" to "%.0f%%".format(stereoWidth * 100f),
                    "COMP" to if (compressorEnabled) "ON" else "OFF",
                    "LIMITER" to if (limiterEnabled) "ON" else "OFF"
                )
            ) { (label, value) ->
                val enabledState = label !in setOf("EQ", "COMP", "LIMITER") || value == "ON"
                LiquidGlassValueChip(label, value, active = enabledState)
            }
        }
    }
}

@Composable
fun HomeQuickPresetSection(
    currentPreset: String,
    favorites: List<String>,
    recent: List<String>,
    quickPresets: List<String>,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroup by remember {
        mutableStateOf(when {
            favorites.isNotEmpty() -> 0
            recent.isNotEmpty() -> 1
            else -> 2
        })
    }
    val presets = when (selectedGroup) {
        0 -> favorites
        1 -> recent
        else -> quickPresets
    }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "QUICK PRESETS",
            subtitle = "Current · $currentPreset"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("FAVORITES", "RECENT", "QUICK").forEachIndexed { index, label ->
                LiquidGlassChoiceChip(
                    text = label,
                    onClick = { selectedGroup = index },
                    selected = index == selectedGroup
                )
            }
        }
        if (presets.isEmpty()) {
            Text(
                if (selectedGroup == 0) "No favorites yet."
                else if (selectedGroup == 1) "No recently applied presets yet."
                else "No quick presets available.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(presets.distinct()) { preset ->
                    PresetCard(
                        name = preset,
                        description = if (preset == currentPreset) "CURRENT" else "Tap to apply",
                        selected = preset == currentPreset,
                        onClick = { onApply(preset) },
                        modifier = Modifier.width(158.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeQuickActions(
    onEq: () -> Unit,
    onDsp: () -> Unit,
    onReset: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "QUICK ACTIONS")
        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                listOf(
                    "EQ" to onEq,
                    "DSP" to onDsp,
                    "RESET" to onReset,
                    "DIAGNOSTICS" to onDiagnostics
                )
            ) { (label, action) ->
                GlassButton(
                    text = label,
                    onClick = action,
                    secondary = label == "DIAGNOSTICS"
                )
            }
        }
    }
}

@Composable
fun HomeControlChip(label: String, onClick: () -> Unit) {
    GlassButton(
        text = label,
        onClick = onClick,
        secondary = label.endsWith("OFF")
    )
}

@Composable
fun HomeOutputCard(
    routeName: String,
    routeDetail: String?,
    outputState: String,
    sampleRate: Int?,
    bufferMode: String,
    bufferMs: Double?,
    latencyMs: Double?,
    latencyBreakdown: String?,
    routeNote: String?,
    onOpenOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "OUTPUT", subtitle = routeDetail, modifier = Modifier.weight(1f))
            StatusIndicator(
                outputState,
                when (outputState) {
                    "ACTIVE" -> IndicatorStatus.Active
                    "ERROR" -> IndicatorStatus.Error
                    "LIMITED", "WAITING", "BYPASS" -> IndicatorStatus.Warning
                    else -> IndicatorStatus.Inactive
                }
            )
        }
        Text(
            routeName,
            color = SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        val details = buildList {
            if (sampleRate != null && sampleRate > 0) add("${sampleRate / 1000} kHz")
            add("Buffer $bufferMode")
            if (bufferMs != null && bufferMs > 0.0) add("%.1f ms buffer".format(bufferMs))
            if (latencyMs != null && latencyMs > 0.0) add("~%.0f ms latency".format(latencyMs))
        }
        Text(
            details.joinToString("  ·  "),
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp)
        )
        if (latencyBreakdown != null) {
            Text(
                latencyBreakdown,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (routeNote != null) {
            Text(
                routeNote,
                color = SonicCoreColors.Warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        GlassButton(
            text = "OPEN OUTPUT STUDIO",
            onClick = onOpenOutput,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

data class HomePerformanceTelemetry(
    val cpuPercent: Double?,
    val loopMs: Double?,
    val latencyMs: Double?,
    val underruns: Long?,
    val clips: Long?,
    val captureFrames: Long?,
    val outputFrames: Long?,
    val nanEvents: Long?,
    val sessionSeconds: Long?,
    val routeChanges: Long?,
    val bufferChanges: Long?,
    val healthLabel: String,
    val healthKind: IndicatorStatus,
    val healthDetail: String?,
    val healthChecks: List<HomeHealthCheck>
)

data class HomeHealthCheck(
    val label: String,
    val state: String,
    val kind: IndicatorStatus
)

@Composable
fun HomePerformanceCard(
    telemetry: HomePerformanceTelemetry,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "PERFORMANCE / HEALTH",
            action = {
                StatusIndicator(telemetry.healthLabel, telemetry.healthKind)
            }
        )
        val metrics = listOfNotNull(
            telemetry.cpuPercent?.let { "DSP CPU" to "%.1f%%".format(it) },
            telemetry.loopMs?.let { "DSP LOOP" to "%.1f ms".format(it) },
            telemetry.latencyMs?.let { "LATENCY" to "~%.1f ms".format(it) },
            telemetry.underruns?.let { "UNDERRUNS" to it.toString() },
            telemetry.clips?.let { "CLIPS" to it.toString() },
            telemetry.captureFrames?.let { "CAPTURE FRAMES" to it.toString() },
            telemetry.outputFrames?.let { "OUTPUT FRAMES" to it.toString() },
            telemetry.nanEvents?.let { "NaN EVENTS" to it.toString() },
            telemetry.sessionSeconds?.let { "SESSION" to "${it}s" },
            telemetry.routeChanges?.let { "ROUTE CHANGES" to it.toString() },
            telemetry.bufferChanges?.let { "BUFFER CHANGES" to it.toString() }
        )
        if (metrics.isNotEmpty()) {
            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value) ->
                        MetricCard(
                            label = label,
                            value = value,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Text(
                "Live performance counters appear when the capture session reports them.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (telemetry.healthChecks.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(telemetry.healthChecks) { check ->
                    StatusIndicator(
                        "${check.label} · ${check.state}",
                        check.kind
                    )
                }
            }
        }
        if (telemetry.healthDetail != null) {
            Text(
                telemetry.healthDetail,
                color = if (telemetry.healthKind == IndicatorStatus.Error) SonicCoreColors.Error else SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 9.dp)
            )
        }
    }
}

@Composable
fun HomeSpectrumCard(
    bins: FloatArray,
    peaks: FloatArray,
    source: Boolean,
    onSourceChange: (Boolean) -> Unit,
    channel: Int,
    onChannelChange: (Int) -> Unit,
    smoothing: Float,
    onSmoothingChange: (Float) -> Unit,
    peakHoldSeconds: Int,
    onPeakHoldChange: (Int) -> Unit,
    frozen: Boolean,
    onFrozenChange: (Boolean) -> Unit,
    fps: Int,
    onFpsChange: (Int) -> Unit,
    hasSignal: Boolean,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "LIVE SPECTRUM",
            subtitle = if (source) "Input · captured PCM" else "Output · processed PCM"
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .padding(top = 10.dp)
                .semantics { contentDescription = "Spectrum analyzer, 20 hertz to 20 kilohertz" }
        ) {
            val width = size.width
            val height = size.height
            val topPad = 5f
            val barWidth = width / bins.size
            for (grid in 1..3) {
                val y = topPad + (height - topPad) * grid / 4f
                drawLine(
                    SonicCoreColors.Border.copy(alpha = 0.14f),
                    androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(width, y),
                    1f
                )
            }
            for (index in bins.indices) {
                val magnitude = if (hasSignal) bins[index] else 0f
                val barHeight = (height - topPad) * magnitude.coerceIn(0f, 1f)
                if (barHeight > 1f) {
                    drawRoundRect(
                        color = SonicCoreColors.Cyan.copy(alpha = 0.88f),
                        topLeft = androidx.compose.ui.geometry.Offset(index * barWidth + 1f, height - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )
                }
                val peak = if (hasSignal) peaks.getOrElse(index) { 0f } else 0f
                if (peakHoldSeconds > 0 && peak > 0.02f) {
                    val y = height - (height - topPad) * peak.coerceIn(0f, 1f)
                    drawLine(
                        color = SonicCoreColors.Violet,
                        start = androidx.compose.ui.geometry.Offset(index * barWidth + 1f, y),
                        end = androidx.compose.ui.geometry.Offset((index + 1) * barWidth - 1f, y),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(top = 5.dp)
        ) {
            val frequencyLabels = listOf(
                "20 Hz" to 0f,
                "100 Hz" to 0.233f,
                "1 kHz" to 0.566f,
                "10 kHz" to 0.900f,
                "20 kHz" to 1f
            )
            frequencyLabels.forEachIndexed { index, (label, position) ->
                val labelWidth = 44.dp
                val x = when (index) {
                    0 -> 0.dp
                    frequencyLabels.lastIndex -> maxWidth - labelWidth
                    else -> maxWidth * position - labelWidth / 2
                }
                Text(
                    label,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        frequencyLabels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.offset(x = x).width(labelWidth)
                )
            }
        }
        if (!hasSignal) {
            Text(
                "No signal — play audio to populate the analyzer.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            GlassButton(if (source) "SOURCE" else "PROCESSED", { onSourceChange(!source) })
            listOf("L+R", "L", "R").forEachIndexed { index, label ->
                GlassButton(label, { onChannelChange(index) }, secondary = channel == index)
            }
            GlassButton(if (frozen) "RESUME" else "FREEZE", { onFrozenChange(!frozen) }, secondary = frozen)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SMOOTH", color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
            GlassSlider(
                value = smoothing,
                onValueChange = onSmoothingChange,
                valueRange = 0.2f..0.9f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(30.dp)
            )
            Text("${fps} fps", color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PEAK HOLD", color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
            listOf(0, 1, 3, 5).forEach { seconds ->
                GlassButton(
                    if (seconds == 0) "OFF" else "${seconds}s",
                    { onPeakHoldChange(seconds) },
                    secondary = peakHoldSeconds == seconds
                )
            }
            listOf(30, 15, 8).forEach { rate ->
                GlassButton("$rate", { onFpsChange(rate) }, secondary = fps == rate)
            }
        }
        Text(
            "20 Hz–20 kHz log scale · FFT of captured PCM; not a measurement of the physical speaker output.",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
