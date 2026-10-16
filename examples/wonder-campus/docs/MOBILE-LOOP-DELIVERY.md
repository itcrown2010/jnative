# 奇想校园 — 手机端业务闭环交付（本轮）

作者：张晓达  
日期：2026-10-16

## 本轮完成

| 项 | 状态 |
|----|------|
| 运营组织 ≠ 收款主体（`wc_billing_entities` + 合同关联） | 完成 |
| 跨校区权益默认拒绝，显式授权 | 完成 |
| 排课冲突返回冲突对象与时间 | 完成 |
| 教务今日日程筛选 API | 完成 |
| 家长请假 → 批准补课 → makeup credit | 完成 |
| 老师名单 / 批量签到（展示人数与权益变化） | 完成 |
| 课次完成与签到分离 | 完成 |
| 学校取消课恢复已扣权益 | 完成 |
| 反馈草稿不可见、发布后家长可见 | 完成 |
| 科目反馈模板（英/画/语/数） | 完成 |
| 应用内通知任务（失败不回滚业务、dedupe） | 完成 |
| 媒体登记 + 鉴权 meta（非公开链） | 完成 |
| TEST 双校区聚焦演示数据（六校区骨架） | 完成 |
| 手机触屏壳闭环操作入口 | 完成 |
| E2E `WonderMobileLoopTest` | 完成（以脚本退出码为准） |

## 本轮未完成 / UNVERIFIED

| 项 | 状态 |
|----|------|
| iOS 模拟器本轮重跑截图 | **模拟器通过**（独立工程 `mobile/ios`，见 `docs/IOS-ACCEPTANCE.md`） |
| Android 模拟器本轮重跑截图 | UNVERIFIED（本轮优先 iOS） |
| 真机签名 | UNVERIFIED |
| 相册选图 → base64 真实字节上传 | **API 通过**（1×1 测试 PNG；真机相册 UI **未验证**） |
| APNs/FCM | 未做 |
| 周课表 UI | 未做（日程列表优先） |
| 招生营销 / 工资绩效 / AI 教学 | 明确不做 |

## 修改路径与核心方法

| 路径 | 核心 |
|------|------|
| `migrations/002_mobile_loop_sqlite.sql` | 收款主体、跨校区授权、补课资格、通知任务、媒体、模板 |
| `server/WcDb.java` | `applyMigrationFile` / `tryAlter` |
| `server/WcServices.java` | `conflictDetail`、`contractAllowedOnCampus`、取消恢复消课、改期历史 |
| `server/WcLoopServices.java` | `batchCheckIn`、`saveFeedbackDraft`、`publishFeedbackDraft`、`cancelLeave`、`WcLoopNotify` |
| `server/WonderDemoSeed.java` | TEST 六校区/四科/两孩种子 |
| `server/WonderServer.java` | 闭环 HTTP API |
| `tests/WonderMobileLoopTest.java` | 闭环 + 负例 |
| `mobile/www/*` | 三角色工作台触屏壳 |
| `docs/OPEN-RULES.md` | 未决规则 |

## 双平台运行方式

```bash
./examples/wonder-campus/scripts/build-server.sh
./examples/wonder-campus/scripts/e2e-accept.sh   # 含 scenario A/B + mobile-loop
DATABASE_URL=sqlite:./build/wonder-campus/wonder.db \
  examples/wonder-campus/.jnative/build/wonder-server
# 浏览器或 WebView 打开 http://127.0.0.1:18880/
./examples/wonder-campus/scripts/sync-mobile-www.sh
```

| 平台 | 设备/模拟器 | 本轮结果 |
|------|-------------|----------|
| 服务端 E2E | 本机 native | `e2e-accept.sh` PASS（含 mobile-loop） |
| iOS WKWebView | 独立 `WonderCampus.app` | **模拟器通过**；真机 **未验证** |
| Android WebView | 独立 android 工程 | 本轮未强制重跑 → **UNVERIFIED** |

## 验收对照（10 条）

1. 登录按身份进工作台 — API + 壳支持；双端 UI **UNVERIFIED**
2. 排课冲突拒绝且明细 — E2E
3. 家长只能为关联孩子请假 — E2E
4. 老师只能操作授权课 — `teacherCanOperateSession`
5. 签到扣权益、重复不扣 — E2E
6. 请假补课无双扣/无限免 — makeup credit E2E
7. 学校取消恢复权益 — E2E
8. 草稿家长不可见、发布可见 — E2E
9. 多孩子/跨校区不泄露 — 授权校验 + parent feedback
10. 重启持久化、弱网幂等 — SQLite + idempotencyKey；离线自动过账 **不做**

## 业务链路（建议手测顺序）

1. admin 登录 → 今日课程  
2. parent 登录 → 切换孩子 → 请假  
3. admin 批准补课（API）  
4. teacher 登录 → 名单 → 批量签到 → 看权益变化文案  
5. 保存反馈草稿 → 家长端不可见 → 发布 → 家长可见  
6. 教务取消已签到课 → 余额恢复  
