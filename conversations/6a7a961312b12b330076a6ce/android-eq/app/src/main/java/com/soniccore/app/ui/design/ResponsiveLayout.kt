package com.soniccore.app.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SONICCORE — RESPONSIVE LAYOUT
 *
 * One place that answers "how wide is this window, and what does that change?".
 * Every screen used to answer it for itself — or not at all — which is why the
 * same phone layout was stretched across a tablet, why three stat tiles were
 * squeezed into a 395 dp row, and why one screen used `screenWidthDp >= 840`
 * while another compared its own `maxWidth` to a different number.
 *
 * The decision logic is plain Kotlin ([WindowSizeClass], [ResponsiveMetrics],
 * [adaptiveColumnCount], [categoryColumnCount]) so `ResponsiveLayoutTest` can
 * pin it on the JVM, and the window class is read from
 * [LocalConfiguration.screenWidthDp] — the same signal Compose already uses for
 * every configuration change, including split-screen, rotation and a resizable
 * window on a foldable. No new dependency, no Android framework call in the
 * decision path.
 *
 * Layout only: nothing here touches DSP, capture, buffers or the sample rate.
 */
enum class WindowSizeClass {
    /** Phones in portrait, and a very narrow split-screen window. */
    COMPACT,

    /** Large phones, small tablets, and a landscape phone. */
    MEDIUM,

    /** Tablets, foldables unfolded, and desktop-style resizable windows. */
    EXPANDED
}

object ResponsiveBreakpoints {
    const val MEDIUM_MIN_WIDTH_DP = 600f
    const val EXPANDED_MIN_WIDTH_DP = 840f

    /**
     * Below this width a card grid uses one column — a two-column phone layout
     * narrower than this cannot fit the card's title plus its capability badge.
     */
    const val SINGLE_COLUMN_MAX_WIDTH_DP = 380f

    /** Widest a centred content column gets before the two-pane layout takes over. */
    const val TWO_PANE_MIN_WIDTH_DP = 840f

    /** Default cap on columns in an adaptive tile grid. */
    const val MAX_TILE_COLUMNS = 3
}

fun windowSizeClassOf(widthDp: Float): WindowSizeClass = when {
    widthDp < ResponsiveBreakpoints.MEDIUM_MIN_WIDTH_DP -> WindowSizeClass.COMPACT
    widthDp < ResponsiveBreakpoints.EXPANDED_MIN_WIDTH_DP -> WindowSizeClass.MEDIUM
    else -> WindowSizeClass.EXPANDED
}

/**
 * How many equal cells of at least [minCellWidth] fit into [availableWidth],
 * separated by [gap]. Never returns 0, never exceeds [maxColumns].
 */
fun adaptiveColumnCount(
    availableWidth: Dp,
    minCellWidth: Dp,
    gap: Dp = GlassSpacing.gapSmall,
    maxColumns: Int = ResponsiveBreakpoints.MAX_TILE_COLUMNS
): Int {
    if (maxColumns <= 1) return 1
    val minimum = minCellWidth.value
    if (availableWidth.value <= 0f || minimum <= 0f) return 1
    val spacing = gap.value.coerceAtLeast(0f)
    // n cells and n-1 gaps must fit: n * min + (n - 1) * gap <= available.
    val fit = ((availableWidth.value + spacing) / (minimum + spacing)).toInt()
    return fit.coerceIn(1, maxColumns)
}

/** Columns for Home's category cards, before the user's own column preference. */
fun categoryColumnCount(availableWidth: Dp): Int = when {
    availableWidth < ResponsiveBreakpoints.SINGLE_COLUMN_MAX_WIDTH_DP.dp -> 1
    availableWidth < ResponsiveBreakpoints.EXPANDED_MIN_WIDTH_DP.dp -> 2
    else -> 3
}

/**
 * The measured window, resolved into the handful of values the screens need.
 *
 * Pure data: constructed from a width and a height only, so tests can assert
 * the whole responsive contract without a device.
 */
data class ResponsiveMetrics(
    val widthDp: Float,
    val heightDp: Float
) {
    val windowClass: WindowSizeClass get() = windowSizeClassOf(widthDp)

    /** Wider than tall — the case where vertical space is the scarce resource. */
    val isLandscape: Boolean get() = widthDp > heightDp

    /** True when a screen should split into two panes instead of stacking. */
    val twoPane: Boolean get() = widthDp >= ResponsiveBreakpoints.TWO_PANE_MIN_WIDTH_DP

    /** Horizontal gutter for the current window class. */
    val screenPadding: Dp
        get() = when (windowClass) {
            WindowSizeClass.COMPACT -> GlassSpacing.screenHorizontal
            WindowSizeClass.MEDIUM -> GlassSpacing.screenHorizontalMedium
            WindowSizeClass.EXPANDED -> GlassSpacing.screenHorizontalExpanded
        }

    /** Vertical rhythm between sections for the current window class. */
    val sectionGap: Dp
        get() = when (windowClass) {
            WindowSizeClass.COMPACT -> GlassSpacing.sectionGapCompact
            WindowSizeClass.MEDIUM -> GlassSpacing.sectionGapMedium
            WindowSizeClass.EXPANDED -> GlassSpacing.sectionGapExpanded
        }

    /**
     * Cap for the centred content column. A compact window is its own cap, so
     * phone layouts are byte-for-byte what they were.
     */
    val contentMaxWidth: Dp
        get() = when (windowClass) {
            WindowSizeClass.COMPACT -> widthDp.dp
            WindowSizeClass.MEDIUM -> GlassSpacing.contentMaxWidthMedium
            WindowSizeClass.EXPANDED -> GlassSpacing.contentMaxWidthExpanded
        }

    /**
     * The width the content column actually receives: the cap, minus both
     * gutters. Grid decisions use this, not the raw window width, so a grid on a
     * wide tablet is planned against the column the user sees.
     */
    val contentWidth: Dp
        get() {
            val usable = contentMaxWidth.value - screenPadding.value * 2f
            return (if (usable > 0f) usable else 0f).dp
        }

    /** Columns for Home's category cards. */
    val categoryColumns: Int get() = categoryColumnCount(contentWidth)

    /** Columns an adaptive grid of at least [minCellWidth] cells can use. */
    fun columnsFor(minCellWidth: Dp = GlassSpacing.minTileWidth, maxColumns: Int = ResponsiveBreakpoints.MAX_TILE_COLUMNS): Int =
        adaptiveColumnCount(contentWidth, minCellWidth, GlassSpacing.gapSmall, maxColumns)

    companion object {
        /** A 412 × 915 dp phone — the fallback when the window size is unknown. */
        val Default = ResponsiveMetrics(412f, 915f)

        /**
         * Builds metrics from a configuration's screen size. Zero, negative or
         * otherwise unusable values fall back to [Default] per axis so a screen
         * never lays out against a nonsense width.
         */
        fun of(widthDp: Int, heightDp: Int): ResponsiveMetrics {
            val width = if (widthDp > 0) widthDp.toFloat() else Default.widthDp
            val height = if (heightDp > 0) heightDp.toFloat() else Default.heightDp
            return ResponsiveMetrics(width, height)
        }
    }
}

/** The window metrics in force for the current composition. */
val LocalResponsiveMetrics = compositionLocalOf { ResponsiveMetrics.Default }

/**
 * The current window metrics.
 *
 * `LocalConfiguration` already invalidates the composition on every
 * configuration change — rotation, split-screen resize, a foldable changing
 * posture — so this is recomputed exactly when the window actually changed.
 */
@Composable
fun rememberResponsiveMetrics(): ResponsiveMetrics {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        ResponsiveMetrics.of(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}

/**
 * Equal-width cells that reflow instead of being squeezed.
 *
 * The old pattern — `Row { tile.weight(1f); tile.weight(1f); tile.weight(1f) }` —
 * gives every tile the same width no matter how narrow the window is, so on a
 * compact phone "Not readable" was clipped into a ~110 dp cell. This drops to
 * fewer columns before the content has to truncate, and fills the last row so
 * the grid never looks lopsided.
 */
@Composable
fun AdaptiveGrid(
    itemCount: Int,
    modifier: Modifier = Modifier,
    minItemWidth: Dp = GlassSpacing.minTileWidth,
    maxColumns: Int = ResponsiveBreakpoints.MAX_TILE_COLUMNS,
    horizontalGap: Dp = GlassSpacing.gapSmall,
    verticalGap: Dp = GlassSpacing.gapSmall,
    item: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    if (itemCount <= 0) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = adaptiveColumnCount(maxWidth, minItemWidth, horizontalGap, maxColumns)
        val rows = (0 until itemCount).chunked(columns)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(verticalGap)
        ) {
            rows.forEach { rowIndices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap)
                ) {
                    rowIndices.forEach { index ->
                        Box(modifier = Modifier.weight(1f)) {
                            item(index, Modifier.fillMaxWidth())
                        }
                    }
                    repeat(columns - rowIndices.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
