# NeonEQ — Base44 Setup Notes

## What this project is

A **native Android app** (Kotlin + Jetpack Compose + NDK C++ DSP engine). It builds a debug APK — there is no web server and it cannot be displayed in a browser preview on port 3000. The actual source lives in `conversations/6a7a961312b12b330076a6ce/android-eq/`.

## How to build and test (Docker)

All commands run from the repo root.

```bash
# Build the debug APK (JDK 17 + Android SDK 34 + NDK + CMake)
docker compose -f docker-compose.base44.yml run --rm build-apk

# Run the native DSP harness (C++ self-test with ASan/UBSan)
docker compose -f docker-compose.base44.yml run --rm dsp-test

# Run JVM unit tests (preset validation, output contract, band map)
docker compose -f docker-compose.base44.yml run --rm build-apk sh -c "./gradlew testDebugUnitTest --no-daemon"
```

The built APK appears at `conversations/.../android-eq/app/build/outputs/apk/debug/app-debug.apk` inside the container. Copy it out with:
```bash
docker compose -f docker-compose.base44.yml run build-apk sh -c "cp app/build/outputs/apk/debug/app-debug.apk /project/app-debug.apk"
```

## Build requirements

- JDK 17 (eclipse-temurin:17-jdk base image)
- Android SDK: platform-tools, platforms;android-34, build-tools;34.0.0
- NDK 26.3.11579264 + CMake 3.22.1 (for native DSP in app/src/main/cpp/neondsp.cpp)
- Gradle 8.5 (via wrapper)
- g++ with ASan/UBSan (for the DSP harness, not the APK)

## No external secrets needed

The project has no external service dependencies — all audio processing is local on-device. No API keys or credentials are required.
