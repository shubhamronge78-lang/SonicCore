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

| Service | What it does |
| --- | --- |
| `web` | nginx on host port 3000: status page + the built APK, test report and logs from `./build-output`. Long-running. |
| `dsp-test` | One-shot. Compiles the **real** production DSP (`tools/dspharness/harness.cpp` `#include`s `neondsp.cpp`) with ASan+UBSan and runs its assertions. Takes seconds. |
| `android-build` | One-shot. `./gradlew :app:testDebugUnitTest :app:assembleDebug`, then copies APK + test report + build log into `./build-output`. |

```bash
docker compose -f docker-compose.base44.yml up -d --build   # full build
docker compose -f docker-compose.base44.yml logs -f android-build
docker compose -f docker-compose.base44.yml run --rm dsp-test   # quick engine check
```

There is **no live-reload dev server** for an Android app: after editing source,
re-run `up -d --build` (the Gradle layer is cached in the `gradle-home` volume, so
subsequent builds are much faster) and then reload the preview.

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
- **Debug signing is pinned** to the checked-in `app/debug.keystore.p12`
  (`storeType PKCS12`, alias `androiddebugkey`, password `android`). Every build is
  signed identically so installs update cleanly over each other. Don't remove it or
  AGP will auto-generate a new random key per build.
- **No credentials are required.** The app is offline — Android `AudioEffect` API
  plus local native DSP. Nothing reads `process.env` or a config file, so there are
  no secrets and no `.env` file to provide.
- **`workflow-template.yml` at the repo root** is a stale, partial copy of
  `.github/workflows/build.yml` (it builds from the repo root, which has no Gradle
  project). The workflows under `.github/workflows/` are the real ones; don't
  "fix" the template by copying the root into the build.
- `test.txt` at the root is a scratch file, not a test.

## Sandbox notes

No sandbox-specific overrides are needed: there is no dev server, no host/origin
allowlist, no cookie or auth configuration to adapt, and no `BASE44_PREVIEW_MODE`
behaviour in the app. Shared application configuration is untouched.
