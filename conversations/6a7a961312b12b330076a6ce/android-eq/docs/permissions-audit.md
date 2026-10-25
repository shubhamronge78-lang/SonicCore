# Permissions & privacy audit

Audit date: **2026-10-25**. Method: compare the app's own manifest with the
**merged** manifest AGP actually builds, then check that each entry has a real
code path that needs it.

Reproduce:

```bash
docker compose -f docker-compose.base44.yml exec -T app sh -c \
  'cd /src && grep -o "uses-permission android:name=\"[^\"]*\"" \
     app/build/intermediates/merged_manifest/debug/AndroidManifest.xml | sort -u'
```

## 1. Every permission in the merged manifest

12 entries — 11 declared by this project, 1 merged in from AndroidX.

| Permission | Protection | Why it is required | Where it is used |
| --- | --- | --- | --- |
| `MODIFY_AUDIO_SETTINGS` | normal (no prompt) | Audio effect and route work through `AudioManager`/`AudioEffect`. | `EqualizerEngine`, `DeviceToolsManager` audio-focus actions |
| `RECORD_AUDIO` | **dangerous** (runtime prompt) | `AudioRecord` cannot open the capture session without it. | playback-capture pipeline, `CapabilityRegistry` probe |
| `FOREGROUND_SERVICE` | normal | The engine runs in a foreground service. | `EQService`, `CaptureEqService` |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | normal | Required on API 34 for a `mediaPlayback` service type. | `EQService` |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | normal | Required on API 34 for a `mediaProjection` service type. | `CaptureEqService` |
| `POST_NOTIFICATIONS` | **dangerous** (API 33+, runtime prompt) | The foreground service notification must be postable. | `MainActivity` runtime request, `CapabilityRegistry` |
| `RECEIVE_BOOT_COMPLETED` | normal | Optional "start on boot" resume of the engine. | `engine/BootReceiver` |
| `WAKE_LOCK` | normal | Keeps the audio session alive with the screen off. | `CaptureEqService`, `SoftwareEq` (`PARTIAL_WAKE_LOCK`) |
| `BLUETOOTH_CONNECT` | **dangerous** (API 31+, runtime prompt, optional) | Reads Bluetooth output device names for route reporting. Denying it only hides names. | `CapabilityRegistry`, `PermissionCenterModel` |
| `ACCESS_NETWORK_STATE` | normal | Read-only network state for the device dashboard's network card. | `DeviceCapabilityProbe` (`ConnectivityManager`) |
| `moe.shizuku.manager.permission.API_V23` | signature, held by the Shizuku manager | Required by the Shizuku API for an app to talk to the manager. Grants nothing on its own and only applies when the user installed and authorized Shizuku. | `shizuku/ShizukuBridge` |
| `com.soniccore.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature (app-local) | **Merged from `androidx.core:core:1.12.0`**, not declared here. Keeps dynamically registered receivers non-exported. | AndroidX internals |

`android:protectionLevel` for the merged entry is confirmed as `signature` in
`app/build/outputs/logs/manifest-merger-debug-report.txt`.

## 2. Deliberately absent

These are the permissions a "complete toolkit" app is most tempted to request.
None of them is in the merged manifest:

| Not requested | Consequence (and why that is the right call here) |
| --- | --- |
| `INTERNET` | Captured audio cannot leave the device. There is no analytics, no upload and no remote config in this app — the manifest makes that structural, not a promise. |
| `QUERY_ALL_PACKAGES` | SonicCore sees only apps that expose a launcher activity, through the `<queries>` declaration. No full package enumeration. |
| `MANAGE_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` | Sessions, presets and reports are written to the app's own storage and shared explicitly through `FileProvider` + `ACTION_SEND`, so no broad storage access is needed. |
| `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS` | Overlay windows and usage stats are not part of any feature; the Permissions Center reports usage access as not granted without ever asking for it. |
| Location, contacts, camera, microphone-for-recording, `READ_PHONE_STATE` | No feature needs them. |
| `WRITE_SECURE_SETTINGS`, permission-grant or secure-settings writes through Shizuku | Refused by design; those operations are listed as `NOT_IMPLEMENTED` rather than enabled. See `docs/shizuku-operations.md`. |

## 3. Runtime prompts and denial handling

Only three permissions can ever show a system prompt: `RECORD_AUDIO`,
`POST_NOTIFICATIONS` (API 33+) and `BLUETOOTH_CONNECT` (API 31+, optional).
They are requested once, together, and a denial is never fatal:

- `permissions/PermissionCenterModel.kt` distinguishes **declared** from
  **granted**, so a permission the app does not declare is reported as
  `NOT_DECLARED` and never as "denied" — no fake toggle is shown.
- `capabilities/CapabilityRegistry.kt` re-reads the live state and reports
  capture as blocked with the reason (`"RECORD_AUDIO permission is required"`)
  instead of pretending to work.
- The Permissions & Privacy Center offers a real recovery path per permission
  (app details, notification settings, Bluetooth settings, battery-optimisation
  and usage-access screens) and states that a denied permission only disables
  the feature that needs it.
- The capture consent itself is a `MediaProjection` token granted per session
  by the user in the system dialog, separate from any manifest permission.

## 4. What this audit changed

Nothing had to be removed: every declared permission already has a live code
path, and no permission is declared "for later". The one merged entry
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) is app-local and signature-level,
so it cannot be used by any other app.
