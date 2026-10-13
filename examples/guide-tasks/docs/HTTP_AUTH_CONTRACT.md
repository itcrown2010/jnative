# guide-tasks：HTTP 结果与认证契约

作者：张晓达

## 认证方案（单一）

采用 **Bearer Token 会话**（`Authorization: Bearer …`）。

- **不**并行实现 Cookie 会话 / CSRF（避免两套冲突）。
- 登录：`POST /v1/auth/login` → `{ token, userId, username, expiresAt }`
- 恢复：`GET /v1/auth/me`
- 退出：`POST /v1/auth/logout`（服务端 `revoked=1`）
- 可选自动化：`JN_SYNC_TOKEN` 静态服务令牌，或 `JN_SYNC_OPEN=1`（仅本地契约测试）
- 客户端令牌默认存 **sessionStorage**（关标签即清）。**普通浏览器存储不是安全凭据库**，文档与 UI 均如此声明。

本机开发种子用户（勿对公网）：`alice` / `password-alice`，`bob` / `password-bob`。

## HTTP 结果分类（`web/lib/httpClient.js`）

| kind | 含义 |
|------|------|
| `ok` | 2xx，含可解析 JSON（或空体由上层按幂等处理） |
| `http_error` | 非成功状态码（**不是**连接失败） |
| `network` | `fetch` 传输失败（DNS/连接拒绝等） |
| `timeout` | 客户端超时并 `AbortController.abort()` |
| `cancelled` | 用户/页面卸载取消，已 abort 浏览器侧请求 |
| `parse_error` | 体过大、非 JSON 等 |

超时与取消：**会中止浏览器侧请求**；**不能保证服务器侧操作已取消**。消息文案明确「服务器是否完成未知」。凭据不进入诊断日志。

## 请求能力

- 方法：GET / POST（任务写删均走 `POST /v1/ops`）
- 请求头、JSON 编解码、Content-Type 检查、1 MiB 响应上界
- `abortAllHttp()`：页面 `pagehide` / 退出时清理
- **默认不对非幂等写入自动换新 op**；临时失败仅用**同一 `opId`** 重试（服务端幂等表）

CORS：显式来源白名单（`JN_SYNC_CORS_ORIGINS`），不使用 `*` 放开所有来源。

## 表单与权限

- 客户端即时校验（`formController` + `TaskCore`）
- 服务端再次校验 → `400` + `fields`
- 提交中防重复（`submitting` 锁）
- 失败保留输入；成功采用服务器 `task` 权威字段
- 版本冲突 → 既有冲突 UI
- 归属按 `user_id` 服务端过滤；隐藏按钮不是安全边界

## 会话 UX

登录 / 恢复 / 退出 / 过期提示 / `return-hash` 回原页。  
退出：abort 请求、作废 in-flight pull、清 IDB 业务数据，避免串用户。  
未同步数据切换用户时**明确确认**，不静默上传到错误账户。

## 平台矩阵（网络与认证）

| 能力 | Web (浏览器) | Android WebView | iOS WKWebView |
|------|--------------|-----------------|---------------|
| `fetch` + AbortController | ✅ | ✅（同壳） | ✅（同壳） |
| Token 登录 / sessionStorage | ✅ | ✅ | ✅ |
| IndexedDB 队列 | ✅ | ✅ | ✅ |
| 回前台触发同步 | `visibilitychange` | 同左 | 同左 |
| 系统级后台常驻同步 | ❌ 不承诺 | ❌ | ❌ |
| Cookie 会话 | ❌ 不采用 | ❌ | ❌ |

## 验收脚本

```bash
node examples/guide-tasks/scripts/verify_sync.mjs          # OPEN 模式 ops 契约
node examples/guide-tasks/scripts/verify_auth_http.mjs     # 真实 Token + 隔离
node examples/guide-tasks/scripts/verify_idb_sync.mjs      # 浏览器队列 + 冲突
```
