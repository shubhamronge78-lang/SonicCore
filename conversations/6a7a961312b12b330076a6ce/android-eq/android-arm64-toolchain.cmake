# Android ARM64 NDK Toolchain Configuration
# This toolchain file is used to build for ARM64 Android targets

# Cross-compiler paths from Android NDK
set(CMAKE_C_COMPILER "/root/android-sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang")
set(CMAKE_CXX_COMPILER "/root/android-sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang++")

# Architecture and system name
set(CMAKE_SYSTEM_NAME "Linux")
set(CMAKE_SYSTEM_PROCESSOR "aarch64")

# Android-specific settings
set(ANDROID_ABI "arm64-v8a")
set(ANDROID_STL "c++_shared")
set(ANDROID_CPP_FEATURES "rtti exceptions")

# Output directory configuration
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")

# Compiler flags for Android ARM64
set(CMAKE_C_FLAGS "-fPIC -shared")
set(CMAKE_CXX_FLAGS "-fPIC -shared")

# Android NDK specific paths
set(ANDROID_NDK_ROOT "/root/android-sdk/ndk/25.1.8937393")
set(ANDROID_PLATFORM "21")  # Android 5.0 Lollipop

# Linker flags
set(CMAKE_EXE_LINKER_FLAGS "-Wl,--gc-sections")
set(CMAKE_SHARED_LINKER_FLAGS "-Wl,--gc-sections,-Wl,-z,norelro,-soname,libneondsp.so")

# Include paths
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -I${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -I${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include")

# Find Android log library
set(LOG_LIB "${ANDROID_NDK_ROOT}/sources/android/log")

message(STATUS "Using Android ARM64 toolchain for NEONDSP")
message(STATUS "C compiler: ${CMAKE_C_COMPILER}")
message(STATUS "CXX compiler: ${CMAKE_CXX_COMPILER}")