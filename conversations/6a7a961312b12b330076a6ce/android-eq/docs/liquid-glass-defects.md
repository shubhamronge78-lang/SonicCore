# Liquid Glass — defect audit and rebuild

Audit date: 2026-10-26 · branch `audio-section-restructure`

## 1. What this document is

Every visual defect found while auditing SonicCore's Liquid Glass appearance,
the root cause behind it, and what the rebuild changed. The fixes are in the
shared design system, not in individual screens: the app had two parallel glass
implementations whose tokens disagreed, so patching one screen left the next one
broken. Fixing the token layer fixes all of them.

Code:

| File | Role |
| --- | --- |
| `app/src/main/java/com/soniccore/app/ui/design/GlassTokens.kt` | Palette, radii, spacing, elevation, opacity and blur policy — the single source of truth |
| `app/src/main/java/com/soniccore/app/ui/design/GlassComponents.kt` | The shared components: scaffold, top bar, surface, card, navigation, switch, slider, text field, dialog |
| `app/src/main/java/com/soniccore/app/ui/design/SonicCoreComponents.kt` | Widgets that live inside a surface; also the shared-button / slider / switch vocabulary |
| `app/src/main/java/com/soniccore/app/LiquidGlass.kt` | The legacy chrome helpers, now reading the same tokens |

Tests: `GlassTokensTest` (tokens and theme contracts),
`LiquidGlassMaterialTest` (glass level ordering), `ThemeStudioTest` (the twelve
themes).

## 2. The defect checklist

| # | Defect | Root cause | Fix |
| --- | --- | --- | --- |
| GL-01 | Glass looked different on different screens; some surfaces had no glass at all | Two independent glass implementations — `LiquidGlass.kt` (`T`/`S` palette, `appGlassState`) and `ui/design/SonicCoreComponents.kt` (`SonicCoreColors`, `liquidGlassMaterialSpec`) — with different gradients, edges and defaults, so a screen's look depended on which one it happened to call | One token layer (`GlassTokens.kt`). Both implementations now read it; the legacy helpers and the shared components produce the same material |
| GL-02 | Body text unreadable on the HIGH ("Crystal") glass setting | Surface backing fell to **0.12 alpha** (`glassBackingAlpha`) and 0.58 (`liquidGlassMaterialSpec`), and AlertDialogs used `glassSurfaceAlpha()` down to 0.66 — text sat on the backdrop photograph | `GlassOpacity.MIN_BACKING = 0.72` is a hard floor on every path: glass surfaces, legacy backing, and every dialog/sheet |
| GL-03 | Cards washed out / visually disconnected; stacked cards looked bright | Every panel painted a **full-size radial gradient** plus a vertical veil plus an inner glow — three full-card overdraws each, all stacking | One three-stop gradient for depth plus a single hairline highlight. The per-card radial glow is gone |
| GL-04 | Eight different corner radii and inconsistent spacing for the same kind of surface (12/14/16/17/18/20/22/26 dp) | Radii were literals at each call site | `GlassRadius` (chip 8 · tile 12 · control 16 · card 20 · surface 22 · sheet 26 · navigation 30 · pill) and `GlassSpacing`. All 30 literal radii across 21 files were normalised to a token |
| GL-05 | Switching theme, accent or appearance mode left stale colours and mismatched surfaces until something else forced a repaint | `SonicCoreColors` was written in a **`SideEffect`**, which runs *after* composition and layout — so the first frame after a theme change still painted the previous palette | The palette is applied inside `remember(palette)` **during** composition, before `content()` composes. Every reader sees the new palette in the same frame; switching themes needs no restart |
| GL-06 | System insets had **no owner**: two call sites applied an inset and nothing else did, so a screen that needed one had to remember it, and no surface coordinated status bar, navigation bar, keyboard and cutout | There was no scaffold component at all; `navigationBarsPadding()` appeared inside the dock and inside one sheet | `GlassScaffold` owns the insets for the whole app (`statusBarsPadding` + `imePadding`), and `GlassBottomSheet` carries its own IME and navigation-bar insets. **Caveat:** this app is not edge-to-edge — `Theme.NeonEQ` sets an explicit `android:statusBarColor` and nothing calls `setDecorFitsSystemWindows(false)` or `enableEdgeToEdge()`, so the decor already insets the content and these padding modifiers most likely resolve to zero today. Treat GL-06 as structural (a single owner, correct if the window becomes edge-to-edge, e.g. once `targetSdk` reaches 35) rather than as a confirmed on-screen overlap that was fixed |
| GL-07 | Dock item labels had no overflow handling at all | Six items share the width of a narrow phone, so each gets roughly 52 dp; the longest label (`SHIZUKU`) at 9 sp is right at that boundary and Compose clips at the layout edge rather than ellipsising | `Ellipsis` on the dock label, and the dock moved to the shared `GlassNavigation`, which enforces the token radius, a 56 dp item and a real disabled state. Whether it clipped on a particular device could not be measured here |
| GL-08 | Scrolling lag and needless GPU work | A `rememberInfiniteTransition` animated three full-screen radial gradients **forever** on every screen; blur was never used but neither was it avoided by policy | `GlassBlur` decides the one permitted blur pass: on the static backdrop photograph only, only on Android 12+, only above the Subtle level, so the result is cached. No card ever blurs |
| GL-09 | The twelve themes did not look like one product | Each theme carried its own text colours (`#FFFFFF` / `#D8E0F5`) and the default theme's ramp was a near-black purple-tinted set, not the specified navy. The documented palette existed nowhere in code | The specification's palette is now `SonicCoreGlassDark`, the default token values, `SurfaceModes.DARK` and the `classic` ("Obsidian Glass") theme verbatim. All twelve themes share the spec's primary/secondary ink and every glass token; each keeps its own surfaces and accent |
| GL-10 | Unnecessary continuous animation, ignoring the motion preference | The backdrop drift ran regardless of the Reduced/Off motion setting | The drift freezes at the same composition and the transition is not created at all when the motion preference reduces motion |
| GL-11 | Dialogs, sheets and menus each had their own container colour, radius and padding | No shared dialog/sheet component; each was an `AlertDialog` with hand-set `containerColor` | `GlassDialog` and the repaired `GlassBottomSheet` (token radius, IME inset, legibility floor on the scrim and container) |

### Found but deliberately not changed

* **`LiquidButton`, `LiquidOrb`, `LiquidToggle`** remain in `LiquidGlass.kt`.
  `LiquidOrb` and `LiquidButton` have no call sites. `LiquidToggle`'s four call
  sites in the engine settings sheet were migrated to `GlassSwitch`; the
  composable itself is left in place rather than deleted, so nothing that
  imports it breaks. It reads the shared tokens, so it no longer looks foreign.
* **Small inner tiles keep 8–12 dp radii.** They are badges and stat pills, not
  glass panels; they map to `GlassRadius.chip` and `GlassRadius.tile`.
* **`NeonCard` / `riseIn` entrance animation** is unchanged. It is a one-shot
  entrance, not a continuous animation, and it already honours
  `motionDurationMillis`.

## 3. What could not be verified here

SonicCore is an Android app; this workspace has no display and no device. The
following are **not** verified automatically and need a physical device:

* On-screen rendering of any screen, at any glass level or theme.
* Layout on small phones, landscape and tablets.
* Font scaling and accessibility contrast on a real display.
* Whether the single allowed blur pass actually runs (API 31+ device).
* Gate/limiter behaviour and the MediaProjection capture flow (pre-existing).

What *is* verified here:

* `./gradlew :app:testDebugUnitTest` — **241 tests, 0 failures, 0 skipped**,
  including the new `GlassTokensTest` (9 tests) and the existing
  `LiquidGlassMaterialTest` / `ThemeStudioTest`.
* The native DSP host harness — `HARNESS PASS (0 failures)`.
* `./gradlew :app:assembleDebug` — `BUILD SUCCESSFUL`; the debug APK is built
  and served.
* `./gradlew :app:lintDebug` — **6 errors, unchanged from before this work**
  (the capture `AudioRecord` path ×2, `SettingsToolScreens`' API-28
  `signingInfo` read ×2, and the two Shizuku `RestrictedApi` calls). None of
  them is in a file this work touches, and no new warning was introduced in the
  new design-system files.
* `tools/license_check.py` — PASS (no copyleft licence).
* `tools/check_docs_links.py` — PASS (this document included).
