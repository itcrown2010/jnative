# Author: 张晓达
# Windows host build for JNative Archive (Phase B).
# Requires: clang/MSVC + cmake, target/jnative.jar, zlib.
# WINDOWS_E2E_UNVERIFIED until smoke runs on a Windows host — Mac success is not a Windows claim.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Repo = Split-Path -Parent (Split-Path -Parent $Root)
$Jar = if ($env:JNATIVE_JAR) { $env:JNATIVE_JAR } else { Join-Path $Repo "target\jnative.jar" }
$Out = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Root "build" }
$Native = Join-Path $Root "native"
$Tp = Join-Path $Root "third_party"
$MzBuild = Join-Path $Tp "build-windows"

if (-not (Test-Path $Jar)) {
  throw "Missing $Jar — build JNative compiler first."
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null

if (-not (Test-Path (Join-Path $MzBuild "minizip-ng.lib")) -and
    -not (Test-Path (Join-Path $MzBuild "Release\minizip-ng.lib")) -and
    -not (Test-Path (Join-Path $MzBuild "libminizip-ng.a"))) {
  Write-Host "==> cmake minizip-ng (Windows)"
  $Src = Join-Path $Tp "minizip-ng"
  if (-not (Test-Path (Join-Path $Src "CMakeLists.txt"))) {
    throw "Vendor minizip-ng 4.0.8 before build.ps1 (or run fetch-deps on a POSIX host and copy)"
  }
  New-Item -ItemType Directory -Force -Path $MzBuild | Out-Null
  cmake -S $Src -B $MzBuild -DCMAKE_BUILD_TYPE=Release `
    -DMZ_COMPAT=OFF -DMZ_BZIP2=OFF -DMZ_LZMA=OFF -DMZ_ZSTD=OFF `
    -DMZ_LIBCOMP=OFF -DMZ_OPENSSL=OFF -DMZ_PKCRYPT=OFF -DMZ_WZAES=ON `
    -DMZ_ICONV=ON -DMZ_FETCH_LIBS=ON -DBUILD_SHARED_LIBS=OFF `
    -DMZ_BUILD_TESTS=OFF -DMZ_BUILD_UNIT_TESTS=OFF
  cmake --build $MzBuild --config Release --target minizip-ng
}

Write-Host "==> compile jn_zip.c"
$Cc = if ($env:CC) { $env:CC } else { "clang" }
& $Cc -c -O2 "-I$Native" "-I$Tp\minizip-ng" -DHAVE_ZLIB -D_WIN32 `
  "$Native\jn_zip.c" -o "$Out\jn_zip.o"
if ($LASTEXITCODE -ne 0) { throw "jn_zip.c compile failed" }

$MzLib = $null
foreach ($cand in @(
    (Join-Path $MzBuild "libminizip-ng.a"),
    (Join-Path $MzBuild "Release\minizip-ng.lib"),
    (Join-Path $MzBuild "minizip-ng.lib")
  )) {
  if (Test-Path $cand) { $MzLib = $cand; break }
}
if (-not $MzLib) { throw "minizip-ng library not found under $MzBuild" }

$JnZipLib = Join-Path $Out "libjn_zip.a"
if ($MzLib -like "*.a") {
  $Tmp = Join-Path $Out "mz_objs"
  Remove-Item -Recurse -Force $Tmp -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $Tmp | Out-Null
  Push-Location $Tmp
  ar x $MzLib
  Pop-Location
  Remove-Item -Force $JnZipLib -ErrorAction SilentlyContinue
  $objs = @("$Out\jn_zip.o") + (Get-ChildItem $Tmp -Filter "*.o" | ForEach-Object { $_.FullName })
  ar rcs $JnZipLib @objs
} else {
  Write-Host "MSVC .lib detected — linking jn_zip.o + $MzLib separately"
  Copy-Item $MzLib (Join-Path $Out "minizip-ng.lib") -Force
  ar rcs $JnZipLib "$Out\jn_zip.o"
}

# Multi-unit compile (no source concat). WINDOWS_E2E_UNVERIFIED on this host.
$libSrcs = @()
$libSrcs += Get-ChildItem (Join-Path $Root "src\java\util\zip") -Filter "*.java" | ForEach-Object { $_.FullName }
$libSrcs += Get-ChildItem (Join-Path $Root "src\org\jnative\archive") -Recurse -Filter "*.java" |
  Where-Object { $_.Name -ne "JnArchiveCli.java" } | ForEach-Object { $_.FullName }
$cliSrc = Join-Path $Root "src\org\jnative\archive\JnArchiveCli.java"
$Bin = Join-Path $Out "jn-archive.exe"
Write-Host "==> jnative multi-unit → $Bin"
$linkExtra = @("-L", $Out, "-l", "jn_zip", "-l", "z")
if ($MzLib -like "*.lib") {
  $linkExtra += @("-L", $Out, "-l", "minizip-ng")
}
# bcrypt / advapi may be needed depending on minizip crypto backend — verify on Windows host
& java -jar $Jar @libSrcs $cliSrc -o $Bin -O1 @linkExtra
if ($LASTEXITCODE -ne 0) { throw "jnative link failed" }

Write-Host "OK $Bin"
Write-Host "WINDOWS_E2E_UNVERIFIED: run smoke on Windows 10/11 x64; Unicode path + TTY password need host validation."
