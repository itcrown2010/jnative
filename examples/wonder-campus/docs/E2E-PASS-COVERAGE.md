# E2E PASS 覆盖范围（诚实边界）

作者：张晓达  
重跑日期：2026-10-16  
命令：`./examples/wonder-campus/scripts/e2e-accept.sh` → 退出码 0

## 结论

脚本 PASS **仅证明**本机 native 服务端在隔离 SQLite（`build/wonder-campus/e2e.db`、端口无关）上的业务闭环与负例。  
**不得**把该 PASS 扩展为 iOS/Android 已验证。

## 已断言（服务端）

| 类别 | 覆盖 |
|------|------|
| 余额对齐 | 签到后 `packRemaining` 减 1000；撤销/停课后余额回补 |
| DB 计数 | `wc_lesson_ledger` 行数；学期 `PRESENT`/`LEAVE` 计数 |
| 权限拒绝 | 非家长请假；跨校区无授权排课；陌生人反馈列表 |
| 幂等 | 重复签到/批量同 key 不重复扣；收款同 idem 同 paymentId |
| 冲突 | 排课冲突 `ok==0` 且 message 含冲突对象/时间 |
| 反馈可见性 | 草稿字符串不在家长列表；发布后可见 |
| 补课资格 | 批准后消费 makeup credit；取消课冲正消课 |

## 未充分断言 / 缺口

| 项 | 说明 |
|----|------|
| 完整 SQL 事务回滚用例 | 依赖执行路径失败即不提交；无独立「中途抛错回滚」注入测试 |
| 并发双写压力 | 余额 CAS 更新有乐观条件；无多线程压力套件 |
| HTTP 层 Cookie/会话 | E2E 直调服务方法，非 HTTP 客户端 |
| 手机 UI / 双平台 | **不在**本脚本范围 |

## 与双平台关系

| 证据 | 含义 |
|------|------|
| `PASS wonder_campus_e2e` | 场景 A/B + 负例 |
| `PASS wonder_campus_mobile_loop` | 手机闭环服务逻辑 |
| iOS/Android 安装与截图 | 见 `docs/MOBILE-APP-ACCEPTANCE.md`，另计 |
