# Repository notes

## What this repository is

A native **Android** app — SonicCore / Neon EQ (Kotlin + Jetpack Compose, with a
C++ DSP engine built through CMake/NDK). There is no web frontend and no API
server, so nothing in this repo can run inside a browser preview.

The Android project lives in `conversations/6a7a961312b12b330076a6ce/android-eq`
(not at the repository root). Two GitHub workflows build it:
`.github/workflows/android-apk.yml` (SDK 34, build-tools 34.0.0, NDK 25.1.8937393,
CMake 3.22.1, JDK 17) and `.github/workflows/build.yml`. The other workflow,
`.github/workflows/release.yml`, publishes the debug APK on tags.

No external services or API keys are used — the app is fully offline.

## Base44 sandbox setup

`docker-compose.base44.yml` runs two services:

- `apk` — one-shot build job on `.base44/Dockerfile.android` (JDK 17 + Android
  SDK/NDK/CMake toolchain image). `app/` source is bind mounted, so each run
  compiles the current working tree. The script `.base44/build-and-report.sh`
  runs the native DSP self-test, the JVM unit tests and `assembleDebug`, then
  writes a report page plus the APK into the shared `dist` volume.
- `web` — nginx serving that volume on host port 3000, which is what the browser
  preview shows. That page is the only browser-visible surface: the download
  button plus the three verification results.

Bring up / rebuild:

```bash
docker compose -f docker-compose.base44.yml up -d --build   # first boot
docker compose -f docker-compose.base44.yml restart apk     # after a source edit
```

The `apk` container builds, publishes the page and APK, then idles (`sleep
infinity`) so the stack stays up. Its healthcheck waits for `/dist/index.html`
plus the `/dist/.build-complete` marker the script writes when the build phase
ends, so a long first compile does not flap. The one-line result is in
`docker compose -f docker-compose.base44.yml logs apk`.

A cold run takes roughly 5 minutes (SDK image build, Gradle distribution and
dependency download, Kotlin + NDK compile). Gradle caches persist in the
`gradle-cache` volume, so later rebuilds are much faster.

### Quirk: `android.cmake.path`

`conversations/.../android-eq/gradle.properties` pins
`android.cmake.path = /opt/soniccore-tools/cmake-3.22.6`, a path that only exists
on the original author's machine. CI works around it by deleting the line. The
sandbox build script does **not** edit the tracked file — it overrides the
property on the Gradle command line instead
(`-Pandroid.cmake.path=$ANDROID_SDK_ROOT/cmake/3.22.1`), which is equivalent to
letting AGP use the SDK-managed CMake. Keep it that way so the developer's
on-disk configuration keeps working.

## Verification

The project's own checks, run from
`conversations/6a7a961312b12b330076a6ce/android-eq`:

```bash
# native DSP host self-test (ASan/UBSan over the real engine)
g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer \
  -I tools/dspharness -I . tools/dspharness/harness.cpp -o /tmp/dsp_harness -lpthread
/tmp/dsp_harness

# JVM unit tests, then the debug APK
./gradlew testDebugUnitTest --no-daemon --max-workers=2
./gradlew assembleDebug --no-daemon --max-workers=2
```

Use `--max-workers=2`: the sandbox has 4 cores and 8 GB RAM, and the Kotlin
daemon plus the NDK build will otherwise compete with the preview services.

A green build only proves compilation. MediaProjection consent, capture
eligibility, audio routing and audible output can only be validated on a real
device — the app's own `ANDROID_PLATFORM_NOTES.md` documents those limits.
