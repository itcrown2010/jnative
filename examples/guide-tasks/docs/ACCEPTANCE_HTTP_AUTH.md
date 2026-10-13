# 验收结果：HTTP / 认证 / 表单提交

作者：张晓达  
日期：2026-10-13

## 环境

- 独立 SQLite：`.build-staging/sync-data/verify-*.db`
- 真实 `sync-server.mjs`（Token 或 OPEN 测试模式）
- Web：Chrome + Puppeteer（`verify_idb_sync.mjs`）
- 移动：与 Web 同壳资产（`sync_mobile_assets.sh`）；设备侧需本机可达的开发机服务

## 命令与结果

| 脚本 | 结果 |
|------|------|
| `node examples/guide-tasks/scripts/verify_auth_http.mjs` | `GUIDE_TASKS_AUTH_HTTP_OK login-fail,login-ok,session-me,crud-write,validation,timeout,idempotent-lost,logout-revoke,isolation,delete,bad-token-http_error` |
| `node examples/guide-tasks/scripts/verify_sync.mjs` | `GUIDE_TASKS_SYNC_OK health,create,idempotent-retry,version-conflict,delete-edit-conflict,cursor,syncore-wasm` |
| `node examples/guide-tasks/scripts/verify_idb_sync.mjs` | `GUIDE_TASKS_IDB_SYNC_OK offline-create,reload-queue,sync-upload,conflict-ui` |

## 对照验收项

1. 登录成功与失败 — 覆盖（401 归类为 `http_error`，非 network）
2. 会话恢复及过期 — `me` + 吊销后 401；客户端 `restoreSession` / 过期门禁
3. 新增、编辑、删除落库 — auth + sync 脚本
4. 非法输入与权限拒绝 — `fields` 校验；跨用户不可见/不可写
5. 网络中断、超时、非法/空响应 — timeout 分类；`dropResponseOnce` 空体
6. 重复提交 — 同 `opId` 幂等
7. 服务已写入但响应丢失 — idempotent-lost / idempotent-retry
8. 页面离开及退出 — `abortAllHttp` + pullGen；退出清 IDB
9. 不同用户隔离 — bob 看不到 alice 任务
10. Web 已测；移动同壳同步资产，真机依赖开发机 URL

故障注入仅 `/v1/admin/fault`（localhost），非公开业务接口。
