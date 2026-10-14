# AGENTS.md — SonicCore Equalizer

## Project Overview
Android system-wide audio equalizer app (Kotlin + Jetpack Compose + native C++ DSP).
The actual Android project lives at `conversations/6a7a961312b12b330076a6ce/android-eq/`.
Current version: v3.8.0 (Liquid Glass redesign, full SonicCore Equalizer rebranding).

## Why the Base44 preview is a test dashboard, not the app
This is an Android APK project — it cannot run in a browser. The Base44 preview
server (`base44_preview_server.py`) compiles and runs the native C++ DSP
self-test harness (same ASan+UBSan flags as CI) and serves the results on
port 3000. A green run proves the native DSP chain compiles and is numerically
correct.

## Build commands
- **C++ harness** (host, no Android SDK needed):
  ```
  cd conversations/6a7a961312b12b330076a6ce/android-eq
  g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I tools/dspharness -I . tools/dspharness/harness.cpp -o /tmp/dsp_harness -lpthread
  /tmp/dsp_harness
  ```
- **Full APK** (requires Android SDK + JDK 17 + Gradle 8.5):
  ```
  cd conversations/6a7a961312b12b330076a6ce/android-eq
  ./gradlew assembleDebug --no-daemon -x test
  ```
- **Unit tests** (requires Android SDK):
  ```
  ./gradlew testDebugUnitTest --no-daemon --continue
  ```

## Architecture notes
- `neondsp.cpp` is the real-time DSP chain (preamp → PEQ → graphic EQ → shelves →
  compressor → stereo → convolver → limiter). Parameter changes use a seqlock +
  block-boundary commit + coefficient ramp (no audio-thread allocation).
- v146 fixed a critical limiter/compressor defect: both stages now track GAIN
  REDUCTION (≤0 dB) instead of smoothed detector level, which was crushing
  normal material (limiter ON by default attenuated -6 dBFS sine to -68 dBFS).
- `harness.cpp` includes `neondsp.cpp` directly via a stub `jni.h` — single TU,
  no Android dependency. 23 test sections, 66 assertions.
- New v146 telemetry JNI functions: `limiterGrMs`, `limiterActive`,
  `compressorGrMs`, `dspProcUs`, `paramVersion`.

## Base44 setup
- `docker-compose.base44.yml` — runs `python:3.12-slim`, installs g++, serves
  the harness results on port 3000.
- `.base44/environment.json` — metadata (no external secrets needed).
- No external secrets required — everything runs locally.
