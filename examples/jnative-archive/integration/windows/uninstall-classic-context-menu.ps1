# Author: 张晓达 — remove only JNativeArchive* classic verbs from HKCU.
$ErrorActionPreference = "Stop"
Write-Host "Will remove HKCU classic verbs matching JNativeArchive* only."
if ($env:JN_ARCHIVE_INSTALL_YES -ne "1") {
  $ans = Read-Host "Continue? [y/N]"
  if ($ans -notmatch '^[yY]') { Write-Host "Cancelled"; exit 0 }
}

$roots = @(
  "HKCU:\Software\Classes\*\shell",
  "HKCU:\Software\Classes\Directory\shell",
  "HKCU:\Software\Classes\SystemFileAssociations\.zip\shell"
)
foreach ($root in $roots) {
  if (-not (Test-Path $root)) { continue }
  Get-ChildItem $root -ErrorAction SilentlyContinue | Where-Object {
    $_.PSChildName -like "JNativeArchive*"
  } | ForEach-Object {
    Write-Host "Removing $($_.PSPath)"
    Remove-Item $_.PSPath -Recurse -Force
  }
}
Write-Host "Done. User archives and other apps' verbs untouched."
Write-Host "WINDOWS_E2E_UNVERIFIED"
