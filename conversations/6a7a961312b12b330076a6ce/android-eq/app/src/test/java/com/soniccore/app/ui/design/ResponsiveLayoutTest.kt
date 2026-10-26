package com.soniccore.app.ui.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the responsive layout layer.
 *
 * These are the decisions every screen now inherits instead of making its own:
 * which window class a width belongs to, how wide the centred content column
 * may get, when a screen splits into two panes, and when a tile grid must drop
 * a column rather than squeeze its content. Each one used to be a literal
 * compared somewhere in a screen (`screenWidthDp >= 840`, `maxWidth >= 380.dp`,
 * three tiles forced into one `Row`), so pinning them here is what stops the
 * drift from coming back.
 */
class ResponsiveLayoutTest {

    // ── Window size classes ──

    @Test
    fun windowClassesFollowTheMaterialBreakpoints() {
        assertEquals(WindowSizeClass.COMPACT, windowSizeClassOf(320f))
        assertEquals(WindowSizeClass.COMPACT, windowSizeClassOf(599f))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassOf(600f))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassOf(839f))
        assertEquals(WindowSizeClass.EXPANDED, windowSizeClassOf(840f))
        assertEquals(WindowSizeClass.EXPANDED, windowSizeClassOf(1280f))
    }

    @Test
    fun unusableWindowSizeFallsBackToAPhoneInsteadOfZeroWidth() {
        val fromZero = ResponsiveMetrics.of(0, 0)
        assertEquals(ResponsiveMetrics.Default.widthDp, fromZero.widthDp, 0f)
        assertEquals(ResponsiveMetrics.Default.heightDp, fromZero.heightDp, 0f)
        assertEquals(WindowSizeClass.COMPACT, fromZero.windowClass)

        // One good axis is kept, only the bad one falls back.
        val mixed = ResponsiveMetrics.of(1000, -1)
        assertEquals(1000f, mixed.widthDp, 0f)
        assertEquals(WindowSizeClass.EXPANDED, mixed.windowClass)
        assertEquals(ResponsiveMetrics.Default.heightDp, mixed.heightDp, 0f)
    }

    // ── The content column ──

    @Test
    fun compactWindowKeepsTheCurrentPhoneLayout() {
        val phone = ResponsiveMetrics.of(427, 915)
        assertEquals(WindowSizeClass.COMPACT, phone.windowClass)
        // A phone is its own cap: nothing about a phone screen changes width.
        assertEquals(427f, phone.contentMaxWidth.value, 0f)
        assertEquals(GlassSpacing.screenHorizontal, phone.screenPadding)
        assertFalse(phone.isLandscape)
        assertFalse(phone.twoPane)
    }

    @Test
    fun widerWindowsGetAWiderGutterAndACappedContentColumn() {
        val tablet = ResponsiveMetrics.of(1000, 1400)
        assertEquals(GlassSpacing.screenHorizontalExpanded, tablet.screenPadding)
        assertEquals(GlassSpacing.contentMaxWidthExpanded, tablet.contentMaxWidth)
        // The cap must be narrower than the window it is centring inside, or it
        // would do nothing on a tablet.
        assertTrue(tablet.contentMaxWidth.value < tablet.widthDp)

        val smallTablet = ResponsiveMetrics.of(700, 1000)
        assertEquals(GlassSpacing.screenHorizontalMedium, smallTablet.screenPadding)
        assertEquals(GlassSpacing.contentMaxWidthMedium, smallTablet.contentMaxWidth)
        assertTrue(smallTablet.contentMaxWidth.value < smallTablet.widthDp)
    }

    @Test
    fun theContentWidthIsTheCapMinusBothGutters() {
        // A phone: cap = window width, so content = window - 2 × 16 dp.
        assertEquals(395f, ResponsiveMetrics.of(427, 915).contentWidth.value, 0f)
        // A tablet: cap = 960 dp, so content = 960 - 2 × 32 dp.
        assertEquals(896f, ResponsiveMetrics.of(1280, 800).contentWidth.value, 0f)
        // A window too narrow to have any content at all must not go negative.
        assertEquals(0f, ResponsiveMetrics.of(20, 900).contentWidth.value, 0f)
    }

    @Test
    fun aLandscapePhoneIsWiderThanTallAndKeepsTwoPanesOnDemand() {
        val landscapePhone = ResponsiveMetrics.of(915, 412)
        assertTrue(landscapePhone.isLandscape)
        assertTrue(landscapePhone.twoPane)
        // Expanded gutter, not the compact one, because the window class comes
        // from the width — a landscape phone has the room for it.
        assertEquals(GlassSpacing.screenHorizontalExpanded, landscapePhone.screenPadding)
    }

    @Test
    fun twoPaneStartsExactlyAtTheSharedBreakpoint() {
        assertFalse(ResponsiveMetrics.of(839, 1000).twoPane)
        assertTrue(ResponsiveMetrics.of(840, 1000).twoPane)
    }

    @Test
    fun verticalRhythmGrowsWithTheWindow() {
        val compact = ResponsiveMetrics.of(412, 915).sectionGap
        val medium = ResponsiveMetrics.of(700, 1000).sectionGap
        val expanded = ResponsiveMetrics.of(1000, 1400).sectionGap
        assertTrue(compact < medium)
        assertTrue(medium < expanded)
    }

    // ── Category cards ──

    @Test
    fun categoryCardsUseOneColumnOnlyWhenTwoCannotFit() {
        assertEquals(1, categoryColumnCount(320.dp))
        assertEquals(1, categoryColumnCount(379.dp))
        assertEquals(2, categoryColumnCount(380.dp))
        assertEquals(2, categoryColumnCount(427.dp))
        assertEquals(2, categoryColumnCount(839.dp))
        assertEquals(3, categoryColumnCount(840.dp))
    }

    @Test
    fun metricsReportCategoryColumnsFromTheirOwnContentWidth() {
        assertEquals(2, ResponsiveMetrics.of(427, 915).categoryColumns)
        assertEquals(3, ResponsiveMetrics.of(1000, 1400).categoryColumns)
        // A medium window is still a two-column grid, never three.
        assertEquals(2, ResponsiveMetrics.of(720, 1000).categoryColumns)
    }

    // ── Adaptive tile grids ──

    @Test
    fun tileGridsDropAColumnBeforeTheContentHasToTruncate() {
        // A compact phone's content width (427 - 2 × 16) with a two-line tile.
        assertEquals(2, adaptiveColumnCount(395.dp, GlassSpacing.minTileWidthWide))
        // The same width with a single-number tile: three fit.
        assertEquals(3, adaptiveColumnCount(395.dp, GlassSpacing.minTileWidthNumeric))
    }

    @Test
    fun theGapIsCountedWhenDecidingIfAColumnFits() {
        // Two 168 dp cells plus one 8 dp gap need 344 dp exactly.
        assertEquals(2, adaptiveColumnCount(344.dp, 168.dp, 8.dp))
        assertEquals(1, adaptiveColumnCount(343.dp, 168.dp, 8.dp))
    }

    @Test
    fun tileGridsNeverExceedTheirMaximumOrFallBelowOneColumn() {
        assertEquals(3, adaptiveColumnCount(2000.dp, 168.dp, 8.dp, maxColumns = 3))
        assertEquals(5, adaptiveColumnCount(2000.dp, 168.dp, 8.dp, maxColumns = 5))
        assertEquals(1, adaptiveColumnCount(120.dp, 168.dp))
        assertEquals(1, adaptiveColumnCount(0.dp, 168.dp))
        assertEquals(1, adaptiveColumnCount(400.dp, 0.dp))
        // A degenerate maximum still yields a usable layout.
        assertEquals(1, adaptiveColumnCount(400.dp, 100.dp, 8.dp, maxColumns = 0))
    }

    @Test
    fun aWideWindowGivesEveryTileTheSameFloorAsTheSharedTokens() {
        val expanded = ResponsiveMetrics.of(1280, 900)
        assertEquals(3, expanded.columnsFor(GlassSpacing.minTileWidthWide))
        assertEquals(
            ResponsiveBreakpoints.MAX_TILE_COLUMNS,
            expanded.columnsFor(GlassSpacing.minTileWidthNumeric)
        )
    }

    // ── Parameter controls side by side ──

    @Test
    fun aPhonePutsTwoControlsSideBySideAndATabletCanFitThree() {
        // The common phone: one centred column, so two of the three available
        // cells are wide enough for a labelled slider and the third is not.
        val phone = ResponsiveMetrics.of(412, 915)
        assertEquals(2, phone.controlColumns)

        val tablet = ResponsiveMetrics.of(1280, 800)
        assertEquals(ResponsiveBreakpoints.MAX_CONTROL_COLUMNS, tablet.controlColumns)
    }

    @Test
    fun aControlRowDropsToASingleColumnBeforeItWouldSqueeze() {
        // Exactly two 148 dp controls plus one 12 dp gap fit; one dp less does not.
        assertEquals(2, controlColumnCount(308.dp, 148.dp, 12.dp))
        assertEquals(1, controlColumnCount(307.dp, 148.dp, 12.dp))
        assertEquals(1, controlColumnCount(120.dp))
        assertEquals(1, controlColumnCount(0.dp))
    }

    @Test
    fun theControlRowNeverExceedsItsCap() {
        assertEquals(
            ResponsiveBreakpoints.MAX_CONTROL_COLUMNS,
            controlColumnCount(4000.dp, 148.dp, 12.dp)
        )
        // A narrow window can still be asked for two columns explicitly and gets
        // one, rather than two clipped cells.
        assertEquals(1, controlColumnCount(200.dp, 148.dp, 12.dp))
    }

    @Test
    fun theControlFloorIsTheSharedToken() {
        assertEquals(148.dp, GlassSpacing.minControlWidth)
        assertEquals(
            GlassSpacing.minControlWidth.value,
            ResponsiveBreakpoints.MIN_CONTROL_WIDTH_DP,
            0f
        )
    }
}
