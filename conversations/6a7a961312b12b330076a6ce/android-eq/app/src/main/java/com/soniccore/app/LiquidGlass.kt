package com.soniccore.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.ui.design.GlassOpacity
import com.soniccore.app.ui.design.GlassRadius
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════════
// SONICCORE — APPLE "LIQUID GLASS" MATERIAL SYSTEM
//
// One place for the redesign's material language, shared by every console
// surface: thick frosted panels with a specular top edge, a soft drop shadow,
// a bottom inner glow and translucent veils so the backdrop reads THROUGH the
// glass. The chrome is neutral; the selectable neon themes stay as luminous
// accents on top of it.
//
// The chrome adapts to the surface mode: dark mode uses white veils/edges over
// the backdrop, light mode uses dark hairlines and a light backing so panels
// stay legible in both. Pure presentation — nothing here touches DSP, capture,
// audio buffers, latency or sample rate.
// ════════════════════════════════════════════════════════════════════════════

// Radii come from the shared tokens so a glass panel cannot invent its own
// corner any more (defect GL-04: eight different radii for the same surface).
val GLASS_RADIUS: Dp get() = GlassRadius.surface
val GLASS_SHEET_RADIUS: Dp get() = GlassRadius.sheet

private val light: Boolean get() = appModeState.value == SurfaceModes.LIGHT

// Backing tint under every glass panel — this is what the "GLASS EFFECT"
// setting (LOW/MEDIUM/HIGH) still controls: higher glass = more see-through.
//
// GL-02: it used to reach 0.12 at the HIGH setting, which left body text on the
// bare backdrop. The scale is now floored by GlassOpacity.MIN_BACKING, so the
// setting changes how crisp the glass looks, never how readable the text is.
fun glassBackingAlpha(): Float = when (appGlassState.value) {
    0 -> 0.62f
    2 -> GlassOpacity.MIN_BACKING
    else -> 0.44f
}

// Hairline edges — brighter at the top, darker in light mode.
fun glassEdgeTop(): Color =
    if (light) Color(0xFF33406B).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.55f)
fun glassEdgeBottom(): Color =
    if (light) Color(0xFF33406B).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f)

// Tracks, dial cores and inks.
fun glassTrack(): Color =
    if (light) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
fun dialCoreBrush(): Brush =
    if (light) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.92f), Color(0xFFD9E2F2).copy(alpha = 0.92f)))
    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color(0xFF141E50).copy(alpha = 0.80f)))
fun dialInk(): Color = if (light) Color(0xFF101828) else Color.White
fun dialEdge(): Color =
    if (light) Color(0xFF33406B).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.30f)

// ── .chip ──────────────────────────────────────────────────────────────────
// background rgba(10,18,50,.5) · border 1px rgba(255,255,255,.2)
// inset 0 1px 0 rgba(255,255,255,.35) · transition .2s
// :hover rgba(60,120,200,.45) · :active rgba(60,120,200,.65)
enum class GlassChipKind { Neutral, Active, Muted, Warn, Error, Selected }

fun chipFill(pressed: Boolean, hovered: Boolean): Color = when {
    !light && pressed -> Color(0xFF3C78C8).copy(alpha = 0.65f)
    !light && hovered -> Color(0xFF3C78C8).copy(alpha = 0.45f)
    light && pressed -> T.primary.copy(alpha = 0.45f)
    light && hovered -> T.primary.copy(alpha = 0.22f)
    else -> S.cardDeep.copy(alpha = 0.55f)
}

fun chipSelectedBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFF96F0FF).copy(alpha = 0.55f), Color(0xFF508CFF).copy(alpha = 0.40f))
)

fun chipInk(): Color = S.textSoft
fun chipOnInk(): Color = if (light) Color(0xFF0B7A55) else Color(0xFF6DFFC0)
fun chipWarnInk(): Color = if (light) Color(0xFF8A6100) else Color(0xFFFFC107)
fun chipErrInk(): Color = if (light) Color(0xFFB3261E) else Color(0xFFFF8A94)

private fun chipEdge(kind: GlassChipKind): Color = when (kind) {
    GlassChipKind.Active -> (if (light) Color(0xFF12A36C) else Color(0xFF5AFFBE)).copy(alpha = 0.65f)
    GlassChipKind.Error -> (if (light) Color(0xFFB3261E) else Color(0xFFFF6E78)).copy(alpha = 0.65f)
    GlassChipKind.Warn -> (if (light) Color(0xFF8A6100) else Color(0xFFFFC107)).copy(alpha = 0.65f)
    GlassChipKind.Muted -> glassEdgeTop()
    GlassChipKind.Selected -> glassEdgeTop()
    GlassChipKind.Neutral -> glassEdgeTop()
}

private fun chipFg(kind: GlassChipKind): Color = when (kind) {
    GlassChipKind.Active -> chipOnInk()
    GlassChipKind.Error -> chipErrInk()
    GlassChipKind.Warn -> chipWarnInk()
    GlassChipKind.Muted -> S.textSoft.copy(alpha = 0.62f)
    GlassChipKind.Selected -> Color.White
    GlassChipKind.Neutral -> chipInk()
}

@Composable
fun LiquidChip(
    label: String,
    kind: GlassChipKind = GlassChipKind.Neutral,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()

    val selected = kind == GlassChipKind.Selected
    // :active .65 → :hover .45 → resting, animated over .2s.
    val targetFill = chipFill(pressed, hovered)
    val fill by animateColorAsState(targetFill, tween(200), label = "chipFill")
    val edge = chipEdge(kind)
    val fg = chipFg(kind)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier
                    .shadow(
                        8.dp, RoundedCornerShape(50),
                        ambientColor = Color(0xFF6EDCFF), spotColor = Color(0xFF6EDCFF)
                    )
                    .background(chipSelectedBrush())
                else Modifier.background(fill)
            )
            // 1px edge, brighter at the top — also carries the inset highlight.
            .border(
                1.dp,
                Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * 0.55f))),
                RoundedCornerShape(50)
            )
            .then(
                if (onClick != null)
                    Modifier
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        if (glyph != null) {
            Text(glyph, fontSize = 8.sp, color = fg)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ── .btn ───────────────────────────────────────────────────────────────────
// background linear-gradient(180deg, rgba(255,255,255,.3), rgba(100,160,255,.25))
// border 1px rgba(255,255,255,.4) · radius 16 · inset 0 1px 0 rgba(255,255,255,.6)
// :active linear-gradient(180deg, rgba(255,255,255,.2), rgba(100,160,255,.4))
// .btn.d — border-color rgba(255,120,130,.5), color #ffc0c6
@Composable
fun LiquidButton(
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(GlassRadius.control)

    val topAlpha = if (pressed) 0.20f else 0.30f
    val bottomAlpha = if (pressed) 0.40f else 0.25f
    val tint = if (light) Color(0xFFBFD4F5) else Color(0xFF64A0FF)
    val fg = when {
        destructive && light -> Color(0xFF8A1F27)
        destructive -> Color(0xFFFFC0C6)
        light -> Color(0xFF101828)
        else -> Color.White
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = when { !enabled -> 0.12f; light -> 0.75f; else -> topAlpha }),
                        tint.copy(alpha = when { !enabled -> 0.10f; light -> 0.55f; else -> bottomAlpha })
                    )
                )
            )
            .border(
                1.dp,
                if (destructive) Color(0xFFFF7882).copy(alpha = 0.5f) else glassEdgeTop(),
                shape
            )
            .then(
                if (enabled)
                    Modifier.clickable(
                        interactionSource = interaction, indication = null, onClick = onClick
                    )
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        // inset 0 1px 0 rgba(255,255,255,.6) — top inner highlight
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = if (enabled) 0.6f else 0.2f))
        )
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) fg else fg.copy(alpha = 0.4f)
        )
    }
}

// ── .tg  switch ────────────────────────────────────────────────────────────
// 38x22 pill · on: linear-gradient(90deg,#3fe0c0,#4a8bff) knob right
// off: rgba(255,255,255,.18) knob left · inset 0 1px 3px rgba(0,0,0,.4)
@Composable
fun LiquidToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    val knobOffset by animateDpAsState(if (checked) 18.dp else 2.dp, tween(200), label = "tgKnob")
    val offTrack = if (light) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) Brush.horizontalGradient(listOf(Color(0xFF3FE0C0), Color(0xFF4A8BFF)))
                else Brush.horizontalGradient(listOf(offTrack, offTrack))
            )
            .border(
                1.dp,
                if (checked) Color.White.copy(alpha = 0.35f) else glassEdgeTop(),
                RoundedCornerShape(50)
            )
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = knobOffset)
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

// ── .orb — power orb ───────────────────────────────────────────────────────
// 46px circle · radial-gradient(circle at 35% 28%, #fff, #9ff3ff 18%,
// #2fb8e8 55%, #3a4fd8 100%) · glow rgba(80,210,255,.7) · pulse 4s
@Composable
fun LiquidOrb(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val infinite = rememberInfiniteTransition(label = "orbPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orbBrightness"
    )
    val lift = 0.15f * pulse

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                if (active) 22.dp else 10.dp, RoundedCornerShape(50), clip = false,
                ambientColor = Color(0xFF50D2FF), spotColor = Color(0xFF50D2FF)
            )
            .clip(RoundedCornerShape(50))
            .drawWithCache {
                val orb = if (active) Brush.radialGradient(
                    0f to Color.White,
                    0.18f to Color(0xFF9FF3FF),
                    0.55f to Color(0xFF2FB8E8),
                    1f to Color(0xFF3A4FD8),
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.28f),
                    radius = this.size.minDimension * 0.78f
                ) else Brush.radialGradient(
                    0f to Color(0xFFB9C4D8),
                    0.35f to Color(0xFF4A566E),
                    1f to Color(0xFF2A3242),
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.28f),
                    radius = this.size.minDimension * 0.78f
                )
                // inset 0 -6px 10px rgba(30,40,160,.5) — bottom inner shade
                val shade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xFF1E28A0).copy(alpha = if (active) 0.45f else 0.25f),
                    startY = this.size.height * 0.55f,
                    endY = this.size.height
                )
                onDrawBehind {
                    drawCircle(orb)
                    drawCircle(shade)
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
            .clickable { onClick() }
    ) {
        // filter:brightness() pulse — a soft white lift on the crest.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = lift), Color.Transparent)),
                    RoundedCornerShape(50)
                )
        )
    }
}

// ── .a — entrance rise ─────────────────────────────────────────────────────
// @keyframes rise {from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:none}}
// animation: rise .8s cubic-bezier(.2,.8,.2,1) both
@Composable
fun Modifier.riseIn(delayMillis: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(800, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "riseIn"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 14.dp.toPx()
    }
}
