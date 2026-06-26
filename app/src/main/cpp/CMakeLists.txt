cmake_minimum_required(VERSION 3.5)
project(zstd-jni C ASM)

# 并行编译
include(ProcessorCount)
ProcessorCount(N)
if(NOT N EQUAL 0)
    set(CMAKE_BUILD_PARALLEL_LEVEL ${N})
endif()

set(ZSTD_DIR "${CMAKE_CURRENT_SOURCE_DIR}/native")

# Include directories
include_directories(
    ${ZSTD_DIR}
    ${ZSTD_DIR}/common
    ${ZSTD_DIR}/compress
    ${ZSTD_DIR}/decompress
    ${ZSTD_DIR}/dictBuilder
    ${ZSTD_DIR}/legacy
)

# Collect source files
file(GLOB ZSTD_COMMON_SOURCES "${ZSTD_DIR}/common/*.c")
file(GLOB ZSTD_COMPRESS_SOURCES "${ZSTD_DIR}/compress/*.c")
file(GLOB ZSTD_DECOMPRESS_SOURCES "${ZSTD_DIR}/decompress/*.c" "${ZSTD_DIR}/decompress/*.S")
file(GLOB ZSTD_DICTBUILDER_SOURCES "${ZSTD_DIR}/dictBuilder/*.c")
file(GLOB ZSTD_LEGACY_SOURCES "${ZSTD_DIR}/legacy/*.c")
file(GLOB ZSTD_JNI_SOURCES "${ZSTD_DIR}/jni_*.c")

# Build shared library
add_library(zstd-jni SHARED
    ${ZSTD_COMMON_SOURCES}
    ${ZSTD_COMPRESS_SOURCES}
    ${ZSTD_DECOMPRESS_SOURCES}
    ${ZSTD_DICTBUILDER_SOURCES}
    ${ZSTD_LEGACY_SOURCES}
    ${ZSTD_JNI_SOURCES}
)

# 输出文件名带版本号，与 Maven JAR 的 Native.java 加载名一致
set_target_properties(zstd-jni PROPERTIES OUTPUT_NAME "zstd-jni-1.5.7-9")

target_compile_definitions(zstd-jni PRIVATE ZSTD_LEGACY_SUPPORT=4)
target_compile_options(zstd-jni PRIVATE -O3 -fPIC)
target_link_libraries(zstd-jni log)
