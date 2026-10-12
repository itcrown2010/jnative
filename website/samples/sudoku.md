# 跨平台 sample：数独

路径：`examples/sudoku/`  
验证记录：`examples/sudoku/docs/PLATFORM.md`

## 架构

| 层 | 路径 | 说明 |
|----|------|------|
| 共享核心 | `core/SudokuCore.java` | 规则 / 求解 / 存档；UI 不得重写 |
| 桌面 | `desktop/SudokuDesktop.java` | `jnative.ui` 原生窗口 |
| Web | `web/` | WASM + JS 壳（非 WebUI Html 全量路径） |
| 移动触屏壳 | `mobile/web/` | `body.touch` |
| Android 容器 | `mobile/android/` | WebView + assets/www |
| iOS 容器 | `mobile/ios/Calculator/` | WKWebView + 自定义 scheme |

**共享的是核心逻辑，不是一份 UI 自动适配所有平台。**

## 构建命令（仓库脚本）

```bash
./examples/sudoku/scripts/build_core_tests.sh
./examples/sudoku/scripts/build_desktop.sh
./examples/sudoku/scripts/verify_web.sh
./examples/sudoku/scripts/cross_compare.sh
./examples/sudoku/scripts/sync_mobile_assets.sh
./examples/sudoku/scripts/build_android.sh
./examples/sudoku/scripts/build_ios.sh   # 模拟器；真机见 install_ios_device.sh
```

## 验证状态（摘自 PLATFORM.md，以该文件为准）

| 目标 | 状态 |
|------|------|
| 核心测试 | PASS（记录时 51/51） |
| 桌面 macOS | 实验性（曾启动） |
| Web + 指纹交叉 | 实验性（曾 PASS） |
| Android assembleDebug | 实验性（APK；设备游玩另行验证） |
| iOS Simulator | 曾因 runtime 不匹配 BLOCKED；随本机 Xcode/runtime 变化请重跑 |
| iOS 真机 | 实验性（需信任开发者） |

## 与 guide-tasks 的关系

入门闭环用 [任务管理器](/tutorials/desktop/)；数独是 **进阶** 跨端一致性示例（同一核心、多套 UI/壳）。

## 复现工具链缺陷

`examples/sudoku/docs/repro/` 保留最小复现（clinit、容器容量等），供维护者对照，不是应用教程正文。
