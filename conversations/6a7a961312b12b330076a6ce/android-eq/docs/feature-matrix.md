# SonicCore feature matrix

Status of every destination in the six navigation categories, measured against
the code in this repository. **Nothing here is a decorative claim:** an entry is
`Implemented` only when there is a real code path, `Bounded` when Android
restricts what a third-party app may do, `Unavailable` when the platform offers
no API for it, and `Not implemented` when SonicCore deliberately does not do it.

`Device-verified` means it was exercised on real hardware. This repository's CI
and sandbox have no Android device, so device-only behaviour is listed as
**not device-verified** and must be checked on a phone before release.

## Category 1 — Audio

Audio is the largest workspace, so its screens are grouped into six sections in
the **Audio Center**. The Audio Center's sub-navigation is a chip row on phones
and a grouped list on tablets; every group below is reachable from it, and no
screen is duplicated — each destination still has exactly one rendering path,
which `SonicCoreNavigationTest` asserts.

| Destination | Group | Status | Notes | Device-verified |
| --- | --- | --- | --- | --- |
| Audio Dashboard | Overview | Implemented | Live engine state, measured meters, spectrum, counters, output route | No |
| Audio Console | Sound Controls | Implemented | One surface for every effect the engine really has: the eight-stage signal chain with real reordering and per-stage bypass, then an inline card per effect (preamp, shelves, graphic/parametric EQ, stereo, convolver, compressor, limiter) with its own toggle and an output block that holds a measurement from the native bridge or says which of "waiting for audio" / "not measurable" applies. The **convolver card loads an impulse response**: a 16/24/32-bit PCM or 32-bit float WAV, mono or stereo, truncated to the engine's 2048 taps and resampled to the engine's rate, with every transformation and every refusal stated in words; the decoded taps are stored and restored after a restart, and clearing loads a unit impulse so the stage is pass-through rather than silent. Effects this build does not offer are listed with their reason and have **no control** | No |
| Equalizer | Sound Controls | Implemented | Graphic + parametric EQ, preamp, A/B, undo/redo, per-band controls | No |
| Bass & Treble | Sound Controls | Implemented | Independent low/high shelf gains with the modelled shelf response curve | No |
| Stereo & Spatial | Sound Controls | Implemented | Width, balance, channel swap and mono downmix — the four native `setStereo` parameters | No |
| Dynamics | Sound Controls | Implemented | The platform `NoiseSuppressor` on the capture session (a capture-session control, **not** a native chain stage), the native limiter's threshold, and the anti-clip state. Compressor parameters stay in DSP Studio | No |
| DSP Studio | DSP Processing | Implemented | The native stage list with its parameters and the limiter/compressor telemetry. Per-stage bypass here and in the Audio Console's chain editor move the same engine flag (`stageOn[]`) | No |
| Player | Playback & Capture | Implemented | Built-in player through SonicCore's own software processing path | No |
| Audio Capture | Playback & Capture | Bounded | Playback capture needs a user-approved `MediaProjection` token; the source app must permit capture. Protected/opted-out audio is not available | No |
| Audio Sessions | Playback & Capture | Implemented | Session records, comparison of factual aggregates, export through `FileProvider` | No |
| Output & Devices | Devices & Routing | Implemented | Routes Android publishes to this app with their own capabilities; routing is never forced | No |
| Signal Path | Devices & Routing | Implemented | Measured capture-to-output stages; reports "no frames" rather than a guess | No |
| Audio Presets | Presets & Diagnostics | Implemented | Built-in and custom profiles, save, apply, delete, import/export bundles, and AutoEQ `ParametricEQ`/`GraphicEQ` profile import: paste the text or open the `.txt` file, review the parsed filters/points, the preamp and the band curve, then deliberately save it or apply it (31-band approximation, preamp written once to the native preamp parameter, malformed and unsupported profiles refused with their reason) | No |
| Audio Diagnostics | Presets & Diagnostics | Implemented | Session counters, platform compatibility probes, the project's own audio self-test, log and redacted report export, and the **offline loudness measurement**: pick a 16-bit PCM WAV and read integrated LUFS, loudness range and true peak (EBU R 128 / ITU-R BS.1770-4 over the vendored libebur128). Offline analysis only — it is a separate `libloudness.so` that nothing in the playback or capture path loads — and silence or a span shorter than one 400 ms gating block is reported as **not measurable** rather than as a number | No |

Every Audio screen is a pure consumer of `SonicCoreToolHost`: it reads an
`AudioControlState` snapshot and hands a new one back. The host validates it
against `AudioControlRanges` (NaN and infinity collapse instead of reaching the
audio thread), merges it over the persisted `DspParams` so one screen can never
clear another's parameters, and applies it through the same `DspParams.applyTo`
path the DSP rack uses.

The native DSP (`app/src/main/cpp/neondsp.cpp`) is covered by **two** host
harnesses that compile the production code under ASan+UBSan, both run from the
app container:

- the **DSP harness** (`HARNESS PASS (0 failures)`), which also measures that a
  reorder really changes the processed audio, that a refused non-permutation
  leaves the committed chain untouched, and that a bypassed stage passes at unity
  while keeping its parameters (section [18]);
- the **offline loudness harness** (`LOUDNESS HARNESS PASS (0 failures)`) over
  the vendored libebur128.

Neither substitutes for a device run: there is no emulator and no physical device
in this environment, so every `Device-verified` column below reads **No**, and
"No" means *unverified on hardware*, not "known broken".

## Category 2 — Device Center

| Destination | Status | Notes | Device-verified |
| --- | --- | --- | --- |
| Diagnostics | Implemented | CPU/RAM/storage/battery/thermal/display/network/sensors, audio + DSP health, bounded operation log, redacted report export | No |
| Capabilities | Implemented | Capability registry with the first blocker named per row | No |
| Device Tools | Implemented | Shortcuts to the platform's own audio/system screens plus public audio-focus probes | No |
| App Manager | Bounded | Only launcher-visible packages (no `QUERY_ALL_PACKAGES`); per-app preset profiles | No |

Bounded details: per-core CPU frequency only when the kernel exposes
`cpuinfo_max_freq` (otherwise "Not readable"); storage reports the app-visible
data partition, not the physical disk; per-app CPU/memory breakdown is not
available to third-party apps on Android 8+.

## Category 3 — Shizuku Center

| Destination | Status | Notes | Device-verified |
| --- | --- | --- | --- |
| Shizuku | Implemented (bridge) | Official MIT client 13.1.5, binder hand-off provider, install/running/authorized state, in-app AUTHORIZE, `DISCONNECTED` after a dead binder | No |
| Shizuku | Implemented (read-only) | One elevated operation: bounded IPC diagnostics (server uid/privilege, API version, patch, SELinux context) | No |
| Shizuku | Not implemented | Audio-policy read, route force, secure-settings read/write, permission grant, binder enumeration — each states what it would need; writes are refused by policy | — |

Root is not used anywhere: SonicCore ships no root code path, and root-only work
stays outside the app. `docs/shizuku-operations.md` has the per-operation detail.

## Category 4 — Customization

| Destination | Status | Notes | Device-verified |
| --- | --- | --- | --- |
| Customization | Implemented | Twelve themes (legacy ids preserved), preview + apply, custom-accent swatches, liquid-glass intensity, appearance mode | No |
| Customization | Bounded | Dynamic Wallpaper theme uses the wallpaper colour only when `WallpaperManager` exposes it to this app; otherwise it keeps its own palette and says so | No |
| Repository Manager | Implemented | Searchable catalogue of the 51 audited upstream projects: category and status filters, licence verdict, privilege requirement, maintenance and release evidence, measured coverage, fork relations and a source link per project. Only projects whose code is in this tree and tested are shown as Integrated | No |
| Customization | Unavailable | Launcher icon packs, system-wide accent override, force-dark in other apps — Android does not permit a third-party app to do these; each row says so, and the Custom Accent theme recolors SonicCore's own surfaces instead | — |
| Quick Settings tile, home-screen widget, notification controls | Implemented | Pre-existing services | No |
| Launcher icon | Implemented | Adaptive cyan/violet SC monogram (`mipmap-anydpi-v26` + vector foreground, monochrome layer) | No |

## Category 5 — Permissions & Privacy

| Destination | Status | Notes | Device-verified |
| --- | --- | --- | --- |
| Permissions | Implemented | Live declared-vs-granted state, battery-optimisation and usage-access probes, per-permission recovery screens, per-session capture consent | No |
| Permissions | Implemented | Privacy statement backed by the manifest: no `INTERNET` permission, so captured audio cannot leave the device | — |

Full detail: `docs/permissions-audit.md`. Accessibility and notification-listener
access are reported as `NOT_DECLARED` because the app ships neither service —
never shown as a working toggle.

## Category 6 — Settings

| Destination | Status | Notes | Device-verified |
| --- | --- | --- | --- |
| Settings | Implemented | Preferences hub (engine, UI, platform tools) and app information; opens the Android app-info screen | No |

## Cross-cutting

| Item | Status | Notes |
| --- | --- | --- |
| Navigation | Implemented | Six categories on Home and in the bottom dock (which never lists an internal tool); a category opens a hub listing its tools, so the scrolling 16-chip dock is gone and every destination is reachable |
| Splash | Implemented | Animated first-frame overlay; skipped when system animations are off |
| Tests | Implemented | **374 JVM tests across 40 classes, 0 failures, 0 errors, 0 skipped** (`:app:testDebugUnitTest`, from a clean build) — the platform expansion, the DSP chain model and its saved chains, the AutoEQ parser *and* import flow, the offline loudness decisions, the responsive/scroll-ownership contracts and `RepositoryInventoryTest`'s licence-compliance contract. Automated coverage only: nothing in this table has been run on a device |
| Native DSP regression | Implemented | ASan+UBSan host harness over the production DSP — `HARNESS PASS (0 failures)`, including section [18]'s measurement that a reorder really changes the processed audio. Automated, not device-verified |
| Offline loudness regression | Implemented | ASan+UBSan host harness over the vendored libebur128 and the module's JNI facade — `LOUDNESS HARNESS PASS (0 failures)`. Automated, not device-verified |
| Lint | Implemented | `:app:lintDebug` passes: **0 errors, 67 warnings**. The warnings are pre-existing and none is in a file this work changed (dependency version bumps, `InlinedApi` permission constants, `BatteryLife` on the battery-optimisation shortcut, hardcoded `/data/` in a diagnostic readout). Lint is part of the sandbox pipeline, so a new error fails the build |
| Licence audit | Implemented | `tools/license_check.py` — `PASS: no copyleft licence on any resolved configuration`; `docs/repository-integration.md`; the audited upstream inventory is `docs/repository-inventory.md` |
| Documentation links | Implemented | `tools/check_docs_links.py` — `PASS: every relative link resolves in 14 file(s)` |
