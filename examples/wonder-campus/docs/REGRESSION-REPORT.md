# 奇想校园第一阶段 — 权限与双链路回归报告

作者：张晓达

日期：2026-10-16

## 范围

独立应用 `examples/wonder-campus/`：多身份、课时包 / 学期制合同、资金与权益流水、排课签到请假补课。

隔离：端口默认 **18880**（拒绝 18700）；库 `build/wonder-campus/*.db`；表前缀 `wc_`；不复用 ERP 表。

## 证据入口

| 项 | 路径 |
|----|------|
| 能力边界 | `CAPABILITY.md` |
| 收费/考勤规则 | `RULES.md` |
| 数据模型 | `migrations/001_init_sqlite.sql` |
| 业务服务 | `server/WcServices.java` |
| HTTP API | `server/WonderServer.java` |
| E2E | `tests/WonderE2eTest.java` + `scripts/e2e-accept.sh` |
| 手机壳声明 | `mobile/BUILD-RECORD.md` |

## 场景 A — 英语课时包

| 步骤 | 期望 | 结果 |
|------|------|------|
| 购 40 课时（40000 lesson_milli） | 合同 + 余额桶 | **PASS** |
| 登记微信收款 | 实收累加、收据幂等 | **PASS** |
| 排课 | 生成 session | **PASS** |
| 冲突排课 | CONFLICT | **PASS** |
| 签到扣 1000 milli | 余额减 1 课时 | **PASS** |
| 重复签到 | 拒绝且不重复扣 | **PASS** |
| 请假 + 补课 | 审批生成 makeup session | **PASS** |
| 撤销错误签到 | 余额回补 | **PASS** |
| 家长关联核对 | `isGuardianOf` + ledger | **PASS** |

## 场景 B — 画画学期制

| 步骤 | 期望 | 结果 |
|------|------|------|
| 学期报名（承诺 16 次） | TERM 合同，非无限课时包 | **PASS** |
| 分次收款 | 两笔实收登记 | **PASS** |
| 学校停课 | CANCELLED_BY_SCHOOL，不记学生缺勤 | **PASS** |
| 改期/替课 | 新安排 | **PASS** |
| 请假补课 | leave + makeup | **PASS** |
| 签到 + 课后反馈 | PRESENT + 应用内通知 | **PASS** |
| 部分退款 | 不超过可退 | **PASS** |
| 超额退款 | 拒绝 | **PASS** |

## 负例

| 用例 | 期望 | 结果 |
|------|------|------|
| 老师访问未授权校区 | `canAccessCampus` = 0 | **PASS** |
| 错误家长请假 | FORBIDDEN | **PASS** |
| 课时包过期签到 | 拒绝 | **PASS** |
| 重复收款幂等键 | 同一 paymentId | **PASS** |

## HTTP 权限面（API）

| 身份 | 允许 | 拒绝示例 |
|------|------|----------|
| ADMIN | 报名/收款/排课/停课/批假补课/撤销签到 | — |
| TEACHER | 签到、反馈、老师工作台 | 报名收费 |
| PARENT | 请假、孩子列表、合同流水（本人关联） | 他人孩子 ledger |

## 金额与课时单位

- 货币：整数分（`*_cents`）
- 课时：整数 `lesson_milli`（1000 = 1 课时）
- 首版收款：仅登记已发生渠道（微信/支付宝/银行卡/现金），无支付网关

## 手机端

见 `mobile/BUILD-RECORD.md`：WebView 触屏壳；**非** JNative 原生手机 UI；真机签名 UNVERIFIED。

## 如何复跑

```bash
./examples/wonder-campus/scripts/build-server.sh
./examples/wonder-campus/scripts/e2e-accept.sh
```

本机已执行：`./examples/wonder-campus/scripts/e2e-accept.sh` → 退出码 0，日志含 `PASS wonder_campus_e2e`（2026-10-16）。
