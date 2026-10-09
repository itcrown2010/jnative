# JNative 数独（跨平台示例）

作者：张晓达

同一份 **JNative 游戏核心**（`core/SudokuCore.java`）驱动三个独立 UI：

| 端 | 技术 | 说明 |
|----|------|------|
| **Desktop** | `jnative.ui` 原生窗口 | 不是网页容器 |
| **Web** | wasm32-browser + 浏览器壳 | 鼠标/键盘响应式布局 |
| **Mobile** | 同一 WASM + 触屏壳 + iOS/Android WebView | 大按钮、安全区；**不**依赖系统软键盘 |

规则、冲突、求解、提示、存档校验**只**在核心中实现；Swift / Kotlin / JS 不重写求解器。

## 目录

```text
examples/sudoku/
├── core/SudokuCore.java     # 共享核心
├── desktop/SudokuDesktop.java
├── web/                     # 浏览器 UI（非触屏优先）
├── mobile/web/              # 触屏 UI（可进 WebView）
├── mobile/android|ios/      # 容器（复用 calculator 桥接）
├── tests/SudokuCoreTests.java
├── scripts/
└── docs/                    # 复现、验证记录
```

## 前置

```bash
mvn -DskipTests package
# 需要原生 WABT wat2wasm（不要用 npm wabt 的 Node 包装，大会 OOM）
brew install wabt
export PATH="/opt/homebrew/opt/wabt/bin:$PATH"
```

## 构建与运行

```bash
# 核心测试（native）
./examples/sudoku/scripts/build_core_tests.sh

# 桌面
./examples/sudoku/scripts/build_desktop.sh
./build/sudoku/SudokuDesktop

# Web（始终从当前源码重编 WASM）
./examples/sudoku/scripts/verify_web.sh
cd examples/sudoku/web/dist && python3 -m http.server 8765
# 打开 http://127.0.0.1:8765/

# 三端状态指纹对比（棋盘；不含计时）
./examples/sudoku/scripts/cross_compare.sh

# 移动：同步触屏壳 + 构建
./examples/sudoku/scripts/sync_mobile_assets.sh
./examples/sudoku/scripts/build_ios.sh          # 优先 iPhone 模拟器
./examples/sudoku/scripts/build_android.sh      # APK；模拟器 UI 视本机环境
```

### 操作（中文）

1. 点「简单 / 中等 / 困难」开局（题库为**唯一解**固定题）。
2. 点格子选中；点数字或键盘 1–9 填入；清除用「清除」/ Delete。
3. 「笔记」切换笔记模式；冲突格会高亮并带无障碍名称（不只靠颜色）。
4. 「提示」= **填入一个正确数字**（不做推理解说）。
5. 「存档 / 读档」：桌面写 `build/sudoku/sudoku-save.jnsdk`；Web/移动走 bridge → localStorage / 偏好。
6. 损坏存档不会覆盖当前有效对局。

### 难度标签说明

题库难度是**目录分档**（选入题库时的线索目标带 + 唯一解校验），**不是**“挖空数 = 人类难度”的宣称。详见 `docs/PLATFORM.md`。

## 技术方案摘要

- **Desktop**：`jnative.ui` Grid/Button/键盘；核心经源码拼接进同一 CU。
- **Web**：`SudokuCore` → `app.wasm`；`web/app-shell.js` 只调导出。
- **Mobile**：同一 `app.wasm` + `mobile/web` 触屏布局；iOS WKWebView / Android WebView 加载本地 www；lifecycle 后台暂停并保存。

## 验证状态（本机实测，非历史描述）

见 `docs/PLATFORM.md`（构建时填写）。未跑通的端会标明 **NOT VERIFIED / BLOCKED**。

## 工具链缺陷与修复（本任务暴露）

| 问题 | 复现 | 处理 |
|------|------|------|
| `static final` 字段被拒 | `docs/repro/01-static-final.java` | Parser 允许 static final 常量 |
| 双 `main` → 重复 `@jn_main` / `%args.addr` | 两 class 各有 main | 每 CU 仅一个 entry main |
| 未限定 static 赋值未标 `WRITES_GLOBAL` → DCE 误删 | `CallResult2` | EffectAnalyzer 识别 VariableExpression 静态写 |
| InlinePass 未 remap `LoadStatic` | CallResult3 @ -O2 | IrUtil.mapValues + 仅 inline pure |
| npm `wat2wasm` Node 包装遇大 WAT OOM | 684KB WAT | 优先 Cellar 原生 WABT，跳过 Node shim |
| wasm `static int[] a = new int[N]` clinit 损坏 | `docs/repro/05-...` | 核心改为 lazy `ensureInit()` |
| wasm 静态 `String` 字段不可靠 | newGame 读 P0 | 题库改为方法内 local string lit |
| 递归求解 wasm 栈溢出 | unreachable | 迭代显式栈求解 |
| wasm 未调用 `jnative_clinit`，`static final int` 恒为 0 | `docs/repro/06-wasm-static-clinit.java` | `jnative_start` 先 `call $jnative_clinit`；WebUiIrToWat 收集 clinit |
| UI `Container`/`GridPane` 子控件上限 64，9×9 棋盘崩溃 | `docs/repro/07-ui-container-capacity.java` | `UiLib` 倍增扩容 `children` 与 grid 元数据 |

随机生成（种子、唯一解、限次）已在核心 `generate` 中实现（阶段二）；UI 未强绑，失败返回 error 7。

## 截图

- `screenshots/01-web.png` — 浏览器 + WASM
- `screenshots/02-desktop.png` — `jnative.ui` 桌面窗口
- `screenshots/03-mobile-touch-shell.png` — 触屏壳（本机 iOS 模拟器容器构建见 `docs/PLATFORM.md` BLOCKED 说明）
