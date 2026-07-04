cmake_minimum_required(VERSION 3.22.1)
project("sleeptimer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
        sleeptimer
        SHARED
        SleepTimer.cpp
        jni_bridge.cpp
)

# Link Android log library for __android_log_print
find_library(log-lib log)

target_link_libraries(
        sleeptimer
        ${log-lib}
)