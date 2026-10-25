# AGENTS.md — running this repository

## What this repository actually contains

This is a **Base44 agent workspace**, not a web application. It holds:

- `.agents/` — the agent's platform docs, memory and identity.
- `conversations/6a7a961312b12b330076a6ce/android-eq/` — the only real project:
  **Neon EQ / SonicCore**, an Android app (Kotlin + Jetpack Compose + native C++
  DSP; `versionName 3.6.0`, minSdk 26, targetSdk 34, applicationId and namespace
  `com.soniccore.app`). The Kotlin tree is the historical package layout,
  mechanically renamed from `com.neon.eq` to `com.soniccore.app`, so `capture/`,
  `engine/`, `ui/category/`, `ui/navigation/` and the rest keep their shape and
  only the package prefix differs.
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
  `ui/navigation/` (`SonicCoreNavigation.kt` for the model, `SonicCoreNavStack.kt`
  for the back stack, `SonicCoreNavigator.kt` for the moves); screens are
  dispatched from the single large `EqualizerScreen` composable in
  `MainActivity.kt`.

Navigation is **six categories**, not a flat dock: `SonicCoreDestination` in
`ui/navigation/SonicCoreNavigation.kt` names every destination and
`SonicCoreCategory` groups them into Audio, Device Center, Shizuku Center,
Customization, Permissions & Privacy and Settings. The launcher starts in
`SonicCoreNavHost` (`MainActivity.kt`), which owns the navigation stack and
renders Home, a category hub, or one tool inside a category. Home shows
**exactly six categories** — Audio, Device Center, Shizuku Center,
Customization, Permissions & Privacy, Settings — and the bottom dock shows the
same six, never internal tools. A tool either reuses a long-standing
full-screen body (`SonicCoreTool.destination`, e.g. the equalizer) or has its own
screen in `ui/navigation/SonicCoreToolScreens.kt`; nothing may have both or
neither. `SonicCoreNavigationTest` asserts all of that (six categories in order,
one rendering path per tool, no two tools claiming a destination) — adding a
category or a tool requires updating that test.

Audio is the one category with a second level of its own: its thirteen tools each
carry a `SonicCoreAudioGroup` (Overview, Sound Controls, DSP Processing, Playback
& Capture, Devices & Routing, Presets & Diagnostics), which the **Audio Center**
hub renders as a chip row on phones and as a grouped list on tablets. The
screens that live in `ui/audio/` share one settings vocabulary
(`AudioSettingsComponents.kt`) and never touch the engine: they read an
`AudioControlState` snapshot from `SonicCoreToolHost` and hand a validated one
back, which `MainActivity` merges over the persisted `DspParams` (parametric
slots left alone) and applies through the same `DspParams.applyTo` path the DSP
rack uses. `AudioControlRanges.clampTo` is where NaN and infinity stop.
`SonicCoreNavigationTest` asserts the grouping and `AudioControlStateTest` the
clamping/merge contract, so a new Audio tool needs a group in the registry and a
route (own screen) or a destination.

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
   (215 tests across 30 classes: the platform expansion, the Shizuku IPC work, the
   Home/layout contracts, the twelve-theme catalogue, the splash, the Audio Center
   grouping/rendering contract and the Audio control-parameter validation) plus the
   debug APK, built from the mounted source. All 215 pass with 0 skipped.
   `:app:lintDebug` is **not** run here: it reports 6 pre-existing errors (the
   capture path, `SettingsToolScreens`' API-28 signing-info read, and the two
   Shizuku restricted-API calls), none of them in a file this work touches, so
   adding it to the pipeline would make a green build impossible.
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

Two checks the project ships that are **not** part of the compose pipeline
(each runs inside the app container, from the mounted source):

```bash
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/license_check.py'
docker compose -f docker-compose.base44.yml exec -T app sh -c 'cd /src && python3 tools/check_docs_links.py'
```

`license_check.py` reads the resolved dependency graph from Gradle and each
artifact's licence from its published POM (a handful of artifacts publish no
licence metadata and print `unknown`), and exits 1 on a copyleft licence.
`check_docs_links.py` resolves every relative Markdown link in the project.

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

- **Shizuku uses the official client, but only for read-only work.**
  `dev.rikka.shizuku:api` + `:provider` 13.1.5 (`ShizukuProvider` declared in the
  manifest as `${applicationId}.shizuku`) give `ShizukuBridge` a real binder:
  install / running / authorized state, binder received + dead listeners,
  `Shizuku.requestPermission`, and `Shizuku.getUid/getVersion/getSELinuxContext`.
  The single executed elevated operation is `ShizukuDiagnostics` (read-only IPC
  probe, 2 s bound through `ShizukuInvoker`). Everything else resolves to
  `AVAILABLE` / `BLOCKED` / `UNSUPPORTED` / `NOT_IMPLEMENTED` — never a stub
  reported as success. Do not "fix" a `NOT_IMPLEMENTED` entry by making it look
  available; `docs/shizuku-operations.md` records what each one would need.
- **`newProcess` is private in the v13 client.** Spawning a shell, and the
  deprecated `SystemServiceHelper` transaction-code helpers, are not usable from
  an app process. The documented remaining path is a Shizuku user service (AIDL +
  privileged process); it is deliberately out of scope. Nothing in SonicCore
  writes secure settings, grants permissions or reroutes audio through Shizuku.
- **`ShizukuState.DISCONNECTED` is distinct from `NOT_RUNNING`.** The bridge
  remembers that a binder was seen in this process (`hasSeenBinder`), so a service
  that dies after connecting is reported as "connection lost" instead of
  "never started". Adding a `ShizukuState` value breaks every exhaustive `when`
  over it — `CapabilityRegistry`, `DeviceToolsScreen` and `ShizukuCenterScreen`
  each handle it.
- **The theme catalogue is twelve themes and `Themes.ALL` is a getter, not a
  constant.** Eight legacy ids (`classic`, `aurora`, `synthwave`, `glacier`,
  `emerald`, `ember`, `midnight`, `adaptive`) are persisted user preferences and
  must never be renamed; `Themes.catalog(customAccent, wallpaperAccent)` adds
  System Default, Light Studio, Custom Accent and Dynamic Wallpaper. The last two
  are built from live inputs — the saved accent swatch
  (`ui/design/ThemeAccentPreference.kt`) and `WallpaperAccentProbe`, which reads
  `WallpaperManager.getWallpaperColors` behind a try/catch and reports "not
  readable" on an ordinary install. `NeonTheme.preferredMode` is what makes
  System Default follow the system and Light Studio apply the light appearance;
  legacy themes leave the mode alone.
- **The splash is an overlay, not a second activity.** `SonicCoreSplashOverlay`
  renders itself over the console in `MainActivity`, hides itself after ~1.4 s,
  swallows taps while visible, and disappears immediately when
  `Settings.Global.ANIMATOR_DURATION_SCALE` is 0. It draws no resource, so there
  is nothing to keep in sync with the launcher icon.
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
- **JNI symbol names are hard-wired to the Kotlin package.** `neondsp.cpp`
  exports `Java_com_soniccore_app_dsp_NeonDsp_<fn>` and
  `tools/dspharness/harness.cpp` calls those same C symbols directly, so
  `dsp/NeonDsp.kt` must stay in `com.soniccore.app.dsp`. Renaming the package
  requires renaming the exported symbols in **both** files together and
  rebuilding — a mismatch compiles fine and only fails at runtime, or breaks the
  DSP harness at link time. Verify after any rename:
  `llvm-nm -D --defined-only libneondsp.so | grep Java_com_soniccore_app_dsp_`
  (34 symbols, and no `Java_com_neon_eq_`).
- **The `sync/mobile-harness` lineage was never buildable — do not re-merge it
  as-is.** Commits `28aa75a`/`bd95978` (PR #16) restructured the app by deleting
  the `capabilities/`, `diagnostics/`, `shizuku/`, `ui/category/`,
  `ui/navigation/`, `ui/design/` theme and component modules and splicing
  `MainActivity.kt` together from two source versions, leaving ~1200 Kotlin
  compile errors — `bd95978` itself never compiled (`:app:compileDebugKotlin`
  failed). Everything the restructure aimed at (six categories, Shizuku Center,
  Customization, Audio/DSP, JNI) already exists in the renamed tree. Its only
  genuinely reusable output was the namespace change to `com.soniccore.app` and
  the matching JNI renames, both already applied here.
