param(
    [string]$Ndk = 'I:/Android/ndk/android-ndk-r27d',
    [string]$Cmake = 'cmake',
    [string]$Generator = 'Ninja',
    [string[]]$Abis = @('arm64-v8a', 'armeabi-v7a'),
    [switch]$SkipFetch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$fetcher = Join-Path $root 'scripts/fetch-openvpn-sources.ps1'
if (!$SkipFetch) {
    & $fetcher
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Pinned source fetch failed.' }
}

$lock = Get-Content -LiteralPath "$root/third_party/openvpn3/source-lock.json" -Raw | ConvertFrom-Json
$core = Join-Path $root "third_party/openvpn3/openvpn3-$($lock.coreCommit)"
if (!(Test-Path -LiteralPath (Join-Path $core 'client/ovpncli.cpp'))) {
    throw "Pinned OpenVPN core is missing: $core"
}

$llvm = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$toolchain = Join-Path $Ndk 'build/cmake/android.toolchain.cmake'
$built = @()

foreach ($abi in $Abis) {
    if ($abi -notin @('arm64-v8a', 'armeabi-v7a')) { throw "Unsupported ABI: $abi" }
    $build = Join-Path $root "build/openvpn/$abi"
    & $Cmake -S "$root/app/src/main/cpp/openvpn" -B $build -G $Generator `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
        "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static `
        "-DOPENVPN_CORE_DIR=$core" `
        -DCMAKE_BUILD_TYPE=Release '-DCMAKE_POLICY_VERSION_MINIMUM=3.5'
    if ($LASTEXITCODE) { throw "OpenVPN CMake configure failed for $abi." }

    & $Cmake --build $build --parallel 4
    if ($LASTEXITCODE) { throw "OpenVPN native build failed for $abi." }

    $library = Join-Path $build 'libmleopenvpn.so'
    if (!(Test-Path -LiteralPath $library)) { throw "Native library was not produced for $abi." }
    & (Join-Path $llvm 'llvm-strip.exe') $library
    if ($LASTEXITCODE) { throw "Native strip failed for $abi." }

    $symbols = & (Join-Path $llvm 'llvm-nm.exe') -D $library
    $nmExitCode = $LASTEXITCODE
    $hasApiVersionExport = [bool]($symbols | Select-String -SimpleMatch 'Java_com_mle_vpn_engine_openvpn_MleOpenVpnNative_apiVersion')
    if ($nmExitCode -ne 0 -or -not $hasApiVersionExport) {
        throw "Expected MLE JNI apiVersion export is missing for $abi."
    }

    $programHeaders = & (Join-Path $llvm 'llvm-readelf.exe') -lW $library
    if ($LASTEXITCODE) {
        throw "ELF program-header inspection failed for $abi."
    }

    # Validate every PT_LOAD segment, not merely the presence of the text
    # "0x4000" somewhere in readelf output. PowerShell's -notmatch operator
    # on an array returns non-matching lines, which made the previous check
    # fail even when the ELF was correctly aligned.
    $loadSegments = @($programHeaders | Where-Object { $_ -match '^\s*LOAD\s+' })
    if ($loadSegments.Count -eq 0) {
        throw "No ELF PT_LOAD segments were reported for $abi."
    }
    foreach ($segment in $loadSegments) {
        $columns = ($segment.Trim() -split '\s+')
        $alignment = $columns[-1]
        if ($alignment -notmatch '^0x[0-9a-fA-F]+

    $destination = Join-Path $root "app/src/main/jniLibs/$abi/libmleopenvpn.so"
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $library -Destination $destination -Force
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    $built += [pscustomobject]@{ Abi = $abi; File = $destination; Sha256 = $hash }
}

$built | Format-Table -AutoSize
Write-Host 'OpenVPN libraries built, JNI symbol checked and 16-KiB ELF alignment verified.'
 -or
            [Convert]::ToInt64($alignment.Substring(2), 16) -lt 16384) {
            throw "ELF PT_LOAD segment is not 16-KiB aligned for ${abi}: $segment"
        }
    }

    $destination = Join-Path $root "app/src/main/jniLibs/$abi/libmleopenvpn.so"
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $library -Destination $destination -Force
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    $built += [pscustomobject]@{ Abi = $abi; File = $destination; Sha256 = $hash }
}

$built | Format-Table -AutoSize
Write-Host 'OpenVPN libraries built, JNI symbol checked and 16-KiB ELF alignment verified.'
