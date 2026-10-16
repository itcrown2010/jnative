# 奇想校园 iOS 验收报告（本轮）

作者：张晓达  
日期：2026-10-16

## 结论摘要

| 项 | 结果 |
|----|------|
| 独立 iOS App「奇想校园」模拟器安装运行 | **模拟器通过** |
| 连接真实 wonder-server（18880）登录与三角色工作台 | **模拟器通过** |
| 教务→家长→老师→反馈→家长 业务闭环（同源 API + 触屏壳） | **通过**（见 loop JSON；界面为 WebView 壳） |
| 服务端 e2e（A/B + mobile-loop） | **通过**（`e2e-accept.sh`） |
| 小屏 SE / 大屏 iPhone 15 布局截图 | **模拟器通过** |
| iPhone 真机签名安装 | **未验证** |
| App Store 发布 | **未验证** |

**模拟器通过不代表真机或 App Store 通过。**

## 界面技术声明

- 界面：**WKWebView 触屏网页壳**（`examples/wonder-campus/mobile/www`）
- **不是**原生 SwiftUI / UIKit 业务界面
- JNative：独立 HTTP 服务（权限、账本、课次状态、媒体字节上传）

## 截图（均来自实际模拟器运行）

目录：`build/wonder-campus/ios-shots/`

| 文件 | 内容 | 平台 |
|------|------|------|
| `WC-iPhone-15-admin.png` / `*-admin-wb.png` | 登录 / 教务工作台 | 模拟器 iPhone 15 |
| `WC-iPhone-15-admin-loop.png` | 教务首页含今日课次列表 | 模拟器 iPhone 15 |
| `WC-iPhone-15-teacher-wb.png` / `*-teacher-loop.png` | 老师工作台 | 模拟器 iPhone 15 |
| `WC-iPhone-SE-admin.png` / `*-parent-wb.png` / `*-parent-loop.png` | SE 登录 / 家长工作台 | 模拟器 iPhone SE |
| `loop/*.json` | 闭环 API 逐步结果（含图片上传） | 本机服务 |

## 闭环步骤与证据

顺序：教务排课 → 家长请假 → 教务批准补课 → 老师签到（权益变化）→ 反馈草稿/上传/发布 → 家长查看

| 步骤 | 结果摘录 |
|------|----------|
| 排课 | `session created` sessionId=3 |
| 请假 | `leave submitted` leaveId=1 |
| 补课 | `leave approved with makeup` makeupSessionId=4 |
| 签到 | `批量签到：成功1人，失败0人，合计权益变化 1000 milli` |
| 媒体 | `uploaded` mediaId=1（1×1 PNG 测试图，非真实儿童资料） |
| 发布 | `feedback published` |
| 家长可见 | parent feedback items 含已发布文案 |
| 流水 | contracts/1/ledger ok |

请假须满足合同快照截止时间（默认课前 12 小时）；壳内快速排课默认 +13h。

## 已完成

- 独立 iOS 工程与 Bundle（不再借用 calculator）
- 显示名「奇想校园」、启动屏配置、安全区、键盘 inset
- Config.dev / test / prod；prod 关闭演示登录并收紧 ATS
- 模拟器远程加载真实服务；失败可见提示
- 登录/退出（`/api/wc/auth/logout` 已加；旧进程未热更新时客户端仍清本地）
- 四栏导航：首页 / 课表 / 消息 / 我的（角色内容不同）
- 图片：相册/文件选择 → base64 真实字节上传 → 反馈附件（服务端落盘与鉴权）
- 小屏/大屏模拟器截图

## 未完成 / 已知问题

| 项 | 状态 |
|----|------|
| 真机 Developer 签名与安装 | **未验证**（缺团队证书；脚本默认 simulator SDK） |
| 真机相册 UI 手测 | **未验证**（API 上传路径已通） |
| APNs 推送 / 支付 / 招生营销 / 绩效 / AI | 明确不做 |
| 周课表网格 UI | 未做（日程列表） |
| `WC_ACCEPT_*` 须用 `SIMCTL_CHILD_` 前缀注入 | 已写入 README |
| 服务进程若在本轮前启动，需重启才加载 logout 路由 | 已知 |

## 真机签名所需条件（集中说明）

1. Apple Developer 账号与团队
2. Xcode 真机 Signing（或 CI 证书）——本仓库不提交
3. 真机 SDK 构建（扩展 `build_ios.sh` 或 Xcode 工程）
4. `WC_API_BASE=http://<Mac局域网IP>:18880`（禁止真机 127.0.0.1）
5. 开发包才开明文 HTTP；正式包 HTTPS + `Config.prod`

## 复现命令

```bash
./examples/wonder-campus/scripts/e2e-accept.sh
./examples/wonder-campus/scripts/run-dev-server.sh   # 另开终端
./examples/wonder-campus/mobile/ios/scripts/run_sim.sh WC-iPhone-15
# 详见 mobile/ios/README.md
```
