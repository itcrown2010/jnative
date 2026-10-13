# JNative Task Monitor — 实现说明

## 产品

**JNative Task Monitor** — Windows 任务监视器示例，界面结构对齐 macOS Activity Monitor（顶栏 / 进程表 / 底栏）。

## 目录

```
examples/windows-task-monitor/
  README.md
  src/TaskMonitor.java          # UI + 采集调度（JNative）
  scripts/build.ps1             # Windows 主机构建
  scripts/build.sh              # Git Bash / MinGW 交叉 ABI

examples/activity-monitor/
  core/AmCore.java              # 共享解析 / CPU 差分 / 终止 / 保护
  native/jn_am.h                # 跨平台快照 ABI
  native/jn_am_windows.c        # Toolhelp32 + GetProcessTimes + …
  native/jn_am_macos.c          # 既有 macOS 实现
```

## 复用的 JNative 能力

- 编译器 AOT → 原生 EXE（`--app-type gui`）  
- `UiLib`：`Window` / `TableView` / `SearchField` / `Button` / `Dialogs` / `Application.invokeLater`  
- `Thread` + 后台采样线程  
- `@Native` + `library = "jn_am"`  
- `Clipboard`（Win32 实现已补）  

## Windows API（采样）

| API | 用途 |
|-----|------|
| `CreateToolhelp32Snapshot` / `Process32FirstW` / `NextW` | 进程枚举、线程数、PPID、名称 |
| `GetProcessTimes` | CPU 时间、创建时间（身份） |
| `GetProcessMemoryInfo` (`PROCESS_MEMORY_COUNTERS_EX`) | WorkingSet / PrivateUsage |
| `OpenProcessToken` / `GetTokenInformation` / `LookupAccountSidW` | 用户名 |
| `QueryFullProcessImageNameW` / `GetModuleFileNameExW` | 路径 |
| `GetSystemTimes` / `GlobalMemoryStatusEx` / `GetSystemInfo` | 系统 CPU / 内存 / 逻辑核心 |
| `OpenProcess` + `TerminateProcess` | 结束进程 |
| `QueryPerformanceCounter` | 单调时钟 |

不可用字段：**N/A** / flag 位关闭，不伪造。

## CPU 口径

- 进程：两次采样的 `(kernel+user)` FILETIME 差 / 墙钟差 / `ncpu` → 百分数；**100% = 全部逻辑核心打满** 时的归一化与 macOS 示例一致（`AmFormat.cpuPct`，内部 milli）。  
- 系统：`GetSystemTimes` 差分（kernel 含 idle，已减去 idle）。  
- 首次采样显示「— / 等待采样」。  

## 架构

```
UI Thread (Win32 message loop)
  ← invokeLater ← Collector Thread (jn_am_snapshot)
TableView / Footer / Detail
```

关闭窗口：`alive`/`sampleToken`/`refreshGen` 停止后续回写。

## 构建

**Windows 10/11 x64（MinGW clang）：**

```powershell
# 先构建编译器
mvn -DskipTests package
.\examples\windows-task-monitor\scripts\build.ps1
.\build\windows-task-monitor\jnative-task-monitor.exe
```

**macOS（仅 ABI 交叉 + 逻辑 smoke）：**

```bash
./examples/windows-task-monitor/scripts/build.sh
# → build/windows-task-monitor/libjn_am.a (COFF)
```

## 运行时改动（不破坏 Darwin）

- `NativeRuntimeSources` / `Toolchain` 仅在 `WINDOWS_X86_64` 分支变化。  
- macOS Activity Monitor 路径未改逻辑；`jn_am_macos.c` 改 include `jn_am.h`。  

## 已知限制

1. 无 Windows CI / 本机无 EXE E2E。  
2. Win32 UI 非完整 Cocoa 对等（Toolbar/SF Symbol/TrendChart 绘制弱）。  
3. 无独立 LLVM `windows-x64` 交叉目标。  
4. 结束进程映射为 `TerminateProcess`（无 POSIX SIGTERM 语义）。  
5. Crash handler / TLS / 完整 socket 仍未上 Windows。  
