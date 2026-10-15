#!/usr/bin/env bash
# Author: 张晓达
# Echo Archive library sources for multi-unit jnative compile.
set -euo pipefail

archive_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

# JDK-compat + JNative archive API (no CLI entrypoint).
# Note: do not use -path '*/examples/*' — this tree lives under examples/jnative-archive.
archive_lib_sources() {
  local root
  root="$(archive_root)"
  find "$root/src/java/util/zip" "$root/src/org/jnative/archive" \
    -name '*.java' \
    ! -name 'JnArchiveCli.java' \
    | sort
}
