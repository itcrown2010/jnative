# 移动教程：任务管理器（容器方案）

## 方案说明（必读）

当前路线是 **Android WebView / iOS WKWebView** 加载本地 `www/` 中的 **WASM TaskCore + 触屏壳**。  
**不是** UIKit / Jetpack Compose 原生控件 UI。

| 层 | 技术 |
|----|------|
| 业务 | `examples/guide-tasks/core/TaskCore.java` → `app.wasm` |
| 触屏 UI | `mobile/web/`（`body.touch`） |
| 容器 | `mobile/android/`、`mobile/ios/Calculator/`（自数独容器模式派生） |

## 平台状态（2026-10-09，本机）

| 项 | 状态 |
|----|------|
| `jnative doctor --mobile` | **稳定**（本机全绿） |
| 触屏资源同步 / WASM 复验 | **实验性**（`verify_mobile_assets.sh` OK） |
| Android `assembleDebug` | **实验性**（APK 已生成；**安装到设备/模拟器游玩未验证**） |
| iOS Simulator `xcodebuild` | **实验性**（本机构建 OK；交互时长未压测） |
| iOS 真机签名安装 | **未验证**（guide-tasks；数独曾成功，需信任开发者） |
| 应用商店发布 | **未支持**（未验收） |

## 课表

| 课 | 主题 |
|----|------|
| [01](./lessons/01) | 移动项目结构 |
| [02](./lessons/02) | 工具链检查 |
| [03](./lessons/03) | WebView / WKWebView 容器 |
| [04](./lessons/04) | 本地资源加载 |
| [05](./lessons/05) | 安全区域、触摸与软键盘 |
| [06](./lessons/06) | 生命周期及后台恢复 |
| [07](./lessons/07) | 平台能力桥接 |
| [08](./lessons/08) | 本地存储 |
| [09](./lessons/09) | 模拟器及设备运行 |
| [10](./lessons/10) | 调试与打包 |

## 快速命令

```bash
jnative doctor --mobile
./examples/guide-tasks/scripts/verify_mobile_assets.sh
./examples/guide-tasks/scripts/build_android.sh
./examples/guide-tasks/scripts/build_ios.sh
```
