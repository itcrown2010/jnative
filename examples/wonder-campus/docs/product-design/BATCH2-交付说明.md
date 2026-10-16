# Batch 2 交付说明（家长首页深化 + 老师课堂链路 + 前端拆分）

日期：2026-10-16  
技术路线：JNative 后端 + HTML/CSS/JS + Swift WKWebView（未换栈、未改 ERP）

## 1. 模拟器 / SPA 截图

目录：`build/wonder-campus/ios-shots/batch2/`

| 文件 | 说明 | 环境 |
| --- | --- | --- |
| `01-parent-home.png` | 家长首页：孩子切换、下一课主题/时间/校区教室老师、四入口、已发布反馈 | iOS 模拟器 + 真实 API |
| `02-teacher-home.png` | 老师今天：真实计数、下一课主题与地点、进入课堂 | iOS 模拟器 + 真实 API |
| `03-teacher-checkin.png` | 签到：真实人数按钮「确认签到（N人）」、权益说明、纠错入口 | 同源 SPA（与壳同一 www） |
| `04-teacher-feedback-preview.png` | 反馈表单 + 学员选择；发布预览含孩子/接收范围/作品说明 | 同源 SPA |

真机：未跑 → **UNVERIFIED**

## 2. 端到端操作记录（真实接口）

库：`build/wonder-campus/wonder-batch2.db`，服务 `:18880`

1. `admin/ADMIN` 创建课次 → `sessionId=1`，合同#1（充足余额）  
2. `GET /api/wc/sessions/1/roster` 返回  
   `theme=TEST趣味英语`，`teacherName=TEST专职老师`，`campusName=TEST阳光校区`，`roomName=TEST彩虹教室`  
3. `teacher1` 批量签到 1 人 → `okCount=1, consumedMilli=1000`  
4. 同学员再次签到 → `ALREADY_CHECKED_IN`，不重复扣课  
5. 反馈草稿 → 发布 → `parent1` `GET /api/wc/parent/feedback?studentId=1` 可见已发布项（`mediaIds=[]`）  
6. `./scripts/e2e-accept.sh`：**PASS**（`wonder_campus_e2e` + `wonder_campus_mobile_loop`）

## 3. 已修改文件与方法（摘要）

### 后端
- `server/WcLoopServices.java`  
  - `sessionLabelsJson` / `themeEscape`：课次展示名  
  - `sessionRosterJson`：附带 theme/teacher/campus/room  
  - `adminTodayJson`：附带展示名  
  - `parentFeedbackJson`：`mediaIds`（仅已完成上传）  
  - `canReadMedia`：家长仅可读已发布反馈关联且 `size_bytes>0` 的作品（草稿/他孩不可读）  
  - `publishFeedbackDraft`：拒绝未完成上传附件（`MEDIA_INCOMPLETE`）  
- `server/WonderServer.java`：`/api/wc/sessions/today` 附带展示名  

### 前端（拆分）
| 文件 | 职责 |
| --- | --- |
| `mobile/www/js/wc-core.js` | 状态、API、格式化、空/错组件 |
| `mobile/www/js/wc-ui.js` | Shell、导航、登录、我的、课次卡片 |
| `mobile/www/js/wc-parent.js` | 家长首页/请假/权益/成长档案（孩子切换 `loadSeq` 防串数据） |
| `mobile/www/js/wc-teacher.js` | 老师今天、课次详情、签到、反馈 |
| `mobile/www/js/wc-ops.js` | 教务/负责人/排课/合同等 |
| `mobile/www/app.js` | 路由与启动 |
| `mobile/www/index.html` | 按序加载模块 |
| `mobile/www/app.css` | 孩子选择器、时间行、作品缩略图等 |
| `mobile/*/scripts/sync_www.sh` | 同步含 `js/` |

权威业务规则仍在后端（扣课、权限、冲突、财务）。

## 4. 设计差异（具体）

1. **下一课主卡**：已用主题名+时分+校区教室老师名；未做年龄/课程概况副文案条（规格 P01「问候后概况」待补轻量字段）。  
2. **作品缩略图**：有 `mediaIds` 时展示；本次 e2e 未上传图片故为空（非演示图）。  
3. **签到**：确认按钮真实人数；出勤/迟到用勾选批量，请假缺勤引导纠错/教务，未做四人状态分段控件（规格 T02 完整版）。  
4. **反馈预览**：文案明确孩子与监护接收范围；仍用系统 `confirm`，非独立 ReviewSheet 组件。  
5. **课次编号输入**：反馈页仍保留课次数字框（已联动名单）；规格倾向纯上下文进入，编号框可后续隐藏。  
6. **品牌**：登录后首页不再放大品牌说明；符合规格「品牌让位于业务」。

## 5. 验收表

| 项 | 结果 | 说明 |
| --- | --- | --- |
| e2e-accept（改前基线） | PASS | 本批开始前已跑通 |
| e2e-accept（改后回归） | PASS | 含 mobile-loop |
| 家长首页真实下一课字段 | PASS | 模拟器截图 |
| 孩子切换防串数据 | PASS | `loadSeq` + childId 校验（逻辑） |
| 首页/详情/请假同一课次 | PASS | `childSessionMap` + 请假课次选择器 |
| 空课程 / 空反馈 / 失败区分 | PASS | 空态文案 vs `retryBox` 错误 |
| 签到真实人数与权益说明 | PASS | SPA 截图 |
| 重复签到不重复扣课 | PASS | `ALREADY_CHECKED_IN` |
| 考勤纠错入口 | PASS | 冲正按钮 |
| 反馈草稿+发布预览范围 | PASS | SPA；草稿家长不可见（后端 PUBLISHED 过滤） |
| 未完成上传不可发布附件 | PASS | 后端 `MEDIA_INCOMPLETE` + 前端 pending 拦截 |
| 前端模块拆分（无新框架） | PASS | 6 脚本全局作用域 |
| 小屏 / Dynamic Type / 真机 | UNVERIFIED | 仅模拟器+桌面窄视口 |
| 键盘遮挡 / VoiceOver | UNVERIFIED | 未专项测 |
| 教务排课/报名收费深化 | FAIL（本轮不做） | 下一批 |

## 6. 剩余问题

- 经营金额接口仍未提供（负责人页保持 unavailable）。  
- 签到未做逐人 PRESENT/LATE/ABSENT/LEAVE 分段编辑（仅出勤勾选 + 冲正）。  
- 反馈作品上传需在 UI 再截一张「含缩略图」的家长首页（需实际上传文件）。  
- 模拟器通过 ≠ 真机通过。  
- 下一步：教务排课冲突预览、报名与人工收款。
