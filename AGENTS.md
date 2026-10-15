# AGENTS.md — SonicCore (NeonEQ) working notes

Non-obvious facts for anyone (human or agent) picking this repo up. Manifests,
Gradle files and the app's own comments are not repeated here.

## Repository layout is unusual

The Gradle project is **not** at the repo root. It lives at:

    conversations/6a7a961312b12b330076a6ce/android-eq/

That path shows up in the CI workflow (`.github/workflows/build.yml`), which
`cd`s into it before running Gradle. Anything at the repo root
(`workflow-template.yml`, `test.txt`) is scaffolding, not the app.

## This is a native Android app — there is no web entry point

SonicCore is a Kotlin/Jetpack-Compose + C++ (NeonDsp) Android application. It has
no HTTP server and no browser UI, so **nothing serves host port 3000** and the
Base44 preview iframe has nothing to render. "Verified" for this repo means the
Gradle build and the JVM unit tests pass, plus the APK packaging the expected
resources — not a page load. Do not invent a preview server to satisfy the
port-3000 convention.

## Sandbox build environment

`docker-compose.base44.yml` runs a `android-toolchain` service that mirrors CI
(JDK 17 + Android SDK 34 + build-tools 34, driven by the checked-in Gradle
wrapper). It does no port mapping — it is a build/test shell, not a server.

    docker compose -f docker-compose.base44.yml up -d
    docker compose -f docker-compose.base44.yml exec android-toolchain \
      ./gradlew testDebugUnitTest --no-daemon
    docker compose -f docker-compose.base44.yml exec android-toolchain \
      ./gradlew assembleDebug --no-daemon -x test

Notes learned the hard way:

- `scripts/base44-android-sdk.sh` bootstraps the SDK into a named volume on
  container start. It is idempotent; the SDK download is large, so the volume is
  worth keeping.
- The Android **NDK is installed automatically by AGP** on the first
  `:app:compileDebugKotlin` / `assembleDebug` (no manual `sdkmanager` call
  needed). Expect the first build to take noticeably longer.
- `testDebugUnitTest` + `assembleDebug` together take ~45 s once the SDK and the
  Gradle dependency cache are warm.
- CI also runs a native host self-test (`g++ … tools/dspharness/harness.cpp` with
  ASan/UBSan). That compiler is **not** in the toolchain image; run it on a host
  that has g++ if you touch `app/src/main/cpp/neondsp.cpp`.

## Liquid-glass UI system (v147 redesign)

- `app/src/main/java/com/neon/eq/LiquidGlass.kt` is the single source of the
  material language: `GlassSurface` (the frosted panel), `LiquidChip`,
  `LiquidButton`, `LiquidToggle`, `LiquidOrb`, `Modifier.riseIn`.
- The old component names still exist in `MainActivity.kt` and simply delegate to
  the new material (`NeonCard`, `GlassChip`, `StatusChip`, `GlassButton`,
  `GlassBottomSheet`, `GradientText`), so call sites stay unchanged.
- `appGlassState` (the user's LOW/MEDIUM/HIGH "GLASS EFFECT" setting) still has an
  effect: it drives `glassBackingAlpha()` — the panel backing tint. Never make the
  panels fully opaque; the backdrop is meant to read through them.
- The chrome adapts to `appModeState`: white veils/edges in dark mode, dark
  hairlines and a light backing in light mode. New glass UI must use the
  `chipInk()/glassEdgeTop()/glassTrack()/dial*()` helpers rather than literal
  white, or light mode becomes unreadable.
- Backdrop/imagery assets live in `app/src/main/res/drawable-nodpi/`
  (`glass_bg`, `glass_banner`, `glass_output`).
- All of this is presentation only. It never touches DSP, capture, buffers,
  latency or sample rate.
