# Author: 张晓达
# Visual QA helper for W11 process-page parity (run on Windows / Parallel).
# Requires: same DPI/theme/window size for ref TM and JNative; optional ImageMagick.
param(
  [string]$RefPng = "",
  [string]$JnPng = "",
  [string]$OutDir = "",
  [string]$Tag = "default"
)

$ErrorActionPreference = "Stop"
$Repo = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
if (-not $OutDir) {
  $OutDir = Join-Path $Repo "docs\screenshots\w11-process"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host @"
=== JNative Task Monitor visual QA ===
1. Set identical Windows theme (light), DPI, and client size.
2. Capture Task Manager Processes page → ref-$Tag.png
3. Capture jnative-task-monitor.exe Processes page → jn-$Tag.png
4. Re-run: .\visual-qa.ps1 -RefPng path\ref.png -JnPng path\jn.png -Tag $Tag

Mask dynamic name/value cells before judging geometry.
See docs\windows-task-monitor-visual-qa.md
Status marker: WINDOWS_E2E_UNVERIFIED until overlays exist.
"@

if (-not $RefPng -or -not $JnPng) {
  Write-Host "No inputs yet — checklist only."
  exit 0
}
if (-not (Test-Path $RefPng)) { Write-Error "Missing $RefPng" }
if (-not (Test-Path $JnPng)) { Write-Error "Missing $JnPng" }

$overlay = Join-Path $OutDir "overlay-$Tag.png"
$diff = Join-Path $OutDir "diff-$Tag.png"

$magick = Get-Command magick -ErrorAction SilentlyContinue
if ($magick) {
  & magick "$RefPng" "$JnPng" -resize 1180x760! -compose blend -define compose:args=50 -composite "$overlay"
  & magick "$RefPng" "$JnPng" -resize 1180x760! -compose difference -composite -auto-level "$diff"
  Write-Host "Wrote $overlay"
  Write-Host "Wrote $diff"
} else {
  Copy-Item $RefPng (Join-Path $OutDir "ref-$Tag.png") -Force
  Copy-Item $JnPng (Join-Path $OutDir "jn-$Tag.png") -Force
  Write-Host "ImageMagick not found — copied inputs only. Install ImageMagick for overlay/diff."
}

Write-Host "Done. Review overlays; do not treat 'uses Windows theme' as visual proof."
