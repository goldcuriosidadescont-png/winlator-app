cmake_minimum_required(VERSION 3.22.1)
project(cerberus LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../core")
add_library(cerberus SHARED bridge.cpp "${CORE_DIR}/machine.cpp")
target_include_directories(cerberus PRIVATE "${CORE_DIR}")
# Explicit 16 KiB ELF alignment. NDK r28 handles this by default too.
target_link_options(cerberus PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
