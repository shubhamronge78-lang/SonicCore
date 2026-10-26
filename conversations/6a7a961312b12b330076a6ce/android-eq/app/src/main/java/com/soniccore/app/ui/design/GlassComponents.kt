package com.soniccore.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * SONICCORE — LIQUID GLASS COMPONENTS
 *
 * The reusable material vocabulary every screen renders with. All of it is
 * driven by `GlassTokens.kt`, so the appearance of the whole app changes in one
 * place rather than screen by screen.
 *
 * Layering rule (performance, GL-08): exactly one blur pass ever runs, and only
 * on the decorative backdrop in [GlassScaffold] — never per card, never
 * stacked. Cards composite translucent surfaces over that one backdrop, which
 * is what the specification calls the fallback surface. On devices without
 * `RenderEffect` (below Android 12) the same code path renders with no blur
 * pass at all.
 *
 * Presentation only: nothing here touches DSP, capture, buffers, latency or the
 * sample rate.
 */

// ════════════════════════════════════════════════════════════════════════════
// SURFACE PRIMITIVES
// ════════════════════════════════════════════════════════════════════════════

/** True when the active palette is a light one, so edges flip to dark hairlines. */
@Composable
internal fun glassPaletteIsLight(): Boolean =
    SonicCoreColors.Background.luminance() > 0.5f

/** Hairline edge, brighter at the top — the "lip" of the glass. */
@Composable
internal fun glassEdgeTop(): Color =
    if (glassPaletteIsLight()) Color(0xFF33406B).copy(alpha = 0.26f)
    else Color.White.copy(alpha = 0.42f)

@Composable
internal fun glassEdgeBottom(): Color =
    if (glassPaletteIsLight()) Color(0xFF33406B).copy(alpha = 0.12f)
    else Color.White.copy(alpha = 0.14f)

/** Where a panel sits relative to the surface behind it. */
enum class LiquidGlassDepth {
    /** Above the backdrop — carries a shadow and a specular top sheen. */
    Floating,

    /** Recessed into a panel — flatter, no sheen, reads as a well. */
    Inset
}

/**
 * The glass panel. Every card, sheet, dialog and toolbar in the app is this.
 *
 * @param shape corner shape; take it from [GlassRadius] so surfaces stay
 *   consistent — the app used to mix eight different radii for the same thing.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    depth: LiquidGlassDepth = LiquidGlassDepth.Floating,
    shape: Shape = RoundedCornerShape(GlassRadius.surface),
    contentPadding: Dp = GlassSpacing.card,
    ringColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val level = LocalGlassIntensity.current
    val palette = LocalGlassPalette.current
    val isInset = depth == LiquidGlassDepth.Inset

    val material = liquidGlassMaterialSpec(level)
    // GL-02: a recessed well must be *more* opaque than the panel around it —
    // never less — so text inside it never sits on the bare backdrop.
    val backing = (material.surfaceAlpha + if (isInset) 0.06f else 0f)
        .coerceAtMost(1f)
        .coerceAtLeast(GlassOpacity.MIN_BACKING)

    val edgeTop = glassEdgeTop()
    val edgeBottom = glassEdgeBottom()
    val tint = palette.card
    val elevated = palette.elevated

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isInset) GlassElevation.inset else material.shadowElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.background,
                spotColor = palette.background
            )
            .clip(shape)
            .background(
                // Layered translucency instead of blur: a three-stop ramp keeps
                // the panel reading as a solid object while the backdrop still
                // shows through it.
                Brush.verticalGradient(
                    listOf(
                        elevated.copy(alpha = backing * 0.92f),
                        tint.copy(alpha = backing),
                        tint.copy(alpha = backing * 0.96f)
                    )
                )
            )
            // The edge is drawn with `border` rather than inside the draw
            // cache: `border` follows the real shape, so a sheet's two rounded
            // top corners and a pill's 50% ends get the right hairline. The
            // old hand-rolled round-rect assumed a single uniform radius.
            .border(
                1.dp,
                if (ringColor != null) {
                    Brush.verticalGradient(listOf(ringColor, ringColor))
                } else {
                    Brush.verticalGradient(listOf(edgeTop, edgeBottom))
                },
                shape
            )
            .drawWithCache {
                // GL-03/GL-08: the old panel painted a full-size radial gradient
                // on every card — one overdraw of the whole card per card — and
                // that is what made stacked cards look washed out. A single
                // hairline along the top gives the same specular read for a
                // fraction of the pixels.
                val highlight = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = material.reflectionAlpha * 0.9f),
                        Color.Transparent
                    )
                )
                val inset = 0.5.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (!isInset) {
                        drawLine(
                            brush = highlight,
                            start = Offset(size.width * 0.12f, inset),
                            end = Offset(size.width * 0.88f, inset),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
            .padding(contentPadding),
        content = content
    )
}

/**
 * Glass card — the standard container for one group of content.
 *
 * Takes the standard card radius and padding unless the caller overrides them
 * for a genuinely different surface (a sheet, a tile, a toolbar).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = GlassSpacing.card,
    shape: Shape = RoundedCornerShape(GlassRadius.card),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        content = content
    )
}

/** Recessed well inside a card: meters, read-outs, grouped rows. */
@Composable
fun GlassInset(
    modifier: Modifier = Modifier,
    contentPadding: Dp = GlassSpacing.cardCompact,
    shape: Shape = RoundedCornerShape(GlassRadius.tile),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        depth = LiquidGlassDepth.Inset,
        shape = shape,
        contentPadding = contentPadding,
        content = content
    )
}

// ════════════════════════════════════════════════════════════════════════════
// SCAFFOLD
// ════════════════════════════════════════════════════════════════════════════

/**
 * Root frame of every SonicCore screen.
 *
 * Two jobs, both of them defects that used to be handled — or not handled — per
 * screen:
 *
 *  - **Insets (GL-06).** Content is padded away from the status bar and the
 *    keyboard in exactly one place. Before this, only the dock and one sheet
 *    applied any inset at all, so the first row of every screen sat under the
 *    status bar and a bottom text field was covered by the keyboard.
 *  - **One backdrop (GL-08).** The decorative backdrop is composed exactly
 *    once, and it is the only place a blur pass may run — see
 *    [GlassBlur]. Nothing in the content layers a second blur on top, so the
 *    cost does not grow with the number of cards on screen.
 *
 * @param background the decorative backdrop, drawn behind everything.
 * @param applyInsets set false for a screen that manages its own insets.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    applyInsets: Boolean = true,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            background()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (applyInsets) {
                        // The app is edge-to-edge (`enableEdgeToEdge()` in
                        // MainActivity), so the decorative backdrop runs under
                        // the system bars while the content is inset once, here:
                        // status bar, display cutout in any orientation, and the
                        // keyboard. The bottom navigation bar is inset by
                        // `GlassNavigation` itself — the one component that sits
                        // against that edge — so it is deliberately not added
                        // again here (that would double-pad the dock).
                        Modifier.statusBarsPadding().displayCutoutPadding().imePadding()
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// CHROME
// ════════════════════════════════════════════════════════════════════════════

/**
 * Screen header.
 *
 * Replaces the ad-hoc headers that each screen used to build, so the back
 * affordance, the title scale and the action button are in the same place on
 * every screen.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    actionIcon: String? = null,
    actionDescription: String = "Action",
    onAction: (() -> Unit)? = null,
    accent: Color = SonicCoreColors.Cyan
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = GlassSpacing.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
    ) {
        if (onBack != null) {
            GlassIconButton(contentDescription = "Back to previous screen", onClick = onBack) {
                Text("‹", color = SonicCoreColors.Cyan, fontSize = 28.sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Text(
                title.uppercase(),
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionIcon != null && onAction != null) {
            GlassIconButton(contentDescription = actionDescription, onClick = onAction) {
                Text(actionIcon, color = accent, fontSize = 18.sp)
            }
        }
    }
}

/** Uppercase section caption above a group of cards. */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = SonicCoreColors.Cyan,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = GlassSpacing.gapTiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
    ) {
        // A short accent rule instead of a glow — cheap and unmistakable.
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(GlassRadius.pill))
                .background(accent.copy(alpha = 0.75f))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                color = SonicCoreColors.PrimaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        action?.invoke(this)
    }
}

/**
 * The category dock.
 *
 * Deliberately generic: [GlassNavigation] knows nothing about categories, so
 * the same control can carry the six categories, six tabs, or a future second
 * level without a second implementation drifting away from this one.
 */
data class GlassNavItem(
    val label: String,
    val icon: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun GlassNavigation(
    items: List<GlassNavItem>,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    accentOf: (GlassNavItem) -> Color = { SonicCoreColors.Cyan }
) {
    val level = LocalGlassIntensity.current
    val material = liquidGlassMaterialSpec(level)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = if (landscape) 18.dp else 10.dp,
                vertical = GlassSpacing.gapSmall
            ),
        contentPadding = 7.dp,
        shape = RoundedCornerShape(GlassRadius.navigation)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items.forEach { item ->
                val accent = accentOf(item)
                val activeSurface by animateColorAsState(
                    targetValue = if (item.selected) accent.copy(alpha = material.tintAlpha + 0.10f)
                    else Color.Transparent,
                    label = "glassNavSelection"
                )
                val contentColor = when {
                    !item.enabled -> SonicCoreColors.SecondaryText.copy(alpha = 0.45f)
                    item.selected -> accent
                    else -> SonicCoreColors.SecondaryText
                }
                androidx.compose.material3.Surface(
                    onClick = item.onClick,
                    enabled = item.enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = item.label
                            stateDescription = if (item.selected) "Selected" else "Not selected"
                        },
                    shape = RoundedCornerShape(GlassRadius.surface),
                    color = activeSurface,
                    contentColor = contentColor,
                    border = BorderStroke(
                        0.8.dp,
                        if (item.selected) accent.copy(alpha = material.edgeAlpha)
                        else Color.Transparent
                    ),
                    shadowElevation = if (item.selected) GlassElevation.control else GlassElevation.flat
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            item.icon,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = contentColor
                        )
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            // GL-07: labels used to run off the edge of a narrow
                            // item with no overflow handling and clip mid-letter.
                            overflow = TextOverflow.Ellipsis,
                            color = if (item.selected) SonicCoreColors.PrimaryText
                            else SonicCoreColors.SecondaryText
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// CONTROLS
// ════════════════════════════════════════════════════════════════════════════

/**
 * Glass switch with a real disabled state and a minimum touch target.
 *
 * The Material 3 switch carries the interaction and accessibility semantics
 * (role, state description, toggle action); only its colours and its target
 * size come from the tokens, so font scaling, TalkBack and Switch Access keep
 * working exactly as before.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = GlassSpacing.touchTarget),
        colors = SwitchDefaults.colors(
            checkedThumbColor = SonicCoreColors.Background,
            checkedTrackColor = SonicCoreColors.Cyan,
            checkedBorderColor = SonicCoreColors.Cyan.copy(alpha = 0.45f),
            uncheckedThumbColor = SonicCoreColors.SecondaryText,
            uncheckedTrackColor = SonicCoreColors.ElevatedSurface,
            uncheckedBorderColor = SonicCoreColors.Border.copy(alpha = 0.35f),
            disabledCheckedThumbColor = SonicCoreColors.SecondaryText.copy(alpha = 0.5f),
            disabledCheckedTrackColor = SonicCoreColors.Cyan.copy(alpha = 0.35f),
            disabledUncheckedThumbColor = SonicCoreColors.SecondaryText.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = SonicCoreColors.ElevatedSurface.copy(alpha = 0.5f)
        )
    )
}

/**
 * Glass text field.
 *
 * A single implementation for every text input in the app, so the focus ring,
 * the placeholder colour and the padding are identical everywhere. The inner
 * field is a stock `BasicTextField`, so IME behaviour, autofill, selection and
 * font scaling are unchanged.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leadingIcon: String? = null,
    maxLines: Int = if (singleLine) 1 else 4
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val ringColor by animateColorAsState(
        targetValue = if (focused) SonicCoreColors.Cyan.copy(alpha = 0.55f)
        else SonicCoreColors.Border.copy(alpha = 0.20f),
        label = "glassFieldRing"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label.uppercase(),
                color = SonicCoreColors.SecondaryText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(bottom = GlassSpacing.gapTiny)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassRadius.control))
                .background(
                    SonicCoreColors.Background.copy(alpha = 0.55f)
                )
                .border(1.dp, ringColor, RoundedCornerShape(GlassRadius.control))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassSpacing.gapSmall)
        ) {
            if (leadingIcon != null) {
                Text(leadingIcon, color = SonicCoreColors.Cyan, fontSize = 15.sp)
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    interactionSource = interaction,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyMedium.copy(color = SonicCoreColors.PrimaryText)
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(SonicCoreColors.Cyan, SonicCoreColors.Cyan)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// OVERLAYS
// ════════════════════════════════════════════════════════════════════════════

/**
 * Modal glass panel.
 *
 * Built on `Dialog`, the same primitive the app's existing sheets use, so the
 * scrim, the system back gesture and the window insets behave identically.
 * Nothing is decorated onto the scrim beyond the [GlassOpacity.scrim] token.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = SonicCoreColors.Cyan,
    confirmButton: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(GlassSpacing.screenHorizontal),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(GlassRadius.sheet),
                contentPadding = GlassSpacing.card,
                content = {
                    if (title != null) {
                        Text(
                            title.uppercase(),
                            color = accent,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(Modifier.height(GlassSpacing.gapSmall))
                    }
                    body()
                    if (confirmButton != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = GlassSpacing.gap),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            content = confirmButton
                        )
                    }
                }
            )
        }
    }
}

/**
 * Content padding every screen uses, so a new screen cannot invent its own.
 */
object GlassScreenPadding {
    val content = PaddingValues(
        horizontal = GlassSpacing.screenHorizontal,
        vertical = GlassSpacing.screenVertical
    )
}
