# Changelog

## Unreleased — platform completion (six categories, twelve themes, splash)

Not a tagged release: `versionName` is still **3.6.0** and `versionCode` still
comes from `GITHUB_RUN_NUMBER`, so the owner decides which of these changes ships
as which release. No signing configuration was changed. Every item below was
verified with the commands listed at the end of this entry.

### Navigation — six categories instead of a scrolling 16-chip dock

`SonicCoreCategory` groups all 16 destinations into the six categories the
platform plan asks for: **Audio**, **Device Center**, **Shizuku Center**,
**Customization**, **Permissions & Privacy**, **Settings**. The dock now shows
those six, evenly weighted so it never scrolls sideways, plus a contextual row
for the destinations of the active category — so no destination became
unreachable when the flat dock was removed. `SonicCoreDestination` keeps every
existing id and label; only `consoleDestinations` (the flat list) is gone, and
`SonicCoreNavigationTest` now asserts the category contract instead: six
categories in order, every destination owned exactly once, each tab landing
inside its own category.

### Customization — twelve themes, one accent picker, honest wallpaper tint

- The catalogue grew from 8 to **12 themes** with every legacy id unchanged
  (`classic`, `aurora`, `synthwave`, `glacier`, `emerald`, `ember`, `midnight`,
  `adaptive`), plus **System Default**, **Light Studio**, **Custom Accent** and
  **Dynamic Wallpaper**.
- `NeonTheme` gained `preferredMode`: System Default applies the system
  appearance and Light Studio applies the light one, while the legacy themes
  keep leaving the user's appearance mode alone.
- **Custom Accent** is real: six swatches persist through the existing UI
  preferences and rebuild the palette live (`ThemeAccentPreference`).
- **Dynamic Wallpaper** reads `WallpaperManager.getWallpaperColors` for the
  system wallpaper's dominant colour. Android only exposes that to apps holding
  `READ_WALLPAPER_INTERNAL`, so on an ordinary install the probe reports it as
  not readable and the theme keeps its own palette — the Customization Center
  shows which of the two happened instead of pretending.

### UI — animated splash

An animated first-frame overlay (monogram scales in over a cyan→violet waveform
sweep, then fades out and removes itself). It loads no resource, reports no fake
progress, swallows taps while it covers the console, and is skipped entirely
when the system animation scale is off.

### Documentation and checks

- New: `docs/feature-matrix.md`, `docs/permissions-audit.md`,
  `docs/repository-integration.md` (licence provenance, what was reused, what was
  refused and why), plus a repository-root `README.md`.
- New tools: `tools/license_check.py` (dependency licences from the resolved
  Gradle graph and the published POMs; fails on copyleft) and
  `tools/check_docs_links.py` (every relative Markdown link).

### Verification for this entry

| Check | Command | Result |
| --- | --- | --- |
| JVM unit tests | `./gradlew :app:testDebugUnitTest --no-daemon` | **176 tests, 0 failures** (was 159) |
| Debug APK | `./gradlew :app:assembleDebug --no-daemon` | BUILD SUCCESSFUL |
| Native DSP regression | `tools/dspharness/harness.cpp` under ASan+UBSan | HARNESS PASS (0 failures) |
| Dependency licences | `python3 tools/license_check.py` | PASS — 109 shipped / 112 test-only artifacts, no copyleft |
| Documentation links | `python3 tools/check_docs_links.py` | PASS — every relative link resolves |
| Android lint | `./gradlew :app:lintDebug --no-daemon` | **4 errors, 55 warnings** — all four errors are pre-existing and in files this change does not touch (see below); lint is not part of CI, and no error class was introduced here |

The four pre-existing lint errors, none of them in a file this change touches:

| Location | Issue |
| --- | --- |
| `capture/AudioCapabilityManager.kt:63` | `MissingPermission` — `AudioRecord.Builder()` |
| `capture/CaptureEqService.kt:276` | `MissingPermission` — same capture path |
| `shizuku/ShizukuBridge.kt:161` | `RestrictedApi` — `Shizuku.getServerPatchVersion` |
| `shizuku/ShizukuDiagnostics.kt:84` | `RestrictedApi` — the same call |

The first two sit in the playback-capture path, which is deliberately preserved
as-is; the last two read an API the Shizuku client marks as restricted to its own
library group. Fixing them (an explicit permission check, or dropping/wrapping
the patch-version read) is a separate decision and was not part of this work.

Device-only behaviour (capture consent, routing, wallpaper colours, Shizuku
authorization) is **not** verified here: this environment has no Android device.
Those paths are listed as not device-verified in `docs/feature-matrix.md`.

## v2.0.2 (Build #58) — volume + visualizer fixes

### Fixed: EQ toggle ON was quieter than OFF

**Root cause:** `EqualizerEngine.computePreampDb()` subtracted preamp gain for
*every* active effect — BassBoost (~1 dB per 100 strength), Virtualizer
(~0.5 dB per 100) and LoudnessEnhancer (~1 dB per 100 mB). With the default
150/150/150 settings that applied a constant **−2 dB to all band levels**
whenever the EQ was enabled. Since LoudnessEnhancer at 150 only adds +1.5 dB,
the net effect of turning the EQ on was *quieter* audio — and compensating
loudness defeated the point of a loudness slider entirely.

**Fix:**
- **LoudnessEnhancer and Virtualizer are no longer compensated.** Loudness is
  the user explicitly requesting gain; the virtualizer is a stereo-width
  effect with negligible level gain.
- **BassBoost** (the actual distortion source on small speakers) is still
  compensated but at half the old rate: ~0.5 dB per 100 strength. Default
  150 costs nothing; maximum 300 trims ~1.5 dB.
- **Band boosts** now count toward compensation — but only above +6 dB, at
  half the excess, computed from the maximum positive band level.
- Total compensation cap lowered from −6 dB to **−4 dB**.

**Result:** default settings are now transparent (0 dB preamp) — the EQ toggle
no longer changes perceived volume, and loudness actually adds the gain it
promises. Distortion protection still engages for extreme settings
(bass 300, bands pushed past +6 dB).

### Fixed: Visualizer stalling and freezing

**Root cause 1 — MIUI silently kills the capture:** the `Visualizer(0)`
waveform callbacks stop without any error after screen-off, track changes or
output switches. The object stays "enabled" but delivers nothing, and nothing
ever re-attached it.

**Fix:** a **self-heal watchdog** in the session poller (`scanForActiveSessions()`,
1.5 s cadence). If the EQ is enabled, audio is playing (active playback configs
present) and no waveform has arrived for **> 4 s**, the capture is re-attached
on the audio executor. Repeat failures back off progressively (4 s × retry
count, capped ≈ 60 s) so devices that never deliver data aren't hammered.
`lastWaveformAt` and `visRetryCount` are `@Volatile` — they are written by the
audio capture thread and the session-poll thread.

**Root cause 2 — frozen frame when the toggle is OFF:** `setEnabled(false)`
disables the capture, so callbacks stop — but the UI kept rendering the *last
stale buffer* forever. The `active` parameter passed to `VisualizerBars` was
ignored in the render path.

**Fix:** `VisualizerBars` now gates on `live = active && waveform.isNotEmpty()`.
When the EQ is off (or no capture data exists), the idle breathing pulse
renders instead of a frozen spectrum. Applies to all three styles (Bars, Wave,
Circle) via the shared `ampFor()` amplitude extractor.

**Build #60 follow-up:** the freshness of the capture data is now also gated —
if the waveform goes stale for >1.5s while the EQ is ON (during the watchdog's
re-attach window after a MIUI kill), the idle pulse takes over immediately
instead of drawing a frozen frame for up to ~4s. The idlePulse animation
invalidates the canvas every frame, so the clock check re-evaluates continuously
even though the stale buffer no longer triggers recomposition.

### Prior release — v2.0.1 (Build #57)
- New band canvas: smooth neon bezier curve through band tops, dashed 0 dB
  reference line, active-band glow + floating value bubble while dragging.
- Zero steady-state allocations in all redraw paths: Paths, Paints, Brushes
  and buffers hoisted with `remember()`; unpositioned brushes size to the
  drawn geometry (v56 removed per-band Paint churn, v57 removed 32–41
  gradient-brush allocations per frame in the visualizer).
