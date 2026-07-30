cmake_minimum_required(VERSION 3.22.1)
project(xfqdeobf2)

find_library(ANDROID_LOG log)

# Build LSPlant as static library
set(LS_PLANT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/LSPlant/lsplant/src/main/jni)
set(LSPLANT_BUILD_SHARED OFF CACHE BOOL "" FORCE)
add_subdirectory(${LS_PLANT_ROOT} lsplant_static)

add_library(xfqdeobf2 SHARED
    init.cc
    menu_hook.cc
)

target_include_directories(xfqdeobf2 PRIVATE ${LS_PLANT_ROOT})

target_link_libraries(xfqdeobf2
    lsplant_static
    ${ANDROID_LOG}
)

target_compile_options(xfqdeobf2 PRIVATE -std=c++20 -fno-rtti -fno-exceptions)
