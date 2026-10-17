# Author: 张晓达
# Build the JNative Windows Task Monitor (Windows, MinGW/LLVM clang + JDK 17+ to run the compiler jar).
# Output: build\windows-task-monitor\jnative-task-monitor.exe
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Repo = (Resolve-Path (Join-Path $Root "..\..")).Path
$Jar = if ($env:JNATIVE_JAR) { $env:JNATIVE_JAR } else { Join-Path $Repo "target\jnative.jar" }
$OutDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Repo "build\windows-task-monitor" }
$AmNative = (Resolve-Path (Join-Path $Root "..\activity-monitor\native")).Path
$TmNative = Join-Path $Root "native"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (-not (Test-Path $Jar)) {
  Write-Error "Missing $Jar - build the compiler first (mvn -DskipTests package)."
}

function Build-Static($srcFile, $name) {
  $obj = Join-Path $OutDir "$name.o"
  $lib = Join-Path $OutDir "lib$name.a"
  & clang -c -O2 -I"$AmNative" -I"$TmNative" "$srcFile" -o "$obj"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  if (Test-Path $lib) { Remove-Item $lib }
  & ar rcs "$lib" "$obj"
  Copy-Item $lib (Join-Path $OutDir "$name.lib") -Force
}

Write-Host "==> compile native libs (jn_am, jn_tm)"
Build-Static (Join-Path $AmNative "jn_am_windows.c") "jn_am"
Build-Static (Join-Path $TmNative "jn_tm_windows.c") "jn_tm"

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
  "TmTheme.java",
  "TmData.java",
  "TmGrid.java",
  "TmBuild.java",
  "TmPerf.java",
  "TmDialogs.java",
  "TaskMonitor.java"
)
$Parts = New-Object System.Collections.Generic.List[string]
foreach ($name in $SrcOrder) {
  $p = Join-Path $Root "src\$name"
  if (-not (Test-Path $p)) { Write-Error "Missing $p" }
  $t = [System.IO.File]::ReadAllText((Resolve-Path $p), $Utf8)
  if ($t.Length -gt 0 -and $t[0] -eq [char]0xFEFF) { $t = $t.Substring(1) }
  $Parts.Add($t.Replace("`r`n", "`n"))
}
$MergedText = "// AUTO-GENERATED - windows-task-monitor`n" + ($body -join "`n").Replace("`r", "") + "`n" + ($Parts -join "`n`n")
[System.IO.File]::WriteAllText($Merged, $MergedText, $Utf8)

$Out = Join-Path $OutDir "jnative-task-monitor.exe"
Write-Host "==> jnative -> $Out"
& java -jar $Jar $Merged -o $Out --app-type gui -O1 -L $OutDir -l jn_am -l jn_tm -l version -l advapi32 -l iphlpapi -l shell32 -l ole32
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "OK $Out"
