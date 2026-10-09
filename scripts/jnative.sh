#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/target/jnative.jar"
if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi
exec java -jar "$JAR" "$@"
