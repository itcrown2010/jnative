# Author: 张晓达
# Build JNative Task Monitor on Windows x64 (MinGW clang or LLVM clang).
# Requires: Java 17+, target\jnative.jar, clang capable of host Windows linking.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Repo = Resolve-Path (Join-Path $Root "..\..")
$Jar = if ($env:JNATIVE_JAR) { $env:JNATIVE_JAR } else { Join-Path $Repo "target\jnative.jar" }
$OutDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Repo "build\windows-task-monitor" }
$NativeDir = Join-Path $Root "..\activity-monitor\native"
$NativeDir = Resolve-Path $NativeDir

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-Path $Jar)) {
  Write-Error "Missing $Jar — build the compiler first (mvn -DskipTests package)."
}

Write-Host "==> compile jn_am (Windows)"
$Src = Join-Path $NativeDir "jn_am_windows.c"
$Obj = Join-Path $OutDir "jn_am_windows.o"
$Lib = Join-Path $OutDir "libjn_am.a"
& clang -c -O2 -I"$NativeDir" "$Src" -o "$Obj"
if (Test-Path $Lib) { Remove-Item $Lib }
& ar rcs "$Lib" "$Obj"
$LibA = Join-Path $OutDir "jn_am.lib"
Copy-Item $Lib $LibA -Force

Write-Host "==> merge sources"
$Merged = Join-Path $OutDir "merged-task-monitor.java"
$Utf8 = New-Object System.Text.UTF8Encoding $false
$CorePath = Join-Path $Root "..\activity-monitor\core\AmCore.java"
$Core = [System.IO.File]::ReadAllText((Resolve-Path $CorePath), $Utf8)
if ($Core.Length -gt 0 -and $Core[0] -eq [char]0xFEFF) { $Core = $Core.Substring(1) }
$Core = $Core -replace "public class AmCore", "class AmCore"
$lines = $Core -split "`n"
$body = New-Object System.Collections.Generic.List[string]
$i = 0
while ($i -lt $lines.Length) {
  if ($lines[$i] -match '^\s*public static void main\(String\[\] args\) \{') {
    $depth = 0
    $started = $false
    while ($i -lt $lines.Length) {
      foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++; $started = $true }
        elseif ($ch -eq '}') { $depth-- }
      }
      $i++
      if ($started -and $depth -eq 0) { break }
    }
    continue
  }
  $body.Add($lines[$i])
  $i++
}

$SrcOrder = @(
  "TmTokens.java",
  "TmViewModel.java",
  "TaskMonitor.java",
  "TmActions.java",
  "TmCommands.java"
)
$DeskParts = New-Object System.Collections.Generic.List[string]
foreach ($name in $SrcOrder) {
  $p = Join-Path $Root "src\$name"
  if (-not (Test-Path $p)) { Write-Error "Missing $p" }
  $t = [System.IO.File]::ReadAllText((Resolve-Path $p), $Utf8)
  if ($t.Length -gt 0 -and $t[0] -eq [char]0xFEFF) { $t = $t.Substring(1) }
  $DeskParts.Add($t)
}
$MergedText = "// AUTO-GENERATED - windows-task-monitor`n" + ($body -join "`n") + "`n" + ($DeskParts -join "`n`n")
[System.IO.File]::WriteAllText($Merged, $MergedText, $Utf8)

$Out = Join-Path $OutDir "jnative-task-monitor.exe"
Write-Host "==> jnative → $Out"
& java -jar $Jar $Merged -o $Out --app-type gui -O1 -L $OutDir -l jn_am
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "OK $Out"
Write-Host "Run: $Out"
Write-Host "Dump: $Out --dump"
Write-Host "Status: WINDOWS_E2E_UNVERIFIED until Parallel visual QA"
