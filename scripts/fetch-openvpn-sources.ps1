param(
    [string]$CacheDirectory = '',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$deps = Join-Path $root 'third_party/openvpn3'
$lockPath = Join-Path $deps 'source-lock.json'
if (!(Test-Path -LiteralPath $lockPath)) { throw "Missing source lock: $lockPath" }
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
if (!$CacheDirectory) { $CacheDirectory = Join-Path $root '.cache/openvpn-sources' }
New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $deps | Out-Null

$mapping = @{
    'OpenVPN 3' = "openvpn3-$($lock.coreCommit)"
    'Mbed TLS 3.6.7' = 'mbedtls-mbedtls-3.6.7'
    'Mbed TLS framework' = 'mbedtls-mbedtls-3.6.7/framework'
    'Asio 1.30.2' = 'asio-asio-1-30-2'
    'LZ4 1.10.0' = 'lz4-1.10.0'
    'fmt 11.1.4' = 'fmt-11.1.4'
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Expand-VerifiedArchive([string]$Archive, [string]$Destination) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $fullDestination = [System.IO.Path]::GetFullPath($Destination)
    $prefix = $fullDestination.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $rootName = ($zip.Entries | Where-Object { $_.FullName -match '^[^/]+/' } | Select-Object -First 1).FullName.Split('/')[0]
        if (!$rootName) { throw "Archive has no single top-level directory: $Archive" }
        foreach ($entry in $zip.Entries) {
            $relative = $entry.FullName
            if ($relative -eq $rootName -or $relative -eq "$rootName/") { continue }
            if (!$relative.StartsWith("$rootName/")) { throw "Unexpected archive root in $Archive" }
            $relative = $relative.Substring($rootName.Length + 1)
            if ([string]::IsNullOrWhiteSpace($relative)) { continue }
            $target = [System.IO.Path]::GetFullPath((Join-Path $fullDestination ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))))
            if (!$target.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Archive path traversal detected: $($entry.FullName)"
            }
            if ($entry.FullName.EndsWith('/')) {
                New-Item -ItemType Directory -Force -Path $target | Out-Null
                continue
            }
            New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
            $input = $entry.Open()
            try {
                $output = [System.IO.File]::Create($target)
                try { $input.CopyTo($output) } finally { $output.Dispose() }
            } finally { $input.Dispose() }
        }
    } finally { $zip.Dispose() }
}

foreach ($source in $lock.sources) {
    if (!$mapping.ContainsKey($source.name)) { throw "No extraction mapping for locked source: $($source.name)" }
    if ($source.url -notmatch '^https://codeload\.github\.com/') { throw "Unexpected source host: $($source.url)" }
    if ($source.sha256 -notmatch '^[0-9a-f]{64}$') { throw "Invalid SHA-256 in lock for $($source.name)" }

    $archiveName = ($source.name -replace '[^A-Za-z0-9._-]', '_') + '.zip'
    $archivePath = Join-Path $CacheDirectory $archiveName
    $destination = Join-Path $deps $mapping[$source.name]
    $marker = Join-Path $destination '.mlm-source-sha256'

    if ((Test-Path -LiteralPath $destination) -and (Test-Path -LiteralPath $marker)) {
        if ((Get-Content -LiteralPath $marker -Raw).Trim() -eq $source.sha256) {
            Write-Host "Verified source already extracted: $($source.name)"
            continue
        }
    }
    if ((Test-Path -LiteralPath $destination) -and !$Force) {
        throw "Destination exists without matching provenance marker: $destination (use -Force only after review)"
    }

    if (!(Test-Path -LiteralPath $archivePath) -or (Get-Sha256 $archivePath) -ne $source.sha256) {
        if (Test-Path -LiteralPath $archivePath) { Remove-Item -LiteralPath $archivePath -Force }
        Write-Host "Downloading $($source.name)"
        Invoke-WebRequest -Uri $source.url -OutFile $archivePath
    }
    $actual = Get-Sha256 $archivePath
    if ($actual -ne $source.sha256) {
        Remove-Item -LiteralPath $archivePath -Force
        throw "SHA-256 mismatch for $($source.name): expected $($source.sha256), got $actual"
    }

    $parent = Split-Path $destination -Parent
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $staging = "$destination.extracting"
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $staging | Out-Null
    try {
        Expand-VerifiedArchive -Archive $archivePath -Destination $staging
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
        Move-Item -LiteralPath $staging -Destination $destination
        Set-Content -LiteralPath (Join-Path $destination '.mlm-source-sha256') -Value $source.sha256 -NoNewline
        Write-Host "Fetched and verified: $($source.name)"
    } catch {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
        throw
    }
}
Write-Host 'All pinned OpenVPN source archives passed SHA-256 verification and extraction.'
