# AGENTS.md — running this repository

## What this repository actually contains

This is a **Base44 agent workspace**, not a web application. It holds:

- `.agents/` — the agent's platform docs, memory and identity.
- `conversations/6a7a961312b12b330076a6ce/android-eq/` — the only real project:
  **Neon EQ / SonicCore**, an Android app (Kotlin + Jetpack Compose + native C++
  DSP; `versionName 3.6.0`, minSdk 26, targetSdk 34, applicationId `com.neon.eq`).
- `.github/workflows/` — the app's CI: `build.yml`, `android-apk.yml`, `release.yml`.
- `.base44/` — this sandbox setup: the builder image and the served status page.

**There is no web frontend, no HTTP server and no database anywhere in this
repo.** An Android app has no browser surface, so it can never render inside the
browser preview. Do not go looking for a web app, and do not invent one.

The app itself now has two layers:

- **Audio**: native C++ DSP (`app/src/main/cpp/neondsp.cpp`), `EqualizerEngine`,
  the capture pipeline, presets. Untouched by the platform expansion.
- **Platform centers**: `system/` (device probes), `permissions/` (permission
  model), `shizuku/` (bridge + operation registry), `diagnostics/` (device
  health, operation log, report export), `ui/customization/`,
  `ui/settings/`. The Compose navigation lives in
  `ui/navigation/SonicCoreNavigation.kt`; screens are dispatched from the single
  large `EqualizerScreen` composable in `MainActivity.kt`.

Every platform center is reachable from the bottom dock
(`SonicCoreDestination.consoleDestinations`), and
`SonicCoreNavigationTest` asserts that list exactly — adding a destination
requires updating that test.

## How the app is run / verified here

`docker-compose.base44.yml` builds the app **from the cloned source** (the source
is bind-mounted at `/src`, never baked into an image) and serves the results on
host port 3000, because that is the only port the preview can show.

Everything lives in **one long-running service, `app`**:

0. `python3 -m http.server` on port 80 starts **first**, serving `./build-output`
   (host port 3000) with the status page. Port 3000 answers within seconds,
   before any building happens — the status page probes for the APK over HTTP and
   reports "build still running" until it exists.
1. Native DSP host self-test — compiles the **real** production DSP
   (`tools/dspharness/harness.cpp` `#include`s `neondsp.cpp`) with ASan+UBSan and
   runs its assertions. Takes seconds and needs no Android SDK.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug` — the JVM unit tests
   (120 tests as of the platform expansion) plus the debug APK, built from the
   mounted source.
3. The APK and the test report are copied into `./build-output`, so the page
   switches to "APK ready to install".

```bash
docker compose -f docker-compose.base44.yml up -d --build   # first run / after compose or image changes
docker compose -f docker-compose.base44.yml ps              # wait for "healthy"
docker compose -f docker-compose.base44.yml logs -f app     # harness + Gradle output
```

- **Why one service and not three.** The DSP test and the Gradle build are
  one-shot jobs. A compose service that exits — *even with exit code 0* — is
  reported as a failed application service by the platform's health monitoring,
  so they must not be declared as their own services. The single `app` service
  runs them to completion and then stays up serving, with a healthcheck on the
  served page.
- **Why the HTTP server starts before the build.** The from-source build takes
  minutes on a cold cache. If the port stayed silent until it finished, the
  preview and the platform's start probe would see a dead port and report the app
  as failed to start.
- **A failed build must not look healthy.** Each run deletes the previous APK,
  logs and test report first, and `bash -ec` aborts on any failure — the trap then
  kills the server and the container exits non-zero, so `restart: on-failure`
  retries instead of serving a stale "ready" page. `build-output/build.log` holds
  the error.
- **Re-running the checks after editing source.** The source is bind-mounted, so
  `docker compose -f docker-compose.base44.yml restart app` re-runs the harness,
  the tests and the APK build against the current code. `up -d --build` is only
  for changes to `docker-compose.base44.yml` or `.base44/android/Dockerfile`.
  Never run two Gradle invocations at once against the shared `gradle-home`
  volume.

## How to check it works

```bash
curl -fsS http://localhost:3000/                     # status page
curl -fsSI http://localhost:3000/NeonEQ-debug.apk    # APK present
ls build-output/                                     # NeonEQ-debug.apk, build.log, dsp-test.log, tests/
tail -5 build-output/dsp-test.log                    # must end with "HARNESS PASS (0 failures)"
tail -5 build-output/build.log                       # must end with "BUILD SUCCESSFUL"
```

`build-output/` is generated and gitignored.

## Sandbox-only overrides

There are **none in the application code**. The Android project, its
`gradle.properties` and `app/build.gradle.kts` are byte-for-byte the repository's
own and behave identically outside the sandbox. Specifically:

- `BASE44_PREVIEW_MODE` is **not** read by anything here and is not passed into
  the stack: the preview is a plain static page served by `python3 -m
  http.server`, so there is no dev server, no host/origin allowlist and no HMR to
  gate. Nothing changes when the flag is set to `1` or unset.
- The only sandbox-specific files are the ones Base44 owns:
  `docker-compose.base44.yml`, `.base44/android/Dockerfile`,
  `.base44/preview/index.html`, `.base44/environment.json` and this file.

## Non-obvious quirks worth knowing

- **Shizuku is detection-only on purpose.** `ShizukuBridge` really checks
  install/running/permission state, but no privileged call is executed:
  SonicCore bundles no third-party Shizuku IPC client. `ShizukuOperations`
  therefore resolves every privileged operation to an explicit
  `NOT_IMPLEMENTED` (or the earlier blocking step) and only the state probe and
  "open Shizuku Manager" ever report `AVAILABLE`. That is a deliberate honest
  status, **not** a bug to "fix" by making operations look available.
- **Platform logic is split pure/Android so it can be unit-tested.** Anything
  needing a device lives behind a `probe(context)` function; the decision logic
  is a pure `evaluate(plainData)` that JVM tests call directly
  (`PermissionCenter.evaluate`, `ShizukuOperations.evaluate`,
  `DeviceCapabilityProbe` formatters). There is no Robolectric in this project —
  do not add Android framework calls to the `evaluate` paths or the tests break
  with "not mocked" errors.
- `ACCESS_NETWORK_STATE` was added for the device dashboard's network card. It
  is a normal permission (granted at install), so it never shows a runtime
  prompt.
- `gradle.properties` carries the original author's
  `android.cmake.path = /opt/soniccore-tools/cmake-3.22.6`. That directory does
  not exist here — and it does not need to: `android.cmake.path` is not an AGP
  property (AGP's is `cmake.dir` in `local.properties`), so it is ignored and AGP
  uses the SDK-managed CMake installed in the image / on the runner.
  `android-apk.yml` deletes the line before building; that `sed` is harmless and
  not required.
- `app/build.gradle.kts` pins the debug signing keystore (`app/debug.keystore.p12`,
  password `android`) so every CI build is signed identically and installs
  update cleanly instead of Android refusing the update.
- `versionCode` comes from `GITHUB_RUN_NUMBER` when set (1 locally/elsewhere).
- The three `*.jpg` files under `app/src/main/res/drawable-nodpi/` are real
  binaries in git: `.gitattributes` routes most binary types through LFS but
  `.base44`-unrelated rule `.../android-eq/app/src/main/res/** -filter` exempts
  the resource tree, so AAPT can compile them.
- The CI workflow `android-apk.yml` used to fail deterministically on `main`
  after ~21 s, at the SDK step, before Gradle ever ran. The step passed
  `api-level`, `build-tools` and `ndk` to `android-actions/setup-android@v3`,
  but v3 declares only `cmdline-tools-version`, `accept-android-sdk-licenses`,
  `log-accepted-android-sdk-licenses` and `packages`; unknown inputs are
  ignored, so the action fell back to its default
  `packages: "tools platform-tools"` and ran `sdkmanager tools`. The `tools`
  package no longer exists in the runner's cmdline-tools 16 and the action
  treats that as fatal (`Warning: Failed to find package 'tools'` →
  `Error: The process '.../sdkmanager' failed with exit code 1`), which is what
  broke runs 40070746189 / 40071309858 / 40072560294. The action step now asks
  only for `platform-tools` and accepts the licences non-interactively, and a
  second step installs `platforms;android-34`, `build-tools;34.0.0`,
  `ndk;25.1.8937393` and `cmake;3.22.1` with `sdkmanager --install` itself, so
  the install command, its output and its exit code are visible in the log.
  `build.yml` never used that action and always passed.
- A workflow fix is only live on the branch that triggers it. The fix lived on
  `fix-main-482fc2707b92` while `main` still had the broken YAML, and GitHub
  takes the workflow file from the ref that runs — so re-dispatching the
  workflow on `main` re-ran the old file and failed exactly the same way
  (run 40072560294). Reach `main` (merge) before expecting a green run.
- `android-apk.yml` only triggers on pushes to `main` and on manual dispatch,
  so pushing a feature branch produces **no** run. Validate a workflow change
  here instead: parse it (`python3` + pyyaml), then run the same Gradle goals
  the workflow runs (see "How to check it works"). Live run logs can be read
  from this sandbox (`gh run list`, `gh run view <id> --log-failed`), which is
  how the root cause above was confirmed.
- The native DSP build needs NDK 25.1.8937393 and CMake 3.22.1 (AGP 8.2.2
  defaults); both come from the SDK, not from the host.
