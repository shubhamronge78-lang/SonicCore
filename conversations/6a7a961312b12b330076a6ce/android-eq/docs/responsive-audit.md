# Responsive refinement — screen audit and rebuild

Audit date: 2026-10-26 · branch `app-responsive-redesign`

## 1. What this document is

The audit behind the responsive pass over SonicCore: every layout defect found,
its root cause, the fix, and what could genuinely be verified. It follows the
same shape as `liquid-glass-defects.md`, and for the same reason — the fixes are
in the shared layer first and in the screens second, so a new screen inherits
correct behaviour instead of re-deriving it.

The scope is layout only. Nothing here changes DSP, the JNI bridge, audio
capture, presets, the repository catalogue's data, or the permission model.

## 2. How the app was audited — and the limit of that audit

An Android app has **no browser surface**, so none of this can be checked in the
preview iframe, and this sandbox has no emulator or device. Every finding below
is therefore a **code-level** finding: it names the exact call site and the
mechanism that makes it wrong. Where a finding depends on how Compose measures,
the reasoning is stated so it can be challenged.

What *was* executed is listed in section 6 — the JVM unit tests, the native DSP
harness, `assembleDebug`, `lintDebug`, the licence audit and the documentation
link check. Nothing in this document claims a visual check that did not happen.

## 3. The defect checklist

| # | Severity | Defect | Root cause | Fix |
| --- | --- | --- | --- | --- |
| RD-01 | **Critical** | Eight tool bodies crash when opened: Device Diagnostics, Capabilities, Device Tools, App Manager, Shizuku Center, Customization Center, Settings hub and Permissions & Privacy each rendered a root `Column(Modifier.verticalScroll(…))` **inside** the launcher shell's own `Column(Modifier.verticalScroll(…))` | The restructure that introduced the single shell scroll left the per-screen scroll modifiers in place. Compose rejects a vertical scrollable measured with an unbounded height (`checkScrollableContainerConstraints`), and an outer `verticalScroll` hands its child an infinite maximum height | The shell is the single scroll owner. All eight bodies keep their `Column` but no longer scroll themselves — see `DeviceDiagnosticsScreen.kt` for the note that names the rule |
| RD-02 | High | Navigating from a long screen to a short one opened the short one part-way down, with its first cards above the fold | One `rememberScrollState()` was shared by every destination, so the offset survived navigation | The body's `ScrollState` is keyed on `SonicCoreNavState.scrollKey` (the frame's category + tool), so each destination starts at the top — pinned by `ScrollOwnershipTest` |
| RD-03 | High | On a tablet, an unfolded foldable or a landscape phone every card stretched edge to edge: text lines ran over 1000 dp, cards became wide empty bands, and the 16 dp phone gutter was unchanged | The shell had one fixed `padding(horizontal = 16.dp)` and no content-width cap; nothing in the app read the window size | The shell reads `ResponsiveMetrics`: gutters scale 16 / 24 / 32 dp and the content column is capped at 680 dp (medium) / 960 dp (expanded) and centred |
| RD-04 | Medium | Home's six category cards never showed more than two columns, even on a tablet | `CategoryGrid` computed `AUTO` as `if (maxWidth >= 380.dp) 2 else 1` — a third column was impossible | `categoryColumnCount` gives 1 / 2 / 3 by width; the user's ONE/TWO preference still overrides it |
| RD-05 | Medium | Device Center's five hardware tiles were cramped on a phone: two fixed weighted rows (3 + 2) gave each tile ≈110 dp, so "Not readable" and the battery footer were truncated | `Row { tile.weight(1f) … }` repeats the available width across a fixed number of cells regardless of how narrow that is | `AdaptiveGrid` over 5 tiles with a 168 dp minimum cell: two columns on a phone, three when there is room, last row padded |
| RD-06 | Low | Permissions hub's three tiles had the same fixed-weight problem | Same pattern | Same `AdaptiveGrid`, 112 dp minimum cell — three fit on a phone |
| RD-07 | Medium | Repository Manager's four summary cells carried 8 sp labels in ~90 dp cells on a phone | Fixed weighted `Row` of four | `AdaptiveGrid`: 2 × 2 on a phone, one row of four when there is room |
| RD-08 | Medium | Repository Manager had filtering but **no sorting**, so finding an entry meant scrolling the whole catalogue | Only `search(query, category, status)` existed | `RepositorySort` (Name / Status / Category) with a pure `RepositoryInventory.orderedBy`, pinned by four new tests. Status order is the inventory's compliance order, so actionable entries come first |
| RD-09 | Medium | The status legend (seven `StatusPill` rows) sat fully expanded above every result, explaining labels each card already shows | It was written as a permanent card | Collapsed disclosure, opened on demand; the catalogue gets the screen |
| RD-10 | Low | The empty state was one sentence with no way out | — | Explains what the search covers and offers a **Clear filters** action |
| RD-11 | Low | Filter chips were ≈28 dp tall | `padding(vertical = 6.dp)` and no minimum size | 36 dp minimum with centred content. **Still below the 48 dp guideline** — see section 7 |
| RD-12 | Medium | The two-pane breakpoint existed three times: `screenWidthDp >= 840` in the Audio hub, `maxWidth >= 840.dp` in the Home dashboard, and no shared definition anywhere | Each screen compared its own literal | `ResponsiveBreakpoints.TWO_PANE_MIN_WIDTH_DP`; the Audio hub reads `LocalResponsiveMetrics.current.twoPane` and `homeUsesTwoColumns` delegates to the same constant |
| RD-13 | Medium | The glass-intensity slider was pinned to 30–32 dp, defeating the shared slider's own 48 dp touch target | `Modifier.height(30.dp)` passes *fixed* constraints, so `GlassSlider`'s `defaultMinSize(minHeight = touchTarget)` had no effect | The fixed heights are gone; both call sites get the full touch target |
| RD-14 | Medium | Repository Manager built its whole catalogue with `forEach` inside the app-wide scroll, so every card of a large inventory was composed at once and the screen could not be made lazy | A lazy list cannot be measured inside a `verticalScroll` parent, and the shell owned the only scroll — scalability had been traded away to keep the shell scrolling | `ScrollOwner` (`SHELL` / `SCREEN`) on `SonicCoreTool`, surfaced as `SonicCoreNavState.screenOwnsScroll`: the shell applies `verticalScroll` **only** when the frame does not own its own, and hands a screen-owned frame the remaining height. `custom.repositories` declares `SCREEN`; its catalogue is a `LazyColumn` |

## 4. What changed, and why it is a shared change

| File | Role |
| --- | --- |
| `ui/design/ResponsiveLayout.kt` (new) | `WindowSizeClass`, `ResponsiveMetrics`, `LocalResponsiveMetrics`, `rememberResponsiveMetrics()`, `AdaptiveGrid`, and the pure decisions `windowSizeClassOf`, `adaptiveColumnCount`, `categoryColumnCount` |
| `ui/design/GlassTokens.kt` | The responsive half of `GlassSpacing`: per-class gutters, section gaps, content caps, minimum tile widths. Appearance constants stay in the one token file |
| `MainActivity.kt` | The shell: one scroll container, per-destination scroll state, responsive gutter and content cap |
| `ui/home/SonicCoreHomeScreen.kt` | Category grid columns follow the window; the user preference still wins |
| `ui/category/CategoryHubs.kt` | Device and Privacy tile grids; Audio hub reads the shared two-pane decision; glass slider keeps its touch target |
| `ui/customization/RepositoryManagerScreen.kt` | Adaptive summary grid, sort control, collapsed legend, actionable empty state, taller filter chips, and a lazy catalogue (RD-14) |
| `ui/navigation/SonicCoreNavigation.kt`, `SonicCoreNavStack.kt` | `ScrollOwner` on the tool model and `screenOwnsScroll` on the navigation frame — the per-destination scroll-ownership rule, pinned by `ScrollOwnershipTest` |
| `repository/RepositoryInventory.kt` | `RepositorySort` and the pure `orderedBy` (data module, no Android imports) |
| Eight tool bodies | Root `verticalScroll` removed (RD-01): Device Diagnostics, Capabilities, Device Tools, App Manager, Shizuku Center, Customization Center, Settings hub, Permissions & Privacy |

### The three decisions worth knowing

1. **Window size classes come from `LocalConfiguration.screenWidthDp`, not from
   a new dependency.** `compose-material3-window-size-class` would add a
   dependency for one comparison chain, and its class is not available to a JVM
   unit test. The breakpoints are Material's own: compact < 600, medium < 840,
   expanded ≥ 840 dp. Because `LocalConfiguration` invalidates the composition on
   every configuration change, rotation, split-screen resize and a foldable
   changing posture all re-evaluate it.
2. **Grid decisions use the *content* width, not the raw window width.**
   `ResponsiveMetrics.contentWidth` is the cap minus both gutters, so a grid on a
   tablet is planned against the column the user actually sees. `AdaptiveGrid`
   additionally measures its own `maxWidth`, so a grid nested in a narrower card
   still drops its columns correctly.
3. **One scroll owner.** The shell scrolls the whole body, including the
   breadcrumb, so a screen never needs to know whether it is tall. That is what
   removes the nested-scroll class of defect (RD-01) rather than patching one
   screen at a time.

## 5. Category by category

| Category | What the audit found | State now |
| --- | --- | --- |
| **Audio** | Grouped hub is the only category with a second navigation level; its wide layout compared `screenWidthDp >= 840` itself | Reads `twoPane` from the shared metrics; group chips still collapse to one group at a time on a phone |
| **Device Center** | Five hardware tiles forced into 3 + 2 weighted rows; Devices/Diagnostics/Capabilities/App Manager bodies each nested a scroll | Adaptive tile grid; Diagnostics, Capabilities, Tools and App Manager render into the shell's single scroll |
| **Shizuku Center** | Hub connection card is bounded; the tool body nested a scroll | Shared scroll; connection state, environment rows and the read-only IPC limitation note unchanged |
| **Customization** | Hub's glass slider pinned to 30 dp; the Center and Repository Manager nested/duplicated content | Slider keeps its touch target; Repository Manager gained sorting, a collapsed legend and a real empty state |
| **Permissions & Privacy** | Three fixed-weight tiles; the Center body nested a scroll | Adaptive tiles; shared scroll. Permission *state* logic untouched |
| **Settings** | The hub and the full settings sheet both existed and both scrolled | Hub body uses the shared scroll; the sheet is unchanged (dialog-owned scrolling) |

## 6. Verification executed

Run inside the builder container, against the mounted source
(`docker compose --project-name app -f $BASE44_CONFIG_ROOT/docker-compose.base44.yml exec -T app …`),
and re-measured from a **clean** build for this revision so no up-to-date task
stands in for a run:

| Check | Command | Result |
| --- | --- | --- |
| JVM unit tests | `sh gradlew clean :app:testDebugUnitTest` | **374 tests, 40 classes, 0 failures, 0 errors, 0 skipped** — re-run from a clean build. The 311/36 that this table carried before was the state of the pass that added the responsive layer, before the console and the chain editor landed |
| Native DSP harness | `g++ … harness.cpp` (ASan + UBSan over the real `neondsp.cpp`) | `HARNESS PASS (0 failures)`, exit 0 |
| Loudness harness | `gcc -std=gnu11 … ebur128.c` + `g++ … loudness_harness.cpp` under the same sanitizers | `LOUDNESS HARNESS PASS (0 failures)`, exit 0 |
| Debug build | `sh gradlew :app:assembleDebug` | `BUILD SUCCESSFUL`; `app-debug.apk`, 13 998 228 bytes |
| Lint | `sh gradlew :app:lintDebug` | **PASS: 0 errors, 67 warnings.** The six errors that used to fail this task are fixed in source rather than suppressed wholesale: the two `MissingPermission` calls in the capture path now check the permission (and report the app's own error code), the API-28 signing read is version-guarded with the pre-28 accessor below it, and the two `RestrictedApi` `Shizuku.getServerPatchVersion` calls are narrowed to one guarded helper each with the reason stated. The 67 remaining warnings are pre-existing and none is in a file this pass changed |
| Lint — **correction** | as above | An earlier revision of this document claimed lint "is part of the container pipeline now, so this failure cannot come back unnoticed". **It was not, and it did.** The edge-to-edge pass (8.1) added `android:windowLightNavigationBar` (API 27) and `android:enforceNavigationBarContrast` (API 29) to an unqualified `values/styles.xml`, and `:app:lintDebug` failed again with 2 `[NewApi]` errors against minSdk 26. Both attributes now live in `values-v27/styles.xml` and `values-v29/styles.xml` over a shared `Theme.NeonEQ.Base`, and every result in this table comes from a **clean** `clean … :app:lintDebug` run, so this defect is fixed and provably so. **What was not achievable here:** adding `:app:lintDebug` and the loudness harness to the environment-owned compose file the sandbox actually runs. The platform's configuration store rejected the update in this session (`409 Configuration changed or was not inspected`), so that file still invokes only `testDebugUnitTest` and `assembleDebug`. Until it carries all four gates, lint and the loudness harness have to be run by hand from the container — which is how this defect got in, and the reason it is written down here rather than assumed fixed |
| Licence audit | `python3 tools/license_check.py` | `PASS: no copyleft licence on any resolved configuration` |
| Documentation links | `python3 tools/check_docs_links.py` | `PASS: every relative link resolves in 14 file(s)` |

The tests added by this pass:

- `ui/design/ResponsiveLayoutTest` — window classes at their boundaries, the
  fallback for an unusable window size, the content cap and gutter per class,
  `contentWidth` (cap minus gutters, never negative), the two-pane boundary at
  exactly 840 dp, category columns 1/2/3, and the adaptive grid: gap accounting,
  column ceiling, and the degenerate cases (zero width, zero cell, zero maximum).
- `repository/RepositoryInventoryTest` — ordering by name, the compliance order
  of `STATUS`, category grouping, and that no ordering drops or duplicates an
  entry.
- `ui/navigation/ScrollOwnershipTest` — the scroll-ownership rule (RD-14): only
  the Repository Manager may claim `ScrollOwner.SCREEN`, Home and every category
  hub stay on the shell's scroll, every other tool frame is measured by the
  shell, and the tool that claims its own scroll is still routed to its own
  screen and not to a full-screen destination. It also pins the key the shell
  `remember`s its scroll container on — `SonicCoreNavState.scrollKey`, which used
  to be an inline `(category, tool)` pair only the compiler saw: three distinct
  destinations have three distinct keys, the same destination reached twice keeps
  the same key, a hub differs from the tools inside it, and no two tools share
  one. That is what makes "every destination opens at the top" a checked rule.
- `engine/AutoEqProfileTest` — the AutoEQ integration added in the same batch:
  both profile formats, the OFF-slot and unsupported-filter refusals, the
  oversize guard, the name sanitiser, and the mapping onto SonicCore's 31-band
  curve including the clamp. Extended when the import flow was completed: every
  refusal now carries a reason that is asserted, out-of-range gains and Q are
  reported when they are normalised, a curve that does not fit ±15 dB reports
  what it wanted, a declared `Preamp:` is distinguished from no preamp line, and
  the input limits (128 KB, 64 filters, 8192 points) are checked at the boundary
  and one past it.
- `engine/AutoEqImportTest` — the import *flow*, driven through the same lambdas
  `MainActivity` wires: a valid parametric and a valid graphic profile, the preamp
  applied exactly once (and not at all when the profile declares none), save →
  reload → reapply unchanged, replacing a same-named profile while keeping the
  others, the seven refusal inputs applying nothing, cancellation, a failed store,
  a failed activation and a failed preamp write each leaving the sound and the
  profiles as they were, and the review text stating what is replaced and what is
  kept.
- `dsp/DspChainTest` (15) — the chain model behind the console: the default order
  is the engine's own sequence, every stage id round-trips, the limiter is the
  only stage that cannot be bypassed, a bypass keeps a stage's parameters *and*
  its place in the order, a stage can be moved but moving the limiter off the end
  or any stage past it is refused with a reason, a move outside the chain is
  refused instead of crashing, a no-op move changes nothing, an invalid order is
  not a valid chain, stored ids are untrusted, and `reset` returns the engine
  default after edits.
- `dsp/DspChainPresetsTest` (14) — the saved-chain store *and* the telemetry
  wording: a chain round-trips through its stored line, a damaged line yields
  nothing instead of a bad chain, a stored order that is not a valid chain falls
  back to the engine default, names are sanitised and a long name still
  round-trips, saving the same name replaces in place, the store is capped and
  drops the oldest, `remove` leaves the rest alone — plus the `DspTelemetryText`
  decisions (a missing bridge is "not measurable" *with its reason*, a running
  bridge with no frames is "waiting for audio" rather than zero reduction, a real
  measurement is a number, clipping is named when it was measured).
- `dsp/AudioControlStateTest` (9) — the validation contract for every Audio
  screen: numeric fields clamp to the engine's own ranges, `NaN` and infinity
  never reach the DSP, merging writes the controls and leaves the parametric slots
  alone, a snapshot reads back what was merged, and the modelled shelf curve is
  flat at zero, shapes opposite ends independently and stays inside the engine's
  range across the audible band.

## 7. Not verified, and what still needs a device

- **No before/after screenshots.** There is no emulator or physical device in
  this environment and an Android app cannot render in the browser preview, so
  none are provided rather than fabricated.
- **RD-01's crash is reasoned from the Compose measurement contract**, not
  observed. Opening each of the eight bodies on a device is the confirmation
  step, and it is also the test that the shared scroll works from top to bottom.
- **Compact / medium / expanded layouts** are pinned as decisions by unit tests;
  how they *look* — line lengths on a tablet, the 2 × 2 stats grid, the tile
  gutters — needs a device or an emulator with "Resizeable" / split-screen
  enabled.
- **Font scaling and display scaling** are respected by construction (no fixed
  text heights, `maxLines` with ellipsis everywhere text can overflow), but
  nothing here measured 200 % font scale.
- **Landscape, split-screen and foldable posture** re-evaluate the metrics
  because they all deliver a new `Configuration`; that they reflow correctly on
  a real device is unverified.
- **TalkBack focus order** was not exercised. `AdaptiveGrid` keeps declaration
  order and every control keeps its semantics, but screen-reader traversal is a
  device check.
- **RD-11 remains open by choice**: the filter chips are 36 dp, not 48 dp. Going
  to 48 dp would make a horizontally scrolling chip row dominate the card, so
  the trade-off is recorded rather than silently made.
- **RD-14 is closed in code, not on a device.** The Repository Manager is a
  `LazyColumn` and the shell hands it the remaining height instead of a scroll
  container of its own. `ScrollOwnershipTest` pins that it is the *only*
  destination allowed to claim its own scroll — but that the list scrolls under a
  finger, that the filter card stays reachable and that nothing measures
  unboundedly on a real screen is a device check, because a Compose measurement
  contract is reasoned about here, never observed. This is the item that most
  deserves the first emulator run.
- **The AutoEQ import has a device half that cannot be checked here.** The parser,
  the band mapping and now the whole import flow are covered by 56 JVM tests
  (`AutoEqProfileTest` 29, `AutoEqImportTest` 27) driven through the same lambdas
  `MainActivity` wires, so what an import *does* is pinned. What it *sounds like*
  is not: no emulator or device exists here, so "import a profile and hear the
  correction" — including the system file picker's own gesture and reading a real
  `ParametricEQ.txt` from storage — remains a device check. It is a real gap and
  is reported as one rather than inferred from the tests.
- **The chain editor's effect on *sound* is reasoned from the harness, not heard.**
  Section [18] of the DSP harness measures that the committed order changes the
  processed samples and that a bypassed stage passes at unity, which is the part
  a unit test cannot reach. What no check here covers is the audible result on a
  phone: that a reorder is glitch-free across the block boundary in a real
  AudioTrack session, and that the 40 ms-ish ramp on a coefficient change is
  inaudible through the device's own volume and codec path.
- **The eight stages and the bypass flags are exercised on the host JVM and in
  the host DSP harness only.** The Android side of the same code — `NeonDsp`
  loading `libneondsp.so` through `System.loadLibrary`, the JNI `setStageOrder`
  guard and the `appliedStageOrder` read-back — is compiled by `assembleDebug`
  but has never run on a device. `LoudnessTest` pins the same pattern for the
  loudness library by asserting the *honest refusal* on a host JVM, where
  `available == false`; the DSP chain has no equivalent device-side assertion.
- **The edge-to-edge theme fix is verified by lint and the build, not by eye.**
  The two API-gated attributes now live in `values-v27/styles.xml` and
  `values-v29/styles.xml` over a shared `Theme.NeonEQ.Base`; `:app:lintDebug`
  reports `0 errors` and the APK builds, which proves the resources resolve.
  That the bars actually look right behind the glass — and separately on API 26,
  API 27–28 and API 29+ — is a device check.
- **The loudness numbers are the reference implementation's, not a phone's.**
  The harness pins what the vendored libebur128 produces for known signals, and
  what the app *reports* is JVM-tested; neither says the numbers match a
  measurement taken on real hardware from a real file. The module's own doc
  comment says the same thing.

## 8. Full-screen layout, one inline console, and a real chain editor

This pass took the same shared layer further, and it is the pass that makes the
window the only thing that decides the layout:

### 8.1 The window is now edge-to-edge, with the insets owned in one place

- `MainActivity.onCreate` calls `enableEdgeToEdge()` before `setContent`, and
  `Theme.NeonEQ` sets **transparent** status and navigation bars
  (`windowDrawsSystemBarBackgrounds`, `enforceNavigationBarContrast="false"`), so
  the glass backdrop runs to the screen edges instead of stopping at a black
  band no theme could match.
- `GlassScaffold` applies `statusBarsPadding() + displayCutoutPadding() +
  imePadding()` **once** for the whole app. The cutout inset is the part the old
  code lacked: in landscape a cutout sits on a *side*, and a status-bar-only inset
  put the first card under it. `GlassNavigation` keeps its own
  `navigationBarsPadding()` because the dock is the component that sits against
  that edge — applying it in the scaffold too would double-pad the dock, which is
  why it is deliberately not added there.
- The activity keeps `android:windowSoftInputMode="adjustResize"`, which is what
  makes the `imePadding()` above receive a real keyboard inset.
- **RD-15 (new):** no screen may add its own system-bar padding now that the
  scaffold is edge-to-edge. A screen that does will double-pad; the rule is the
  same shape as the existing "the shell owns the scroll" rule.

### 8.2 Related controls share a row, and never below a floor

`controlColumnCount` (pure, in `ResponsiveLayout.kt`) and `AdaptiveControlRow`
put parameter controls side by side when each can keep
`GlassSpacing.minControlWidth` (148 dp) and stack them when they cannot. Nothing
hard-codes "two sliders": a 412 dp phone gets two columns because two cells of
148 dp plus the gap fit its content width, and a 1280 dp tablet gets three. A
cell narrower than the floor wraps to the next row instead of squeezing its own
value label into the slider — which is the specific clipping this rule exists to
prevent (`ResponsiveLayoutTest`).

### 8.3 One console, every effect inline

`ui/audio/AudioConsoleScreen.kt` (new Audio tool `audio.console`, first entry in
the Sound Controls group) renders every effect as an inline card built from the
shared vocabulary: `AudioEffectCard` gives it a title, a live status, its own
enable/bypass toggle on the same row, its controls, and an `OUTPUT` block
immediately underneath. The per-band faders stay on the Equalizer screen by
design — 31 touch targets do not fit a dense console without shrinking below a
usable size — and the console says so where it links to them.

Two rules the console keeps, both testable:

- **A toggle is the real control.** An effect card's switch moves either the
  native stage enable (`NeonDsp.setStageEnabled`) or the parameter switch the
  engine already has (`compOn`, `limiterOn`). No card flips a UI-only boolean.
- **Output is measured or declined.** `DspTelemetryText` (unit-tested) decides
  whether a dynamics card shows a measured reduction, "Waiting for audio", or
  "Not measurable" plus the bridge's own reason. The offline loudness analysis is
  a shared card (`ui/audio/LoudnessAnalysisSection.kt`, used by both the console
  and Audio Diagnostics) and stays explicitly offline — it is analysis, not a
  processing stage, and it is never presented as a live meter.

### 8.4 The chain editor moves audio, not just pixels

The reorder is real in the engine, not in the UI:

- `dsp/DspChain.kt` (pure) holds the eight stages, their ids, the engine's default
  order, and the rules — including that the limiter stays last, refused *before*
  anything is applied, with the reason a screen shows.
- `neondsp.cpp` **walks the committed order** instead of a hard-coded sequence:
  `stageOrder[8]` and `stageOn[8]` live in the same seqlock-published snapshot as
  every other parameter and commit at a block boundary, so the audio thread can
  never observe a half-written chain. `setStageOrder` refuses anything that is not
  a full permutation of the eight ids and keeps the order it had.
- `tools/dspharness/harness.cpp` section **[18]** measures it: with the limiter
  last a +20 dB signal is held at the −1 dBFS ceiling (≈29 200), and with the
  limiter moved first the *same* signal reaches 32 767. Same input, same
  parameters, different measured output — that is what makes the reorder honest.
  The same section measures a bypassed preamp passing at unity while keeping its
  +20 dB setting, a refused non-permutation leaving the committed chain untouched,
  and the mandatory stage staying enabled.

### 8.5 The eight stages, the order rule, and what a saved chain does and does not store

The chain editor is backed by one pure file, `dsp/DspChain.kt`, so "can SonicCore
run this chain?" is a function rather than a branch inside a screen.

**Supported stages.** Exactly the eight the native engine processes, carrying the
ids `neondsp.cpp` uses: Preamp (0), Parametric EQ (1 — eight peaking slots),
Graphic EQ (2 — 10 / 15 / 31 bands), Bass & Treble (3 — one low and one high
shelf), Stereo (4 — width / balance / swap / mono), Convolver (5), Compressor (6)
and Limiter (7). The convolver is bypassable but **no impulse response can be
loaded in this build**, so it passes audio through unchanged and its row says so.
There is no reverb, no virtualizer, no haptic bass and no system-wide stage: the
console lists each of those under "Not available on this path" with its reason and
renders **no control** for it — a toggle that changed nothing would be worse than
saying so.

**Chain-order validation.** `DspChainRules.isValidOrder` requires a full
permutation of the eight ids with the limiter last, and `DspChainRules.move`
refuses a move that would break it **before** anything is applied — the refusal
carries the sentence the screen shows, and a refused move leaves the chain exactly
as it was (`DspChainTest`). The engine re-validates at the audio boundary:
`NeonDsp.setStageOrder` returns `false` for anything that is not a permutation and
does not touch the committed order, so the UI is not the last line of defence.
`DspChain.of` applies the same rule to *stored* ids and falls back to the engine's
default order instead of forwarding a damaged chain to the audio thread.

**The mandatory limiter.** `ChainStage.LIMITER` has `bypassable = false`; its row
renders no toggle (it reads "Always last — output ceiling"), it cannot be dragged
or moved past the end, and no stored chain can place a stage after it. That is
what makes "nothing runs after the output ceiling" true. One honest caveat: the
engine's **own** limiter enable (`limiterOn`) is a separate, older parameter that
the console's Limiter card and Settings expose, and it *can* switch the stage off.
The chain editor cannot disable the limiter; the app can, through that control.
The native output clamp and the NaN/Inf guard still bound whatever leaves the
chain either way.

**What a saved chain stores — and what it does not.** `DspChainPresets` encodes
one chain as a single short line (`order=…;off=…`) in the app's own
`dsp_chain_presets` preferences, up to eight entries; past that the oldest is
dropped. It stores the **order and the bypass flags only**. An effect's
*parameters* (preamp gain, shelf gains, thresholds, band values) are not part of a
chain preset — they live in the persisted `DspParams` JSON — so loading a chain
reorders and re-enables stages without moving a single value
(`applyDspChain` reloads the persisted parameters and re-applies them unchanged),
and a chain preset can never overwrite a preset bundle or an AutoEQ import: those
go through `Presets` / `saveCustomPreset`, a different store. That is a deliberate
limitation rather than an oversight — a chain preset is a routing decision, and
bundling values with it would make "load" silently move controls the user did not
ask to move. Names are sanitised and capped (`DspChainPresets.sanitizeName`), a
damaged stored line decodes to `null` rather than to a chain the engine must not
run, and saving under an existing name replaces it in place (`DspChainPresetsTest`).

### 8.6 What the console shows, and what it declines to show

A card's output block holds a measurement from the native bridge
(`DspTelemetry`: processed frames, compressor/limiter gain reduction, output
peak, clip state) or one of `DspTelemetryText`'s sentences — "Waiting for audio",
"Not measurable: <the bridge's own reason>", "Output peak: not measured". The text
decisions are pure and pinned by JVM tests, so a card cannot draw a number it does
not have. The two scope sentences that matter are both in the UI, not only here:
the counters belong to the capture path that runs the native chain, so on the
in-app player path gain reduction is genuinely not measurable and is reported as
such rather than borrowed from the other path; and the response curves are
modelled from the parameters the DSP was given, labelled as such, and never
presented as a measurement. One known discrepancy is recorded rather than fixed:
the shelf curve's corner constants in `dsp/AudioControlState.kt`
(`LOW_SHELF_HZ` / `HIGH_SHELF_HZ` = 200 Hz / 4000 Hz) do not match the frequencies
the native shelves are actually built at (100 Hz and 8000 Hz in `neondsp.cpp`), so
the corners the UI states are wrong by an octave each. The one-pole shape the
curve uses cannot represent the engine's RBJ shelf at 8 kHz without changing the
curve and the thresholds `AudioControlStateTest` pins, so it is reported here
instead of being silently re-tuned; fix both together if you touch it.
