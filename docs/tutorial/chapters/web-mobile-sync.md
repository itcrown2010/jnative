# Web / 移动：离线队列与同步

作者：张晓达

## 里程碑

1. 单客户端离线操作 + 可靠重试（IndexedDB 事务队列 + 幂等 `opId`）
2. 版本冲突与多客户端（409 + 冲突界面）
3. Service Worker 整包缓存 + 移动回前台触发同步

## 快速验证

```bash
./examples/guide-tasks/scripts/verify_core.sh
./examples/guide-tasks/scripts/verify_web.sh
node examples/guide-tasks/scripts/verify_sync.mjs
node examples/guide-tasks/scripts/verify_auth_http.mjs
node examples/guide-tasks/scripts/verify_idb_sync.mjs
```

期望：`GUIDE_TASKS_SYNC_OK …`、`GUIDE_TASKS_AUTH_HTTP_OK …` 与 `GUIDE_TASKS_IDB_SYNC_OK offline-create,reload-queue,sync-upload,conflict-ui`。

登录与 HTTP 分类见 [web-mobile-http-auth.md](./web-mobile-http-auth.md)。

## 架构

| 层 | 职责 |
|----|------|
| `TaskCore` | 表单校验 |
| `SyncCore` | 合并规则、状态机、错误分类、退避 |
| IndexedDB | 任务 + 队列同一事务 |
| `sync-server.mjs` | SQLite + 幂等 + 游标 |
| JS 壳 | 存储/网络/多标签锁/冲突 UI |

契约详见 `examples/guide-tasks/docs/SYNC_CONTRACT.md`。

## 本地开发

```bash
node examples/guide-tasks/server/sync-server.mjs
node examples/guide-tasks/scripts/dev_web.mjs
```

启动后先登录（Token），或「仅本地使用」。设置页可改同步 URL。非 loopback 需 `JN_SYNC_ALLOW_PUBLIC=1`。

## 界面提示

同步条显示：用户 / 在线 / 待同步 / 阶段。  
详情页冲突框：采用服务器 / 重新提交本地。  
保存成功仅表示 **本地事务已提交**；未上传时显示「待同步」。

## 移动

WebView 复用同一壳；Android 已声明 `INTERNET` 以便指向开发机同步服务。  
前后台：`visibilitychange` 回前台触发同步；**不承诺**系统级后台常驻同步。
