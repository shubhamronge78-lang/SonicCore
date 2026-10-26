package com.soniccore.app.ui.home

import com.soniccore.app.ui.design.GlassRadius
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.ui.category.CapabilityBadge
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.design.AdaptiveGrid
import com.soniccore.app.ui.design.AppearanceState
import com.soniccore.app.ui.design.GlassCard
import com.soniccore.app.ui.design.GlassIconButton
import com.soniccore.app.ui.design.GlassSpacing
import com.soniccore.app.ui.design.HomeColumnChoice
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.LocalMotionScale
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.categoryColumnCount
import com.soniccore.app.ui.design.color
import com.soniccore.app.ui.design.motionDurationMillis
import com.soniccore.app.ui.navigation.SonicCoreCategory
import com.soniccore.app.ui.navigation.SonicCoreTool
import com.soniccore.app.ui.navigation.SonicCoreToolRegistry
import com.soniccore.app.ui.navigation.color

/** Live audio summary the Home header reports — measured values only. */
data class HomeAudioSummary(
    val engineEnabled: Boolean,
    val stateLabel: String,
    val stateDetail: String,
    val toneIsActive: Boolean,
    val toneIsError: Boolean,
    val routeName: String,
    val meters: MeterLevels,
    val hasMeasuredSignal: Boolean,
    val clipCount: Long,
    val cpuPercent: Double?,
    val latencyMs: Double?,
    val captureRunning: Boolean,
    /**
     * Whether the capture chain is in bypass *right now*.
     *
     * The Home card used to colour its BYPASS button from `captureRunning`, so
     * it read "on" whenever a session was running even though the DSP was
     * bypassed — a state the user cannot get anywhere else on this screen.
     * It is its own field so the button can never borrow another flag.
     */
    val bypassActive: Boolean = false
)

/**
 * SonicCore Home.
 *
 * Exactly SIX top-level categories as cards — Audio, Device Center, Shizuku
 * Center, Customization, Permissions & Privacy and Settings. Internal tools are
 * never promoted to this level; they live inside their category's own hub.
 */
@Composable
fun SonicCoreHomeScreen(
    summary: HomeAudioSummary,
    onCategoryClick: (SonicCoreCategory) -> Unit,
    onToolClick: (SonicCoreTool) -> Unit,
    onToggleEngine: () -> Unit,
    onToggleBypass: () -> Unit,
    onSettingsClick: () -> Unit,
    onModeToggle: () -> Unit,
    modeLabel: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val results = SonicCoreToolRegistry.search(searchQuery)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HomeHeader(
            summary = summary,
            modeLabel = modeLabel,
            onModeToggle = onModeToggle,
            onSettingsClick = onSettingsClick
        )
        HomeAudioStatusCard(summary = summary, onToggleEngine = onToggleEngine, onToggleBypass = onToggleBypass)
        HomeSearchBar(query = searchQuery, onQueryChange = onSearchChange)

        if (searchQuery.isNotBlank()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (results.isEmpty()) "NO MATCHING TOOL" else "TOOLS MATCHING \"${searchQuery.uppercase()}\"",
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                if (results.isEmpty()) {
                    Text(
                        "Search covers every tool inside all six categories. Try \"battery\", \"eq\", \"glass\" or \"shizuku\".",
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    results.take(12).forEach { tool ->
                        HomeSearchResultRow(tool = tool, onClick = { onToolClick(tool) })
                    }
                    if (results.size > 12) {
                        Text(
                            "${results.size - 12} more results — refine the search.",
                            color = SonicCoreColors.SecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        CategoryGrid(
            onCategoryClick = onCategoryClick,
            enabled = searchQuery.isBlank()
        )

        HomeQuickActionsRow(
            summary = summary,
            onToggleBypass = onToggleBypass,
            onCategoryClick = onCategoryClick
        )
    }
}

@Composable
private fun HomeHeader(
    summary: HomeAudioSummary,
    modeLabel: String,
    onModeToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "SonicCore",
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "PRO AUDIO CONSOLE",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        GlassIconButton(contentDescription = "Open settings", onClick = onSettingsClick) {
            Text("⚙", color = SonicCoreColors.SecondaryText, fontSize = 18.sp)
        }
        GlassIconButton(contentDescription = "Toggle colour mode", onClick = onModeToggle) {
            Text(modeLabel, color = SonicCoreColors.Violet, fontSize = 17.sp)
        }
    }
}

@Composable
private fun HomeAudioStatusCard(
    summary: HomeAudioSummary,
    onToggleEngine: () -> Unit,
    onToggleBypass: () -> Unit
) {
    val stateColor = when {
        summary.toneIsError -> SonicCoreColors.Error
        summary.toneIsActive -> SonicCoreColors.Success
        else -> SonicCoreColors.Warning
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AUDIO STATUS",
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    summary.stateLabel,
                    color = stateColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    summary.stateDetail,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (summary.engineEnabled) SonicCoreColors.Cyan.copy(alpha = 0.28f)
                                else SonicCoreColors.ElevatedSurface.copy(alpha = 0.8f),
                                SonicCoreColors.Surface.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        if (summary.engineEnabled) SonicCoreColors.Cyan.copy(alpha = 0.40f)
                        else SonicCoreColors.Border.copy(alpha = 0.16f),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onToggleEngine)
                    .semantics {
                        contentDescription = if (summary.engineEnabled) "Turn master engine off" else "Turn master engine on"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⏻",
                    color = if (summary.engineEnabled) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
                    fontSize = 26.sp
                )
            }
        }

        HomeSignalStrip(summary)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeMiniButton(
                label = if (summary.engineEnabled) "ENGINE ON" else "ENGINE OFF",
                active = summary.engineEnabled,
                stateLabel = if (summary.engineEnabled) "On" else "Off",
                onClick = onToggleEngine,
                modifier = Modifier.weight(1f)
            )
            HomeMiniButton(
                // The label states what the DSP is doing, not what the capture
                // service is doing: "BYPASS ON" only when it really is bypassed.
                label = if (summary.bypassActive) "BYPASS ON" else "BYPASS OFF",
                active = summary.bypassActive,
                stateLabel = if (summary.bypassActive) "On" else "Off",
                onClick = onToggleBypass,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Measured from the live capture session — SonicCore never reports processing that is not happening.",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun HomeSignalStrip(summary: HomeAudioSummary) {
    val motion = LocalMotionScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeMiniStat("OUTPUT", summary.routeName.ifBlank { "Not detected" }, Modifier.weight(1f))
            HomeMiniStat(
                "SIGNAL",
                if (summary.hasMeasuredSignal) "MEASURED" else "NO FRAMES",
                Modifier.weight(1f)
            )
            if (summary.cpuPercent != null) {
                HomeMiniStat("DSP CPU", "%.1f%%".format(summary.cpuPercent), Modifier.weight(1f))
            } else if (summary.latencyMs != null) {
                HomeMiniStat("LATENCY", "~%.0f ms".format(summary.latencyMs), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        listOf("IN L", "IN R", "OUT L", "OUT R").forEachIndexed { index, label ->
            val target = if (summary.hasMeasuredSignal) {
                summary.meters.rmsForChannel(index).coerceIn(0f, 1f)
            } else 0f
            val level by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(motionDurationMillis(90, motion)),
                label = "homeMeter$index"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    label,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 9.sp,
                    modifier = Modifier.width(38.dp)
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .semantics { contentDescription = "$label measured level" }
                ) {
                    val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    drawRoundRect(
                        color = SonicCoreColors.Border.copy(alpha = 0.12f),
                        size = size,
                        cornerRadius = radius
                    )
                    if (level > 0f) {
                        drawRoundRect(
                            color = if (index < 2) SonicCoreColors.Cyan else SonicCoreColors.Violet,
                            size = androidx.compose.ui.geometry.Size(size.width * level, size.height),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }
        if (summary.clipCount > 0L) {
            Text(
                "Clipping detected (${summary.clipCount} events) — reduce preamp or EQ gain. Nothing is changed automatically.",
                color = SonicCoreColors.Warning,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun HomeMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(SonicCoreColors.Background.copy(alpha = 0.5f))
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(label, color = SonicCoreColors.SecondaryText, fontSize = 8.5.sp, letterSpacing = 0.6.sp)
        Text(
            value,
            color = SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun HomeMiniButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Spoken on/off state. Null for a navigation shortcut, which has no state. */
    stateLabel: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = GlassSpacing.touchTarget)
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(
                if (active) SonicCoreColors.Cyan.copy(alpha = 0.14f)
                else SonicCoreColors.ElevatedSurface.copy(alpha = 0.7f)
            )
            .border(
                1.dp,
                if (active) SonicCoreColors.Cyan.copy(alpha = 0.35f)
                else SonicCoreColors.Border.copy(alpha = 0.14f),
                RoundedCornerShape(GlassRadius.tile)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp)
            .semantics {
                contentDescription = label
                if (stateLabel != null) stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun HomeSearchBar(query: String, onQueryChange: (String) -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 4.dp,
        shape = RoundedCornerShape(GlassRadius.control)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SonicCoreColors.PrimaryText),
            cursorBrush = Brush.verticalGradient(listOf(SonicCoreColors.Cyan, SonicCoreColors.Cyan)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .semantics { contentDescription = "Search every tool in all six categories" },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", color = SonicCoreColors.Cyan, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search every tool inside all six categories",
                                color = SonicCoreColors.SecondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeSearchResultRow(tool: SonicCoreTool, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = GlassSpacing.touchTarget)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${tool.title} in ${tool.category.title}" }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(tool.icon, color = tool.category.color(), fontSize = 14.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tool.title,
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                tool.category.title,
                color = tool.category.color(),
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
        }
        Text("›", color = tool.category.color(), fontSize = 14.sp)
    }
}

@Composable
private fun CategoryGrid(
    onCategoryClick: (SonicCoreCategory) -> Unit,
    enabled: Boolean
) {
    val choice = AppearanceState.homeColumns
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // AUTO follows the window: one column on a very narrow window, two on a
        // phone, three once there is genuinely room (tablet, unfolded foldable).
        // The user's own ONE/TWO preference always wins.
        val columns = when (choice) {
            HomeColumnChoice.ONE -> 1
            HomeColumnChoice.TWO -> 2
            HomeColumnChoice.AUTO -> categoryColumnCount(maxWidth)
        }
        val categories = SonicCoreCategory.entries
        val rows = categories.chunked(columns)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { category ->
                        CategoryCard(
                            category = category,
                            onClick = { onCategoryClick(category) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size < columns) {
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: SonicCoreCategory,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = category.color()
    val capability = categoryCapability(category)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppearanceState.corners.radius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SonicCoreColors.ElevatedSurface.copy(alpha = 0.92f),
                        SonicCoreColors.Surface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                AppearanceState.border.width,
                tone.copy(alpha = if (enabled) 0.30f else 0.14f),
                RoundedCornerShape(AppearanceState.corners.radius)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = "${category.title}, ${SonicCoreToolRegistry.countFor(category)} tools"
            }
            .padding(
                horizontal = 14.dp,
                vertical = AppearanceState.density.verticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(GlassRadius.control))
                    .background(tone.copy(alpha = 0.14f))
                    .border(1.dp, tone.copy(alpha = 0.30f), RoundedCornerShape(GlassRadius.control)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon, color = tone, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${SonicCoreToolRegistry.countFor(category)} TOOLS",
                color = SonicCoreColors.SecondaryText,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Text(
            category.title,
            color = SonicCoreColors.PrimaryText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            category.subtitle,
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CapabilityBadge(capability, compact = true)
            Spacer(Modifier.weight(1f))
            Text("OPEN ›", color = tone, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        }
    }
}

/**
 * A category's headline capability. Every category in SonicCore is reachable and
 * has a real implementation, so this reports AVAILABLE — except Shizuku, whose
 * state depends on a service the user installs and starts themselves.
 */
private fun categoryCapability(category: SonicCoreCategory): SonicCoreCapability =
    if (category == SonicCoreCategory.SHIZUKU) SonicCoreCapability.NEEDS_PERMISSION
    else SonicCoreCapability.AVAILABLE

@Composable
private fun HomeQuickActionsRow(
    summary: HomeAudioSummary,
    onToggleBypass: () -> Unit,
    onCategoryClick: (SonicCoreCategory) -> Unit
) {
    // These four used to be one Row of weight(1f) cells. On a 320 dp window with
    // the system font scaled up each cell fell to about 60 dp, so "DEVICE" and
    // "BYPASS" were cut mid-word. AdaptiveGrid drops a column instead of
    // squeezing the label, and every cell keeps the 48 dp touch target.
    val actions: List<Pair<String, () -> Unit>> = listOf(
        "EQ" to { onCategoryClick(SonicCoreCategory.AUDIO) },
        "BYPASS" to onToggleBypass,
        "DEVICE" to { onCategoryClick(SonicCoreCategory.DEVICE) },
        "THEME" to { onCategoryClick(SonicCoreCategory.CUSTOMIZE) }
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "QUICK ACTIONS",
            color = SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        AdaptiveGrid(
            itemCount = actions.size,
            modifier = Modifier.padding(top = 10.dp),
            minItemWidth = GlassSpacing.minTileWidthNumeric
        ) { index, cell ->
            val (label, action) = actions[index]
            val isBypass = index == 1
            HomeMiniButton(
                // The bypass shortcut reads the same real flag as the status
                // card above it, so the two can never disagree.
                label = if (isBypass && summary.bypassActive) "BYPASS ON" else label,
                active = isBypass && summary.bypassActive,
                stateLabel = if (isBypass) (if (summary.bypassActive) "On" else "Off") else null,
                onClick = action,
                modifier = cell
            )
        }
    }
}
