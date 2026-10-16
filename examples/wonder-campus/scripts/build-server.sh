#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
mkdir -p "$ROOT/build/wonder-campus"
(cd "$ROOT" && java -jar target/jnative.jar build --project examples/wonder-campus)
echo "BIN=$ROOT/examples/wonder-campus/.jnative/build/wonder-server"
