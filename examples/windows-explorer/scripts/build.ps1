# Author: 张晓达
# Build the JNative Windows Explorer as a native .exe (no JRE at runtime).
# Requires on the Windows host: Java 17+ (compiler only), target\jnative.jar, clang + ar (LLVM).
#
# Output: build\windows-explorer\WindowsExplorer.exe
# Status: WINDOWS_E2E_UNVERIFIED until run on a real Windows 10/11 machine.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Repo = Resolve-Path (Join-Path $Root "..\..")
$Jar = if ($env:JNATIVE_JAR) { $env:JNATIVE_JAR } else { Join-Path $Repo "target\jnative.jar" }
$OutDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Repo "build\windows-explorer" }
$Cc = if ($env:CC) { $env:CC } else { "clang" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (-not (Test-Path $Jar)) {
  Write-Error "Missing $Jar - build the compiler first (mvn -DskipTests package)."
}

$Obj = Join-Path $OutDir "jn_fm_windows.o"
$Lib = Join-Path $OutDir "libjn_fm.a"
Write-Host "==> compile jn_fm_windows.c ($Cc)"
& $Cc -c -O2 -I (Join-Path $Root "native") (Join-Path $Root "native\jn_fm_windows.c") -o $Obj
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (Test-Path $Lib) { Remove-Item $Lib }
& ar rcs $Lib $Obj
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Sources = Get-ChildItem (Join-Path $Root "src") -Filter *.java | ForEach-Object { $_.FullName }
$Out = Join-Path $OutDir "WindowsExplorer.exe"
Write-Host "==> jnative AOT -> $Out (native Win32 GUI, no JRE)"
& java -jar $Jar @Sources -o $Out --app-type gui -O1 -L $OutDir -l jn_fm -l shell32 -l ole32 -l user32 -l advapi32
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "OK $Out"
Write-Host "Run (no Java needed): $Out [folder]"
Write-Host "Status: WINDOWS_E2E_UNVERIFIED"
