# Android ARM64 LLVM 18 Toolchain for SonicCore
#
# This toolchain file enables cross-compilation for Android arm64-v8a using the
# ARM64-hosted LLVM 18 clang (/opt/soniccore-tools/clang+llvm-18.1.8-aarch64-linux-gnu)
# together with the Android NDK 25.1.8937393 sysroot, headers, and C++ STL.
#
# Required because the NDK 25.1.8937393 at /root/android-sdk/ndk/25.1.8937393 only
# ships linux-x86_64 prebuilt toolchains, whose clang cannot execute on this aarch64
# host (exit code 127: "not found" — x86-64 ELF cannot run on aarch64 Linux).
#
# Key fix: Use CMAKE_SYSTEM_NAME=Linux to bypass CMake's Platform/Android.cmake which
# searches for x86_64 NDK gcc/clang. Use `clang` (not `clang++`) as the C++ linker
# driver because clang++ mis-handles .o files as C++ source code during linking.
#
# The cmake wrapper at /opt/soniccore-tools/cmake-3.22.6/bin/cmake injects this
# toolchain file after AGP's arguments so it takes precedence.

# ---------------------------------------------------------------------------
# Prevent CMake's built-in Android support from searching for *-gcc
# in the NDK's linux-x86_64 toolchain directory.
# Use CACHE FORCE so this overrides AGP's -DCMAKE_SYSTEM_NAME=Android.
# ---------------------------------------------------------------------------
set(CMAKE_ANDROID_STANDALONE_TOOLCHAIN "" CACHE STRING "" FORCE)
set(CMAKE_SYSTEM_NAME Linux CACHE STRING "" FORCE)
set(CMAKE_SYSTEM_VERSION 26 CACHE STRING "" FORCE)
set(CMAKE_SYSTEM_PROCESSOR aarch64 CACHE STRING "" FORCE)

# Pre-set Clang standard defaults to avoid CMake error:
#   "CMAKE_C_STANDARD_COMPUTED_DEFAULT and CMAKE_C_EXTENSIONS_COMPUTED_DEFAULT
#    should be set for Clang"
set(CMAKE_C_STANDARD_COMPUTED_DEFAULT 11 CACHE STRING "" FORCE)
set(CMAKE_C_EXTENSIONS_COMPUTED_DEFAULT ON CACHE STRING "" FORCE)
set(CMAKE_CXX_STANDARD_COMPUTED_DEFAULT 17 CACHE STRING "" FORCE)
set(CMAKE_CXX_EXTENSIONS_COMPUTED_DEFAULT OFF CACHE STRING "" FORCE)

set(ANDROID_NDK /root/android-sdk/ndk/25.1.8937393 CACHE STRING "" FORCE)

# ---------------------------------------------------------------------------
# NDK library paths
# ---------------------------------------------------------------------------
set(NDK_CLANG_RESOURCE_DIR ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/lib64/clang/14.0.6)
set(NDK_COMPILER_RT_DIR    ${NDK_CLANG_RESOURCE_DIR}/lib/linux)
set(NDK_UNWIND_DIR         ${NDK_CLANG_RESOURCE_DIR}/lib/linux/aarch64)
set(NDK_SYSROOT            ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot)
set(NDK_LIB_DIR_26         ${NDK_SYSROOT}/usr/lib/aarch64-linux-android/android-26)

# ---------------------------------------------------------------------------
# Compiler selection: ARM64-hosted LLVM 18 clang/clang++
# Force-set as cache variables so CMake uses these instead of x86_64 NDK tools.
# ---------------------------------------------------------------------------
set(LLVM18_ROOT /opt/soniccore-tools/clang+llvm-18.1.8-aarch64-linux-gnu)

set(CMAKE_C_COMPILER   "${LLVM18_ROOT}/bin/clang"   CACHE FILEPATH "C compiler" FORCE)
set(CMAKE_CXX_COMPILER "${LLVM18_ROOT}/bin/clang++" CACHE FILEPATH "C++ compiler" FORCE)
# NOTE: CMAKE_CXX_LINKER is NOT overridden here. CMake will use CMAKE_CXX_COMPILER
# (clang++) for linking, but the clang++ wrapper script at LLVM18/bin/clang++
# delegates linking to clang (which correctly handles .o files as linker input,
# unlike clang++ which mis-handles them as C++ source code).
set(CMAKE_AR           "${LLVM18_ROOT}/bin/llvm-ar"           CACHE FILEPATH "Archiver" FORCE)
set(CMAKE_NM           "${LLVM18_ROOT}/bin/llvm-nm"           CACHE FILEPATH "Symbol table" FORCE)
set(CMAKE_RANLIB       "${LLVM18_ROOT}/bin/llvm-ranlib"       CACHE FILEPATH "Ranlib" FORCE)
set(CMAKE_STRIP        "${LLVM18_ROOT}/bin/llvm-strip"        CACHE FILEPATH "Strip" FORCE)
set(CMAKE_OBJCOPY      "${LLVM18_ROOT}/bin/llvm-objcopy"      CACHE FILEPATH "Objcopy" FORCE)

# Android target triple for the compiler
set(CMAKE_C_COMPILER_TARGET   aarch64-linux-android26 CACHE STRING "" FORCE)
set(CMAKE_CXX_COMPILER_TARGET aarch64-linux-android26 CACHE STRING "" FORCE)

# ---------------------------------------------------------------------------
# Sysroot from the Android NDK
# ---------------------------------------------------------------------------
set(CMAKE_SYSROOT ${NDK_SYSROOT})

# ---------------------------------------------------------------------------
# C++ Standard Library (NDK's llvm-libc++)
# ---------------------------------------------------------------------------
set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(NDK_CXX_STL_INCLUDE ${ANDROID_NDK}/sources/cxx-stl/llvm-libc++/include)
set(NDK_CXX_STL_LIB_64  ${NDK_LIB_DIR_26})

# ---------------------------------------------------------------------------
# Compiler flags for Android ARM64
# ---------------------------------------------------------------------------
set(CMAKE_C_FLAGS_INIT
    "-target aarch64-linux-android26 --sysroot=${NDK_SYSROOT} -DANDROID -D__STDC_VERSION__=199901L -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -include ${NDK_SYSROOT}/usr/include/stdint.h -include ${NDK_CLANG_RESOURCE_DIR}/include/stddef.h -include ${NDK_SYSROOT}/usr/include/limits.h -I${NDK_SYSROOT}/usr/include -I${NDK_CXX_STL_INCLUDE}"
)

set(CMAKE_CXX_FLAGS_INIT
    "-target aarch64-linux-android26 --sysroot=${NDK_SYSROOT} -DANDROID -D__STDC_VERSION__=199901L -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -frtti -fexceptions -include cstdio -include cstdlib -include ${NDK_SYSROOT}/usr/include/stdint.h -include ${NDK_CLANG_RESOURCE_DIR}/include/stddef.h -include ${NDK_SYSROOT}/usr/include/limits.h -I${NDK_SYSROOT}/usr/include -I${NDK_CXX_STL_INCLUDE}"
)

# ---------------------------------------------------------------------------
# Linker flags: use lld linker, include NDK library paths
# -lc++_shared is needed because clang (not clang++) is used as linker driver
# ---------------------------------------------------------------------------
set(CMAKE_SHARED_LINKER_FLAGS_INIT
    "-target aarch64-linux-android26 --sysroot=${NDK_SYSROOT} -fuse-ld=lld -Wl,-soname,libneondsp.so -Wl,--gc-sections -lc++_shared -L${NDK_COMPILER_RT_DIR} -L${NDK_UNWIND_DIR} -L${NDK_LIB_DIR_26} -L${NDK_CXX_STL_LIB_64}"
)

set(CMAKE_EXE_LINKER_FLAGS_INIT
    "-target aarch64-linux-android26 --sysroot=${NDK_SYSROOT} -fuse-ld=lld -Wl,--gc-sections -lc++_shared -L${NDK_COMPILER_RT_DIR} -L${NDK_UNWIND_DIR} -L${NDK_LIB_DIR_26} -L${NDK_CXX_STL_LIB_64}"
)

# ---------------------------------------------------------------------------
# Find Android libraries
# ---------------------------------------------------------------------------
find_library(LOG_LIB log PATHS ${NDK_LIB_DIR_26})
find_library(ANDROID_LIB android PATHS ${NDK_LIB_DIR_26})

# ---------------------------------------------------------------------------
# Output directories
# ---------------------------------------------------------------------------
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# ---------------------------------------------------------------------------
# Status messages
# ---------------------------------------------------------------------------
message(STATUS "Android ARM64 LLVM 18 toolchain for neondsp")
message(STATUS "  C compiler:   ${CMAKE_C_COMPILER}")
message(STATUS "  CXX compiler: ${CMAKE_CXX_COMPILER}")
message(STATUS "  C linker:     ${CMAKE_C_LINKER}")
message(STATUS "  CXX linker:   ${CMAKE_CXX_LINKER}")
message(STATUS "  Target:       ${CMAKE_C_COMPILER_TARGET}")
message(STATUS "  Sysroot:      ${CMAKE_SYSROOT}")
message(STATUS "  NDK:          ${ANDROID_NDK}")
