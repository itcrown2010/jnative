# VS Code 移动工具链验收记录（本轮）

作者：张晓达  
日期：2026-10-13

各平台**独立记录**，不互相推断。

## 工具版本（本机）

| 工具 | 版本 |
|------|------|
| macOS | darwin 25.2.0 |
| Java | 21.0.6 LTS |
| Xcode | 26.6 (17F113) |
| wat2wasm (WABT) | 1.0.39 |
| jnative.jar | 仓库 `mvn -DskipTests package` 产物 |
| VS Code 扩展 | `tools/vscode-jnative` 0.8.1（源码编译通过，单元测试 14 passing） |
| adb / 在线 Android 设备 | **未就绪**（本机 PATH 无 adb；`mobile devices --target android` 为空） |

## 验收矩阵

| # | 项 | Web | Android | iOS |
|---|----|-----|---------|-----|
| 1 | 从 VS Code/CLI 选择目标 | **已验证**（CLI + 扩展 QuickPick 代码） | **已验证**（设备列表为空时明确报错路径） | **已验证**（多模拟器 JSON + 明确选 ID） |
| 2 | 构建并运行任务示例 | **已验证** `mobile build --target web` → `dist/` | **已验证** `assembleDebug` APK；**安装运行未验证**（无设备） | **已验证** 构建 + `simctl` 安装启动 iPhone 17 |
| 3 | 修改 JNative 逻辑后产物变化 | **已验证**（`app.wasm` sha 变化） | 同 Web 产物同步进 assets（构建链路已跑） | 同左 |
| 4 | 编译错误定位 | **已验证**（`merged-core.java:line:col: error`；失败拒绝 stamp） | 依赖 Web 先成功 | 同左 |
| 5 | 桥接/网络错误日志 | **已验证**（`web/lib/diag.js` 分层 + eventId；token 脱敏） | 容器日志命令已提供；**真机未验证** | 进程已启动；Safari 检查流程已文档化，**本轮未人工点开 Inspector** |
| 6 | 重启后数据保留 | 升级脚本语义 **已验证**；浏览器冷启动 **未在本轮重跑** | **未验证** | **未验证**（需 Simulator 内操作） |
| 7 | 发布配置产物、脱离开发服务器 | **已验证** `--configuration release` + `asset-manifest.json` | APK debug 已出；release APK **未在本轮重跑**（unsigned 路径已实现） | 模拟器产物 `store_ready=false` |
| 8 | 升级后数据与待同步保留 | **已验证** `scripts/verify_upgrade_retention.sh` | 不通过清空存储；容器重建后桥接依赖 debug 构建重装 — **Android 未验证** | 重新 `mobile run` 后进程起来 — **IDB 内容未在本轮断言** |

## 截图

- iOS 模拟器：[`screenshots/guide-tasks-ios-sim.png`](./screenshots/guide-tasks-ios-sim.png)（任务示例导航：列表 / 新建 / 设置）

## WASM 源码调试

见 [`WASM_SOURCE_DEBUG.md`](./WASM_SOURCE_DEBUG.md)：**不支持**；未增加无效调试按钮。

## 产物清单（guide-tasks 示例）

| 路径 | 说明 |
|------|------|
| `examples/guide-tasks/dist/` | 同一次构建的 wasm/js/壳资源 + `asset-manifest.json` |
| `examples/guide-tasks/.jnative/mobile-build.json` | 构建记录（哈希、配置、标识） |
| `mobile/android/.../app-debug.apk` | 调试 APK |
| DerivedData `…/Calculator.app` | iOS 模拟器包（未签名，非商店分发） |

## 未解决问题

1. Android：本机无 adb/设备，安装运行与 WebView `chrome://inspect` **未验证**。
2. JNative `.jn` 行号：合并编译错误落在 `merged-core.java`，不伪造业务文件行号。
3. WASM 浏览器源级调试缺口见 WASM_SOURCE_DEBUG.md。
4. 发布 APK/IPA 无商店签名；不得标为可公开分发。
5. iOS 工程仍沿用 `Calculator` Xcode 工程名（bundle id 已为 guidetasks）。
