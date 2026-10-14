# Builder image for the Neon EQ / SonicCore Android app.
#
# Nothing in this repository is a web app: the only project is an Android
# application (Kotlin + Jetpack Compose + native C++ DSP) kept under
# conversations/<conversation-id>/android-eq. This image provides exactly what
# the project's own CI (.github/workflows/build.yml) uses to build it from
# source:
#   * JDK 17                (AGP 8.2.2 / Kotlin 1.9.22, jvmTarget 17)
#   * Android SDK platform 34 + build-tools 34.0.0
#   * NDK 25.1.8937393      (AGP 8.2.x default; the app compiles native code)
#   * CMake 3.22.1          (version pinned in app/build.gradle.kts)
#   * python3               (static file server for the preview on port 3000)
#
# The source is bind-mounted at /src by docker-compose.base44.yml, never baked
# into the image, so edits in the repo are picked up without a rebuild.
FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Android command-line tools. sdkmanager expects them in cmdline-tools/latest.
ARG CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip
RUN mkdir -p /opt/android-sdk/cmdline-tools \
 && curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/${CMDLINE_TOOLS}" \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
 && mv /tmp/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
 && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

# `yes |` answers the licence prompts; sdkmanager exits non-zero on SIGPIPE.
RUN yes | sdkmanager --sdk_root=/opt/android-sdk --licenses > /dev/null 2>&1 || true

RUN sdkmanager --sdk_root=/opt/android-sdk --install \
      "platform-tools" \
      "platforms;android-34" \
      "build-tools;34.0.0" \
      "ndk;25.1.8937393" \
      "cmake;3.22.1"

# Kept as the LAST layer on purpose: the SDK download above is ~1.5 GB and must
# stay cacheable when only this list changes.
#   python3 — static file server for the preview on port 3000
#   g++     — compiles the native DSP self-test (ASan/UBSan ship with it)
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 g++ \
 && rm -rf /var/lib/apt/lists/*
