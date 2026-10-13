# guide-tasks 同步契约

作者：张晓达

## 范围

- 真实 SQLite（`node:sqlite`）+ HTTP API（默认仅 `127.0.0.1`）
- 客户端：IndexedDB 事务写任务 + 队列；规则在 WASM `SyncCore`
- 认证：**Bearer Token 会话**（见 [HTTP_AUTH_CONTRACT.md](./HTTP_AUTH_CONTRACT.md)）；可选 `JN_SYNC_TOKEN` / `JN_SYNC_OPEN=1`（测试）

## 状态

| 值 | 含义 |
|----|------|
| 1 LOCAL | 本地已保存（无待传时可短暂） |
| 2 PENDING | 待同步 |
| 3 SYNCING | 同步中 |
| 4 SYNCED | 已同步 |
| 5 CONFLICT | 版本冲突 |
| 6 FAILED | 失败（含临时失败后） |

## 操作与合并

类型：`create` / `update` / `delete`。  
同一任务队列末尾合并（`SyncCore.mergeDecision`）：

- CREATE+UPDATE → 替换为带最新载荷的 CREATE
- CREATE+DELETE（从未同步）→ 两者删除（净零）
- UPDATE+UPDATE / UPDATE+DELETE → 替换末条
- DELETE+* → 丢弃新操作

禁止把上述规则折叠成改变净结果的任意合并。

## API

### `POST /v1/ops`

```json
{ "opId": "...", "type": "create|update|delete", "taskId": "...",
  "expectedVersion": 0, "payload": { "title", "notes", "status", "dueYmd" } }
```

- 幂等：`opId` 与业务写入同一 SQLite 事务
- 版本：`update`/`delete` 要求 `expectedVersion == server.version`
- 冲突：`409` + `{ code:"conflict", server }`
- 故障注入：`dropResponseOnce` 可返回 200 空体；客户端必须用同一 `opId` 重试

### `GET /v1/changes?since=&limit=`

按 `updated_seq` 增量；返回 `next` 游标。

## 冲突 UX

记录级：展示本地 vs 服务器差异；用户选择「采用服务器」或「重新提交本地」（仍带当前服务器版本做 expectedVersion）。删除与修改冲突不静默丢弃。

## 运行

```bash
node examples/guide-tasks/server/sync-server.mjs
# GUIDE_TASKS_SYNC_SERVER http://127.0.0.1:8780/
node examples/guide-tasks/scripts/dev_web.mjs
```

测试：

```bash
node examples/guide-tasks/scripts/verify_sync.mjs
node examples/guide-tasks/scripts/verify_auth_http.mjs
node examples/guide-tasks/scripts/verify_idb_sync.mjs
```

HTTP/认证细节见 [HTTP_AUTH_CONTRACT.md](./HTTP_AUTH_CONTRACT.md)。

## 明确不做

- 字段级自动合并
- 默认公网暴露
- 将 localStorage 当作事务队列
- 用客户端时钟裁决最后写入
- iOS/Android 任意后台持续同步（仅前台/回前台触发）
