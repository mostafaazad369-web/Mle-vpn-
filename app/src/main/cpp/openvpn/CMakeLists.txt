cmake_minimum_required(VERSION 3.20)
project(MleOpenVpn LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

set(DEPS "${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/openvpn3")
file(READ "${DEPS}/source-lock.json" SOURCE_LOCK)
string(JSON CORE_COMMIT GET "${SOURCE_LOCK}" coreCommit)
set(OPENVPN_CORE_DIR "${DEPS}/openvpn3-${CORE_COMMIT}" CACHE PATH "Pinned OpenVPN source")
set(CORE "${OPENVPN_CORE_DIR}")

if(NOT EXISTS "${CORE}/client/ovpncli.cpp")
    message(FATAL_ERROR "Pinned OpenVPN source is missing. Run scripts/fetch-openvpn-sources.ps1 first.")
endif()

set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(GEN_FILES OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(FMT_TEST OFF CACHE BOOL "" FORCE)
set(FMT_DOC OFF CACHE BOOL "" FORCE)

add_subdirectory("${DEPS}/mbedtls-mbedtls-3.6.7" mbedtls EXCLUDE_FROM_ALL)
add_subdirectory("${DEPS}/fmt-11.1.4" fmt EXCLUDE_FROM_ALL)

add_library(mleopenvpn SHARED
    bridge.cpp
    "${CORE}/client/ovpncli.cpp"
    "${CORE}/openvpn/crypto/data_epoch.cpp"
    "${DEPS}/lz4-1.10.0/lib/lz4.c"
)

target_include_directories(mleopenvpn PRIVATE
    "${CORE}"
    "${DEPS}/asio-asio-1-30-2/asio/include"
    "${DEPS}/lz4-1.10.0/lib"
)

target_compile_definitions(mleopenvpn PRIVATE
    USE_ASIO
    ASIO_STANDALONE
    USE_TUN_BUILDER
    OPENVPN_PLATFORM_ANDROID
    USE_MBEDTLS
    HAVE_LZ4
    OPENVPN_CORE_API_VISIBILITY_HIDDEN
)

target_compile_options(mleopenvpn PRIVATE -O2 -ffunction-sections -fdata-sections)
target_link_options(mleopenvpn PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
target_link_libraries(mleopenvpn PRIVATE mbedtls mbedx509 mbedcrypto fmt::fmt log)
