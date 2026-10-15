# Author: 张晓达 — Windows launcher: write job file, start one GUI instance.
# WINDOWS_E2E_UNVERIFIED
param(
  [Parameter(Mandatory = $true)][string]$Action,
  [Parameter(ValueFromRemainingArguments = $true)][string[]]$Paths
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$App = if ($env:JNATIVE_ARCHIVE_EXE) { $env:JNATIVE_ARCHIVE_EXE } else { Join-Path $Root "build\jn-archive-gui.exe" }
if (-not (Test-Path $App)) { throw "Missing $App" }
if (-not $Paths -or $Paths.Count -eq 0) { throw "No paths" }

$inbox = Join-Path $env:APPDATA "JNativeArchive\shell-inbox"
New-Item -ItemType Directory -Force -Path $inbox | Out-Null
$job = Join-Path $inbox ("job-{0}-{1}.job" -f [DateTimeOffset]::UtcNow.ToUnixTimeSeconds(), $PID)

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("JN1")
[void]$sb.AppendLine($Action)
[void]$sb.AppendLine([string]$Paths.Count)
foreach ($p in $Paths) {
  $ap = [System.IO.Path]::GetFullPath($p)
  [void]$sb.AppendLine([string]$ap.Length)
  [void]$sb.AppendLine($ap)
}
[System.IO.File]::WriteAllText($job, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))

# Single activation — MultiSelectModel=Player should pass all paths to one launch.
# If Explorer still spawns multiple processes, inbox queue serializes work in the app.
$running = Get-Process -Name "jn-archive-gui" -ErrorAction SilentlyContinue
if ($running) {
  # Already running: inbox poller will pick up the job.
  Write-Host "Queued for running instance: $job"
} else {
  Start-Process -FilePath $App | Out-Null
  Write-Host "Started app with job: $job"
}
Write-Host "WINDOWS_E2E_UNVERIFIED"
