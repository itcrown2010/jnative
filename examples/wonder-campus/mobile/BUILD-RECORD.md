# 奇想校园手机端构建记录

作者：张晓达  
日期：2026-10-16

## 声明（必读）

| 项 | 状态 |
|----|------|
| 界面实现 | **WebView 触屏壳**（`mobile/www` HTML/CSS/JS） |
| JNative 原生 UIKit / SwiftUI / Compose | **Not Supported / 未宣称** |
| 业务逻辑位置 | JNative 原生 HTTP 服务（端口 18880） |
| 真机签名 / 商店上架 | **UNVERIFIED** |

## 资产

- 壳页面：`examples/wonder-campus/mobile/www/`
- **独立 iOS 工程**：`examples/wonder-campus/mobile/ios/WonderCampus/`（非 calculator）
- **独立 Android 工程**：`examples/wonder-campus/mobile/android/`（本轮优先 iOS）
- 同步：`./scripts/sync-mobile-www.sh` 或各平台 `scripts/sync_www.sh`
- API：模拟器 `http://127.0.0.1:18880`；Android 模拟器常用 `10.0.2.2:18880`；真机用 Mac 局域网 IP

## iOS（本轮）

| 步骤 | 结果 |
|------|------|
| `mobile/ios/scripts/build_ios.sh` | **通过** → `build/WonderCampus.app` |
| WC-iPhone-15 / WC-iPhone-SE 安装启动 | **模拟器通过**（截图见 `build/wonder-campus/ios-shots/`） |
| 三角色工作台 + 今日课表数据 | **模拟器通过** |
| 业务闭环 API + 图片上传 | **通过**（`ios-shots/loop/`） |
| 真机签名 | **未验证** |

详见 `mobile/ios/README.md`、`docs/IOS-ACCEPTANCE.md`。

## Android（本轮非优先）

| 步骤 | 结果 |
|------|------|
| 独立工程与 `assembleDebug` | 工程已存在；本轮未强制重跑 → 联调截图 **UNVERIFIED** |
| Play 签名 | **UNVERIFIED** |
