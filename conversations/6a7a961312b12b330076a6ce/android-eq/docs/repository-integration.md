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
| [`jaakkopasanen/AutoEq`](https://github.com/jaakkopasanen/AutoEq) | **MIT** (repository `LICENSE`) | **Its profile *file formats* only** — see section 1.1. `engine/AutoEqProfile.kt` reads a pasted `ParametricEQ.txt` / `GraphicEQ.txt`, evaluates the filters at SonicCore's 31 band centres and hands the result to the existing equalizer as a custom preset, with the profile's `Preamp:` line applied to the native preamp parameter. No AutoEQ source, measurement data or target curve is copied, vendored or redistributed, so nothing of AutoEQ ships inside the APK. |
| [`jiixyj/libebur128`](https://github.com/jiixyj/libebur128) — v1.2.6 @ `67b33ab` | **MIT** (repo `COPYING`) | **Vendored source**, the one copied tree in this repository: `app/src/main/cpp/third_party/ebur128` (`ebur128.c` + `ebur128.h` + the licence), compiled into `libloudness.so` for the offline loudness measurement — see section 1.2. Unmodified, checksummed and attributable; never linked into the playback DSP. |
| `junit:junit` 4.13.2 | **EPL-1.0** | JVM unit tests only (`testImplementation`): never packaged into the APK. |
| `org.json:json` 20240303 | **Public Domain** (as declared in the published POM) | Test-scope implementation of `org.json` so JSON contracts can be tested on the JVM; the shipped app uses Android's platform JSON. |
| `com.google.guava:listenablefuture` 1.0, `org.hamcrest:hamcrest-core` 1.3 | **Apache-2.0** and **BSD-3-Clause**; the published POMs of these two carry **no licence metadata**, which is why `tools/license_check.py` prints `unknown` for them. The licences above come from their Maven Central / upstream project metadata. | Pulled in transitively (AndroidX `concurrent-futures`, JUnit). Both permissive; no action needed. |

`app/src/main` contains the project's own Kotlin and C++
(`app/src/main/cpp/neondsp.cpp`), plus **one** vendored third-party source tree:
`app/src/main/cpp/third_party/ebur128` (libebur128 v1.2.6, MIT — provenance,
pinned commit and checksum in that directory's `README.md`). It is the only copied
source in the project, it carries its upstream licence alongside it, and it is
compiled into its own shared library (`libloudness.so`) rather than into the DSP
engine. Nothing else in `app/src/main` comes from anywhere but this project.

### 1.1 AutoEQ profile import — the one integration built in this batch

Implemented end-to-end, and its boundary is stated in the app itself:

| | |
| --- | --- |
| **Reads** | The two text formats AutoEQ publishes for a model: `ParametricEQ.txt` (`Preamp:` plus `Filter n: ON <PK/LSC/HSC> Fc .. Hz Gain .. dB Q ..`) and `GraphicEQ.txt` (one `GraphicEQ:` line of `frequency gain;` points). A profile reaches the app by paste or by opening its `.txt` through the system file picker. Disabled (`OFF`) slots are skipped; an enabled low/high-pass filter is **refused with its index and type named**, never dropped or silently approximated; the text is size-limited to 128 KB (the picker reads no more than that either); filter gains are normalised into ±40 dB and Q into 0.1–10 **with the adjustment reported**; and a name is required because AutoEQ's text carries none (blank falls back to "AutoEQ profile" and says so). |
| **Writes** | A **custom preset** on the engine's existing 31-slot curve (`engine.saveCustomPreset`, the same model `Presets.CustomPreset` uses) and the profile's preamp through the app's existing native preamp parameter (`DspParams.preamp`). Both are paths the app already used; no second equalizer exists and no band is applied twice. |
| **Review, then decide** | Import is two steps. The **review** (`AutoEqImport.review`) parses and reports only — the formats, the filter/point count, the preamp, the band range, the stored name and every clamp it applied — and touches nothing, so cancelling cannot disturb the active sound. The **apply** (`AutoEqImport.apply`) is deliberate: "Save profile" stores the curve and changes nothing else; "Apply now" additionally makes it the active curve and writes the preamp **once**. The screen states what is replaced (a custom profile of the same name, the active curve, the preamp) and what is kept (every other profile, and bass/virtualizer/loudness). |
| **Failure behaviour** | A refused profile reports its reason and applies nothing. If storing fails, nothing is applied and the existing profiles are untouched; if activation fails, the profile is reported as saved but not applied and the preamp is not written (`AutoEqImportTest` drives each of these through the real code path). A profile with no `Preamp:` line leaves the user's preamp alone. |
| **Where it applies** | Wherever the engine already processes — SonicCore's own player, captured playback and any supported system route. It is not an effect outside the app. |
| **Not taken** | No AutoEQ source code, measurement database, target curve, compensation value or generated profile file is copied or bundled. Only the documented file format is parsed, which is what keeps an MIT data repository's *data* out of this repository. |
| **Approximation, stated** | A parametric profile is evaluated as a sum of bell/transition curves in log-frequency (a peaking filter returns to zero on both sides, a shelf holds its gain on its own side), a graphic profile is interpolated in log-frequency, and both are clamped to ±15 dB before they are stored. The result is an approximation at SonicCore's band centres, not AutoEQ's own biquad response — the import status line says so, and so does this file. |
| **Coverage** | `engine/AutoEqProfileTest` (29 JVM tests) and `engine/AutoEqImportTest` (27) — 56 in total: both formats, the OFF slot, every refusal with its reason (unsupported type, unreadable value, unusable frequency, disabled-only profile, non-profile text, oversize paste, too few points, too many filters/points), the name sanitiser (including path separators, quotes, control characters and the length cap), log-frequency interpolation, the reported ±40 dB / Q normalisation, the reported ±15 dB curve clamp and ±30 dB preamp clamp, boundary input sizes (128 KB, 64 filters, 8192 points), preamp applied exactly once, save → reload → reapply, a built-in name collision being stored under its own name, name-collision replacement surviving an export/import round-trip, and cancellation / failed store / failed activation / a preamp write that does not land each leaving the sound and the stored profiles unchanged (the last one by rolling the active profile back). |
| **Removal / replacement** | Delete `engine/AutoEqProfile.kt` and `engine/AutoEqImport.kt`, the `onReviewAutoEqProfile` / `onApplyAutoEqProfile` host callbacks and the import dialog in `ui/audio/AudioToolScreens.kt`; nothing else references them. Replacing them with a real `AutoEq`-generated filter import changes nothing else, because the output is an ordinary preset. |
| **Device check still open** | Nothing here was heard: there is no emulator or device in this environment, so the audible result of an import (and the file picker's own gesture) remain unverified on hardware. The flow logic is covered on the JVM. |

### 1.2 libebur128 — offline loudness measurement

The second integration, and the first that vendors upstream source. Its boundary
is narrower than the library's capability: it measures **one buffer, after the
fact**, and nothing in the playback path can reach it.

| | |
| --- | --- |
| **Upstream** | <https://github.com/jiixyj/libebur128> |
| **Pinned version / commit** | **v1.2.6**, commit `67b33abe1558160ed76ada1322329b0e9e058b02` (fetched 2026-10-26) |
| **Licence** | **MIT**, © 2011 Jan Kokemüller — verbatim in `app/src/main/cpp/third_party/ebur128/LICENSE`. Permissive, so bundling with attribution is allowed; the notice travels with the source. |
| **Attribution** | The licence file sits beside the source, and `app/src/main/cpp/third_party/ebur128/README.md` records upstream, version, commit, checksum, what was taken and what was not. |
| **Files changed** | *Vendored (unmodified):* `app/src/main/cpp/third_party/ebur128/ebur128.c` (sha256 `c2fc562f…b78d`), `ebur128.h`, `LICENSE`, `README.md`. *Written here:* `app/src/main/cpp/loudness/loudness_jni.cpp` (JNI facade + measurement core), `app/src/main/cpp/CMakeLists.txt` (the `loudness` target), `app/src/main/java/com/soniccore/app/loudness/LoudnessNative.kt` (the binding), `Loudness.kt` (validation, result model, report), `WavPcm.kt` (16-bit PCM WAV reader), the **Loudness (offline)** section of `ui/audio/AudioToolScreens.kt`, and `tools/dspharness/loudness_harness.cpp` (host harness, `tools/dspharness/jni.h` gained the two double-array entry points it needs). |
| **Reads** | Interleaved 16-bit PCM at 8–192 kHz, mono or stereo, from one WAV file the user picks (16-bit PCM only — float, 24-bit, ADPCM and non-RIFF files are refused *by name and format code*), or from any buffer a caller already has. |
| **Produces** | Integrated loudness in LUFS (BS.1770-4 gating, absolute −70 LUFS and relative −10 LU), loudness range in LU, true peak in dBTP (4× oversampled), plus the input format, channel count and the measured span, and a plain-language note for every limit that applied. |
| **Feature boundary** | **Offline only.** Measurement lives in its own shared library (`libloudness.so`), is called from the UI thread after a file is picked, and is never called from `neondsp.cpp`, the capture loop or any audio thread — so it cannot add latency to playback, and the DSP chain is byte-for-byte unchanged (`libneondsp.so` still exports the same 34 `Java_com_soniccore_app_dsp_NeonDsp_*` symbols). The report says, in the app's own words, that SonicCore does not measure live playback and **cannot** measure system-wide audio, because the system route supplies PCM only while a capture session is running. |
| **Not taken** | libebur128's CMake build (the single `.c` file is compiled directly), its `queue/` copy of `sys/queue.h` (the NDK sysroot provides one), its test suite, the `loudness-scanner` companion tool, and any measurement data. |
| **Tests** | `tools/dspharness/loudness_harness.cpp` (ASan+UBSan, run in the container pipeline): the calibration anchors (−23 dBFS RMS mono 1 kHz reads −23.0 LUFS, the same signal in both channels −20.0, true peak −20.0 dBTP), silence and a sub-400 ms span reporting **no** integrated loudness rather than a number, the ~5 LU range of a two-level signal, an fs/4 sine whose samples peak at −3.01 dBFS still reading ≈0 dBTP (which is what proves the peak is oversampled), every refused input, and 50 repeated measurements agreeing. `LoudnessTest` (12 JVM tests) and `WavPcmTest` (9) pin the app-side decisions: every refusal and its wording, the native status mapping, NaN becoming "not measurable", the 3-minute cap and the report's scope sentence. |
| **Limitations** | The numbers are the library's and are **not device-verified**: no device or emulator exists in this environment, so nothing about live audio has been heard or measured on hardware — a device check of one known file is still outstanding. Files longer than 3 minutes are measured only in their first 3 minutes (said in the report). Only 16-bit PCM WAV input is accepted. Loudness matching — acting on the measurement — is not implemented; this batch measures and reports. |

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

`xiph/speexdsp` (BSD-3-Clause), `jiixyj/libebur128`
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
