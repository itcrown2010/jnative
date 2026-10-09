# Sudoku 跨平台验证记录

本文件只记录**本机实测**结果，不把任务描述或历史对话当作完成证明。

实测日期：2026-10-09  
主机：macOS (darwin)，Xcode 26.6，iOS Simulator runtime 仅有 **iOS 17.5**（SDK 为 **26.5**）。

## 架构

| 端 | 产物 | UI 技术 | 游戏核心 |
|----|------|---------|----------|
| Desktop | `build/sudoku/SudokuDesktop` | `jnative.ui` 原生窗口 | `core/SudokuCore.java`（源码拼接） |
| Web | `examples/sudoku/web/dist/` | 浏览器壳 `web/app-shell.js` + WASM | 同一核心 → `app.wasm` |
| Mobile | iOS `Calculator` 工程 + Android APK | WebView + `mobile/web` 触屏壳 | **同一** `app.wasm`（非第二套规则） |

桌面与 Web **不是**同一网页容器。移动端可在 WebView 中运行，但使用 `body.touch` 大按钮布局，并说明为 WKWebView / Android WebView 加载本地 `www/`。

## 验证结果

### 共享核心（native）

| 项 | 结果 |
|----|------|
| `./scripts/build_core_tests.sh` | **PASS** `passed=51 failed=0` |
| 固定题库唯一解 / 冲突 / 固定格 / 完成 / 存档 / 暂停计时 / GC / generate | 覆盖在 `tests/SudokuCoreTests.java` |

### Desktop

| 项 | 结果 |
|----|------|
| 构建 `build_desktop.sh` | **PASS** |
| 实际启动窗口 | **PASS**（修复 Container/GridPane 64 子控件上限后） |
| 截图 | `screenshots/02-desktop.png`（含桌面窗口） |
| 键盘/鼠标完整交互时长测试 | **PARTIAL**（窗口可玩；未做长时压力脚本） |

### Web

| 项 | 结果 |
|----|------|
| `verify_web.sh`（源码重编 WASM） | **PASS** |
| 浏览器加载棋盘 | **PASS**（Chrome headless 截图 `screenshots/01-web.png`） |
| `cross_compare.sh` native↔WASM 棋盘指纹 | **PASS** |

### Mobile

| 项 | 结果 |
|----|------|
| `sync_mobile_assets.sh` | **PASS**（iOS `Calculator/www`、Android `assets/www`） |
| 触屏壳布局（浏览器仿真 `body.touch`） | **PASS** 截图 `screenshots/03-mobile-touch-shell.png` |
| iOS `xcodebuild` 模拟器构建/安装/交互 | **BLOCKED** — Xcode 26.6 的 `iphonesimulator` SDK=26.5，本机未安装 iOS 26.5 Simulator runtime；`simctl` 仅有 iOS 17.5 设备，`xcodebuild -showdestinations` 无可用 Simulator destination |
| Android `assembleDebug` | **PASS** APK：`mobile/android/app/build/outputs/apk/debug/` |
| Android 模拟器安装与触控游玩 | **NOT VERIFIED**（本轮未启动 emulator / 未 `adb install`） |

## 跨平台一致性

同一核心、同一题目、同一动作序列（newGame → 填首个空格解 → undo → redo → exportBoardDigits）：

- native `-O0` fingerprint 与当前源码生成的 WASM fingerprint **一致**（见 `cross_compare.sh` 输出）。
- 计时未纳入指纹比较。

## 本轮暴露的工具链缺陷

| 缺陷 | 复现 | 修复 |
|------|------|------|
| wasm32-browser 不调用 `jnative_clinit`，`static final int` 恒 0，导致 `status == ST_PAUSED` 在 PLAYING 时也为真 | `docs/repro/06-wasm-static-clinit.java` | `BrowserWatEmitter` / `WebUiIrToWat`：收集并在 `jnative_start` 中调用 clinit |
| `Container`/`GridPane` 子控件元数据固定 64，9×9 棋盘崩溃 | `docs/repro/07-ui-container-capacity.java` | `UiLib`：`ensureChildCapacity` / `ensureGridMetaCapacity` 倍增扩容 |
| 既有：static 数组 clinit、递归求解栈、npm wat2wasm OOM 等 | `docs/repro/01`–`05` | 见 README 表 |

## 明确未验证 / 限制

- iOS 模拟器真机容器内游玩：**BLOCKED**（缺匹配 Simulator runtime）。
- Android 设备/模拟器交互：**NOT VERIFIED**。
- 后台长时间求解线程池：核心为迭代限步；UI 未挂随机生成长任务。
- 随机生成阶段二 API 已在核心；三端 UI 默认走固定题库。
