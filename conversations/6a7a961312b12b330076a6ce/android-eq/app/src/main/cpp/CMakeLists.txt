cmake_minimum_required(VERSION 3.22.1)
project("neondsp")

# Native NeonDsp engine: preamp -> parametric EQ -> graphic EQ (10/15/31)
# -> bass/treble shelves -> compressor -> stereo stage -> convolver -> limiter.
add_library(neondsp SHARED neondsp.cpp)
target_link_libraries(neondsp android)

# Offline loudness measurement (EBU R 128 / ITU-R BS.1770-4) over the vendored
# libebur128 (MIT — see third_party/ebur128/README.md). Deliberately its OWN
# shared library: the measurement code and the vendored library never enter
# libneondsp.so, so nothing here can be reached from the real-time DSP chain and
# playback latency is untouched. Nothing in the playback path loads it.
add_library(loudness SHARED loudness/loudness_jni.cpp third_party/ebur128/ebur128.c)
target_include_directories(loudness PRIVATE third_party/ebur128)
target_link_libraries(loudness m)

# Output directory for the shared library
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
