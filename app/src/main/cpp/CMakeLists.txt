cmake_minimum_required(VERSION 3.22.1)
project(supershelf_retro)

# Oboe (audio) is fetched and hash-pinned by CI into
# ${OBOE_DIR} - see .github/workflows/android.yml. It is
# built from source there and never vendored into this
# repository.
if(NOT DEFINED OBOE_DIR)
    message(FATAL_ERROR "OBOE_DIR is not set (the CI fetch step provides it)")
endif()
add_subdirectory(${OBOE_DIR} oboe-build)

# The libretro frontend shim: hosts a core .so loaded by
# path at runtime (cores are separate libraries, fetched
# and hash-pinned by CI into jniLibs, never linked here).
add_library(supershelf_retro SHARED retro_shim.cpp crt_chain.cpp)
target_compile_features(supershelf_retro PRIVATE cxx_std_17)
target_include_directories(supershelf_retro PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(supershelf_retro
    oboe
    EGL
    GLESv3
    android
    log
    dl
)
