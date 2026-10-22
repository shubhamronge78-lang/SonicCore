# SonicCore for Android

SonicCore is an Android audio equalizer and processing application. The checked-in
application configuration identifies this source as version **3.6.0**, with
`minSdk 26`, `compileSdk 34`, and `targetSdk 34`.

## Audio behavior and limitations

The native playback-capture path is:

```text
eligible app playback
  → Android AudioPlaybackCapture / AudioRecord
  → JNI
  → native NeonDsp
  → AudioTrack
  → selected output device
```

This is **not a guarantee that every sound on the device is processed**. Android
playback capture requires a user-approved MediaProjection token and is available
starting at Android 10 (API 29). The source app must use an eligible playback
usage and permit capture; protected or opted-out playback may not be available.
Android can revoke a projection, and the source app's original audio may continue
playing alongside SonicCore's processed output. The app's separate in-app player
uses its own software processing path. Output routing and capture behavior can
also vary by device and Android version.

The app installs on API 26+, but the system playback-capture path has the API 29+
and source-app requirements above. Do not interpret permission granted, capture
requested, or an active UI control as proof that audio is currently flowing.

## Current features in source

- Four primary destinations: Home, Equalizer, Player, and DSP + FX.
- Secondary capture, signal-path, and session destinations.
- Graphic EQ configurations and parametric EQ controls, presets, A/B comparison,
  undo/redo, and import/export workflows where supported by the selected path.
- Native processing stages documented in `app/src/main/cpp/CMakeLists.txt`:
  preamp, parametric EQ, graphic EQ, tone shelves, compressor, stereo processing,
  optional convolution, and limiter.
- Measured meter, spectrum, route, and diagnostic UI; measurements are only
  meaningful when corresponding engine data is available.
- Capture, DSP, and output status only report live activity while recent PCM
  input and accepted output frames are present; A/B meter snapshots are not
  presented as separate measurements when no independent history exists.
- Persisted themes and LOW / MEDIUM / HIGH glass intensity in Settings.
- Adaptive launcher icon assets, including a monochrome layer.

The UI exposes platform-dependent features honestly; availability and measured
behavior must be verified on the target device. See
[`ANDROID_PLATFORM_NOTES.md`](./ANDROID_PLATFORM_NOTES.md) for the platform
constraints and validation status.

## Build and tests

Open `android-eq` in Android Studio or use the command line from this directory.
The project configuration uses Android Gradle Plugin 8.2.2, Kotlin 1.9.22,
JDK 17, compile/target SDK 34, and CMake 3.22.1.

The wrapper uses Gradle 8.5, matching the repository's AGP 8.2.2 configuration
and release workflow. Use JDK 17; newer JDKs may not be understood by the
configured Kotlin 1.9.22 compiler. See
[`ANDROID_PLATFORM_NOTES.md`](./ANDROID_PLATFORM_NOTES.md) for compatibility
observations and validation limits.

```bash
./gradlew testDebugUnitTest --no-daemon --max-workers=2
./gradlew assembleDebug --no-daemon --max-workers=2
git diff --check
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Native and device behavior require the corresponding NDK/toolchain and Android
hardware or emulator. A passing JVM unit test or APK build does not validate
MediaProjection consent, source-app capture eligibility, audio focus, route
changes, latency, or audible output.

## Project map

- `app/src/main/java/com/neon/eq/MainActivity.kt` — Compose host and existing
  application workflows.
- `app/src/main/java/com/neon/eq/ui/` — SonicCore design components, dashboard,
  and navigation.
- `app/src/main/java/com/neon/eq/capture/` — playback-capture service and
  capture capability/path handling.
- `app/src/main/java/com/neon/eq/dsp/` — JNI declarations, DSP parameters, and
  meter presentation helpers.
- `app/src/main/cpp/` — native DSP and CMake configuration.
- `app/src/main/java/com/neon/eq/engine/` — engine, presets, output routes,
  service, widget, and Quick Settings tile.
- `app/src/test/` — JVM contract and regression tests.

## Further documentation

- [Android platform notes and official references](./ANDROID_PLATFORM_NOTES.md)
- [Changelog](./CHANGELOG.md)
