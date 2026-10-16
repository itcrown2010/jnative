# 奇想校园 iOS 里程碑：六页闭环验收

作者：张晓达  
日期：2026-10-16

## 里程碑目标

在 iPhone 模拟器中，通过真实后端完成：

**登录 → 今日课程 → 签到 → 课时流水 → 发布反馈 → 家长查看**

## 运行基线（实际记录）

| 项 | 值 |
|----|-----|
| Xcode | 26.6 (17F113) |
| 模拟器 | WC-iPhone-15（iPhone 15，iOS 17.5 runtime，UDID `F953A0C8-…`） |
| 小屏对照 | WC-iPhone-SE（UDID `A90ABEE8-…`） |
| 构建命令 | `./examples/wonder-campus/mobile/ios/scripts/build_ios.sh` |
| 产物 | `examples/wonder-campus/mobile/ios/build/WonderCampus.app` |
| Bundle ID | `io.jnative.examples.wondercampus`（显示名：奇想校园） |
| 服务端验收 | `./examples/wonder-campus/scripts/e2e-accept.sh` → **PASS** |
| 隔离库 | `DATABASE_URL=sqlite:./build/wonder-campus/wonder-milestone.db` |
| 服务启动 | `WC_ALLOW_DEMO_LOGIN=1 WC_ENV=dev examples/wonder-campus/.jnative/build/wonder-server` |
| API | `http://127.0.0.1:18880`（模拟器同源远程加载） |
| 环境注入 | `SIMCTL_CHILD_WC_*`（非 `--setenv`） |

界面技术：**WKWebView 触屏壳**（`mobile/www`），**不是**原生 SwiftUI/UIKit 业务界面。  
JNative：HTTP + SQLite 业务与鉴权。

## 已完成页面与源码路径

| 页面 | 路径 |
|------|------|
| 登录 | `mobile/www/app.js` → `renderLogin`；正式包关闭演示见 `Config.prod.plist` / `allowDemoLogin` |
| 今日课程 | `renderToday` / `fillSessions` / `renderSession` |
| 签到 | `renderTeach`（预览人数与预计扣课、回读、纠错冲正） |
| 课时流水 | `renderProgress` / `loadProgress`（课时包与学期制分开展示） |
| 课堂反馈（老师） | `renderFeedback`（草稿 / 预览发布） |
| 家长反馈详情 | `renderParentFb` / `loadFb`（切换孩子清空重载） |
| 共用样式 | `mobile/www/app.css`（badge、列表、安全区底栏） |
| iOS 壳 | `mobile/ios/WonderCampus/ViewController.swift` 等 |

后端仅做**字段补齐**（流水 sessionId/时间、名单 attendanceId/课次元数据、家长反馈发布时间与课次信息、老师可纠错本人课次），未重写业务、未改 ERP。

## 模拟器截图（实际运行）

目录：`build/wonder-campus/ios-shots/milestone/`

| 文件 | 内容 |
|------|------|
| `01-login.png` | 登录页（测试环境标识） |
| `02-teacher-home.png` | 老师工作台（今日课程 / 写反馈） |
| `03-parent-home.png` | 家长工作台（课表 / 流水 / 反馈） |
| `04-SE-login.png` | SE 小屏登录布局 |
| `ACCEPTANCE.md` / `*.json` | A–H 接口与库核对结果 |

## 验收 A–H

| 项 | 结果 | 说明 |
|----|------|------|
| A 签到后家长看到扣课 | **PASS** | remaining 40000→39000；`A-ledger-after.json` |
| B 重复签到不二次扣课 | **PASS** | 同幂等键 + 新键后余额仍 39000 |
| C 纠错后流水冲正 | **PASS** | reverse 后 remaining 恢复 40000 |
| D 草稿家长不可读 | **PASS** | 发布前 parent feedback 不含 draft id |
| E 发布后正确家长可见 | **PASS** | studentId=1 可见；切换 studentId=2 无该条 |
| F 退出重登一致 | **PASS** | logout + 重登后反馈仍在 |
| G 网络中断不假成功 | **PASS（代码路径）** | 签到/反馈 `catch` 明确「未确认保存成功」；模拟器飞行模式手测 **UNVERIFIED** |
| H 角色/孩子切换无泄露 | **PASS** | 老师访问 parent feedback 拒绝；孩子切换过滤 |

服务端 e2e（含 mobile-loop）：**PASS**。

## 真机还缺什么

1. Apple Developer 团队与签名证书（本仓库不提交）  
2. 真机 SDK 构建（当前脚本默认 `iphonesimulator`）  
3. `WC_API_BASE=http://<Mac局域网IP>:18880`（禁止真机 127.0.0.1）  
4. 真机相册选图上传 UI 手测  

模拟器通过 ≠ 真机 / App Store 通过。

## 下一轮建议补齐

- 教务排课冲突明细页、请假审批与补课安排完整 UI  
- 模拟器飞行模式断网手测截图（补齐 G）  
- 真机签名安装与相册上传手测  
- Android 同里程碑联调（本轮未做）

## 复现

```bash
./examples/wonder-campus/scripts/build-server.sh
./examples/wonder-campus/scripts/e2e-accept.sh
DATABASE_URL=sqlite:./build/wonder-campus/wonder-milestone.db \
  WC_ALLOW_DEMO_LOGIN=1 WC_ENV=dev \
  examples/wonder-campus/.jnative/build/wonder-server &
./examples/wonder-campus/mobile/ios/scripts/build_ios.sh
# 详见 mobile/ios/README.md（SIMCTL_CHILD_* 注入）
```
