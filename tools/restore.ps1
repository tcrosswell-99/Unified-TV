# Windows version of restore.sh: put the TV back the way it was before disable.ps1, from the state
# recorded at that time - packages that were already disabled stay disabled, Home goes back to the
# component that owned it and the animation scales go back to their recorded values. Pass the same
# list you used with disable.ps1. The record has to come from this TV: one taken on another TV is
# refused, and with no record at all nothing is sent unless $env:RESTORE_WITHOUT_RECORD=1 asks for
# every listed package to be enabled.
#
# Usage: tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt [-Stock stock.launcher.package]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$Stock = 'com.google.android.tvlauncher',
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

# Same as restore.sh: replay the recorded states instead of enabling the whole list, so packages that
# were already disabled stay disabled (#239). recovery.py reads and validates the list itself, checks
# the record's TV id against the TV, and refuses when the record is another TV's or there is none;
# $env:RESTORE_WITHOUT_RECORD=1 lets it fall back to enabling the whole list.
$cmds = & $py -I (Join-Path $repo 'tools\recovery.py') --commands $List $Stock
if ($LASTEXITCODE -ne 0) { throw 'no restore plan was built (see the message above), the TV was not changed' }
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
