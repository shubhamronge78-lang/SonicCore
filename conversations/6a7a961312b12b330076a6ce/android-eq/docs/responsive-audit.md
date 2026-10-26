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
| RD-02 | High | Navigating from a long screen to a short one opened the short one part-way down, with its first cards above the fold | One `rememberScrollState()` was shared by every destination, so the offset survived navigation | The body's `ScrollState` is keyed on `(category, tool)`, so each destination starts at the top |
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

## 4. What changed, and why it is a shared change

| File | Role |
| --- | --- |
| `ui/design/ResponsiveLayout.kt` (new) | `WindowSizeClass`, `ResponsiveMetrics`, `LocalResponsiveMetrics`, `rememberResponsiveMetrics()`, `AdaptiveGrid`, and the pure decisions `windowSizeClassOf`, `adaptiveColumnCount`, `categoryColumnCount` |
| `ui/design/GlassTokens.kt` | The responsive half of `GlassSpacing`: per-class gutters, section gaps, content caps, minimum tile widths. Appearance constants stay in the one token file |
| `MainActivity.kt` | The shell: one scroll container, per-destination scroll state, responsive gutter and content cap |
| `ui/home/SonicCoreHomeScreen.kt` | Category grid columns follow the window; the user preference still wins |
| `ui/category/CategoryHubs.kt` | Device and Privacy tile grids; Audio hub reads the shared two-pane decision; glass slider keeps its touch target |
| `ui/customization/RepositoryManagerScreen.kt` | Adaptive summary grid, sort control, collapsed legend, actionable empty state, taller filter chips |
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

Run from the container built from this source
(`docker compose -f docker-compose.base44.yml run --rm --no-deps app …`):

| Check | Command | Result |
| --- | --- | --- |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` | **259 tests, 33 classes, 0 failures, 0 skipped** — 18 more than before this pass (`ResponsiveLayoutTest` 14, four `RepositoryInventory` ordering tests) |
| Native DSP harness | part of the container entrypoint (ASan + UBSan over the real `neondsp.cpp`) | `HARNESS PASS (0 failures)` |
| Debug build | `./gradlew :app:assembleDebug` | `BUILD SUCCESSFUL`, `app-debug.apk` produced |
| Lint | `./gradlew :app:lintDebug` | **6 errors, all pre-existing** and unrelated to this work: two `MissingPermission` in the capture path, two `NewApi` (API 28 `SigningInfo`) in `SettingsToolScreens`, two `RestrictedApi` (`Shizuku.getServerPatchVersion`). No new error or warning in a file this pass touched |
| Licence audit | `python3 tools/license_check.py` | `PASS: no copyleft licence on any resolved configuration` |
| Documentation links | `python3 tools/check_docs_links.py` | PASS |

The tests added by this pass:

- `ui/design/ResponsiveLayoutTest` — window classes at their boundaries, the
  fallback for an unusable window size, the content cap and gutter per class,
  `contentWidth` (cap minus gutters, never negative), the two-pane boundary at
  exactly 840 dp, category columns 1/2/3, and the adaptive grid: gap accounting,
  column ceiling, and the degenerate cases (zero width, zero cell, zero maximum).
- `repository/RepositoryInventoryTest` — ordering by name, the compliance order
  of `STATUS`, category grouping, and that no ordering drops or duplicates an
  entry.

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
- The Repository Manager still renders its catalogue with `forEach` inside the
  app-wide scroll rather than in a lazy list. A lazy list cannot live inside a
  `verticalScroll` parent; doing it properly means giving that screen the scroll
  ownership back and making the shell's scroll structural per screen — a larger
  change than this pass, and the honest remaining item.
