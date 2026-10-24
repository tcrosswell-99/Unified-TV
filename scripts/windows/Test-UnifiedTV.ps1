<#
Unified TV Preview: user-authorised Windows PC -> Android TV test.
No LAN-wide scan, no TV cleanup, no Google TV Home replacement, no internet uploads.
Requires Google's official adb.exe: https://developer.android.com/tools/releases/platform-tools
#>
[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$PairAddress,
    [string]$ConnectAddress,
    [string]$Serial,
    [switch]$InspectOnly,
    [switch]$RemovePreview
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Write-Host "Unified TV | Safe Windows to Android TV test" -ForegroundColor Cyan
Write-Host "This script NEVER disables TV packages or changes the system Home."
$command = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($command) { $adb = $command.Source }
else {
    $candidate = Join-Path $PSScriptRoot "platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
    else { throw "Install official Android SDK Platform Tools and add adb.exe to PATH, or place platform-tools beside this script." }
}
& $adb start-server | Out-Null
if ($LASTEXITCODE -ne 0) { throw "ADB server failed." }
if ($PairAddress) {
    Write-Host "Pairing at $PairAddress. Enter the pairing code from your TV when prompted."
    & $adb pair $PairAddress
    if ($LASTEXITCODE -ne 0) { throw "Pair failed. Confirm the pairing port and code on TV." }
}
if ($ConnectAddress) {
    & $adb connect $ConnectAddress
    if ($LASTEXITCODE -ne 0) { throw "Connect failed. Debug port differs from pairing port." }
}
$rows = @(& $adb devices -l)
$devices = @($rows | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -eq 0) {
    Write-Host "No authorised TV connection yet." -ForegroundColor Yellow
    Write-Host "On TCL: Settings > System > About > Android TV OS build (press OK 7 times)."
    Write-Host "Then System > Developer options > Wireless debugging, if supported."
    Write-Host "Use 'Pair device with pairing code' to get IP:PAIRPORT and pairing code."
    Write-Host "Note also the different IP:DEBUGPORT shown on the Wireless debugging page."
    Write-Host "Run this script again with -PairAddress 'IP:PAIRPORT' -ConnectAddress 'IP:DEBUGPORT'."
    exit 1
}
if (-not $Serial -and $devices.Count -gt 1) { throw "Multiple devices connected. Supply -Serial for the intended TV." }
if (-not $Serial) { $Serial = $devices[0] }
if ($devices -notcontains $Serial) { throw "Selected -Serial is not a connected ADB device." }
$prefix = @("-s", $Serial)
$model = ((& $adb @prefix shell getprop ro.product.model) -join "").Trim()
$maker = ((& $adb @prefix shell getprop ro.product.manufacturer) -join "").Trim()
$os = ((& $adb @prefix shell getprop ro.build.version.release) -join "").Trim()
$profile = ((& $adb @prefix shell getprop ro.build.characteristics) -join "").Trim()
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect device." }
Write-Host "Connected: $maker $model | Android $os | $profile" -ForegroundColor Green
if ($profile -notmatch 'tv') { throw "Device is not recognised as Android TV. No installation attempted." }
if ($InspectOnly) { Write-Host "Read-only inspection completed."; exit 0 }
if ($RemovePreview) {
    if ((Read-Host "Type REMOVE to uninstall ONLY io.unifiedtv.preview") -cne "REMOVE") { Write-Host "Cancelled."; exit 0 }
    & $adb @prefix uninstall io.unifiedtv.preview
    if ($LASTEXITCODE -ne 0) { throw "Preview uninstall failed." }
    Write-Host "Preview removed. Stock Google TV Home remains unchanged."
    exit 0
}
if (-not $ApkPath) {
    Write-Host "Connection is ready. Supply -ApkPath to install the separate preview APK."
    exit 0
}
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath" }
if ([IO.Path]::GetExtension($ApkPath).ToLowerInvariant() -ne ".apk") { throw "Expected an .apk file." }
Write-Host "Target $maker $model; separate package io.unifiedtv.preview; no HOME registration."
if ((Read-Host "Type INSTALL to authorise installation") -cne "INSTALL") { Write-Host "Cancelled."; exit 0 }
& $adb @prefix install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw "Install failed. If an earlier CI build has a different temporary signature, uninstall ONLY the old preview before retrying." }
& $adb @prefix shell am start -W -n "io.unifiedtv.preview/.UnifiedPreviewActivity"
if ($LASTEXITCODE -ne 0) { throw "Couldn't launch preview." }
Write-Host "Now test with TCL remote: Left/Right select; Up lists apps; hold OK on dock = edit; Back cancels."
Write-Host "Live TV intentionally stays blocked until VPN protection passes testing."
Write-Host "Uninstall later with -RemovePreview. Switch Wireless debugging OFF on the TV afterwards."
