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
| Equalizer | Sound Controls | Implemented | Graphic + parametric EQ, preamp, A/B, undo/redo, per-band controls | No |
| Bass & Treble | Sound Controls | Implemented | Independent low/high shelf gains with the modelled shelf response curve | No |
| Stereo & Spatial | Sound Controls | Implemented | Width, balance, channel swap and mono downmix — the four native `setStereo` parameters | No |
| Dynamics | Sound Controls | Implemented | Noise gate and limiter controls on the existing native chain | No |
| DSP Studio | DSP Processing | Implemented | Native stage list, per-stage bypass, parameters, limiter/compressor telemetry | No |
| Player | Playback & Capture | Implemented | Built-in player through SonicCore's own software processing path | No |
| Audio Capture | Playback & Capture | Bounded | Playback capture needs a user-approved `MediaProjection` token; the source app must permit capture. Protected/opted-out audio is not available | No |
| Audio Sessions | Playback & Capture | Implemented | Session records, comparison of factual aggregates, export through `FileProvider` | No |
| Output & Devices | Devices & Routing | Implemented | Routes Android publishes to this app with their own capabilities; routing is never forced | No |
| Signal Path | Devices & Routing | Implemented | Measured capture-to-output stages; reports "no frames" rather than a guess | No |
| Audio Presets | Presets & Diagnostics | Implemented | Built-in and custom profiles, save, apply, delete, import/export bundles | No |
| Audio Diagnostics | Presets & Diagnostics | Implemented | Session counters, platform compatibility probes, the project's own audio self-test, log and redacted report export | No |

Every Audio screen is a pure consumer of `SonicCoreToolHost`: it reads an
`AudioControlState` snapshot and hands a new one back. The host validates it
against `AudioControlRanges` (NaN and infinity collapse instead of reaching the
audio thread), merges it over the persisted `DspParams` so one screen can never
clear another's parameters, and applies it through the same `DspParams.applyTo`
path the DSP rack uses.

Native DSP (`app/src/main/cpp/neondsp.cpp`) is unchanged by the platform work and
is covered by the host harness (`HARNESS PASS`), which compiles the production
DSP under ASan+UBSan.

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
| Tests | Implemented | 232 JVM tests across 31 classes, 0 failures, 0 skipped (`:app:testDebugUnitTest`), including `RepositoryInventoryTest`'s licence-compliance contract over the repository inventory |
| Native DSP regression | Implemented | ASan+UBSan host harness over the production DSP |
| Lint | Known findings | `:app:lintDebug` reports 6 errors + 68 warnings, none of them in a file this work touches — listed in `PLATFORM_EXPANSION_STATUS.md`. Lint is not part of CI |
| Licence audit | Implemented | `tools/license_check.py` + `docs/repository-integration.md`; the audited upstream inventory is `docs/repository-inventory.md` |
| Documentation links | Implemented | `tools/check_docs_links.py` |
