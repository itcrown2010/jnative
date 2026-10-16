# 奇想校园 — 独立 iOS 工程

作者：张晓达  
日期：2026-10-16

## 技术分工（请勿误解）

| 层级 | 技术 | 说明 |
|------|------|------|
| 业务与权限 | **JNative** HTTP + SQLite（默认端口 **18880**） | 排课、请假、签到、权益、反馈、媒体上传均在服务端校验 |
| iOS 界面 | **WKWebView 触屏网页壳**（`mobile/www` HTML/CSS/JS） | **不是**原生 SwiftUI / UIKit 业务界面 |
| 原生壳职责 | 启动、安全区、键盘避让、ATS/配置注入、Keychain 提示位、Cookie 清理 | 不实现教务/老师/家长页面逻辑 |

显示名称：**奇想校园**  
Bundle ID（可改）：`io.jnative.examples.wondercampus`（构建时 `WC_BUNDLE_ID=...`）

## 目录

```
mobile/ios/
  WonderCampus/
    AppDelegate.swift
    ViewController.swift      # WKWebView 壳
    KeychainStore.swift       # 会话提示安全存储
    Config.dev.plist          # 开发
    Config.test.plist         # 测试
    Config.prod.plist         # 正式（关闭演示登录，无明文 HTTP）
    Info.plist
    www/                      # sync 自 mobile/www
  scripts/build_ios.sh
  scripts/run_sim.sh
  scripts/sync_www.sh
  build/WonderCampus.app
```

## 启动测试后端

```bash
# 仓库根目录
./examples/wonder-campus/scripts/build-server.sh
./examples/wonder-campus/scripts/e2e-accept.sh   # 服务端闭环未退化
DATABASE_URL=sqlite:./build/wonder-campus/wonder.db \
  WC_ALLOW_DEMO_LOGIN=1 WC_ENV=dev \
  ./examples/wonder-campus/scripts/run-dev-server.sh
# 健康检查：http://127.0.0.1:18880/api/wc/health
```

服务不可达时 App 会展示错误页，**不会**静默改用假数据。

## 模拟器构建与运行

```bash
# 需已创建设备（示例）
# xcrun simctl create WC-iPhone-15 com.apple.CoreSimulator.SimDeviceType.iPhone-15 com.apple.CoreSimulator.SimRuntime.iOS-17-5
# xcrun simctl create WC-iPhone-SE com.apple.CoreSimulator.SimDeviceType.iPhone-SE-3rd-generation com.apple.CoreSimulator.SimRuntime.iOS-17-5

./examples/wonder-campus/mobile/ios/scripts/build_ios.sh
./examples/wonder-campus/mobile/ios/scripts/run_sim.sh WC-iPhone-15

# 验收辅助：自动演示登录（仅开发；正式包 allowDemoLogin=false）
# simctl 通过 SIMCTL_CHILD_* 注入子进程环境（不是 --setenv）
export SIMCTL_CHILD_WC_ACCEPT_LOGIN=admin
export SIMCTL_CHILD_WC_ACCEPT_IDENTITY=ADMIN
export SIMCTL_CHILD_WC_API_BASE=http://127.0.0.1:18880
export SIMCTL_CHILD_WC_LOAD_REMOTE=1
xcrun simctl launch --terminate-running-process <UDID> io.jnative.examples.wondercampus
```

模拟器默认远程加载 `http://127.0.0.1:18880/`（同源 Cookie 会话）。  
真机必须把 `WC_API_BASE` / `Config.*.plist` 改成**电脑局域网 IP**，**不能**用真机上的 `127.0.0.1`。

## 环境与安全

| 环境 | 配置 | 演示登录 | 明文 HTTP |
|------|------|----------|-----------|
| dev | `Config.dev.plist` / `WC_ENV=dev` | 允许 | 仅本机/局域网例外 |
| test | `Config.test.plist` | 允许 | 同左 |
| prod | `Config.prod.plist` / `WC_ENV=prod` | **关闭** | 构建脚本移除 ATS 例外 |

- 不提交证书、私钥、签名凭证或真实账号密码。
- 会话 Cookie：`JNSESSION`（WKWebsiteDataStore）；退出时清 Cookie + Keychain 提示位。
- 正式构建关闭演示登录与测试入口；后端始终鉴权。

## 真机签名与安装（本轮未验证）

模拟器通过 ≠ 真机通过 ≠ App Store 通过。

需要你方具备：

1. Apple Developer 团队 ID（付费或个人）
2. 在 Xcode 中为独立 App 配置 Signing（或导出 `.ipa`）
3. 将本工程纳入 Xcode 工程 / 使用 `xcodebuild` 真机 SDK（当前脚本默认 `iphonesimulator`）
4. 真机与 Mac 同一局域网，并配置 `WC_API_BASE=http://<Mac局域网IP>:18880`
5. 开发构建才允许 ATS 明文例外；正式包使用 HTTPS

本仓库**不**附带签名证书。可改 Bundle ID：`WC_BUNDLE_ID=com.yourteam.wondercampus ./scripts/build_ios.sh`

## 相关文档

- 验收结果：`docs/IOS-ACCEPTANCE.md`
- 能力与规则：`CAPABILITY.md` / `RULES.md` / `docs/OPEN-RULES.md`
- 服务端闭环：`docs/MOBILE-LOOP-DELIVERY.md`
