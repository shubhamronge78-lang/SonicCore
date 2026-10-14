# AGENTS.md — running this repository

## What this repository actually contains

This is a **Base44 agent workspace**, not a web application. It holds:

- `.agents/` — the agent's platform docs, memory and identity (`IDENTITY.md`, `USER.md`, `SOUL.md`, `memory.md`).
- `conversations/<conversation-id>/android-eq/` — the only real project: **Neon EQ / SonicCore**, an Android app (Kotlin + Jetpack Compose + native C++ DSP, build 3.6.0).
- `.github/workflows/build.yml` and `release.yml` — the app's CI: unit tests, native DSP host self-test, debug APK, tagged releases.

**There is no web frontend, no HTTP server and no database anywhere in this repo.**
Do not go looking for one, and do not invent one. An Android app has no browser
surface, so it can never render inside a browser preview.

## How the app is run / verified here

`docker-compose.base44.yml` builds the app **from the cloned source** (source is
bind-mounted at `/src`, never baked into an image) and serves the results on host
port 3000, because that is the only port the preview can show.

Everything lives in **one long-running service, `app`**:

0. `python3 -m http.server` on port 80 starts **first**, serving
   `./build-output` (host port 3000) with the status page. Port 3000 answers
   within seconds, before any building happens — the status page probes for the
   APK and reports "build still running" until it exists.
1. Native DSP host self-test — compiles the **real** production DSP
   (`tools/dspharness/harness.cpp` `#include`s `neondsp.cpp`) with ASan+UBSan and
   runs its assertions. Takes seconds and needs no Android SDK.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug` — the JVM unit tests plus
   the debug APK, built from the mounted source.
3. The APK and test report are copied into `./build-output`, so the page switches
   to "APK ready to install".

```bash
docker compose -f docker-compose.base44.yml up -d --build
docker compose -f docker-compose.base44.yml ps          # wait for "healthy"
docker compose -f docker-compose.base44.yml logs -f app
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
  as failed to start. Serving the status page immediately removes that window;
  the page tells the truth about the build state in the meantime.
- **A failed build must not look healthy.** Each run deletes the previous APK,
  logs and test report first, and `bash -ec` aborts on any failure — the trap then
  kills the server and the container exits non-zero, so `restart: on-failure`
  retries instead of serving a stale "ready" page. `build.log` holds the error.
- **There is no live-reload dev server** for an Android app. After editing the
  source, re-run `up -d --build` (the Gradle cache lives in the `gradle-home`
  volume, so the rebuild is far shorter than the cold build), then reload the
  preview.
- Artifacts land in `./build-output` (gitignored): `NeonEQ-debug.apk`,
  `build.log`, `dsp-test.log`, `tests/`.

## Non-obvious findings

- **`NeonEQ.tar.gz` is a Git LFS pointer**, not a real archive (~130 bytes); the
  blob is not fetched in this sandbox. Ignore it — the extracted
  `conversations/6a7a961312b12b330076a6ce/android-eq/` directory is the source
  that actually builds. Nothing the build needs is LFS-tracked except the launcher
  PNGs, which are present as real files.
- **The path matters.** Gradle, the Dockerfile's cache and the workflows all assume
  the project lives at `conversations/<conversation-id>/android-eq`. If the compose
  mount path is changed, keep it in sync here and in the workflows.
- **Toolchain versions are pinned and must match**, or the build fails at the CMake
  step: AGP 8.2.2, Kotlin 1.9.22, Gradle **8.5** (wrapper), compileSdk 34,
  build-tools 34.0.0, **NDK 25.1.8937393** (AGP 8.2.x default), **CMake 3.22.1**
  (pinned in `app/build.gradle.kts`), JDK 17. `.base44/android/Dockerfile` installs
  exactly these.
- **Keep the Dockerfile's `python3` layer last.** The Android SDK/NDK layers above
  it total ~1.5 GB; inserting an apt layer before them throws that cache away and
  makes every rebuild re-download the NDK.
- **Debug signing is pinned** to the checked-in `app/debug.keystore.p12`
  (`storeType PKCS12`, alias `androiddebugkey`, password `android`). Every build is
  signed identically so installs update cleanly over each other. Don't remove it or
  AGP will auto-generate a new random key per build.
- **No credentials are required.** The app is offline — Android `AudioEffect` API
  plus local native DSP. The only environment read anywhere is
  `System.getenv("GITHUB_RUN_NUMBER")` for `versionCode`, which defaults to 1.
  There are no secrets and no `.env` file to provide.
- **The native harness is the cheapest failure detector.** Because it needs no
  Android SDK, `g++ ... tools/dspharness/harness.cpp` runs on the host in seconds
  and catches DSP regressions long before the Gradle build finishes. The Kotlin/
  APK side still has to be built in the container.
- **`workflow-template.yml` at the repo root** is a stale, partial copy of
  `.github/workflows/build.yml` (it builds from the repo root, which has no Gradle
  project). The workflows under `.github/workflows/` are the real ones; don't
  "fix" the template by copying the root into the build.
- `test.txt` at the root is a scratch file, not a test.

## Sandbox notes

No sandbox-specific overrides are needed: there is no dev server, no host/origin
allowlist, no cookie or auth configuration to adapt, and no `BASE44_PREVIEW_MODE`
behaviour in the app. Shared application configuration is untouched.
