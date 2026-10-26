package com.soniccore.app.ui.design

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SONICCORE — LIQUID GLASS TOKENS
 *
 * The single source of truth for the Liquid Glass design system: palette,
 * corner radii, spacing, elevation, surface opacity and the blur policy.
 *
 * Everything visual in the app is meant to come from here — the shared
 * components in [com.soniccore.app.ui.design] read these values, so a fix lands
 * on every screen at once instead of being patched screen by screen. Nothing in
 * this file touches DSP, capture, audio buffers, latency or the sample rate: it
 * is presentation only.
 *
 * See `docs/liquid-glass-defects.md` for the defect list this file resolves
 * (GL-01 … GL-11). The values are plain Kotlin constants and pure functions on
 * purpose, so `GlassTokensTest` can assert the legibility and blur rules on the
 * JVM without a device.
 */

// ════════════════════════════════════════════════════════════════════════════
// PALETTE
// ════════════════════════════════════════════════════════════════════════════

/**
 * The frozen SonicCore appearance.
 *
 * The dark set is the app's signature "premium console" look: a layered navy
 * ramp where each surface is one shade lighter than the one behind it, so
 * depth comes from layering rather than from heavy shadows or glow.
 *
 * A theme may override the surface ramp (the twelve-theme catalogue keeps its
 * own identity), but every *opacity, radius, border and shadow* the glass is
 * built from comes from the objects below — that is what keeps the twelve
 * themes looking like one product.
 */
data class GlassPalette(
    /** Root backdrop. */
    val background: Color,
    /** Second background layer, one step up from [background]. */
    val backgroundRaised: Color,
    /** Glass card fill. */
    val card: Color,
    /** Elevated surface above a glass card. */
    val elevated: Color,
    /** Primary ink. */
    val textPrimary: Color,
    /** Secondary ink — must stay readable on [card] at every glass level. */
    val textSecondary: Color,
    /** Accent reserved for active audio. */
    val audioAccent: Color,
    /** Accent reserved for Customization. */
    val customizationAccent: Color,
    /** Low-contrast separator colour. */
    val border: Color
) {
    /** Hairline separator, used for inner dividers. */
    val borderDim: Color get() = border.copy(alpha = 0.55f)

    /** Defined edge, used for selected / focused states. */
    val borderStrong: Color get() = border.copy(alpha = 0.90f)
}

/** The dark appearance from the specification. */
val SonicCoreGlassDark = GlassPalette(
    background = Color(0xFF080D18),
    backgroundRaised = Color(0xFF0D1422),
    card = Color(0xFF121C2C),
    elevated = Color(0xFF182438),
    textPrimary = Color(0xFFF3F7FF),
    textSecondary = Color(0xFF9EACC2),
    audioAccent = Color(0xFF48DDF5),
    customizationAccent = Color(0xFFA78BFA),
    border = Color(0xFF24314A)
)

/** The light appearance, matched token for token so both modes read the same. */
val SonicCoreGlassLight = GlassPalette(
    background = Color(0xFFF2F3F9),
    backgroundRaised = Color(0xFFFCFDFF),
    card = Color(0xFFFFFFFF),
    elevated = Color(0xFFEFF1F8),
    textPrimary = Color(0xFF151927),
    textSecondary = Color(0xFF454E63),
    audioAccent = Color(0xFF0B7C93),
    customizationAccent = Color(0xFF6A4DD8),
    border = Color(0xFFC6CDDE)
)

/** The palette in force for the current theme + appearance mode. */
val LocalGlassPalette = compositionLocalOf { SonicCoreGlassDark }

// ════════════════════════════════════════════════════════════════════════════
// GEOMETRY
// ════════════════════════════════════════════════════════════════════════════

/**
 * Corner radii (GL-04).
 *
 * The app used to mix 12/14/16/17/18/20/22/26 dp for the same kind of surface,
 * which is what made cards look visually disconnected. Every surface now takes
 * one of these.
 */
object GlassRadius {
    /** Pills, chips and tags. */
    val pill = 50.dp

    /** Badges and status pills. */
    val chip = 8.dp

    /** Small inner tiles: meters, stat tiles, swatches. */
    val tile = 12.dp

    /** Buttons, text fields, icon buttons. */
    val control = 16.dp

    /** Cards inside a screen. */
    val card = 20.dp

    /** Top-level glass panel. */
    val surface = 22.dp

    /** Bottom sheets and dialogs. */
    val sheet = 26.dp

    /** The category dock. */
    val navigation = 30.dp
}

/** Spacing scale (GL-04). */
object GlassSpacing {
    val screenHorizontal = 16.dp
    val screenVertical = 12.dp

    val card = 16.dp
    val cardCompact = 12.dp

    val gap = 12.dp
    val gapSmall = 8.dp
    val gapTiny = 4.dp

    /** Minimum touch target. Nothing interactive may be smaller. */
    val touchTarget = 48.dp

    // ── Responsive scale (see `ResponsiveLayout.kt`) ──
    // Phones keep the historical 16 dp gutter; a window with room gets a wider
    // one so cards do not stretch edge to edge on a tablet. The window class
    // decides which of these applies — no screen picks a number itself.

    /** Horizontal gutter on a medium window (600–839 dp). */
    val screenHorizontalMedium = 24.dp

    /** Horizontal gutter on an expanded window (840 dp and wider). */
    val screenHorizontalExpanded = 32.dp

    /** Vertical rhythm between cards, per window class. */
    val sectionGapCompact = 14.dp
    val sectionGapMedium = 16.dp
    val sectionGapExpanded = 18.dp

    /**
     * Caps for the centred content column. Without them a phone layout is
     * stretched across a tablet: text lines run the full width and every card
     * becomes a very wide, very empty band.
     */
    val contentMaxWidthMedium = 680.dp

    /**
     * A tablet-wide column. Deliberately below the common 1280 dp tablet width
     * so the cap actually binds and leaves real gutters, instead of turning a
     * phone layout into full-bleed bands on a large screen.
     */
    val contentMaxWidthExpanded = 960.dp

    /** Narrowest a tile may get before an adaptive grid drops a column. */
    val minTileWidth = 132.dp

    /** Narrowest a two-line tile (value + footer) may get. */
    val minTileWidthWide = 168.dp

    /** Narrowest a single-number tile may get. */
    val minTileWidthNumeric = 112.dp
}

/** Elevation (GL-03). Soft separation, never a heavy glow. */
object GlassElevation {
    val flat = 0.dp
    val inset = 1.dp
    val control = 3.dp
    val card = 10.dp
    val raised = 18.dp
    val overlay = 28.dp
}

// ════════════════════════════════════════════════════════════════════════════
// OPACITY — the legibility floor
// ════════════════════════════════════════════════════════════════════════════

/**
 * Surface opacity per glass level.
 *
 * The levels get more see-through as they go up, but never past
 * [MIN_BACKING]: at the old "Crystal" setting the backing dropped to 0.12,
 * which put body text straight onto the backdrop image (defect GL-02).
 * The glass level now changes how *crisp* the glass looks — edge definition,
 * inner highlight, shadow — not how unreadable its text becomes.
 */
object GlassOpacity {
    private const val SUBTLE_BACKING = 0.94f
    private const val LIQUID_BACKING = 0.86f
    private const val CRYSTAL_BACKING = 0.78f

    /** Hard floor. No level, theme or preference may go below this. */
    const val MIN_BACKING = 0.72f

    /** Opaque fill of the glass surface itself. */
    fun backing(level: Int): Float = when (level.coerceIn(0, 2)) {
        0 -> SUBTLE_BACKING
        2 -> CRYSTAL_BACKING
        else -> LIQUID_BACKING
    }.coerceAtLeast(MIN_BACKING)

    /** Edge / hairline border strength. */
    fun edge(level: Int): Float = when (level.coerceIn(0, 2)) {
        0 -> 0.10f
        2 -> 0.24f
        else -> 0.16f
    }

    /** Specular top highlight strength. */
    fun highlight(level: Int): Float = when (level.coerceIn(0, 2)) {
        0 -> 0.05f
        2 -> 0.15f
        else -> 0.10f
    }

    /** Cyan inner lift at the bottom of a panel. Restrained. */
    fun innerGlow(level: Int): Float = when (level.coerceIn(0, 2)) {
        0 -> 0.020f
        2 -> 0.055f
        else -> 0.035f
    }

    /** Scrim behind dialogs and sheets. Always dark enough to separate. */
    fun scrim(level: Int): Float = when (level.coerceIn(0, 2)) {
        0 -> 0.55f
        2 -> 0.40f
        else -> 0.48f
    }
}

/**
 * Resolved glass material for a glass level.
 *
 * Kept as a data class + pure function so `LiquidGlassMaterialTest` pins the
 * ordering (higher level = more reflection, thinner surface, still readable).
 */
internal data class LiquidGlassMaterialSpec(
    val surfaceAlpha: Float,
    val tintAlpha: Float,
    val edgeAlpha: Float,
    val reflectionAlpha: Float,
    val shadowElevationDp: Float
)

internal fun liquidGlassMaterialSpec(level: Int): LiquidGlassMaterialSpec {
    val l = level.coerceIn(0, 2)
    return LiquidGlassMaterialSpec(
        surfaceAlpha = GlassOpacity.backing(l),
        tintAlpha = GlassOpacity.innerGlow(l),
        edgeAlpha = GlassOpacity.edge(l),
        reflectionAlpha = GlassOpacity.highlight(l),
        shadowElevationDp = when (l) {
            0 -> 8f
            2 -> 18f
            else -> 13f
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
// BLUR POLICY
// ════════════════════════════════════════════════════════════════════════════

/**
 * Blur policy (GL-01, GL-08).
 *
 * SonicCore never depends on blur alone. Real-time blur (`RenderEffect`) needs
 * Android 12, and on a dark backdrop under a translucent panel it is all but
 * invisible while still costing GPU on a low-end device — so it is used only
 * when the platform supports it *and* the user asked for it. Everywhere else
 * the same layered translucent surface renders, minus the blur pass.
 *
 * The decision is a pure function so it is unit-testable; the Android version
 * check happens in one place, [LocalGlassNativeBlur].
 */
object GlassBlur {
    /** `RenderEffect`-backed compose blur is available from Android 12. */
    const val MIN_API_FOR_NATIVE_BLUR = 31

    /** Blur radius per glass level, or 0 when the blur pass must be skipped. */
    fun radiusDp(level: Int, nativeBlurAvailable: Boolean): Dp = when {
        !nativeBlurAvailable -> 0.dp
        level <= 0 -> 0.dp
        level == 1 -> 12.dp
        else -> 20.dp
    }

    /** True when a solid/translucent fallback surface carries the glass instead. */
    fun usesFallbackSurface(nativeBlurAvailable: Boolean): Boolean = !nativeBlurAvailable
}

/** Whether this device can run the native blur pass. Set once at the theme root. */
val LocalGlassNativeBlur = compositionLocalOf { false }

/** Pure platform check, so the value can be produced without Compose. */
fun glassNativeBlurSupported(sdkInt: Int): Boolean = sdkInt >= GlassBlur.MIN_API_FOR_NATIVE_BLUR
