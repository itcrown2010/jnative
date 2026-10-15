# Author: 张晓达
# Classic Windows context menu (HKCU) for JNative Archive.
# Windows 11 modern "Show more options" / IExplorerCommand package: FUTURE — not claimed here.
# WINDOWS_E2E_UNVERIFIED — no Windows host run in this delivery.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$App = if ($env:JNATIVE_ARCHIVE_EXE) { $env:JNATIVE_ARCHIVE_EXE } else { Join-Path $Root "build\jn-archive-gui.exe" }
$Launcher = Join-Path $PSScriptRoot "jn-archive-launch.ps1"

if (-not (Test-Path $App)) {
  throw "Missing GUI binary: $App — build on Windows first (WINDOWS_E2E_UNVERIFIED)."
}
if (-not (Test-Path $Launcher)) {
  throw "Missing launcher: $Launcher"
}

Write-Host @"
Will register CURRENT-USER classic context menu keys under HKCU:\Software\Classes
  *\shell\JNativeArchive*
  Directory\shell\JNativeArchive*
  SystemFileAssociations\.zip\shell\JNativeArchive*
Does NOT change the default ZIP handler.
Does NOT register Windows 11 modern sparse package menu (FUTURE).
Launcher: $Launcher
App: $App
"@

if ($env:JN_ARCHIVE_INSTALL_YES -ne "1") {
  $ans = Read-Host "Continue? [y/N]"
  if ($ans -notmatch '^[yY]') { Write-Host "Cancelled"; exit 0 }
}

function Register-Verb($keyPath, $title, $action) {
  $base = "HKCU:\Software\Classes\$keyPath"
  New-Item -Path $base -Force | Out-Null
  Set-ItemProperty -Path $base -Name "(default)" -Value $title
  Set-ItemProperty -Path $base -Name "MultiSelectModel" -Value "Player"
  Set-ItemProperty -Path $base -Name "Icon" -Value $App
  $cmd = Join-Path $base "command"
  New-Item -Path $cmd -Force | Out-Null
  # %* expands to selected paths; launcher writes job file — no password on cmdline.
  $line = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$Launcher`" -Action $action -Paths %*"
  Set-ItemProperty -Path $cmd -Name "(default)" -Value $line
}

Register-Verb "*\shell\JNativeArchiveCompress" "JNative Archive: Compress ZIP" "compress"
Register-Verb "*\shell\JNativeArchiveCompressPw" "JNative Archive: Compress ZIP (Password)" "compress-password"
Register-Verb "Directory\shell\JNativeArchiveCompress" "JNative Archive: Compress ZIP" "compress"
Register-Verb "Directory\shell\JNativeArchiveCompressPw" "JNative Archive: Compress ZIP (Password)" "compress-password"
Register-Verb "SystemFileAssociations\.zip\shell\JNativeArchiveOpen" "JNative Archive: Open" "open"
Register-Verb "SystemFileAssociations\.zip\shell\JNativeArchiveExtractHere" "JNative Archive: Extract Here" "extract-here"
Register-Verb "SystemFileAssociations\.zip\shell\JNativeArchiveExtractFolder" "JNative Archive: Extract to Folder" "extract-sibling"
Register-Verb "SystemFileAssociations\.zip\shell\JNativeArchiveExtractTo" "JNative Archive: Extract to..." "extract-to"

Write-Host "Classic context menu installed (HKCU)."
Write-Host "Windows 11 modern top-level menu: FUTURE / UNIMPLEMENTED"
Write-Host "WINDOWS_E2E_UNVERIFIED"
