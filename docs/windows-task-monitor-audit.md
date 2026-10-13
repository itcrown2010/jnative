# JNative Windows Task Monitor — 能力审计

**日期：** 2026-10-13  
**主机：** macOS arm64（无 Windows E2E 主机）  
**结论总览：** Windows 原生 Task Monitor **PARTIAL** — 采样 ABI / 应用源码 / Win32 UI 关键路径已落地；完整 `jnative-task-monitor.exe` 需在 Windows 10/11 x64 主机构建与验收。状态：**WINDOWS_E2E_UNVERIFIED**。

## 13 项能力矩阵

| # | 能力 | 状态 | 源码证据 |
|---|------|------|----------|
| 1 | Windows x64 编译目标 | **PARTIAL** | `CompileTarget` 仍仅 `native`/`wasm32-browser`（`CompileTarget.java`）。Windows 主机上 `Platform.detect()`→`WINDOWS_X86_64` 走 host clang。无独立 `windows-x64` 交叉目标。 |
| 2 | LLVM Windows triple | **MISSING** | 全仓无 `x86_64-pc-windows-msvc/gnu` 选择；链接为 host clang。 |
| 3 | PE/COFF 链接 | **PARTIAL** | `.exe` 命名（`Platform.java`）；GUI `-Wl,--subsystem,windows`（`JNativeCompiler.java`）。`Toolchain` 已为 Windows 去掉 `-rdynamic` 并加 `user32/comctl32/psapi/...`。 |
| 4 | Win32 FFI | **PARTIAL** | 进程采样在 `jn_am_windows.c`（C ABI + `@Native library=jn_am`）。无通用 `LoadLibrary` 绑定。 |
| 5 | Native Desktop UI | **PARTIAL** | `jnative_ui_win32.c`：窗口/消息循环/ListView 列与行/reparent/clipboard/timer 桩已加强；布局由 UiLib `setBounds` 驱动。 |
| 6 | 消息循环 | **READY** | `GetMessageW`/`DispatchMessageW`（`jnative_ui_win32.c`）。 |
| 7 | 多线程 | **PARTIAL** | Windows 运行时现已物化 `jnative_mt.c`（`NativeRuntimeSources.java`）。依赖 MinGW pthread；未在 Windows CI 验证。 |
| 8 | 定时器 | **PARTIAL** | `SetTimer`/`WM_TIMER` → `JN_UI_EVENT_TIMER`；应用侧仍用后台 `Thread.sleep` + `invokeLater`。 |
| 9 | GC / 内存 | **PARTIAL** | Windows 现已物化 `jnative_gc2.c`（malloc 半空间）。未跑 Windows GC stress。 |
| 10 | UTF-8 / UTF-16 | **READY** | UI `MultiByteToWideChar`；采样 `WideCharToMultiByte`。 |
| 11 | 标准库 | **PARTIAL** | UI/FS/string/GC/MT；socket/TLS/crash 仍缺或不完整。 |
| 12 | Windows 构建脚本 | **PARTIAL** | `examples/windows-task-monitor/scripts/build.ps1`、`build.sh`；macOS 可 MinGW 交叉编译 `jn_am_windows.c`。 |
| 13 | Windows CI | **MISSING** | 无 `windows-latest` workflow。 |

## 关键问题答复

| 问题 | 答案 |
|------|------|
| Hello World.exe？ | 理论：Windows 主机 `java -jar target/jnative.jar Hello.java -o Hello`。本机未验证。 |
| 原生窗口？ | 源码 READY；产品级依赖 Windows 主机链接 GC/MT/UI。 |
| kernel32 / psapi？ | 经 `jn_am_windows.c` / Win32 UI 隐式链接；`psapi`/`advapi32` 已在 Toolchain Windows 标志中。 |
| Win32 回调？ | `JnWndProc` + `g_dispatch`；ListView 通知已接。 |
| Windows x64 ABI？ | `X86_64WindowsAbi` 仍为 stub（固定宽度）；MinGW C 调用惯例可用。 |
| MSVC vs MinGW？ | **优先 MinGW/clang + ar**（与现有 `-l`/`lib*.a` 一致）。MSVC 未打通。 |
| macOS 交叉出 EXE？ | **不支持**完整托管 EXE。可交叉编译 `jn_am_windows.o`（已验证）。 |

## 本阶段已做修复（运行时）

1. `NativeRuntimeSources`：Windows 物化 GC2 + MT + tz。  
2. `Toolchain`：Windows 链接标志（无 `-rdynamic`，加 Win32 库）。  
3. `jnative_ui_win32.c`：ListView/表格/reparent/clipboard/timer/缺失符号。  

## 验收标记

- 采样 ABI MinGW 交叉编译：**PASS**（本机）  
- Task Monitor 托管逻辑 macOS smoke（`--dump`）：**PASS**（同 AmCore）  
- Windows EXE E2E：**WINDOWS_E2E_UNVERIFIED**
