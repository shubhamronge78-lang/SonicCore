# Open-source repository inventory

Audit date: **2026-10-25**. Scope: every third-party Android project, library and
fork considered for SonicCore, what it offers, whether its licence allows use in
this MIT-licensed application, and what SonicCore actually did about it.

This document is the written form of the data in
`app/src/main/java/com/soniccore/app/repository/RepositoryInventory.kt`, which is
what the in-app **Repository Manager** (Customization → Repository Manager) shows
and what `RepositoryInventoryTest` asserts. If the two ever disagree, the code is
right and this file is stale.

## How the facts were collected

Every upstream fact below — licence, last push date, star count, maintenance
state, latest release — was read from the GitHub REST API on the audit date:

```bash
# licence, last push, stars, archived flag
curl -sS https://api.github.com/repos/<owner>/<repo>
# the licence file itself (also used when GitHub cannot classify it)
curl -sS https://api.github.com/repos/<owner>/<repo>/license
# release information for the licence-compatible candidates
curl -sS https://api.github.com/repos/<owner>/<repo>/releases/latest
```

Unclassified licences (`NOASSERTION`) were resolved by reading the licence file
the API points at, not by assuming. Four projects publish no licence file at all
and are recorded as **unverified** — nothing may be taken from them until that is
resolved. Release tags were fetched only for the projects that are licence
compatible or already adopted; the rest show their last-push date as the only
availability evidence, and say so.

## The rule this audit enforces

SonicCore is MIT (`LICENSE`, © 2026 Shubham Ronge). Therefore:

- **Permissive** (MIT, Apache-2.0, BSD) may be linked or bundled, with attribution.
- **Weak copyleft** (LGPL) is not bundled into the APK — Android cannot guarantee
  the relinking conditions LGPL needs.
- **Copyleft** (GPL, AGPL, any version) may not be distributed inside an
  MIT-licensed application, so no code, table, preset or impulse response from
  such a project is in this repository.
- **Proprietary** projects need a commercial licence and are not bundled.
- **Unverified** projects are unusable until a licence is established.

`RepositoryInventoryTest` asserts this directly: no copyleft, proprietary or
unverified project may be reported as `Integrated`, and none may be queued as
`Ready to Integrate`.

## Summary

| Verdict | Projects | Meaning |
| --- | --- | --- |
| Permissive | 28 | Usable; 2 adopted, 10 queued, 16 studied and not taken |
| Copyleft (GPL/AGPL) | 17 | Audited, refused, nothing copied |
| Weak copyleft (LGPL) | 1 | Not bundleable in an APK |
| Proprietary | 1 | Needs a commercial licence |
| Unverified (no licence file) | 4 | Nothing may be taken |

**51 projects audited** in total.

| Integration status | Projects |
| --- | --- |
| Integrated | 2 |
| Ready to Integrate | 10 |
| Reference Only | 15 |
| Requires Shizuku | 1 |
| Requires Root | 1 |
| Incompatible | 21 |
| Unsupported | 1 |

`Integrated` is used only where code or an official client is in this repository
**and** covered by tests: `androidx/androidx` (declared dependencies) and
`RikkaApps/Shizuku-API` (the Shizuku client). Everything else states what it
would need.

## Integrated

| Project | Licence | Surface | Coverage | Detail |
| --- | --- | --- | --- | --- |
| `RikkaApps/Shizuku-API` | MIT | Shizuku bridge | Partial | `dev.rikka.shizuku:api` + `:provider` 13.1.5 are real dependencies. `ShizukuBridge` reports binder state; `ShizukuDiagnostics` performs the one read-only elevated call. Every other operation is `NOT_IMPLEMENTED` — see [shizuku-operations.md](shizuku-operations.md). Latest GitHub release is v12.1.0 (2022-10-07); the Maven artifacts used here are 13.1.5. |
| `androidx/androidx` | Apache-2.0 | UI, lifecycle, platform | Adopted | `core-ktx` 1.12.0, `appcompat` 1.6.1, Compose BOM 2024.02.00 + Material3, `activity-compose` 1.8.2, `lifecycle-runtime-ktx` 2.7.0. `tools/license_check.py` reads their licences from the resolved dependency graph. |

## Ready to Integrate — the audited backlog

Licence-compatible, verified, and waiting for an implementation batch. Nothing in
this table is implemented today.

| Project | Licence | Latest release | What it would add |
| --- | --- | --- | --- |
| `jaakkopasanen/AutoEq` | MIT | data repository (no tagged release) | Headphone measurement database and target curves; the derived filters are ordinary parametric bands, so no engine change is needed. |
| `xiph/speexdsp` | BSD-3-Clause | SpeexDSP-1.2.1 (2022-06-17) | Resampling, echo cancellation and preprocessor DSP. |
| `jiixyj/libebur128` | MIT | v1.2.6 (2021-02-14) | EBU R128 / BS.1770 loudness measurement — the basis of a loudness-matching feature. |
| `google/oboe` | Apache-2.0 | 1.11.0 (2026-09-15) | AAudio/OpenSL ES wrapper for a lower-latency output path (AAudio needs API 26+). |
| `androidx/media` | Apache-2.0 | 1.11.1 (2026-09-11) | ExoPlayer and media-session libraries if the built-in player needs wider codec coverage. |
| `chrisbanes/haze` | Apache-2.0 | 2.0.1 (2026-09-29) | Real background blur for Compose with a documented downlevel fallback — the first candidate if the glass vocabulary ever gains a blur layer. |
| `material-foundation/material-color-utilities` | Apache-2.0 | no tagged release | Material You colour science: a full tonal palette from one seed colour. |
| `google/material-design-icons` | Apache-2.0 | Material Icons 4.0.0 (2020-08-31) | The official icon set. |
| `patrykandpatrick/vico` | Apache-2.0 | v3.3.1 (2026-08-28) | Compose chart library, if the number of chart types ever justifies a dependency. |
| `coil-kt/coil` | Apache-2.0 | 3.6.3 (2026-09-18) | Image loading with artwork and palette support for the player. |

## Audited and deliberately not taken

| Project | Licence (verified) | Status | Why not |
| --- | --- | --- | --- |
| `timschneeb/RootlessJamesDSP` | GPL-3.0 | Incompatible | Copyleft. The capability it targets (capturing system playback into a native DSP chain) is already SonicCore's own pipeline. |
| `alienware377/RootlessViPER4Android` | GPL-3.0 | Incompatible | Copyleft; a fork of RootlessJamesDSP. Audited for the effect set only. |
| `james34602/JamesDSPManager` | none published | Incompatible | No licence file, and it requires a root/system-framework position. |
| `pittvandewitt/Wavelet` | none published | Incompatible | Studied for how AutoEQ is presented; the default branch publishes no licence, so the position is unresolved. |
| `bearinmindcat/Equalizer314` | GPL-3.0 | Incompatible | Copyleft; its session-0 documentation matches SonicCore's own capture limits. |
| `HEnquist/camilladsp` | GPL-3.0 | Incompatible | Copyleft desktop engine; informed signal-flow vocabulary only. |
| `werman/noise-suppression-for-voice` | GPL-3.0 | Incompatible | Copyleft, and SonicCore's noise gate already covers the need. |
| `breakfastquay/rubberband` | GPL-2.0 | Incompatible | Copyleft time-stretch; variable-speed playback stays unimplemented rather than half-shipped. |
| `chirlu/soxr` | LGPL-2.1-or-later | Incompatible | An APK cannot guarantee LGPL's relinking conditions. |
| `superpoweredSDK/…Interactive-Audio-Platform` | proprietary | Incompatible | Governed by Superpowered's Master License Agreement; no licence file in the repository. |
| `MuntashirAkon/AppManager` | GPL-3.0-or-later | Incompatible | Copyleft; SonicCore's App Manager is its own implementation on the public launcher-intent queries declaration. |
| `zhanghai/MaterialFiles` | GPL-3.0 | Incompatible | Copyleft; storage analysis compared against SonicCore's own storage card. |
| `VREMSoftwareDevelopment/WiFiAnalyzer` | GPL-3.0 | Incompatible | Copyleft; and Android gates SSID/BSSID behind location access anyway. |
| `Exodus-Privacy/exodus-android-app` | GPL-3.0 | Incompatible | Copyleft, and SonicCore declares no `INTERNET` permission, so it cannot fetch a tracker database. |
| `topjohnwu/Magisk` | GPL-3.0 | Incompatible | Copyleft and root-only. No module, script or binary is executed or bundled. |
| `tiann/KernelSU` | GPL-3.0 | Incompatible | Copyleft and root-only. |
| `bmax121/APatch` | GPL-3.0 | Incompatible | Copyleft and root-only. |
| `RikkaApps/Sui` | GPL-3.0 | Incompatible | Copyleft and root-only. |
| `NeoApplications/Neo-Launcher` | GPL-3.0 | Incompatible | Copyleft, and a home app cannot be embedded in another app. |
| `MM2-0/Kvaesitso` | GPL-3.0 | Incompatible | Copyleft and a home app. |
| `FossifyOrg/Launcher` | GPL-3.0 | Incompatible | Copyleft and a home app. |
| `topjohnwu/libsu` | Apache-2.0 | Requires Root | Licence-compatible and deliberately not added: SonicCore ships no root code path. |
| `8enet/AppOpsX` | MIT | Requires Shizuku | Reading app-ops state is already covered; the write path needs a shell the Shizuku v13 client cannot spawn from an app process. |
| `LawnchairLauncher/lawnchair` | Apache-2.0 | Unsupported | Permissive, but Android requires the user to choose a home app — no launcher can be integrated into SonicCore. |

## Reference Only — audited, compared, nothing taken

| Project | Licence | Coverage | Comparison note |
| --- | --- | --- | --- |
| `google/ExoPlayer` | Apache-2.0 | Studied | Deprecated upstream in favour of `androidx/media`; recorded so nobody integrates the stale line. |
| `jaakkopasanen/Impulcifer` | MIT | Studied | Desktop measurement tool; it cannot run on Android, but it is where a future convolver's impulse responses would come from. |
| `LibChecker/LibChecker` | Apache-2.0 | Studied | Native-library metadata reading; SonicCore's native-bridge card already reports what a third-party app may read. |
| `aosp-mirror/platform_frameworks_base` | Apache-2.0 (AOSP) | Studied | The authoritative reference for what only a system app may call — the basis of every Bounded/Unavailable verdict in [feature-matrix.md](feature-matrix.md). |
| `RikkaApps/Shizuku` | Apache-2.0 | Studied | The manager app the user installs; never bundled, so its licence does not propagate here. |
| `chrisbanes/haze`, `skydoves/Cloudy`, `Kyant0/AndroidLiquidGlass` | Apache-2.0 | Studied | Blur and liquid-glass approaches compared; SonicCore's `LiquidGlassSurface` uses native Compose primitives and no blur dependency. |
| `KieronQuinn/MonetCompat` | MIT | Studied | App-level wallpaper palettes; SonicCore's Dynamic Wallpaper theme reads `WallpaperManager` itself and reports when the platform refuses. |
| `google/fonts` | none at repo root (per-family OFL) | Studied | A font picker would need per-family OFL attribution; the system font stack is used instead. |
| `PhilJay/MPAndroidChart` | Apache-2.0 | Studied | View-based; the alternative to Vico, and it would need an interop wrapper. |
| `airbnb/lottie-android` | Apache-2.0 | Studied | The splash is drawn in Compose; a Lottie dependency would add churn for no visual gain. |
| `google/accompanist` | Apache-2.0 | Studied | Its libraries are being folded into AndroidX. |
| `google/gson` | Apache-2.0 | Studied | Preset import/export uses Android's platform JSON implementation. |
| `permissions-dispatcher/PermissionsDispatcher` | Apache-2.0 | Studied | Generated plumbing would duplicate SonicCore's declared-vs-granted permission model. |
| `seedvault-app/seedvault` | none at repo root (`LICENSES/`) | Studied | A design reference for configuration backup; its licence position is unresolved. |

## Fork and downstream relationships

Recorded so a duplicated feature is visible instead of being integrated twice:

- `alienware377/RootlessViPER4Android` → fork of `timschneeb/RootlessJamesDSP`,
  which links `james34602/JamesDSPManager` (no licence file).
- `google/ExoPlayer` → superseded by `androidx/media`.
- `patrykandpatrick/vico` and `PhilJay/MPAndroidChart` → the same job, different
  UI toolkit; `chrisbanes/haze`, `skydoves/Cloudy` and `Kyant0/AndroidLiquidGlass`
  → the same job, different rendering strategy.
- `RikkaApps/Sui` → a root-only superuser implementation compatible with the
  Shizuku API that SonicCore actually uses.

## Where this surfaces in the app

**Customization → Repository Manager** lists all 51 projects with search, category
and status filters, the licence verdict, the privilege a project would need, its
maintenance and release evidence, its measured feature coverage, fork relations
and a link to the source. The status vocabulary there is exactly the one above,
and the screen shows no project as integrated that this audit did not implement
and test.

## Known limitations of this audit

- **Release tags were fetched for 12 projects only** (the licence-compatible and
  adopted ones). The other entries report their last-push date; no release claim
  is made for them.
- **No project was executed, cloned or fetched into this repository.** Nothing
  here runs downloaded code or scripts.
- **Nothing in the backlog is implemented yet.** The `Ready to Integrate` rows are
  plans with a verified licence, not features.
- **No Android device is available** in this workspace, so every integrated
  capability remains not device-verified.

## Continuation plan

| Batch | Contents | Needs |
| --- | --- | --- |
| A — no new dependency | AutoEq target curves → EQ preset library; `libebur128` loudness metering; `speexdsp` resampling/AEC; `material-color-utilities` seed-based palettes | Native/Native-bridge work plus unit tests; no new Gradle dependency. |
| B — dependency decision | `haze` blur where the OS supports it; `coil` artwork; `vico` charts; `material-design-icons` | An explicit decision per dependency, then `tools/license_check.py` re-run. |
| C — blocked by design | `AppOpsX` write path via a Shizuku user service; every root-only project | Out of scope while SonicCore ships no root code path; documented in [shizuku-operations.md](shizuku-operations.md). |
| D — verification | Device testing of every Audio/Device/Shizuku/Customization feature; release-tag refresh for the remaining entries | A physical device; see [feature-matrix.md](feature-matrix.md). |

## Re-running this audit

```bash
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/license_check.py'
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/check_docs_links.py'
```

`license_check.py` reads the licences of everything the build resolves and fails
on a copyleft licence in a shipped configuration, so the dependency side of this
audit is machine-checked rather than trusted.
