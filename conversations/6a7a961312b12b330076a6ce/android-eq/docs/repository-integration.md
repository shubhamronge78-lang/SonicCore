# Repository integration & licence provenance

Audit date: **2026-10-25**. Scope: every third-party project considered for the
SonicCore platform work, what was actually taken from it, and why the rest was
not taken.

Rule applied: **nothing may be copied into this project unless its licence
permits redistribution inside an MIT-licensed application.** SonicCore is MIT
(`LICENSE`, © 2026 Shubham Ronge), so permissive licences (MIT, Apache-2.0,
BSD) are compatible and copyleft licences (GPL/AGPL, any version) are not.

`python3 tools/license_check.py` prints the licence of every artifact the build
resolves and exits non-zero if a copyleft licence appears on a shipped
configuration, so this audit can be re-run instead of trusted. Last run of this
audit: **PASS**, 109 artifacts on `debugRuntimeClasspath` (shipped) and 112 on
`debugUnitTestRuntimeClasspath` (test-only), no copyleft licence on either.

## 1. Integrated

| Project | Licence (verified) | How SonicCore uses it |
| --- | --- | --- |
| [`RikkaApps/Shizuku-API`](https://github.com/RikkaApps/Shizuku-API) — `dev.rikka.shizuku:api` / `:provider` 13.1.5 | **MIT** (repo `LICENSE`; the Maven POM published by Rikka also declares `MIT License`) | Official client for the *optional* elevated bridge: the binder hand-off provider declared in the manifest, install/running/authorized state, `Shizuku.requestPermission`, and the read-only diagnostics call. Compatible with MIT: attribution only, no source copying. |
| [`RikkaApps/Shizuku`](https://github.com/RikkaApps/Shizuku) (the manager app) | **Apache-2.0** | **Not bundled.** The user installs the manager themselves; it never enters this repository or the APK, so its licence does not propagate here. |
| AndroidX / Jetpack Compose — `core-ktx` 1.12.0, `appcompat` 1.6.1, `compose-bom` 2024.02.00, `material` 1.11.0, `activity-compose` 1.8.2, `lifecycle-runtime-ktx` 2.7.0 | **Apache-2.0** | UI, lifecycle and platform-compat foundation. |
| `junit:junit` 4.13.2 | **EPL-1.0** | JVM unit tests only (`testImplementation`): never packaged into the APK. |
| `org.json:json` 20240303 | **Public Domain** (as declared in the published POM) | Test-scope implementation of `org.json` so JSON contracts can be tested on the JVM; the shipped app uses Android's platform JSON. |
| `com.google.guava:listenablefuture` 1.0, `org.hamcrest:hamcrest-core` 1.3 | **Apache-2.0** and **BSD-3-Clause**; the published POMs of these two carry **no licence metadata**, which is why `tools/license_check.py` prints `unknown` for them. The licences above come from their Maven Central / upstream project metadata. | Pulled in transitively (AndroidX `concurrent-futures`, JUnit). Both permissive; no action needed. |

`app/src/main` contains only originally written Kotlin and the project's own
C++ (`app/src/main/cpp/neondsp.cpp`). There is no vendored or copied source
directory tree anywhere in the project.

## 2. Audited, deliberately not integrated

| Project | Licence | Decision and reasoning |
| --- | --- | --- |
| [`timschneeb/RootlessJamesDSP`](https://github.com/timschneeb/RootlessJamesDSP) | **GPL-3.0** | **Incompatible with an MIT app** — no code, DSP tables or resources were copied. The capability it targets (processing system playback without root) is already covered by SonicCore's own playback-capture pipeline and native DSP, both pre-existing in this repository. |
| [`RootlessViPER4Android`](https://github.com/alienware377/RootlessViPER4Android), ViPER4Android FX | **GPL-3.0** (the rootless port states GPL-3.0 "same as upstream") | Same incompatibility. Audited for the *set* of effects a modern EQ app exposes; no code, presets or impulse responses were taken. SonicCore's limiter, compressor, parametric/graphic EQ and shelves are its own implementation. |
| [`MuntashirAkon/AppManager`](https://github.com/MuntashirAkon/AppManager) | **GPL-3.0** | Audited for how a third-party package manager enumerates apps. SonicCore's App Manager is its own implementation built only on the public `<queries>` launcher-intent declaration and `PackageManager`, so it needs no `QUERY_ALL_PACKAGES`. |
| [`topjohnwu/libsu`](https://github.com/topjohnwu/libsu) | **Apache-2.0** (compatible) | Evaluated for optional root support and **not** added. SonicCore ships no root code path: root-only work stays outside the app. Adding a root shell would be a separate, opt-in feature with its own consent flow. |

## 3. Platform capabilities implemented from documentation, not from forks

These were researched from Android's own documentation and reference rather than
from any third-party repository, so no licence question arises:

- Playback capture (`MediaProjection` + `AudioPlaybackCaptureConfiguration` +
  `AudioRecord`) and the consent/`FOREGROUND_SERVICE_MEDIA_PROJECTION` rules.
- `WallpaperManager.getWallpaperColors` for the Dynamic Wallpaper theme — a
  public API that a normal install is not permitted to read, which the UI
  reports honestly (see `docs/feature-matrix.md`).
- Permission state (`checkSelfPermission`, `PowerManager.isIgnoringBatteryOptimizations`,
  `AppOpsManager` usage-access) for the Permissions & Privacy Center.
- Device probes (`Build`, `ActivityManager`, `StatFs`, `BatteryManager`,
  `PowerManager`, `Display`, `ConnectivityManager`, `SensorManager`).

## 4. Full inventory and the in-app Repository Manager

This file records **what was taken and why not**; the complete audit — 51
projects with their verified licence, last push date, stars, maintenance state,
latest release, privilege requirement, fork relationships and measured feature
coverage — lives in [repository-inventory.md](repository-inventory.md) and in
`app/src/main/java/com/soniccore/app/repository/RepositoryInventory.kt`.

The same data is surfaced in the app itself: **Customization → Repository
Manager** is a searchable catalogue with category and status filters, licence
verdicts, privilege requirements, source links and the status vocabulary
`Integrated`, `Ready to Integrate`, `Reference Only`, `Requires Shizuku`,
`Requires Root`, `Incompatible`, `Unsupported`. `RepositoryInventoryTest`
asserts the compliance rule over the whole inventory, so a new entry cannot
quietly claim to be integrated while carrying a copyleft, proprietary or
unverified licence.

### 4.1 Audited in this pass and refused

Every fact below was read from the GitHub API on 2026-10-25; none of these
projects contributed any code, table, preset, impulse response or resource.

| Project | Licence | Reason |
| --- | --- | --- |
| `tiann/KernelSU`, `bmax121/APatch`, `topjohnwu/Magisk`, `RikkaApps/Sui` | GPL-3.0 | Copyleft **and** root-only. No module, script or binary is executed or bundled. |
| `MuntashirAkon/AppManager`, `zhanghai/MaterialFiles`, `VREMSoftwareDevelopment/WiFiAnalyzer`, `Exodus-Privacy/exodus-android-app` | GPL-3.0 (-or-later) | Copyleft. SonicCore's App Manager, storage and network cards are its own implementations on public APIs. |
| `bearinmindcat/Equalizer314`, `HEnquist/camilladsp`, `breakfastquay/rubberband`, `werman/noise-suppression-for-voice` | GPL-3.0 / GPL-2.0 | Copyleft. Audited for capability comparison only. |
| `chirlu/soxr` | LGPL-2.1-or-later | An APK cannot guarantee LGPL's relinking conditions, so it is classed as not bundleable. |
| `superpoweredSDK/…Interactive-Audio-Platform` | proprietary | Superpowered's Master License Agreement; the repository publishes no licence file. |
| `pittvandewitt/Wavelet`, `james34602/JamesDSPManager`, `seedvault-app/seedvault`, `google/fonts` | none detected | No licence file at the repository root (or only per-family files), so nothing may be taken until that is resolved. |
| `topjohnwu/libsu` | Apache-2.0 | Compatible licence, but root-only: SonicCore still ships no root code path, so it is recorded as `Requires Root` rather than added. |
| `LawnchairLauncher/lawnchair` (Apache-2.0), `NeoApplications/Neo-Launcher`, `MM2-0/Kvaesitso`, `FossifyOrg/Launcher` (GPL-3.0) | mixed | A home app cannot be embedded in another app; Android requires the user to choose one. |

### 4.2 Licence-compatible and queued

`jaakkopasanen/AutoEq` (MIT), `xiph/speexdsp` (BSD-3-Clause), `jiixyj/libebur128`
(MIT), `google/oboe` (Apache-2.0), `androidx/media` (Apache-2.0),
`chrisbanes/haze` (Apache-2.0), `material-foundation/material-color-utilities`
(Apache-2.0), `google/material-design-icons` (Apache-2.0),
`patrykandpatrick/vico` (Apache-2.0) and `coil-kt/coil` (Apache-2.0) are audited
and marked `Ready to Integrate`. They are **not implemented**; the batches and
their prerequisites are in [repository-inventory.md](repository-inventory.md).

### 4.3 Correction found by this audit

Settings → About & Licenses listed the Shizuku client as `Apache License 2.0`. It
is **MIT** (the repository `LICENSE` and the published Maven POM both say so, as
recorded in section 1). The About screen now states the correct licence.

## 5. Re-audit procedure

```bash
docker compose -f docker-compose.base44.yml exec -T app sh -c \
  'cd /src && python3 tools/license_check.py'
```

The script reads the resolved dependency metadata out of the Gradle cache, so it
reports what the build actually uses rather than what the manifest claims.
Re-run it after any dependency change; a copyleft licence on a shipped
configuration fails the check.
