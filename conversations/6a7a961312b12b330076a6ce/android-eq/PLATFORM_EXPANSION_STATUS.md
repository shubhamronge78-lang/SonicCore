# SonicCore — Platform Expansion Status

Status of the universal audio / device-tools / customization / Shizuku platform
work, measured against the running build. Every entry states what actually
exists, so nothing here is a decorative claim.

Legend: **Implemented** = functional and covered by code paths that run on the
build. **Partial** = functional but limited by an Android/OEM restriction.
**Unavailable** = the platform does not offer the API. **Not implemented** =
SonicCore does not do it (yet) and the UI says so.

## Audio & DSP Studio — Implemented (pre-existing, untouched)

| Item | Status |
| --- | --- |
| Native C++ DSP (`neondsp.cpp`), limiter/compressor telemetry | Implemented — `docker compose` harness: `HARNESS PASS (0 failures)` |
| `EqualizerEngine`, graphic + parametric EQ, presets, A/B, sessions | Implemented |
| Capture pipeline (`CaptureEqService`, `AudioCapabilityManager`) | Implemented |
| Signal path diagnostics, meters, analyzer | Implemented |
| Regression tests for DSP/EQ contracts | Implemented — included in the 120-test run |

The platform expansion added no changes to any file under `dsp/`, `engine/`,
`capture/` or `cpp/`, and the DSP host self-test still passes.

## Device Dashboard — Implemented (diagnostics screen)

CPU cores/ABI/frequency, RAM + JVM heap, storage (data partition, free, app
cache), battery (level, source, health, temperature, saver), thermal status,
display (resolution, density, refresh), network (transport, metering, link
estimate, VPN), sensors (7 tracked types), audio service + DSP health.

- **Partial**: CPU per-core frequency only if the kernel exposes
  `cpuinfo_max_freq`; otherwise the UI prints "Not readable".
- **Partial**: storage reports the app-visible data partition/quota, not the
  physical disk.
- **Unavailable**: per-app CPU/memory breakdown — restricted from third-party
  apps on Android 8+.

## Shizuku Center — Implemented (official client + read-only diagnostics)

Full detail and the device-validation checklist: `docs/shizuku-operations.md`.

- Official client integrated (`dev.rikka.shizuku:api` + `:provider` 13.1.5) with
  the `ShizukuProvider` manifest entry: **Implemented**.
- Install / running / authorized state, binder received + dead listeners, and the
  in-app AUTHORIZE request (`Shizuku.requestPermission`) with its result callback:
  **Implemented**. A binder that dies after being seen reports `DISCONNECTED`.
- Setup guidance (4 steps, per-state done/blocked): **Implemented**.
- Operation registry (10 operations) reporting `AVAILABLE` / `BLOCKED` /
  `UNSUPPORTED` / `NOT_IMPLEMENTED` with the first blocker: **Implemented**.
- Read-only IPC diagnostics (binder ping + round-trip, server uid/privilege, API
  version, patch, SELinux context, authorization) behind a 2 s bounded call:
  **Implemented**, and the only elevated operation that runs.
- Audio policy read, route force, secure settings (read/write), permission grant,
  binder enumeration: **Not implemented** — each states what it would need
  (hidden AIDL stubs, a Shizuku user service, or a platform-signed permission),
  and the write/privilege operations are refused by policy.
- **Not validated on a physical device** in this environment; only the JVM state
  machine and the build were verified.

## Customization Center — Implemented (app-level), Unavailable (system-level)

- Appearance mode (system/light/dark), **twelve themes** with preview + apply,
  liquid glass intensity: **Implemented**, persisted through the existing UI
  prefs. The eight original ids (`classic`, `aurora`, `synthwave`, `glacier`,
  `emerald`, `ember`, `midnight`, `adaptive`) are unchanged; System Default
  applies the system appearance, Light Studio the light one, Custom Accent is
  rebuilt from a saved swatch, Dynamic Wallpaper is tinted by the wallpaper.
- **Custom Accent**: six swatches, persisted, applied live —
  **Implemented** (`ThemeAccentPreference`, pure parse/serialise, JVM-tested).
- **Dynamic Wallpaper**: `WallpaperManager.getWallpaperColors` is read behind a
  try/catch. A normal install does not hold `READ_WALLPAPER_INTERNAL`, so the
  probe reports "not readable by this app on this device" and the theme keeps
  its own palette: **Bounded** — the Customization Center shows which happened.
- **Animated splash**: first-frame overlay, skipped when the system animation
  scale is off: **Implemented**.
- Quick Settings tile, home-screen widget, notification controls:
  **Implemented** (pre-existing services).
- Launcher icon packs, system-wide accent color, force-dark for other apps:
  **Unavailable** / **Not implemented** — Android does not permit them for
  third-party apps; each row says so.

## Permissions & Privacy Center — Implemented

Live probes for runtime permissions, declared-vs-granted distinction
("Not declared" is never shown as "Denied"), battery-optimization exemption
(`PowerManager`), usage access (`AppOpsManager`), and per-session capture
consent. Privacy section states that captured audio never leaves the device and
that the operation log is session-scoped.

## Diagnostics Center — Implemented

Capability probes, device sections, bounded session operation log (100 entries,
clearable), and an exportable report shared as a text file through the existing
`FileProvider` (MAC/IP/app-path redaction preserved), plus clipboard copy.

## Navigation — Implemented

`SonicCoreCategory` groups all 16 destinations into the six categories the plan
asks for — **Audio**, **Device Center**, **Shizuku Center**, **Customization**,
**Permissions & Privacy**, **Settings**. Home shows those six and the bottom
dock — the only bottom navigation — shows the same six, each opening that
category's hub, where the tools inside it are listed; a tool never appears in the
dock. The flat 16-chip scrolling dock is gone and every destination stayed
reachable. A tool either reuses a long-standing full-screen body
(`SonicCoreTool.destination`) or has its own screen in
`ui/navigation/SonicCoreToolScreens.kt`, and `SonicCoreToolRegistry` is the single
list of them. `SonicCoreNavigationTest` asserts that contract: six categories in
order, one rendering path per tool, no two tools claiming a destination.

## Verification results (measured 2026-10-25)

```bash
docker compose -f docker-compose.base44.yml restart app
```

| Check | Command | Result |
| --- | --- | --- |
| Native DSP harness | `g++ … tools/dspharness/harness.cpp` under ASan+UBSan | `HARNESS PASS (0 failures)` |
| JVM unit tests | `./gradlew :app:testDebugUnitTest --no-daemon` | 29 suites, **200 tests, 0 failures, 0 skipped** |
| Debug APK | `./gradlew :app:assembleDebug --no-daemon` | `BUILD SUCCESSFUL` — `build-output/NeonEQ-debug.apk`, 13.6 MB |
| Dependency licences | `python3 tools/license_check.py` | **PASS** — 110 shipped / 113 test-only artifacts, no copyleft |
| Documentation links | `python3 tools/check_docs_links.py` | **PASS** — every relative link resolves (10 files) |
| Android lint | `./gradlew :app:lintDebug --no-daemon` | **BUILD FAILED: 6 errors, 68 warnings** — see below |
| APK contents | `aapt2 dump badging`, `apksigner verify` | `com.soniccore.app` 3.6.0, minSdk 26, targetSdk 34, 12 permissions as audited, `libneondsp.so` for `arm64-v8a` + `armeabi-v7a`, adaptive icon `mipmap-anydpi-v26/ic_launcher.xml`, signed with the pinned debug keystore (`CN=NeonEQ Debug`) |

### Known lint findings (pre-existing)

Lint is **not** part of CI (the workflows run the unit tests and the assembly),
and none of the six errors is in a file this platform work touches:

| Location | Issue |
| --- | --- |
| `capture/AudioCapabilityManager.kt:63` | `MissingPermission` — `AudioRecord.Builder()` |
| `capture/CaptureEqService.kt:276` | `MissingPermission` — same capture path |
| `ui/settings/SettingsToolScreens.kt:529` | `NewApi` ×2 — `PackageInfo.signingInfo` / `SigningInfo.getApkContentsSigners` on API 26 |
| `shizuku/ShizukuBridge.kt:161` | `RestrictedApi` — `Shizuku.getServerPatchVersion` |
| `shizuku/ShizukuDiagnostics.kt:84` | `RestrictedApi` — the same call |

The 68 warnings are dominated by the app's long API-level history
(`ObsoleteSdkInt`, `InlinedApi`), plus `IconLauncherShape`, `UnusedResources` and
`GradleDependency`. Fixing them is its own change: the two capture-path errors
must not be "fixed" by loosening code in the path this work preserves.

### Not verified here

No Android device is attached to this environment, so capture consent, output
routing, wallpaper colours, Shizuku authorization and the audible result are
**not device-verified**. `docs/feature-matrix.md` marks each of those rows.

```bash
curl -fsSI http://localhost:3000/NeonEQ-debug.apk   # 200 OK
```
