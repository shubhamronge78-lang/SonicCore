#!/usr/bin/env bash
# Base44 sandbox bootstrap: install the Android command-line tools + SDK
# packages this project's Gradle build needs. Idempotent — safe to re-run.
# Mirrors the GitHub Actions build (JDK 17 + Android SDK 34 + build-tools 34).
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_VERSION="11076708"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[base44] installing Android command-line tools into $SDK"
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl unzip >/dev/null
  mkdir -p "$SDK/cmdline-tools" /tmp/sdk-dl
  curl -fsSL -o /tmp/sdk-dl/cmdtools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
  unzip -q -o /tmp/sdk-dl/cmdtools.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

# Licenses must be accepted before packages install.
yes | sdkmanager --licenses >/dev/null 2>&1 || true

# Only the platforms/build-tools the project pins. NDK/CMake are installed
# lazily by Gradle on demand for the native (neondsp) module.
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

echo "[base44] Android SDK ready at $SDK"
