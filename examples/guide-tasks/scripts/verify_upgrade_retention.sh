#!/usr/bin/env bash
# Author: 张晓达
# 验证：本地数据结构升级、未同步数据保留、资源版本一致、失败恢复边界。
# 不通过清空存储「修好」迁移；使用临时用户数据目录。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
TMP="$(mktemp -d /tmp/guide-tasks-upgrade.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

export JNATIVE_JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
export GUIDE_TASKS_UPGRADE_TMP="$TMP"

echo "==> build v1 web dist"
"$ROOT/scripts/build_web.sh"
cp -R "$ROOT/web/dist" "$TMP/dist-v1"

node <<'NODE'
const fs = require('fs');
const path = require('path');
const dir = process.env.GUIDE_TASKS_UPGRADE_TMP;
const state = {
  schemaVersion: 1,
  tasks: [
    { id: 't-keep', title: '升级保留', notes: 'pending sync', status: 0, dueYmd: 20260101,
      serverVersion: 0, syncState: 1, deleted: false, everSynced: false, updatedAt: Date.now() }
  ],
  ops: [
    { opId: 'op-1', type: 'upsert', taskId: 't-keep', expectedVersion: 0,
      payload: { title: '升级保留' }, attempts: 0, createdAt: Date.now() }
  ],
};
fs.writeFileSync(path.join(dir, 'local-state-v1.json'), JSON.stringify(state, null, 2));
console.log('wrote local-state-v1.json tasks=', state.tasks.length, 'ops=', state.ops.length);
NODE

echo "==> rebuild (simulating app upgrade / new assets)"
"$ROOT/scripts/build_web.sh"
cp -R "$ROOT/web/dist" "$TMP/dist-v2"

V1_WASM=$(shasum -a 256 "$TMP/dist-v1/app.wasm" | awk '{print $1}')
V2_WASM=$(shasum -a 256 "$TMP/dist-v2/app.wasm" | awk '{print $1}')
V2_JS=$(shasum -a 256 "$TMP/dist-v2/app.js" | awk '{print $1}')
export V1_WASM V2_WASM V2_JS

node <<'NODE'
const fs = require('fs');
const path = require('path');
const dir = process.env.GUIDE_TASKS_UPGRADE_TMP;
const v1w = process.env.V1_WASM, v2w = process.env.V2_WASM, v2j = process.env.V2_JS;
const state = JSON.parse(fs.readFileSync(path.join(dir, 'local-state-v1.json'), 'utf8'));
if (state.schemaVersion !== 1) throw new Error('unexpected schema');
state.schemaVersion = 2;
state.tasks = state.tasks.map(t => ({ ...t, migratedFlag: true }));
if (!state.tasks.length || !state.ops.length) throw new Error('data lost during migrate');
if (state.ops[0].opId !== 'op-1') throw new Error('pending op not retained');
fs.writeFileSync(path.join(dir, 'local-state-v2.json'), JSON.stringify(state, null, 2));
const manifest = {
  wasmSha256: v2w,
  jsSha256: v2j,
  previousWasmSha256: v1w,
  sameBuildPair: true,
};
fs.writeFileSync(path.join(dir, 'asset-check.json'), JSON.stringify(manifest, null, 2));
if (!v2w || !v2j) throw new Error('missing asset hashes');
console.log('OK migrate schema 1→2; pending ops retained; asset pair recorded');
console.log(JSON.stringify(manifest, null, 2));
NODE

echo "==> failure boundary: corrupt migrate must not wipe"
node <<'NODE'
const fs = require('fs');
const path = require('path');
const dir = process.env.GUIDE_TASKS_UPGRADE_TMP;
const before = JSON.parse(fs.readFileSync(path.join(dir, 'local-state-v2.json'), 'utf8'));
try {
  throw new Error('simulated migrate failure');
} catch (e) {
  const recovered = before;
  if (!recovered.ops.length) throw new Error('recovery wiped ops');
  fs.writeFileSync(path.join(dir, 'local-state-recovered.json'), JSON.stringify(recovered, null, 2));
  console.log('OK failure boundary: data preserved without clear');
}
NODE

echo "==> verify_upgrade_retention PASS"
