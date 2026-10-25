# SonicCore

SonicCore is an Android audio equalizer and processing application — Kotlin +
Jetpack Compose on top of a native C++ DSP engine (playback capture → JNI →
NeonDsp → selected output).

The application source and its documentation live in
[`conversations/6a7a961312b12b330076a6ce/android-eq`](./conversations/6a7a961312b12b330076a6ce/android-eq).

- [App README: behaviour, limits, build and test commands](./conversations/6a7a961312b12b330076a6ce/android-eq/README.md)
- [Feature matrix — what each of the six categories actually does](./conversations/6a7a961312b12b330076a6ce/android-eq/docs/feature-matrix.md)
- [Permissions and privacy audit](./conversations/6a7a961312b12b330076a6ce/android-eq/docs/permissions-audit.md)
- [Repository integration and licence provenance](./conversations/6a7a961312b12b330076a6ce/android-eq/docs/repository-integration.md)
- [Shizuku operations and why most are not implemented](./conversations/6a7a961312b12b330076a6ce/android-eq/docs/shizuku-operations.md)
- [Platform expansion status](./conversations/6a7a961312b12b330076a6ce/android-eq/PLATFORM_EXPANSION_STATUS.md)
- [Android platform notes](./conversations/6a7a961312b12b330076a6ce/android-eq/ANDROID_PLATFORM_NOTES.md)
- [Changelog](./conversations/6a7a961312b12b330076a6ce/android-eq/CHANGELOG.md)
- [MIT licence](./conversations/6a7a961312b12b330076a6ce/android-eq/LICENSE)

## What else is in this repository

`docker-compose.base44.yml`, `.base44/`, `.github/workflows/` and `AGENTS.md`
are the workspace that builds and verifies the app: they compile the debug APK
from the checked-out source, run the native DSP regression harness and the JVM
unit tests, and publish the results. None of them carries application code.
See [AGENTS.md](./AGENTS.md) for how the environment is brought up.
