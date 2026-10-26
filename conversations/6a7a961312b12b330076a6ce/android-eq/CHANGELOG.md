# Changelog

## Unreleased — the Convolver gains a real input (`fix-ui-redesign`)

The native chain has always had a real FIR convolver (`neondsp.cpp`, `MAXIR`
taps, `NeonDsp.loadIr`, harness section [7]) but nothing in the app could hand it
an impulse response, so the stage could only pass audio through and the Audio
Console listed convolution as "not available in this build". It is available now.

- **`engine/ImpulseResponse.kt`** — a pure-Kotlin WAV reader for impulse
  responses: 16/24/32-bit PCM and 32-bit float, mono or stereo, truncated to the
  engine's 2048 taps and resampled to the engine's rate, with truncation,
  resampling (linear — stated as an approximation), mono duplication and a peak
  above full scale each reported rather than performed silently. Silent files,
  unsupported sample formats, more than two channels and non-WAV files are
  refused by name. `ImpulseResponseTest` (14 tests) pins all of it.
- **`engine/IrStore.kt`** — the decoded taps are stored in the app's files
  directory and restored into the bridge on the next launch, so a response keeps
  working even if the original file is moved. A foreign, truncated or oversized
  store decodes to `null` instead of feeding the convolver (`IrStoreTest`, 7
  tests).
- **Audio Console** — a Convolver card with its own enable/bypass toggle, LOAD
  .WAV and CLEAR actions, and an output block naming the loaded response, its tap
  count and its source rate. Loading a response is the deliberate step and also
  switches the stage on through the validated chain path.
- **Clearing loads a unit impulse**, not zeros: one 1.0 tap is exactly
  pass-through, while an all-zero response would have silenced the chain.
- **`DspParams.applyTo`** now derives `setConvolverEnabled` from
  `DspChain.enableOf(CONVOLVER, …)`, the same answer the stage enable uses, so
  the chain editor and the native convolver flag cannot disagree (the limiter's
  old two-enable conflict, one stage over).
- **DSP Studio** no longer reports a capture format (`"48000 Hz · Stereo"`) when
  no capture session is running — the service's rate starts at the engine's
  default, so the header claimed a format nothing had measured.
- Device-verified: **no** — there is no emulator or phone in this environment, so
  the audible result of a loaded response has not been heard.

## Unreleased — DSP truth, the limiter policy, and the missing pipeline gate (`soniccore-ui-redesign`)

Two long-standing mismatches between what the UI states and what the DSP does are
resolved, and the pipeline now runs every gate it claims to. The native chain,
the DSP parameters, the presets and the capture path are untouched; this is a
model/UI/pipeline change plus tests.

### The shelf corners are now one source of truth (was: wrong by an octave each)

- `LOW_SHELF_HZ` / `HIGH_SHELF_HZ` were 200 Hz / 4000 Hz while `neondsp.cpp`
  builds the shelves at **100 Hz and 8000 Hz**, so every corner label and the
  drawn curve disagreed with the engine. Both constants now match the native
  build and `AudioControlStateTest.theShelfCornersMatchTheNativeEngine` pins them.
- `shelfResponseDb` no longer draws a one-pole sketch: it evaluates the **real
  RBJ shelf** the engine builds (same coefficients, same `alpha = sin(w0) / 1.4`
  slope) as a magnitude response at the engine's default 48 kHz. Half gain at the
  corner, full gain in band, unity out of band and an exact mirror for a cut are
  asserted (`theModelledCurveMatchesTheEnginesRbjShape`,
  `aCutIsTheExactMirrorOfTheSameBoost`), matching what the native harness
  measures in section [14]. It remains a modelled rendering aid, and the screens
  still say so.

### The limiter: mandatory place, one enable (was: two conflicting switches)

- The engine could switch the limiter off two ways — the chain's `stageOn[7]` and
  the older `limOn` behind `setLimiter` — and `DspParams.applyTo` forced
  `stageOn[7] = true` while `limiterOn` went to `limOn`, so the chain editor
  showed the limiter as permanently on while the Limiter card could switch the
  same stage off.
- `DspChain.enableOf(stage, limiterOn)` is now the single answer: the chain owns
  the order and the optional stages' bypass set, and the limiter's enable is the
  app-level `limiterOn`. `DspParams.applyTo` and the chain editor's row both go
  through it, so the model, the UI, the native `stageEnabled` readback and the
  diagnostics agree. `DspChainTest.theLimitersPlaceIsMandatoryAndItsEnableIsTheOneAppParameter`
  pins both halves.

### The offline loudness harness is in the pipeline it belongs to

- `docker-compose.base44.yml` compiled the DSP harness and then went straight to
  Gradle, so `LOUDNESS HARNESS PASS` never ran as a gate even though the
  documentation treated it as one. It is now step 1b (same ASan/UBSan flags, its
  log served at `build-output/loudness-test.log` and linked from the status page).

## Unreleased — production hardening (`harden-autoeq`)

The AutoEQ import is hardened, and one new, deliberately small native module is
added. Nothing in the DSP chain, the capture path or the existing presets changes
behaviour; `libneondsp.so` still exports the same 34 JNI symbols.

### AutoEQ import: three gaps closed

- **A preamp write that does not land is now detected, and the import is rolled
  back.** `AutoEqImport.apply` used to assume the native preamp write succeeded —
  and the app's own preamp path (`DspParams.applyTo` → `applyAudioControl`)
  swallowed every error, so it could not report otherwise. The preamp sink now
  returns whether the value reached the parameter, and when it did not, the
  active profile is put back to the one that was active before the import
  (`previousCurve`), with a message saying so. The native preamp parameter and
  the preset store are still two separate writes with no transaction between
  them; the order and the rollback are what keep every failure from leaving a
  curve playing without its headroom compensation.
- **An import named like a built-in preset is stored under its own name.** A
  custom preset is activated *by name* and the engine resolves a built-in first,
  so an import stored as "Flat" would have activated the built-in flat curve
  while reporting the imported one. It is now stored as "Flat (AutoEQ)" and the
  review says why.
- **Profile names are sanitised as untrusted input.** Path separators, quotes,
  control characters and a leading dot are removed (not just quotes and
  newlines), whitespace is collapsed, and the stored name is capped at 64
  characters. `Presets.MAX_NAME_CHARS` now matches that cap, so a long name no
  longer comes back truncated after an export/import round-trip.

Covered by `AutoEqProfileTest` (29) and `AutoEqImportTest` (27), including the
rollback, the collision, the hostile name and the bundle round-trip.

### Loudness measurement (offline) — libebur128 v1.2.6, vendored

`app/src/main/cpp/third_party/ebur128` holds libebur128 v1.2.6 (MIT, pinned
commit `67b33ab`, licence and provenance beside the source), compiled into its
**own** shared library `libloudness.so` via `app/src/main/cpp/loudness/loudness_jni.cpp`.
Audio → Diagnostics gains a **Loudness (offline)** section: pick a 16-bit PCM
`.wav` file and it reports integrated loudness (LUFS), loudness range (LU) and
true peak (dBTP) together with the input format, the channel count and the
measured span. It is offline by construction — nothing calls it from the audio
thread, so playback latency is untouched — and the report says so, including that
SonicCore does not measure live playback or system-wide audio because the system
route supplies PCM only while a capture session is running. Non-PCM formats,
non-mono/stereo audio, buffers shorter than one 400 ms gating block (or silence)
and any span longer than three minutes are each handled in words rather than
guessed at. Pinned by `tools/dspharness/loudness_harness.cpp` under ASan+UBSan and
by `LoudnessTest` (12) + `WavPcmTest` (9) on the JVM. **Not device-verified.**

## Unreleased — responsive refinement (`app-responsive-redesign`)

Full audit: `docs/responsive-audit.md` (RD-01 … RD-13). Layout only — DSP,
JNI, capture, presets, permission logic and the repository data are untouched.

### AutoEQ import completed (review, then save or apply)

`engine/AutoEqImport.kt` is new: the parsing half (`AutoEqProfile.kt`) is
unchanged in what it accepts, but the import is now two deliberate steps. The
**review** parses and reports only — format, filter or measurement-point count,
preamp, band curve, stored name and every clamp it applied — and changes nothing,
so cancelling cannot disturb the active sound. **"Save profile"** stores the curve
with the engine's existing `saveCustomPreset` and leaves the sound alone;
**"Apply now"** also makes it the active curve through the existing preset-apply
path and writes the profile's `Preamp:` line exactly once through the native
preamp parameter. The profile's `.txt` can be pasted or opened through the system
file picker (read capped at the parser's 128 KB). Every refusal now names its
reason (unsupported filter type and index, unreadable value, unusable frequency,
all-disabled profile, non-profile text, oversize, too few points, too many
filters/points), out-of-range gains and Q are reported rather than silently
normalised, and a failed store or activation reports itself instead of half-
applying. Covered by `AutoEqProfileTest` (28) and `AutoEqImportTest` (19) — 47 of
the suite's 311 tests; the audible result still needs a device.

### One scroll key, in the navigation model

The shell's scroll container is now keyed on `SonicCoreNavState.scrollKey`
instead of an inline `(category, tool)` pair, so `ScrollOwnershipTest` pins that
each destination opens at the top rather than the rule living only in an
expression the compiler reads.

### One scroll owner, and a crash removed

Eight tool bodies (Device Diagnostics, Capabilities, Device Tools, App Manager,
Shizuku Center, Customization Center, Settings hub, Permissions & Privacy) each
rendered a root `Column(Modifier.verticalScroll(...))` **inside** the shell's
own `verticalScroll`. Compose rejects a vertical scrollable measured with an
unbounded height, and an outer vertical scroll hands its child exactly that.
The shell now owns scrolling for the whole body, and the body's `ScrollState` is
keyed on `(category, tool)` so navigating from a long screen to a short one no
longer opens the short one part-way down.

### Responsive layout

`ui/design/ResponsiveLayout.kt` resolves the window once — Material's compact /
medium / expanded classes from `LocalConfiguration.screenWidthDp` — and exposes
gutter, section gap, content cap, two-pane, category columns and adaptive grid
columns. The shared shell uses it, so every screen inherits: gutters of 16 / 24 /
32 dp and a content column capped at 680 / 960 dp instead of a phone layout
stretched across a tablet. Home's category grid gains a third column at expanded
widths (a user's ONE/TWO preference still wins), and the Device Center, Privacy
hub and Repository Manager stat rows become `AdaptiveGrid`s that drop a column
before their cells have to truncate.

### Repository Manager

Added sorting (name / status / category) through a pure
`RepositoryInventory.orderedBy`, collapsed the seven-row status legend that
duplicated the badges on every card, gave the empty state an explanation and a
**Clear filters** action, and raised the filter chips from ~28 dp to 36 dp.

### Controls

The glass-intensity slider was pinned to 30–32 dp, which overrode the shared
slider's 48 dp touch target; the fixed heights are gone.

Verification: 259 unit tests in 33 classes (0 failures — 18 new), DSP harness
`HARNESS PASS`, `assembleDebug` successful, `lintDebug` at its 6 pre-existing
errors with nothing new in a touched file, licence audit and documentation-link
check passing. No screenshots: there is no device or emulator here, and an
Android app cannot render in the browser preview.

## Unreleased — platform completion (six categories, twelve themes, splash)

Not a tagged release: `versionName` is still **3.6.0** and `versionCode` still
comes from `GITHUB_RUN_NUMBER`, so the owner decides which of these changes ships
as which release. No signing configuration was changed. Every item below was
verified with the commands listed at the end of this entry.

### Navigation — six categories instead of a scrolling 16-chip dock

`SonicCoreDestination` keeps every existing id, label and full-screen body, and
`SonicCoreCategory` groups all 16 destinations into the six categories the
platform plan asks for: **Audio**, **Device Center**, **Shizuku Center**,
**Customization**, **Permissions & Privacy**, **Settings**. Home shows those six
and the bottom dock — the only bottom navigation — shows the same six, each
opening that category's hub; a category's tools are listed there and never in
the dock, so the flat 16-chip scrolling dock is gone without making a
destination unreachable. A tool either reuses a long-standing full-screen body
(`SonicCoreTool.destination`, e.g. the equalizer) or has its own screen in
`ui/navigation/SonicCoreToolScreens.kt`, and `SonicCoreToolRegistry` is the
single list of them. `SonicCoreNavigationTest` asserts that contract: six
categories in order, exactly one rendering path per tool, and no two tools
claiming a destination.

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
| JVM unit tests | `./gradlew :app:testDebugUnitTest --no-daemon` | **200 tests across 29 classes, 0 failures, 0 skipped** |
| Debug APK | `./gradlew :app:assembleDebug --no-daemon` | BUILD SUCCESSFUL — `build-output/NeonEQ-debug.apk`, 13.6 MB |
| Native DSP regression | `tools/dspharness/harness.cpp` under ASan+UBSan | HARNESS PASS (0 failures) |
| Dependency licences | `python3 tools/license_check.py` | PASS — 110 shipped / 113 test-only artifacts, no copyleft |
| Documentation links | `python3 tools/check_docs_links.py` | PASS — every relative link resolves (10 files) |
| Android lint | `./gradlew :app:lintDebug --no-daemon` | **6 errors, 68 warnings** — none of them in a file this change touches (see below); lint is not part of CI |

The six lint errors, none of them in a file this change touches:

| Location | Issue |
| --- | --- |
| `capture/AudioCapabilityManager.kt:63` | `MissingPermission` — `AudioRecord.Builder()` |
| `capture/CaptureEqService.kt:276` | `MissingPermission` — same capture path |
| `ui/settings/SettingsToolScreens.kt:529` | `NewApi` ×2 — `PackageInfo.signingInfo` / `SigningInfo.getApkContentsSigners` read on API 26 |
| `shizuku/ShizukuBridge.kt:161` | `RestrictedApi` — `Shizuku.getServerPatchVersion` |
| `shizuku/ShizukuDiagnostics.kt:84` | `RestrictedApi` — the same call |

The two capture-path errors sit in playback capture, which is deliberately
preserved as-is; the settings pair needs an API-level guard around a
signing-info read; the last two read an API the Shizuku client marks as
restricted to its own library group. Fixing them (an explicit permission check,
an API guard, or dropping/wrapping the patch-version read) is a separate
decision and was not part of this work.

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
