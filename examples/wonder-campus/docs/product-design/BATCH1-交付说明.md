# Batch 1 交付说明（iOS 优先）

日期：2026-10-16  
依据：`商业版产品与设计规格.md`；原型仅作视觉/交互参考。

## 截图

目录：`build/wonder-campus/ios-shots/batch1/`

| 文件 | 说明 |
| --- | --- |
| `00-login.png` | 测试登录（模拟器） |
| `01-parent-home.png` | 家长首页（模拟器） |
| `02-teacher-home.png` | 老师首页（模拟器） |
| `03-admin-home.png` | 教务工作台（模拟器） |
| `04-director-home.png` | 负责人概览（模拟器） |
| `05-teacher-checkin.png` | 签到页（同源 SPA） |
| `05b-teacher-checkin-done.png` | 签到成功回读（出勤） |
| `06-teacher-feedback-published.png` | 反馈预览发布 |
| `07-teacher-me.png` | 老师「我的」仅授权身份 |
| `08-admin-me-identities.png` | 教务「我的」多身份切换 |
| `09-parent-home-feedback.png` | 家长首页展示已发布反馈 |

## 修改路径

- `mobile/www/app.css` — 鸢尾蓝设计令牌、按钮/列表/卡片/导航
- `mobile/www/app.js` — 四角色首页、底栏、我的身份切换、学员页、签到/反馈链路
- `mobile/www/index.html` — 登录前品牌头；登录后隐藏
- `shared/WcEnums.java` — `DIRECTOR`
- `server/WonderServer.java` — `/api/wc/home/director`；老师首页计数
- `server/WonderDemoSeed.java` — admin 增加 DIRECTOR 身份
- `docs/product-design/` — 设计交付原样复制（含原型与参考截图）

未改 ERP；未覆盖无关未提交改动。

## 测试结果

| 项 | 结果 |
| --- | --- |
| `node --check mobile/www/app.js` | PASS |
| iOS `build_ios.sh` + 模拟器安装 | PASS |
| 四角色首页截图（真实 `/api/wc/*`） | PASS |
| 课次创建→ 批量签到（合同#1）→ 扣 1000 milli | PASS |
| 反馈草稿 → 发布 → 家长 `parent/feedback` 可见 | PASS |
| 「我的」仅展示授权身份（teacher1→老师；admin→教务/负责人/老师） | PASS |
| 负责人金额指标 | 明确 unavailable，未填演示数 |

## 与设计的差异

1. **课次卡片**仍多用编号（班级/校区/教室/老师 #），规格中的课程名/老师名需名称解析接口（当前未接）。
2. **教务排课**仍为编号表单，非原型级「可视化排课」。
3. **反馈页**学员仍以编号输入为主（自动填 roster 首位），未做成名单选择器。
4. **负责人经营金额**（收款/退款/课消）接口未提供；UI 明示缺口，禁止演示数字。
5. **原型虚构文案/插画**未搬入业务页；空态与文案按真实数据与规格语气。
6. **签到确认**依赖 `window.confirm`（WebView）；后续可改为应用内确认条。

## 缺失接口（待补齐）

- 经营汇总：`receivedCents` / `refundCents` / `consumeRevenueCents`（期间与口径待定）
- 课次/班级/老师/教室的展示名解析（或详情接口扩展字段）
- 可选：老师反馈页 roster 学员选择专用轻量接口（现有 roster 已可用，前端可增强）
