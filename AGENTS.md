# NeonEQ — Base44 Development Environment

## Project Overview

This is an **Android audio equalizer app** (Kotlin + C++ JNI) located at
`conversations/6a7a961312b12b330076a6ce/android-eq/`. It cannot run in a
browser — there is no web frontend or backend API server.

## Why a Status Page Instead of a Live App

The Base44 preview requires a web server on port 3000. Since this is an
Android project, `docker-compose.base44.yml` runs a Python status server
(`base44_status_server.py`) that:

1. Compiles the native DSP self-test harness with `g++` (ASan + UBSan) —
   the same command the CI workflow uses.
2. Runs the harness and parses the 23 test sections.
3. Serves an HTML status page on port 3000 showing pass/fail results.
4. Auto-rebuilds when C++ source files change (reload the page to see
   fresh results).

## Key Paths

- **Android project root:** `conversations/6a7a961312b12b330076a6ce/android-eq/`
- **Native DSP engine:** `app/src/main/cpp/neondsp.cpp`
- **DSP test harness:** `tools/dspharness/harness.cpp`
- **JNI stub header:** `tools/dspharness/jni.h`
- **Kotlin JNI bridge:** `app/src/main/java/com/neon/eq/dsp/NeonDsp.kt`
- **Main UI:** `app/src/main/java/com/neon/eq/MainActivity.kt`
- **CI workflow:** `.github/workflows/build.yml`

## Build Commands

```bash
# Compile and run the DSP harness (from the android-eq directory):
cd conversations/6a7a961312b12b330076a6ce/android-eq
g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer \
  -I tools/dspharness -I . tools/dspharness/harness.cpp \
  -o /tmp/dsp_harness -lpthread
/tmp/dsp_harness

# Gradle unit tests (requires Android SDK):
./gradlew testDebugUnitTest --no-daemon --continue

# Build debug APK (requires Android SDK):
./gradlew assembleDebug --no-daemon -x test
```

## Starting the Preview

```bash
docker compose -f docker-compose.base44.yml up -d --build
```

The status page is served at http://localhost:3000.

## Notes

- The Gradle/Android SDK build cannot run in the sandbox (no Android SDK).
  The C++ harness is the buildable verification target here.
- The harness `#include`s the real `neondsp.cpp` as a single translation
  unit, so it tests the actual production DSP code.
- v146 (commit 58f1465) fixed a critical limiter/compressor gain-reduction
  bug; the harness sections [15]–[23] lock that fix in.
