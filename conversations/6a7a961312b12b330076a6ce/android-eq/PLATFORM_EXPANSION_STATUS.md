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

## Shizuku Center — Partial (detection real, execution not wired)

- Install / running / authorized state: **Implemented** (`ShizukuBridge`, real
  package, provider and permission checks).
- Setup guidance (4 steps, per-state done/blocked): **Implemented**.
- Operation registry (7 operations, each with the first blocking reason):
  **Implemented**.
- Open Shizuku Manager: **Implemented**.
- Privileged operations (audio policy read, route force, secure settings,
  permission grant, binder diagnostics): **Not implemented** — SonicCore bundles
  no Shizuku IPC client. They show `NOT_IMPLEMENTED` even when connected.

## Customization Center — Implemented (app-level), Unavailable (system-level)

- Appearance mode (system/light/dark), 8 themes with preview + apply, liquid
  glass intensity: **Implemented**, persisted through the existing UI prefs.
- Quick Settings tile, home-screen widget, notification controls:
  **Implemented** (pre-existing services).
- Launcher icon packs, system-wide accent color, wallpaper color extraction,
  force-dark for other apps: **Unavailable** / **Not implemented** — Android
  does not permit them for third-party apps; each row says so.

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

16 destinations in the scrollable dock, including the seven requested centers:
Dashboard, EQ (audio), Device Tools, Shizuku, Customization, Permissions,
Diagnostics and the Settings hub. Asserted by `SonicCoreNavigationTest`.

## Verification commands

```bash
docker compose -f docker-compose.base44.yml restart app
tail -3 build-output/dsp-test.log     # HARNESS PASS (0 failures)
tail -3 build-output/build.log        # BUILD SUCCESSFUL
curl -fsSI http://localhost:3000/NeonEQ-debug.apk
```
