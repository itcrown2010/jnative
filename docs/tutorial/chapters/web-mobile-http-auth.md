# Web / 移动：统一 HTTP 与登录提交

作者：张晓达

## 目标

在真实同步服务上完成：统一 HTTP 结果分类、Token 登录会话、任务表单提交（客户端+服务端校验）、用户隔离。

## 快速验证

```bash
node examples/guide-tasks/scripts/verify_auth_http.mjs
node examples/guide-tasks/scripts/verify_sync.mjs
node examples/guide-tasks/scripts/verify_idb_sync.mjs
```

期望：`GUIDE_TASKS_AUTH_HTTP_OK …`（含 login / isolation / idempotent-lost 等）。

## 本地试用

```bash
node examples/guide-tasks/server/sync-server.mjs
node examples/guide-tasks/scripts/dev_web.mjs
```

浏览器打开开发页 → 登录门禁：

- 用户 `alice` / `password-alice`（或 `bob` / `password-bob`）
- 或「仅本地使用」（本标签页 session，可稍后登录）

令牌在 **sessionStorage**；关闭标签即清除。UI 明确：普通浏览器存储不是安全凭据库。

## 要点

1. HTTP 错误状态码归类为 `http_error`，不会误报成连接失败。
2. 超时/取消会 abort 浏览器请求，但文案不宣称服务器已撤销。
3. 写入用固定 `opId` 幂等；丢响应后重试不会双写。
4. 退出会取消进行中请求并清除本机任务队列，防止串用户。
5. 表单失败保留输入；服务端 `fields` 映射到字段错误。

契约：`examples/guide-tasks/docs/HTTP_AUTH_CONTRACT.md`。
