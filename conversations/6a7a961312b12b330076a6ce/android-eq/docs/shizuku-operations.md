# Shizuku operations — what SonicCore actually does

SonicCore integrates the **official** Shizuku client
(`dev.rikka.shizuku:api` + `dev.rikka.shizuku:provider`, version `13.1.5`, MIT,
[RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)). This document is
the contract for that integration: which operations exist, who can run them, what is
gated, and what has and has not been validated.

## Dependency and binder lifecycle

| Piece | Where | Purpose |
| --- | --- | --- |
| `dev.rikka.shizuku:api:13.1.5` | `app/build.gradle.kts` | Shizuku client: binder state, permission request, listeners |
| `dev.rikka.shizuku:provider:13.1.5` | `app/build.gradle.kts` | ContentProvider Shizuku uses to hand this app its binder |
| `rikka.shizuku.ShizukuProvider` | `AndroidManifest.xml` | Declared as `${applicationId}.shizuku`, guarded by `INTERACT_ACROSS_USERS_FULL` exactly as the upstream docs require. Without it the binder is never received. |
| `<queries>` for `moe.shizuku.privileged.api` / `moe.shizuku.manager` | `AndroidManifest.xml` | Package visibility so install detection works on Android 11+ |

`ShizukuBridge` owns the lifecycle and never throws into the UI:

1. **Install detection** — `PackageManager.getPackageInfo` for the manager package.
2. **Service liveness** — `Shizuku.pingBinder()`.
3. **Client protocol** — `Shizuku.isPreV11()`; a pre-v11 server cannot serve this
   client, so it is reported as `UNSUPPORTED` rather than as "not authorized".
4. **Authorization** — `Shizuku.checkSelfPermission()`; `Shizuku.requestPermission(code)`
   is only dispatched from the AUTHORIZE button, and the result arrives through
   `Shizuku.addRequestPermissionResultListener`.
5. **Connection events** — `addBinderReceivedListenerSticky` / `addBinderDeadListener`
   keep the screen honest while Shizuku is started, restarted or killed. A binder
   that disappears after being seen is reported as `DISCONNECTED`, not as
   "service never started".

Every elevated call goes through `ShizukuInvoker`: a daemon worker, a hard timeout
(2 s default), and a classified failure. A blocked or timed-out call can never be
reported as a success.

## Connection states

| State | Meaning | Typical next step |
| --- | --- | --- |
| `NOT_INSTALLED` | Shizuku Manager is not installed | Install it (SonicCore cannot) |
| `NOT_RUNNING` | Installed, binder never seen in this process | Start the service over adb / wireless debugging |
| `PERMISSION_REQUIRED` | Running, SonicCore not authorized (or denied permanently) | Tap AUTHORIZE, or allow it in Shizuku Manager |
| `CONNECTED` | Running, authorized, binder alive | Elevated operations become eligible |
| `DISCONNECTED` | Was connected, binder is gone | Start the service again, refresh |
| `UNSUPPORTED` | Platform too old, or a pre-v11 Shizuku server | Update Shizuku / not available on this device |

## Structured results

Every operation reports exactly one of:

- **AVAILABLE** — prerequisites met *and* a real implementation exists. Only these
  are runnable.
- **BLOCKED** — a prerequisite is missing right now (see the blocker table below).
- **UNSUPPORTED** — the Android API level, Shizuku server version or device cannot
  provide it. The user cannot fix this by authorizing.
- **NOT_IMPLEMENTED** — SonicCore ships no implementation. A stub is never reported
  as a successful operation.

Blockers, in gating order: `API_LEVEL`, `NOT_INSTALLED`, `BINDER_DISCONNECTED`,
`SERVICE_STOPPED`, `PERMISSION_REQUIRED` / `PERMISSION_DENIED`, `SERVER_VERSION`,
`DEVICE_UNSUPPORTED`.

## Operation registry

`access` follows this table; `read-only` means nothing on the device can change.

| Operation | Access | Read-only | Status when connected | Notes |
| --- | --- | --- | --- | --- |
| Connection & permission state | Local | yes | AVAILABLE | Pure client state |
| Open Shizuku Manager | Local | yes | AVAILABLE | Sends the manager's launch intent |
| **Shizuku IPC diagnostics** | Shizuku | yes | **AVAILABLE** | Binder ping + round-trip, server uid/privilege, API version, patch, SELinux context, authorization |
| Read restricted audio policy | Shizuku | yes | NOT_IMPLEMENTED | Needs hidden `IAudioService` AIDL stubs **and** a hidden-API bypass; neither is bundled |
| Force output route | Not possible | no | NOT_IMPLEMENTED | Changing the global route needs a platform-signed permission; shell uid cannot |
| Read secure settings | Shizuku | yes | NOT_IMPLEMENTED | Needs a Shizuku *user service* (or hidden-API access) to read as shell |
| Change secure settings | Shizuku | no | NOT_IMPLEMENTED | Refused by policy: SonicCore never writes secure settings or system policy |
| Grant app permissions | ADB / shell | no | NOT_IMPLEMENTED | Refused by policy: `pm grant` on other packages is a high-risk capability |
| Read kernel audio debug nodes | Root | yes | NOT_IMPLEMENTED | Needs uid 0 **and** a Shizuku user service to read outside the app sandbox |
| Binder service enumeration | Shizuku | yes | NOT_IMPLEMENTED | Enumerating `ServiceManager` needs a user service; none is bundled |

### Access tiers

- **Local** — runs in SonicCore, no elevated identity.
- **Shizuku** — needs the Shizuku service running and SonicCore authorized. Shizuku
  started over `adb` runs as **uid 2000 (shell)**; started as root (or Sui on Magisk)
  it runs as **uid 0 (root)**. The diagnostic reports which one is active.
- **ADB / shell** — works from a shell identity without root (`pm grant`, `settings put`).
- **Not possible** — no third-party app can do it on a stock device, rootless or not.

### Why the "not implemented" entries are not implemented

`newProcess` (spawning a shell) is **private** in the v13 client, `SystemServiceHelper`'s
transaction-code helpers are deprecated and depend on hidden classes, and hidden-API
reflection is blocked for ordinary apps. The documented remaining path is a **Shizuku
user service** (AIDL + a privileged process), which is a much larger surface and is
deliberately out of scope here. Anything that would write settings, grant permissions
or reroute audio is refused by policy regardless of technique — those actions need a
separately reviewed implementation, an explicit user action and supported platform
access, none of which is in place.

## Error mapping

| Failure | Reported outcome | Reason shown |
| --- | --- | --- |
| `DeadObjectException` / `DeadSystemException` | BLOCKED | Connection lost — start the service again |
| `TimeoutException` | BLOCKED | No answer within the bound |
| `SecurityException`, RemoteException mentioning permission | BLOCKED | Authorize SonicCore in Shizuku Manager |
| `RemoteException` | BLOCKED | Service rejected the call, nothing changed |
| `IllegalStateException` ("binder haven't been received") | BLOCKED | Binder not ready |
| `InterruptedException` | BLOCKED | Call interrupted, nothing changed |
| `UnsupportedOperationException`, `NoSuchMethodError` | UNSUPPORTED | Server does not implement the call |
| Anything else | BLOCKED | Unrecognised failure, nothing changed |

The mapping table is asserted by `ShizukuFailureMappingTest`; no entry can map to
AVAILABLE.

## Tests

| Suite | Covers |
| --- | --- |
| `ShizukuOperationEngineTest` | Eligibility matrix: every state × every operation, API gate, server-version gate, unknown version, read-only policy, access tiers |
| `ShizukuFailureMappingTest` | Error classification and reasons |
| `ShizukuInvokerTest` | Timeout, classification, non-positive timeout rejection, "a stuck call does not wedge the next" |
| `ShizukuStateIntegrationTest` | The five states (absent, stopped, unauthorized, authorized, disconnected) end to end through registry + engine + diagnostics gate |
| `ShizukuOperationsTest` | Existing registry contract, now including the four-state outcome |

## Device validation status

- **Validated in this repository (JVM):** the whole eligibility/error state machine,
  the timeout behaviour, and that a blocked environment never reaches the binder.
- **Not validated on a physical device in this environment:** a real binder hand-off,
  the authorization dialog, a live binder death, and the on-screen rendering of the
  Shizuku Center. No device was attached to the build sandbox, so none of this is
  claimed.

To validate on a device:

1. Install the debug APK, install Shizuku, start it (adb or wireless debugging).
2. Open Settings ▸ Shizuku Center: expect `PERMISSION_REQUIRED`, then AUTHORIZE and
   expect the state to switch to `CONNECTED` without a manual refresh.
3. Tap RUN DIAGNOSTICS: expect a populated read-only result and a uid of `2000`
   (adb) or `0` (root).
4. Kill the Shizuku service (`adb shell am force-stop moe.shizuku.privileged.api`):
   expect `DISCONNECTED`, every elevated operation BLOCKED, and no crash.
5. In Shizuku Manager, revoke SonicCore: expect `PERMISSION_REQUIRED` and the
   AUTHORIZE button to return.
