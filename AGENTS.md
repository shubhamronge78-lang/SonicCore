# AGENTS.md — running this repository

## What this repository actually contains

This is a **Base44 agent workspace**, not a web application. It holds:

- `.agents/` — the agent's platform docs, memory and identity.
- `conversations/6a7a961312b12b330076a6ce/android-eq/` — the only real project:
  **Neon EQ / SonicCore**, an Android app (Kotlin + Jetpack Compose + native C++
  DSP; `versionName 3.6.0`, minSdk 26, targetSdk 34, applicationId and namespace
  `com.soniccore.app`). The Kotlin tree is the historical package layout,
  mechanically renamed from `com.neon.eq` to `com.soniccore.app`, so `capture/`,
  `engine/`, `ui/category/`, `ui/navigation/` and the rest keep their shape and
  only the package prefix differs.
- `.github/workflows/` — the app's CI: `build.yml`, `android-apk.yml`, `release.yml`.
- `.base44/` — this sandbox setup: the builder image and the served status page.

**There is no web frontend, no HTTP server and no database anywhere in this
repo.** An Android app has no browser surface, so it can never render inside the
browser preview. Do not go looking for a web app, and do not invent one.

The app itself now has two layers:

- **Audio**: native C++ DSP (`app/src/main/cpp/neondsp.cpp`), `EqualizerEngine`,
  the capture pipeline, presets. Untouched by the platform expansion.
- **Platform centers**: `system/` (device probes), `permissions/` (permission
  model), `shizuku/` (bridge + operation registry), `diagnostics/` (device
  health, operation log, report export), `ui/customization/`,
  `ui/settings/`. The Compose navigation lives in
  `ui/navigation/` (`SonicCoreNavigation.kt` for the model, `SonicCoreNavStack.kt`
  for the back stack, `SonicCoreNavigator.kt` for the moves); screens are
  dispatched from the single large `EqualizerScreen` composable in
  `MainActivity.kt`.

Navigation is **six categories**, not a flat dock: `SonicCoreDestination` in
`ui/navigation/SonicCoreNavigation.kt` names every destination and
`SonicCoreCategory` groups them into Audio, Device Center, Shizuku Center,
Customization, Permissions & Privacy and Settings. The launcher starts in
`SonicCoreNavHost` (`MainActivity.kt`), which owns the navigation stack and
renders Home, a category hub, or one tool inside a category. Home shows
**exactly six categories** — Audio, Device Center, Shizuku Center,
Customization, Permissions & Privacy, Settings — and the bottom dock shows the
same six, never internal tools. A tool either reuses a long-standing
full-screen body (`SonicCoreTool.destination`, e.g. the equalizer) or has its own
screen in `ui/navigation/SonicCoreToolScreens.kt`; nothing may have both or
neither. `SonicCoreNavigationTest` asserts all of that (six categories in order,
one rendering path per tool, no two tools claiming a destination) — adding a
category or a tool requires updating that test.

Audio is the one category with a second level of its own: its thirteen tools each
carry a `SonicCoreAudioGroup` (Overview, Sound Controls, DSP Processing, Playback
& Capture, Devices & Routing, Presets & Diagnostics), which the **Audio Center**
hub renders as a chip row on phones and as a grouped list on tablets. The
screens that live in `ui/audio/` share one settings vocabulary
(`AudioSettingsComponents.kt`) and never touch the engine: they read an
`AudioControlState` snapshot from `SonicCoreToolHost` and hand a validated one
back, which `MainActivity` merges over the persisted `DspParams` (parametric
slots left alone) and applies through the same `DspParams.applyTo` path the DSP
rack uses. `AudioControlRanges.clampTo` is where NaN and infinity stop.
`SonicCoreNavigationTest` asserts the grouping and `AudioControlStateTest` the
clamping/merge contract, so a new Audio tool needs a group in the registry and a
route (own screen) or a destination.

## How the app is run / verified here

`docker-compose.base44.yml` builds the app **from the cloned source** (the source
is bind-mounted at `/src`, never baked into an image) and serves the results on
host port 3000, because that is the only port the preview can show.

The sandbox does **not** run the copy in this repository: the platform runs the
**environment-owned** copy at `$BASE44_CONFIG_ROOT/docker-compose.base44.yml`
(with that environment's own `Dockerfile`, status page and `SETUP.md`). The two
are meant to be the same setup and must be kept in step — a check added to one and
not the other is a check the preview does not run, which is exactly how the
edge-to-edge lint regression below got in.

Everything lives in **one long-running service, `app`**:

0. `python3 -m http.server` on port 80 starts **first**, serving `./build-output`
   (host port 3000) with the status page. Port 3000 answers within seconds,
   before any building happens — the status page probes for the APK over HTTP and
   reports "build still running" until it exists.
1. Native DSP host self-test — compiles the **real** production DSP
   (`tools/dspharness/harness.cpp` `#include`s `neondsp.cpp`) with ASan+UBSan and
   runs its assertions. Takes seconds and needs no Android SDK.
1b. Offline loudness self-test — compiles the vendored libebur128 as C, links it
   with the module's JNI facade (`tools/dspharness/loudness_harness.cpp`
   `#include`s `app/src/main/cpp/loudness/loudness_jni.cpp`) and runs it under the
   same ASan+UBSan. It pins the EBU R 128 numbers the app reports (-23 dBFS RMS
   1 kHz = -23.0 LUFS mono / -20.0 stereo, silence and a sub-400 ms span = *no*
   integrated loudness, an fs/4 sine's true peak, every refused input). A change
   to the measurement fails here instead of reaching a report.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — the JVM
   unit tests (374 tests across 40 classes: the platform expansion, the Shizuku IPC
   work, the Home/layout contracts, the responsive decisions and scroll-ownership
   rule, the DSP chain model (`DspChainTest` — the eight stages, the limiter-last
   rule, refused moves) with its saved-chain store (`DspChainPresetsTest`, which
   also pins `DspTelemetryText`'s "measured or declined" wording), the Audio
   control-parameter validation and clamping, the AutoEQ profile parsing *and*
   import flow (including the preamp-write rollback and the built-in name
   collision) and the offline loudness measurement's own decisions, the
   twelve-theme catalogue, the splash, the Audio Center grouping/rendering
   contract and the Liquid Glass token contracts), the
   debug APK, and lint — all built from the mounted source. All 374 tests pass with
   0 skipped.
   **`:app:lintDebug` is part of the pipeline on purpose.** It used to fail with 6
   errors and was therefore left out (the capture path's two `MissingPermission`
   calls, `SettingsToolScreens`' API-28 signing-info read, and the two Shizuku
   `RestrictedApi` calls). Those are now fixed in source — permission checks with
   the app's own error codes, a version-guarded signing read, and one narrow,
   documented `@SuppressLint("RestrictedApi")` helper per Shizuku call — so lint
   passes and stays in the command: dropping it again would let the same failure
   return unnoticed.
   **It did return once, which is why the pipeline that actually runs here now
   includes lint.** The edge-to-edge pass added `android:windowLightNavigationBar`
   (API 27) and `android:enforceNavigationBarContrast` (API 29) to an unqualified
   `values/styles.xml`, so `:app:lintDebug` failed again with 2 `[NewApi]` errors
   against minSdk 26 — and nothing noticed, because the environment-owned compose
   file that the sandbox actually runs invoked only `testDebugUnitTest` and
   `assembleDebug`. Both attributes now live in `values-v27/styles.xml` and
   `values-v29/styles.xml` over a shared `Theme.NeonEQ.Base`. Current result:
   `0 errors, 67 warnings`.
   **`docker-compose.base44.yml` itself now runs all four gates**: the native DSP
   harness (step 1), the offline loudness harness (step 1b), and
   `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (step 2).
   The loudness harness was the gate that lived only in this document — the
   compose command compiled `harness.cpp` and then went straight to Gradle, so
   `build-output/loudness-test.log` never appeared and a change to the measurement
   reached a report unchallenged. It is now step 1b (same ASan/UBSan flags, same
   `LOUDNESS HARNESS PASS` line) and the served status page links its log.
3. The APK and the test report are copied into `./build-output`, so the page
   switches to "APK ready to install".

```bash
docker compose -f docker-compose.base44.yml up -d --build   # first run / after compose or image changes
docker compose -f docker-compose.base44.yml ps              # wait for "healthy"
docker compose -f docker-compose.base44.yml logs -f app     # harness + Gradle output
```

- **Why one service and not three.** The DSP test and the Gradle build are
  one-shot jobs. A compose service that exits — *even with exit code 0* — is
  reported as a failed application service by the platform's health monitoring,
  so they must not be declared as their own services. The single `app` service
  runs them to completion and then stays up serving, with a healthcheck on the
  served page.
- **Why the HTTP server starts before the build.** The from-source build takes
  minutes on a cold cache. If the port stayed silent until it finished, the
  preview and the platform's start probe would see a dead port and report the app
  as failed to start.
- **A failed build must not look healthy.** Each run deletes the previous APK,
  logs and test report first, and `bash -ec` aborts on any failure — the trap then
  kills the server and the container exits non-zero, so `restart: on-failure`
  retries instead of serving a stale "ready" page. `build-output/build.log` holds
  the error.
- **Re-running the checks after editing source.** The source is bind-mounted, so
  `docker compose -f docker-compose.base44.yml restart app` re-runs the harness,
  the tests and the APK build against the current code. `up -d --build` is only
  for changes to `docker-compose.base44.yml` or `.base44/android/Dockerfile`.
  Never run two Gradle invocations at once against the shared `gradle-home`
  volume.

## How to check it works

```bash
curl -fsS http://localhost:3000/                     # status page
curl -fsSI http://localhost:3000/NeonEQ-debug.apk    # APK present
ls build-output/                                     # NeonEQ-debug.apk, build.log, dsp-test.log, loudness-test.log, tests/
tail -5 build-output/dsp-test.log                    # must end with "HARNESS PASS (0 failures)"
tail -5 build-output/loudness-test.log               # must end with "LOUDNESS HARNESS PASS (0 failures)"
tail -5 build-output/build.log                       # must end with "BUILD SUCCESSFUL"
```

Two checks the project ships that are **not** part of the compose pipeline
(each runs inside the app container, from the mounted source):

```bash
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/license_check.py'
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/check_docs_links.py'
```

`license_check.py` reads the resolved dependency graph from Gradle and each
artifact's licence from its published POM (a handful of artifacts publish no
licence metadata and print `unknown`), and exits 1 on a copyleft licence.
`check_docs_links.py` resolves every relative Markdown link in the project.

`build-output/` is generated and gitignored.

## Sandbox-only overrides

There are **none in the application code**. The Android project, its
`gradle.properties` and `app/build.gradle.kts` are byte-for-byte the repository's
own and behave identically outside the sandbox. Specifically:

- `BASE44_PREVIEW_MODE` is **not** read by anything here and is not passed into
  the stack: the preview is a plain static page served by `python3 -m
  http.server`, so there is no dev server, no host/origin allowlist and no HMR to
  gate. Nothing changes when the flag is set to `1` or unset.
- The only sandbox-specific files are the ones Base44 owns:
  `docker-compose.base44.yml`, `.base44/android/Dockerfile`,
  `.base44/preview/index.html`, `.base44/environment.json` and this file.

## The Liquid Glass design system

`docs/liquid-glass-defects.md` is the audit: every visual defect found, its root
cause and its fix. The code is three files plus the legacy chrome:

- `ui/design/GlassTokens.kt` — palette, radii, spacing, elevation, opacity and
  the blur policy. **This is the only place appearance constants may live.**
- `ui/design/GlassComponents.kt` — `GlassScaffold`, `GlassTopBar`,
  `GlassSurface`, `GlassCard`, `GlassInset`, `GlassNavigation`, `GlassSwitch`,
  `GlassTextField`, `GlassDialog`, plus the shared button/icon-button/slider
  vocabulary in `ui/design/SonicCoreComponents.kt`.
- `LiquidGlass.kt` (package `com.soniccore.app`) — the older chrome helpers
  (`LiquidChip`, `LiquidButton`, `LiquidOrb`). They read the same tokens, so
  they no longer look foreign, but new code should use the `ui/design` set.

Rules the rebuild established, and the tests that hold them:

- **The legibility floor is absolute.** `GlassOpacity.MIN_BACKING` (0.72) caps
  every surface alpha — glass panels, the legacy `glassBackingAlpha()`, and
  every `AlertDialog` container. The glass level changes how crisp the glass
  looks (edge, highlight, shadow), never how readable its text is. Raising the
  level must never lower a surface below the floor: `GlassTokensTest`.
- **One blur pass, on the backdrop photograph only.** `GlassBlur.radiusDp`
  returns 0 unless the device is Android 12+ *and* glass is above Subtle, and
  the only call site is the static backdrop in `GlassBackground`, so the result
  is cached by the renderer. Never blur a card — it would blur its own content,
  and stacking blur layers is the thing this policy exists to prevent.
- **The palette is applied during composition, not in a `SideEffect`.** A
  `SideEffect` runs after composition and layout, so the first frame after a
  theme change painted the previous palette. `SonicCoreTheme` applies it inside
  `remember(palette)` before `content()` composes — switching themes needs no
  restart. Do not move it back into a `SideEffect`.
- **`GlassScaffold` owns the insets, and the app now *is* edge-to-edge.**
  `MainActivity.onCreate` calls `enableEdgeToEdge()` before `setContent`, and
  `Theme.NeonEQ` (`res/values/styles.xml`) draws **transparent** status and
  navigation bars with `windowDrawsSystemBarBackgrounds=true` and
  `enforceNavigationBarContrast=false`, so the glass backdrop runs under both
  bars and under a display cutout instead of stopping at a black band no theme
  could match. `GlassScaffold` then applies
  `statusBarsPadding() + displayCutoutPadding() + imePadding()` **once** for the
  whole app — the cutout inset is the part the old code lacked (in landscape a
  cutout sits on a *side*). `GlassNavigation` keeps its own
  `navigationBarsPadding()` because the dock is the component that sits against
  that edge, so the scaffold deliberately does **not** add it too. The activity
  keeps `android:windowSoftInputMode="adjustResize"`, which is what feeds
  `imePadding()` a real keyboard inset. `GlassBottomSheet` is the only other
  inset call site, and it is legitimate: it renders in its own `Dialog` window,
  outside the scaffold. A *screen* that adds its own system-bar padding will
  double-pad — that is RD-15 in `docs/responsive-audit.md`.
- **All twelve themes share the spec's ink.** `Themes.dark()` sets
  `#F3F7FF` / `#9EACC2` for every dark theme and `Themes.OBSIDIAN` ("classic")
  is the specification's palette verbatim (`#080D18` / `#0D1422` / `#121C2C` /
  `#182438`, accents `#48DDF5` / `#A78BFA`). `GlassTokensTest` asserts both.
- **Radii and spacing come from `GlassRadius` / `GlassSpacing`.** All 30 literal
  corner radii across 21 files were normalised to a token; a new literal is how
  the eight-radius drift started.
- **The native blob is the only thing that can render this app.** There is no
  web surface, so no UI change here can be verified in the browser preview —
  only the tests, the harness, `assembleDebug` and `lintDebug`. Say so in the
  report rather than claiming a visual check.

## Responsive layout

`docs/responsive-audit.md` is the audit (RD-01 … RD-13). The code is one new
file plus the spacing tokens:

- `ui/design/ResponsiveLayout.kt` — `WindowSizeClass` (compact < 600, medium
  < 840, expanded ≥ 840 dp), `ResponsiveMetrics`, `LocalResponsiveMetrics`,
  `rememberResponsiveMetrics()`, `AdaptiveGrid`, and the pure decisions
  `windowSizeClassOf`, `adaptiveColumnCount`, `categoryColumnCount`. No new
  dependency: the window comes from
  `LocalConfiguration.screenWidthDp`, which invalidates on rotation,
  split-screen resize and a foldable changing posture.
- `ui/design/GlassTokens.kt` — the responsive half of `GlassSpacing`
  (per-class gutters, section gaps, content caps, minimum tile widths).

Rules:

- **The shell owns vertical scrolling.** `mainContent` in `MainActivity` is one
  `Column(Modifier.verticalScroll(...))` and its `ScrollState` is keyed on
  `SonicCoreNavState.scrollKey` — the frame's category + tool — so every
  destination opens at the top. That key lives in the navigation model rather
  than in an expression only the compiler reads, and `ScrollOwnershipTest` pins
  it: three distinct destinations have three distinct keys, one destination
  reached twice keeps the same key, and no two tools share one. A tool body must
  **never** add its own `verticalScroll`: an outer vertical scroll hands its
  child an infinite maximum height, and Compose rejects a scrollable measured
  with one (`checkScrollableContainerConstraints`). Eight bodies did exactly
  that (RD-01) — that is why they no longer scroll themselves.
- **One scroll owner, and exactly one exception, declared in the navigation
  model.** A screen whose body *is* a lazy list cannot live inside that
  container at all, so `SonicCoreTool.scrollOwner` defaults to
  `ScrollOwner.SHELL` and only `custom.repositories` declares
  `ScrollOwner.SCREEN`. `SonicCoreNavState.screenOwnsScroll` carries that to the
  shell, which applies `verticalScroll` **only** when the frame does not own its
  own and otherwise hands the screen the remaining height (bounded, so the
  `LazyColumn` in `RepositoryManagerScreen` measures legally). Adding a second
  screen-owned scroll means declaring it on its tool — and
  `ScrollOwnershipTest` fails until that is deliberate.
- **Never compare a breakpoint at a call site.** Use
  `ResponsiveBreakpoints` / `ResponsiveMetrics` (`twoPane`, `contentMaxWidth`,
  `screenPadding`, `sectionGap`, `categoryColumns`, `columnsFor`).
  `homeUsesTwoColumns` reads the shared constant; the Audio hub reads `twoPane`.
- **Do not reflow a fixed `Row { x.weight(1f) … }` with content that can
  truncate.** Use `AdaptiveGrid`, which drops a column before the cells have to
  squeeze; it measures its own width, so a grid inside a card still behaves.
- **A fixed `.height()` on a control defeats its minimum touch target.**
  `GlassSlider`/`GlassSwitch` enforce `GlassSpacing.touchTarget` through
  `defaultMinSize`, which fixed constraints override (RD-13).
- Tests: `ResponsiveLayoutTest` (classes, caps, gutters, two-pane boundary,
  grid columns), `HomeResponsiveLayoutTest`, and
  `RepositoryInventoryTest` for the Repository Manager's sort orders.

## The Audio Console, the eight DSP stages and the chain editor

`ui/audio/AudioConsoleScreen.kt` is the Audio tool `audio.console` (first entry in
the Sound Controls group). It is one continuous surface: the chain editor, then
one inline card per effect — preamp, shelves, graphic EQ, parametric EQ, stereo,
compressor, limiter — each with its own enable/bypass toggle, its controls, and an
`OUTPUT` block immediately underneath. Two rules hold there and are the reason the
file exists in this shape:

- **A toggle is the real control.** A card's switch moves either the native
  chain's own stage enable (`NeonDsp.setStageEnabled` — the same flag the chain
  editor shows) or the parameter switch the engine already has (`compOn`,
  `limiterOn`). Nothing flips a boolean only the UI sees.
- **Output is measured or it is declined.** `DspTelemetry` carries the bridge's
  own counters (processed frames, compressor/limiter gain reduction, output peak,
  clip state); `DspTelemetryText` decides whether a card shows that measurement
  or one of "waiting for audio" / "not measurable: <reason>". Those are pure text
  decisions, pinned by `DspChainPresetsTest`, so a card cannot draw a number it
  does not have. The counters belong to the **capture** path that runs the native
  chain — on the in-app player path (SonicCore's own software chain) gain
  reduction is genuinely not measurable and the cards say exactly that instead of
  borrowing the other path's numbers.

### The eight stages, and which of them are optional

`dsp/DspChain.kt` (`ChainStage`) is the single declaration of what the engine
processes, using the ids `neondsp.cpp` uses:

| id | stage | bypassable |
| --- | --- | --- |
| 0 | Preamp | yes |
| 1 | Parametric EQ — 8 peaking slots | yes |
| 2 | Graphic EQ — 10 / 15 / 31 bands | yes |
| 3 | Bass & Treble — one low, one high shelf | yes |
| 4 | Stereo — width, balance, swap, mono | yes |
| 5 | Convolver | yes, but **no IR can be loaded in this build**, so it passes audio through unchanged and its row says so |
| 6 | Compressor | yes |
| 7 | Limiter | **no — pinned last** |

Anything else is not a stage and the console renders **no control** for it: the
"Not available on this path" section names IR convolution, reverb/virtualizer,
haptic bass and system-wide processing with their reasons, because a toggle that
changed nothing would be worse than saying so.

### The order is real, and invalid orders are refused before they reach audio

- `neondsp.cpp` walks `stageOrder[8]`, not a hard-coded sequence. `stageOrder[]`
  and `stageOn[]` travel in the same seqlock-published snapshot as every other
  parameter and are committed at a **block boundary** by `applyPendingParams()`,
  so the audio thread never observes a half-written chain and a reorder or a
  bypass always lands between blocks.
- `DspChainRules.isValidOrder` requires a full permutation of the eight ids with
  the limiter last; `DspChainRules.move` refuses a move that would break that
  **before** anything is applied and returns the sentence the screen shows. A
  refused move leaves the chain exactly as it was — there is no partial apply.
- `NeonDsp.setStageOrder` re-validates at the audio boundary and returns `false`
  **without touching the committed order** for anything that is not a
  permutation, so the UI is not the last line of defence. `DspChain.of` applies
  the same rule to *stored* ids, falling back to the engine's default order
  rather than forwarding a damaged chain.
- `tools/dspharness/harness.cpp` section **[18]** measures all of it: a +20 dB
  signal is held at the −1 dBFS ceiling with the limiter last and reaches 32 767
  with the limiter first; a bypassed preamp passes at unity while keeping its
  +20 dB setting; a refused non-permutation leaves the committed chain untouched;
  the mandatory stage stays enabled. A reorder is therefore never a label only a
  UI believes.

### The limiter: mandatory *place*, one *enable* (the old conflict is resolved)

`ChainStage.LIMITER` has `bypassable = false`: its row renders no toggle, it cannot
be dragged or moved past the end, and no stored chain can place a stage after it,
which is what makes "nothing runs after the output ceiling" true.

Its **place** was never the problem; its **enable** was. The engine has two ways to
switch the limiter off — the chain's own stage enable (`stageOn[7]`, skippable in
`processFrame`) and the older `limOn` parameter behind `setLimiter` — and the app
used to write them from two different places: `DspParams.applyTo` forced
`stageOn[7] = true` while `limiterOn` went to `limOn`. The chain editor therefore
showed the limiter as permanently on while the Limiter card and Settings could
switch the same stage off, and `NeonDsp.stageEnabled(7)` answered something the
console did not show.

The policy is now one value, routed through one function:

- `DspChain.enableOf(stage, limiterOn)` is the single answer to "is this stage
  running?". Order and the optional stages' bypass set stay the chain's;
  **the limiter's enable is the app-level `limiterOn`** (persisted in `DspParams`,
  surfaced on the console's Limiter card and in Settings).
- `DspParams.applyTo` derives every `setStageEnabled` from it, and so does the
  chain editor's row, so the model, the UI, the native stage enable and the
  diagnostics cannot disagree.
- `DspChainTest.theLimitersPlaceIsMandatoryAndItsEnableIsTheOneAppParameter` pins
  both halves: the chain can never bypass or move the limiter, and its enable is
  exactly `limiterOn` in both directions.
- Either way, the native output clamp and the NaN/Inf guard bound whatever leaves
  the chain, and the chain's `stageBypassed` set can never contain the limiter.

### Saved chains: what is stored, and the persistence limitations

`dsp/DspChainPresets.kt` stores a chain as one short line per preset
(`order=…;off=…`) in the app's own `dsp_chain_presets` preferences. The
limitations are deliberate and worth knowing before changing anything:

- **Order and bypass flags only.** An effect's *parameters* (preamp gain, shelf
  gains, thresholds, band values) are not part of a chain preset; they live in
  the persisted `DspParams` JSON. `MainActivity.applyDspChain` reloads the
  persisted parameters and re-applies them unchanged, so loading a chain reorders
  and re-enables stages without moving a single value.
- **Maximum eight presets**, and a ninth save drops the oldest. A save under an
  existing name replaces it in place.
- **A chain preset cannot corrupt another store.** Custom profiles and AutoEQ
  imports go through `Presets` / `saveCustomPreset`, a different store, so a
  chain save can never overwrite them.
- **Stored data is untrusted.** Names are sanitised and capped
  (`DspChainPresets.sanitizeName`), and a damaged stored line decodes to `null`
  rather than to a chain the engine must not run (`DspChainPresetsTest`).

### The shelf curve and its corners: one source of truth (the old mismatch is fixed)

The shelf response drawn on Bass & Treble and in the console is a **modelled
rendering aid**, derived from the two gains the DSP was given, and the screens say
so. It used to be a one-pole sketch with corner constants (`LOW_SHELF_HZ` /
`HIGH_SHELF_HZ` = 200 Hz / 4000 Hz) that disagreed with the frequencies the native
shelves are actually built at — 100 Hz and 8000 Hz in `neondsp.cpp` — so every
corner the UI stated was wrong by an octave and the curve was a lookalike rather
than the engine's response.

It is now the engine's own response, from one documented set of constants:

- `LOW_SHELF_HZ` = 100 Hz and `HIGH_SHELF_HZ` = 8000 Hz in
  `dsp/AudioControlState.kt` are the UI's copy of `neondsp.cpp`'s `setShelf(…,
  100.0, …)` / `setShelf(…, 8000.0, …)`, and `AudioControlStateTest`
  (`theShelfCornersMatchTheNativeEngine`) pins them. Change the engine's corners
  and that test fails until the label moves with it.
- `shelfResponseDb` evaluates the **real RBJ shelf** the engine builds, using the
  same coefficients and the same `alpha = sin(w0) / 1.4` slope, at
  `SHELF_MODEL_SAMPLE_RATE` (48 kHz, the engine's default — the UI cannot read the
  capture rate). Half gain at the corner, full gain in band, unity out of band and
  an exact mirror for a cut are asserted in `theModelledCurveMatchesTheEnginesRbjShape`
  and `aCutIsTheExactMirrorOfTheSameBoost`, matching what
  `tools/dspharness/harness.cpp` section [14] measures on the native side.
- The screens still say "modelled": it is a rendering aid derived from the two
  gains, never a measurement of processed audio.

## Non-obvious quirks worth knowing

- **Shizuku uses the official client, but only for read-only work.**
  `dev.rikka.shizuku:api` + `:provider` 13.1.5 (`ShizukuProvider` declared in the
  manifest as `${applicationId}.shizuku`) give `ShizukuBridge` a real binder:
  install / running / authorized state, binder received + dead listeners,
  `Shizuku.requestPermission`, and `Shizuku.getUid/getVersion/getSELinuxContext`.
  The single executed elevated operation is `ShizukuDiagnostics` (read-only IPC
  probe, 2 s bound through `ShizukuInvoker`). Everything else resolves to
  `AVAILABLE` / `BLOCKED` / `UNSUPPORTED` / `NOT_IMPLEMENTED` — never a stub
  reported as success. Do not "fix" a `NOT_IMPLEMENTED` entry by making it look
  available; `docs/shizuku-operations.md` records what each one would need.
- **`newProcess` is private in the v13 client.** Spawning a shell, and the
  deprecated `SystemServiceHelper` transaction-code helpers, are not usable from
  an app process. The documented remaining path is a Shizuku user service (AIDL +
  privileged process); it is deliberately out of scope. Nothing in SonicCore
  writes secure settings, grants permissions or reroutes audio through Shizuku.
- **`ShizukuState.DISCONNECTED` is distinct from `NOT_RUNNING`.** The bridge
  remembers that a binder was seen in this process (`hasSeenBinder`), so a service
  that dies after connecting is reported as "connection lost" instead of
  "never started". Adding a `ShizukuState` value breaks every exhaustive `when`
  over it — `CapabilityRegistry`, `DeviceToolsScreen` and `ShizukuCenterScreen`
  each handle it.
- **The theme catalogue is twelve themes and `Themes.ALL` is a getter, not a
  constant.** Eight legacy ids (`classic`, `aurora`, `synthwave`, `glacier`,
  `emerald`, `ember`, `midnight`, `adaptive`) are persisted user preferences and
  must never be renamed; `Themes.catalog(customAccent, wallpaperAccent)` adds
  System Default, Light Studio, Custom Accent and Dynamic Wallpaper. The last two
  are built from live inputs — the saved accent swatch
  (`ui/design/ThemeAccentPreference.kt`) and `WallpaperAccentProbe`, which reads
  `WallpaperManager.getWallpaperColors` behind a try/catch and reports "not
  readable" on an ordinary install. `NeonTheme.preferredMode` is what makes
  System Default follow the system and Light Studio apply the light appearance;
  legacy themes leave the mode alone.
- **The splash is an overlay, not a second activity.** `SonicCoreSplashOverlay`
  renders itself over the console in `MainActivity`, hides itself after ~1.4 s,
  swallows taps while visible, and disappears immediately when
  `Settings.Global.ANIMATOR_DURATION_SCALE` is 0. It draws no resource, so there
  is nothing to keep in sync with the launcher icon.
- **Platform logic is split pure/Android so it can be unit-tested.** Anything
  needing a device lives behind a `probe(context)` function; the decision logic
  is a pure `evaluate(plainData)` that JVM tests call directly
  (`PermissionCenter.evaluate`, `ShizukuOperations.evaluate`,
  `DeviceCapabilityProbe` formatters). There is no Robolectric in this project —
  do not add Android framework calls to the `evaluate` paths or the tests break
  with "not mocked" errors.
- `ACCESS_NETWORK_STATE` was added for the device dashboard's network card. It
  is a normal permission (granted at install), so it never shows a runtime
  prompt.
- **The repository inventory is data, and a test guards its compliance rule.**
  `repository/RepositoryInventory.kt` holds every audited third-party project
  (licence, privileges, maintenance, coverage) with no Android imports, and
  `RepositoryInventoryTest` asserts that nothing copyleft, proprietary or
  unverified is ever reported as `Integrated` or queued as `Ready to Integrate`.
  The in-app **Customization → Repository Manager** screen renders that same list
  and is wired like any other Customization tool: a `custom.repositories` entry in
  `SonicCoreToolRegistry` plus a route in `SonicCoreToolRoutes.ids` and the router
  `when`, which is what `SonicCoreNavigationTest.everyToolHasExactlyOneRenderingPath`
  requires. `docs/repository-inventory.md` mirrors the data — upstream facts there
  were read from the GitHub REST API on 2026-10-25, so re-verify them (and re-run
  `tools/license_check.py`) before repeating a licence claim.
- **The one real repository integration is AutoEQ's profile *format*, not its
  data.** Two pure-Kotlin files, both with JVM tests: `engine/AutoEqProfile.kt`
  parses a pasted or picked AutoEQ `ParametricEQ.txt` / `GraphicEQ.txt` and
  evaluates it at the same 31 band centres the engine's curve uses, and
  `engine/AutoEqImport.kt` decides what an import does with the result.
  `AutoEqImport.review` is deliberately side-effect free — it returns either a
  refusal carrying its reason or a `Plan` the Audio → Presets screen shows — and
  `AutoEqImport.apply` is the only mutating step, wired in `MainActivity` to the
  existing `saveCustomPreset` and to `applyAudioControl(...)` for the preamp, so
  the profile's `Preamp:` line is written **once** through the native preamp
  parameter and no band is applied twice. Import is two steps on purpose (review →
  "Save profile" or "Apply now"), which is what makes cancellation and every
  refusal leave the active sound untouched. It is an approximation at SonicCore's
  band centres and says so in the import status line; no AutoEQ data, curve or
  source is bundled (see `docs/repository-integration.md` §1.1). Do not "improve"
  it by bundling a measured-profile database: that would redistribute upstream
  data whose own licence is not AutoEq's MIT. A clamp the import performs must
  always be reported (`Plan.notes`), never silent — `AutoEqImportTest` fails if
  that stops being true.
- **Three AutoEQ import traps that the tests exist to keep closed.**
  (a) A stored custom preset is activated *by name* and the engine resolves a
  built-in first, so an import whose name is a built-in's is stored as
  `<name> (AutoEQ)` (`AutoEqImport.availableName`) — without that, "apply" would
  activate the built-in curve while reporting the imported one.
  (b) The native preamp parameter and the preset store are two separate writes
  with **no** transaction between them; `setPreamp` therefore returns whether the
  write actually landed (the app's `DspParams.applyTo` / `applyAudioControl`
  report it instead of swallowing it), and a write that did not land makes
  `AutoEqImport.apply` put the previous profile back rather than leave a curve
  playing without its headroom compensation.
  (c) `AutoEqProfile.sanitizeName` treats the name as untrusted input: path
  separators, quotes, control characters and a leading dot are removed and the
  result is capped at `AutoEqProfile.MAX_NAME_CHARS`, which `Presets.MAX_NAME_CHARS`
  now matches so an export/import round-trip cannot rename a stored profile.
- **The loudness measurement is offline and lives in its own library.**
  `app/src/main/cpp/loudness/loudness_jni.cpp` + the vendored
  `app/src/main/cpp/third_party/ebur128` (libebur128 v1.2.6, MIT, provenance and
  checksum in that directory's README — the only copied source tree in the
  project) build `libloudness.so`, which nothing in the playback path loads or
  calls. Do not move it into `neondsp.cpp`: keeping it separate is what makes
  "it cannot add latency to playback" a structural fact rather than a promise.
  `Loudness`/`WavPcm` are pure Kotlin decisions (JVM-tested); `LoudnessNative`
  loads the library and is `available == false` on a host JVM, which is why
  `LoudnessTest` asserts the honest refusal there. Silence, spans under one 400 ms
  gating block and buffers longer than three minutes are each reported in words —
  never as `-inf LUFS`, a zero, or a silently measured part. The numbers are
  **not device-verified**: no device or emulator exists here.
- `gradle.properties` carries the original author's
  `android.cmake.path = /opt/soniccore-tools/cmake-3.22.6`. That directory does
  not exist here — and it does not need to: `android.cmake.path` is not an AGP
  property (AGP's is `cmake.dir` in `local.properties`), so it is ignored and AGP
  uses the SDK-managed CMake installed in the image / on the runner.
  `android-apk.yml` deletes the line before building; that `sed` is harmless and
  not required.
- `app/build.gradle.kts` pins the debug signing keystore (`app/debug.keystore.p12`,
  password `android`) so every CI build is signed identically and installs
  update cleanly instead of Android refusing the update.
- `versionCode` comes from `GITHUB_RUN_NUMBER` when set (1 locally/elsewhere).
- The three `*.jpg` files under `app/src/main/res/drawable-nodpi/` are real
  binaries in git: `.gitattributes` routes most binary types through LFS but
  `.base44`-unrelated rule `.../android-eq/app/src/main/res/** -filter` exempts
  the resource tree, so AAPT can compile them.
- The CI workflow `android-apk.yml` used to fail deterministically on `main`
  after ~21 s, at the SDK step, before Gradle ever ran. The step passed
  `api-level`, `build-tools` and `ndk` to `android-actions/setup-android@v3`,
  but v3 declares only `cmdline-tools-version`, `accept-android-sdk-licenses`,
  `log-accepted-android-sdk-licenses` and `packages`; unknown inputs are
  ignored, so the action fell back to its default
  `packages: "tools platform-tools"` and ran `sdkmanager tools`. The `tools`
  package no longer exists in the runner's cmdline-tools 16 and the action
  treats that as fatal (`Warning: Failed to find package 'tools'` →
  `Error: The process '.../sdkmanager' failed with exit code 1`), which is what
  broke runs 40070746189 / 40071309858 / 40072560294. The action step now asks
  only for `platform-tools` and accepts the licences non-interactively, and a
  second step installs `platforms;android-34`, `build-tools;34.0.0`,
  `ndk;25.1.8937393` and `cmake;3.22.1` with `sdkmanager --install` itself, so
  the install command, its output and its exit code are visible in the log.
  `build.yml` never used that action and always passed.
- A workflow fix is only live on the branch that triggers it. The fix lived on
  `fix-main-482fc2707b92` while `main` still had the broken YAML, and GitHub
  takes the workflow file from the ref that runs — so re-dispatching the
  workflow on `main` re-ran the old file and failed exactly the same way
  (run 40072560294). Reach `main` (merge) before expecting a green run.
- `android-apk.yml` only triggers on pushes to `main` and on manual dispatch,
  so pushing a feature branch produces **no** run. Validate a workflow change
  here instead: parse it (`python3` + pyyaml), then run the same Gradle goals
  the workflow runs (see "How to check it works"). Live run logs can be read
  from this sandbox (`gh run list`, `gh run view <id> --log-failed`), which is
  how the root cause above was confirmed.
- The native DSP build needs NDK 25.1.8937393 and CMake 3.22.1 (AGP 8.2.2
  defaults); both come from the SDK, not from the host.
- **JNI symbol names are hard-wired to the Kotlin package.** `neondsp.cpp`
  exports `Java_com_soniccore_app_dsp_NeonDsp_<fn>` and
  `tools/dspharness/harness.cpp` calls those same C symbols directly, so
  `dsp/NeonDsp.kt` must stay in `com.soniccore.app.dsp`. Renaming the package
  requires renaming the exported symbols in **both** files together and
  rebuilding — a mismatch compiles fine and only fails at runtime, or breaks the
  DSP harness at link time. Verify after any rename:
  `llvm-nm -D --defined-only libneondsp.so | grep Java_com_soniccore_app_dsp_`
  (34 symbols, and no `Java_com_neon_eq_`).
- **The `sync/mobile-harness` lineage was never buildable — do not re-merge it
  as-is.** Commits `28aa75a`/`bd95978` (PR #16) restructured the app by deleting
  the `capabilities/`, `diagnostics/`, `shizuku/`, `ui/category/`,
  `ui/navigation/`, `ui/design/` theme and component modules and splicing
  `MainActivity.kt` together from two source versions, leaving ~1200 Kotlin
  compile errors — `bd95978` itself never compiled (`:app:compileDebugKotlin`
  failed). Everything the restructure aimed at (six categories, Shizuku Center,
  Customization, Audio/DSP, JNI) already exists in the renamed tree. Its only
  genuinely reusable output was the namespace change to `com.soniccore.app` and
  the matching JNI renames, both already applied here.
