# 奇想校园 — 能力边界

作者：张晓达

## 已验证（本仓库可复用）

| 能力 | 证据 |
|------|------|
| JNative 原生 HTTP + 静态资源 | ERP `WebApp` / `futurePost` / `staticFiles` |
| SQLite + 事务 + 幂等键 | ERP `ErpDb` / `ErpPosting`；本应用独立库 |
| 整数最小货币单位（分） | 本应用合同/收款/退款全部用 `INTEGER` 分 |
| 课时定点（千分之一课时） | `lesson_milli`，禁止浮点累计 |
| iOS/Android WebView 容器打包 | `docs/en/mobile/status.md`；calculator / guide-tasks |

## 缺口（不得宣称已完成）

| 项 | 状态 |
|----|------|
| JNative 原生 UIKit / SwiftUI / Compose | **Not Supported** |
| 全栈均由 JNative 实现手机 UI | **禁止宣称**；界面为跨平台 WebView 触屏壳 |
| 真机签名 / 商店上架 | **UNVERIFIED**（见 `mobile/BUILD-RECORD.md`） |
| APNs / FCM 系统推送 | **后续单独验证**；首版仅应用内通知 |
| 支付网关 | **不做**；仅人工登记已发生收款 |
| 通用相册 multipart 上传 | **最小落盘+鉴权 URL**；真机相册选取 UNVERIFIED |

## 选定路径

- **服务端**：本目录 JNative 原生进程（权限、排课、收费、课时、考勤）。
- **手机端**：`mobile/www` 触屏壳 + iOS WKWebView / Android WebView 容器。
- **隔离**：默认端口 `18880`（禁止 `18700`）；库 `build/wonder-campus/*.db`；表前缀 `wc_`；不读 ERP 表。

## 多身份

同一 `wc_users` 账号可绑定教务 / 老师 / 家长身份，登录后切换工作台。

## 组织边界（本轮）

- `wc_orgs`：运营组织（排课/权限）
- `wc_billing_entities`：收款主体（合同/收款关联；**不**与运营组织硬编码为同一概念）
- 跨校区使用权益：默认拒绝，需 `wc_campus_entitlement_grants`

## 手机端闭环证据

- 服务端：`docs/MOBILE-LOOP-DELIVERY.md` + `WonderMobileLoopTest`
- 触屏壳：`mobile/www`（非原生 UI）
- 未决规则：`docs/OPEN-RULES.md`
