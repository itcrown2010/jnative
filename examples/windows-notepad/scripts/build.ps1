# Author: 张晓达
# Build the JNative Windows Notepad as a native .exe (no JRE at runtime).
# Requires on the Windows host: Java 17+ (compiler only), target\jnative.jar,
# and clang/MinGW capable of linking Win32 GUI apps.
#
# Output: build\windows-notepad\Notepad.exe
# Status: WINDOWS_E2E_UNVERIFIED until run on a real Windows 10/11 machine.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Repo = Resolve-Path (Join-Path $Root "..\..")
$Jar = if ($env:JNATIVE_JAR) { $env:JNATIVE_JAR } else { Join-Path $Repo "target\jnative.jar" }
$OutDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Repo "build\windows-notepad" }
$Src = Join-Path $Root "src"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-Path $Jar)) {
  Write-Error "Missing $Jar — build the compiler first (mvn -DskipTests package)."
}

$Sources = @(
  "NpCodec.java",
  "NpUi.java",
  "NpFindDialog.java",
  "NpGotoDialog.java",
  "NpWindow.java",
  "NpApp.java"
)
$Args = New-Object System.Collections.Generic.List[string]
foreach ($name in $Sources) {
  $p = Join-Path $Src $name
  if (-not (Test-Path $p)) { Write-Error "Missing $p" }
  $Args.Add((Resolve-Path $p).Path)
}

$Out = Join-Path $OutDir "Notepad.exe"
Write-Host "==> jnative AOT → $Out (native Win32 GUI, no JRE)"
& java -jar $Jar @Args -o $Out --app-type gui -O1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "OK $Out"
Write-Host "Run (no Java needed): $Out"
Write-Host "Status: WINDOWS_E2E_UNVERIFIED"
