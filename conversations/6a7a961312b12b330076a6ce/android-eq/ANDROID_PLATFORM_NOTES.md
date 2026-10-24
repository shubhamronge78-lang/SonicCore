# Android platform and compatibility notes

This note separates documented Android platform behavior from conclusions about
this repository. It is not a claim of device certification.

## Project configuration observed

| Setting | Repository value |
|---|---|
| `minSdk` | 26 |
| `compileSdk` / `targetSdk` | 34 / 34 |
| Android Gradle Plugin | 8.2.2 |
| Kotlin Gradle plugin | 1.9.22 |
| Java/Kotlin bytecode target | 17 |
| Native build | CMake 3.22.1; NDK revision is not pinned in the module |
| Wrapper | Gradle 8.5 |

The working tree initially pointed the wrapper at Gradle 9.7.1, while AGP 8.2.2
and the release workflow use Gradle 8.5. The wrapper was aligned to 8.5, which
has been validated with JDK 17 and this Android SDK. The environment's default
JDK 25 is not compatible with the configured Kotlin 1.9.22 compiler; use JDK 17.

## Platform behavior and project implications

### Playback capture, `AudioRecord`, and permissions

Android documents AudioPlaybackCapture as an Android 10 (API 29)+ capability
using `MediaProjection` and an `AudioRecord` configured for playback capture.
The source player must use an eligible usage (`MEDIA`, `GAME`, or `UNKNOWN`) and
its effective capture policy must allow capture. Users can revoke a projection;
the app must stop the associated capture in response. Therefore “system-wide”
and “all device audio” are not valid guarantees.

This project declares `RECORD_AUDIO` and uses a user-approved capture flow.
Android requires dangerous permissions to be requested at runtime from API 23
onward. The app's install minimum is API 26, but the playback-capture path has
the stricter API 29 minimum and source-app restrictions. No API-specific
fallback can make opted-out or protected source audio capturable.

### Streaming playback, focus, and routing

`AudioRecord` is a pull API: clients must read in time and handle return/error
codes. Its documentation notes that an overrun can occur if unread data is not
consumed before the recording buffer fills.

`AudioTrack` streaming is a push API. `write()` reports how many samples were
transferred or an error; callers must not infer that an entire buffer was
accepted without checking the result. The capture writer now retries partial
writes from the accepted short-sample offset, accounts only complete stereo
frames accepted, and fails through the existing cleanup path on error/no
progress. Pure Kotlin tests cover full, partial, zero, misaligned, and error
results.

The JNI `process` boundary now rejects null arrays, non-positive frame counts,
and frame counts that exceed array capacity before indexing or processing.
Valid buffers keep the same DSP behavior. Native harness boundary cases passed
with ASan and UBSan; this is bounds validation, not a DSP algorithm change.

Android recommends requesting audio focus immediately before an app starts
playback, reacting to focus loss, and abandoning focus when playback ends. Focus
behavior differs across Android versions. `AudioRouting` exposes current route
information and preferred-device requests, but route availability and actual
routing remain device/system controlled; a preferred route is not a guarantee
that the system will use it.

The native path's intended architecture remains:

```text
AudioPlaybackCapture → AudioRecord → JNI → native C++ DSP → AudioTrack → output
```

The in-app player is a separate software-processing path in the current source.
Neither path is proof that the output is audible or that every source app is
being processed.

### Compose state, lifecycle, and performance

Compose `remember` retains values across recomposition, not configuration or
process recreation. `rememberSaveable` is appropriate for small UI/navigation
state that should be restored; large data should be persisted elsewhere. The
current host uses saveable state for its selected destination and cancels
Home meter/parameter polling when Home is not selected or the Activity is not
resumed. The Home dashboard uses two columns in landscape and on wide windows
(at least 840 dp); broader rotation and process-restoration coverage remains
unverified.

The design uses `LaunchedEffect` for composition-scoped polling and disposable
effects for listener cleanup in relevant screens. The project does not currently
declare `lifecycle-runtime-compose`; do not describe its state collection as
`collectAsStateWithLifecycle` without adding and validating that dependency.
Compose guidance recommends keeping repeated composable work small, using stable
keys for lazy lists, and measuring performance rather than assuming visual
effects are free. The glass implementation uses translucency and borders rather
than sampled panel-backdrop blur, which avoids introducing an expensive blur
dependency. On API 31+, `Modifier.blur` is used only on the app's static ambient
background light fields; it blurs the modifier's own layer, not arbitrary sibling
content behind a glass panel. The panels use layered tint, gradients, reflections,
and edge highlights as the cross-version glass treatment. No window-level
background blur is enabled.
The app uses a small custom destination enum and `BackHandler`, not Navigation
Compose. That existing navigation path has a JVM destination-contract test; no
navigation dependency was added in this pass.

### Adaptive layout, accessibility, icons, and startup

Android recommends adapting to the app window size, not only device orientation,
because split-screen and resizable windows can change dimensions independently.
The current dashboard selects its two-column arrangement from orientation;
large-window and font-scale validation is still required.

Compose accessibility relies on meaningful semantics, descriptions, and state
announcements. Several custom controls expose semantics and touch targets, but
automated accessibility checks and TalkBack verification on a device remain
pending.

The launcher resources include foreground/background adaptive-icon layers and a
monochrome layer. Android uses monochrome adaptive icons for themed icons on
supported launchers from API 33; older launchers continue to use the color icon.
The app has not added a separate splash-screen library or an artificial startup
delay. Android 12+ supplies the platform splash experience; any custom exit
animation should be added only with a measured requirement and lifecycle-safe
implementation.

## Official documentation consulted

| Official source | Relevant documented behavior and project implication |
|---|---|
| [Audio playback capture](https://developer.android.com/media/platform/av-capture) | API 29+, user-approved MediaProjection, source usage/capture-policy requirements, and revocation handling. Supports explicitly documenting capture limitations. |
| [`AudioRecord`](https://developer.android.com/reference/android/media/AudioRecord) | Pull-based reads, buffer/overrun responsibility, error states, and `RECORD_AUDIO` requirement. |
| [`AudioTrack`](https://developer.android.com/reference/android/media/AudioTrack) | Streaming writes transfer PCM in chunks; callers must inspect write results. |
| [`AudioRouting`](https://developer.android.com/reference/android/media/AudioRouting) | Public routing queries and preferred-device requests do not guarantee that a device is valid or selected. |
| [Request runtime permissions](https://developer.android.com/training/permissions/requesting) | Request dangerous permissions in context, check grants, and degrade gracefully after denial. |
| [Audio focus](https://developer.android.com/media/optimize/audio-focus) | Request focus before playback and respond to focus changes; behavior is version dependent. |
| [Compose state](https://developer.android.com/develop/ui/compose/state) | Observable Compose state drives recomposition; state integrations have artifact requirements. |
| [Save UI state](https://developer.android.com/develop/ui/compose/state-saving) | `rememberSaveable` saves small UI state across recreation; avoid placing large state in the instance-state bundle. |
| [Compose lifecycle](https://developer.android.com/develop/ui/compose/lifecycle) | Composition identity and side-effect lifecycle inform effect scoping and cleanup. |
| [Lifecycle-aware coroutines](https://developer.android.com/topic/libraries/architecture/coroutines) | `LaunchedEffect` is composition-scoped; it does not by itself stop work when an Activity is merely backgrounded. Home polling now also checks destination and resumed lifecycle. |
| [Compose performance](https://developer.android.com/develop/ui/compose/performance) and [best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices) | Keep recomposition work bounded, use stable lazy keys, and profile rather than assuming performance. |
| [Compose graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) and [`Modifier.blur`](https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/package-summary#blur) | Compose blur operates on the modified layer; this app blurs only static ambient background lights on API 31+ and does not claim panel backdrop blur. |
| [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | The existing Material 3 theme is customized with SonicCore color and surface tokens; no separate glass/blur library was added. |
| [Compose Navigation](https://developer.android.com/guide/navigation) | Navigation Compose is the official graph-based option; this pass retains the existing custom route model rather than introducing an unneeded dependency/migration. |
| [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) | Use semantics, content descriptions, scalable content, and accessibility testing for custom components. |
| [Adaptive Compose layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive) | Layout decisions should use window size classes and account for resizable/multi-window layouts. |
| [Splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen) | Android 12+ platform splash behavior; avoid unnecessary waits and respect system startup transitions. |
| [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) | Provide foreground/background layers and a monochrome layer for themed icons on supported launchers. |
| [AGP 8.2 release notes](https://developer.android.com/build/releases/past-releases/agp-8-2-0-release-notes) | The project uses AGP 8.2.x and must stay on a compatible Gradle/JDK combination; do not upgrade the wrapper without validating the compatibility matrix. |
| [AGP/Gradle compatibility table](https://developer.android.com/build/releases/about-agp#compatibility) | AGP 8.2 requires Gradle 8.2 or newer; the repository base wrapper is 8.5. The local 9.7.1 change fails in this checkout and remains untouched. |
| [Kotlin Gradle plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) | Kotlin Gradle plugin 1.9.20–1.9.25 is listed as fully supported through Gradle 8.1.1/AGP 8.1.0; this project uses 1.9.22 with newer AGP/Gradle, so the combination is outside that documented fully-supported range despite passing tests here. |
| [Configure CMake for Android](https://developer.android.com/studio/projects/configure-cmake) | Gradle configures the CMake project and packages the resulting shared library; the project pins CMake 3.22.1 but does not pin an NDK revision. |

## Validation boundaries

JVM tests validate pure Kotlin contracts only. An APK build does not establish
that a source app is capturable, a route is honored, audio focus is handled
correctly, or audio is audible. Device testing should cover API 26–28 baseline
UI behavior, API 29+ capture eligibility and revocation, permission denial,
Bluetooth/wired/USB route changes, process recreation, screen lock/unlock, and
portrait/landscape plus resizable windows.
