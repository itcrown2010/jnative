#!/usr/bin/env bash
# Author: 张晓达
# Fetch and build pinned minizip-ng for JNative Archive (reproducible deps).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TP="$ROOT/third_party"
VER="${MINIZIP_NG_VERSION:-4.0.8}"
SRC="$TP/minizip-ng"
URL="https://github.com/zlib-ng/minizip-ng/archive/refs/tags/${VER}.tar.gz"
SHA_FILE="$TP/minizip-ng.CMAKE.sha256"

mkdir -p "$TP"
echo "==> minizip-ng ${VER}"

if [[ ! -d "$SRC/.git" && ! -f "$SRC/CMakeLists.txt" ]]; then
  TMP="$(mktemp -d)"
  ARCHIVE="$TMP/minizip-ng-${VER}.tar.gz"
  echo "==> download $URL"
  curl -fsSL -o "$ARCHIVE" "$URL"
  if [[ -f "$SHA_FILE" ]]; then
    EXPECT="$(awk '{print $1}' "$SHA_FILE" | head -1)"
    if [[ -n "$EXPECT" && "$EXPECT" != "SKIP" ]]; then
      ACTUAL="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
      if [[ "$ACTUAL" != "$EXPECT" ]]; then
        echo "SHA256 mismatch: expected $EXPECT got $ACTUAL" >&2
        exit 1
      fi
    fi
  fi
  tar -xzf "$ARCHIVE" -C "$TMP"
  rm -rf "$SRC"
  mv "$TMP/minizip-ng-${VER}" "$SRC"
  rm -rf "$TMP"
fi

echo "VER=${VER}" > "$TP/VERSION.txt"

OS="$(uname -s 2>/dev/null || echo unknown)"
BUILD_DIR=""
CMAKE_ARGS=(
  -DCMAKE_BUILD_TYPE=Release
  -DMZ_COMPAT=OFF
  -DMZ_BZIP2=OFF
  -DMZ_LZMA=OFF
  -DMZ_ZSTD=OFF
  -DMZ_LIBCOMP=OFF
  -DMZ_OPENSSL=OFF
  -DMZ_PKCRYPT=OFF
  -DMZ_WZAES=ON
  -DMZ_ICONV=ON
  -DMZ_FETCH_LIBS=OFF
  -DBUILD_SHARED_LIBS=OFF
  -DMZ_BUILD_TESTS=OFF
  -DMZ_BUILD_UNIT_TESTS=OFF
)

if [[ "$OS" == Darwin ]]; then
  BUILD_DIR="$TP/build-macos"
  CMAKE_ARGS+=(-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0)
elif [[ "$OS" == MINGW* || "$OS" == MSYS* || "$OS" == CYGWIN* ]]; then
  BUILD_DIR="$TP/build-windows"
else
  BUILD_DIR="$TP/build-linux"
fi

mkdir -p "$BUILD_DIR"
echo "==> cmake → $BUILD_DIR"
cmake -S "$SRC" -B "$BUILD_DIR" "${CMAKE_ARGS[@]}"
cmake --build "$BUILD_DIR" --config Release --target minizip-ng -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)"

LIB=""
if [[ -f "$BUILD_DIR/libminizip-ng.a" ]]; then
  LIB="$BUILD_DIR/libminizip-ng.a"
elif [[ -f "$BUILD_DIR/Release/minizip-ng.lib" ]]; then
  LIB="$BUILD_DIR/Release/minizip-ng.lib"
elif [[ -f "$BUILD_DIR/minizip-ng.lib" ]]; then
  LIB="$BUILD_DIR/minizip-ng.lib"
fi
if [[ -z "$LIB" ]]; then
  echo "minizip-ng static library not found under $BUILD_DIR" >&2
  exit 1
fi
echo "OK $LIB"
echo "Config: MZ_WZAES=ON MZ_PKCRYPT=OFF (no ZipCrypto); zlib system; Apple CommonCrypto on macOS"
