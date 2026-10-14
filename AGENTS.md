# AGENTS.md — Neon EQ (Base44 workspace)

## What this project is

An **Android system-wide equalizer app** (Kotlin + Jetpack Compose + native C++ DSP via
NDK/JNI). The source lives in `conversations/6a7a961312b12b330076a6ce/android-eq/`, not at
the repo root. There is **no web frontend** — the Base44 "preview" is a build-status page
served on port 3000 that shows the CI pipeline results and offers the built APK for download.

## How the Base44 environment works

`docker-compose.base44.yml` (repo root) builds a JDK 17 + Android SDK + NDK + CMake image
(`Dockerfile.base44` in the android-eq dir), bind-mounts the source, and runs
`base44-start.sh` on container start. That script:

1. Compiles and runs the **DSP host harness** (`tools/dspharness/harness.cpp`) with g++ +
   ASan/UBSan — it `#include`s the real `neondsp.cpp` directly (single TU).
2. Runs **Gradle unit tests** (`./gradlew testDebugUnitTest`).
3. Builds the **debug APK** (`./gradlew assembleDebug -x test`).
4. Replaces the placeholder with a final HTML status page and keeps serving it + the APK
   on port 3000 via `python3 -m http.server`.

The HTTP server starts **immediately** with a "building…" placeholder page (auto-refreshing
every 15 s), so the healthcheck passes within seconds. The build runs in the foreground while
the server runs in the background; the page updates when the build finishes. First build takes
~3 min (Gradle dependency download + NDK install); the `gradle-cache` named volume speeds up
restarts. Healthcheck has a 10 s startup grace.

## Key gotchas

- **g++ must be in the image** — the harness is a host-side C++ program, not built by
  Gradle. The initial Dockerfile omitted it; the harness silently failed until added.
- **AGP 8.2.2 auto-installs NDK 25.1.8937393** at build time even though 25.2.9519653 is
  pre-installed in the image — this is normal (AGP's default NDK version differs).
- **No `local.properties` is committed** — the startup script writes `sdk.dir` at runtime.
- The `debug.keystore.p12` is committed for stable debug signing across CI builds.

## CI pipeline (`.github/workflows/build.yml`)

Same three steps: harness → unit tests → assembleDebug. The Base44 compose mirrors this.

## Verifying a change

```sh
docker compose -f docker-compose.base44.yml up -d --build
# wait ~3 min, then:
curl -s http://localhost:3000/ | grep -o 'PASSING\|FAILING'
docker compose -f docker-compose.base44.yml logs --tail 20
```
