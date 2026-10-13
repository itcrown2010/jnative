# JNative Task Monitor — 测试报告

**验收状态：PARTIAL**  
**Windows E2E：WINDOWS_E2E_UNVERIFIED**

## 环境

| 项 | 值 |
|----|-----|
| 审计/开发主机 | macOS arm64 Darwin 25.2 |
| MinGW | `x86_64-w64-mingw32-gcc` 16.2.0（Homebrew） |
| JNative | `target/jnative.jar`（含 Windows GC/MT 物化） |
| Windows 10/11 主机 | **无** |

## 已执行验证

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 1 | `jn_am_windows.c` MinGW 编译 | **PASS** | `build/windows-task-monitor/jn_am_windows.o` / `libjn_am.a` |
| 2 | `jnative_ui_win32.c` MinGW 编译 | **PASS** | `build/windows-task-monitor/jnative_ui_win32.o` |
| 3 | TaskMonitor + AmCore 合并编译（macOS GUI smoke） | **PASS** | `build/windows-task-monitor/TaskMonitor-macos-smoke` |
| 4 | `--dump` 真实进程列表（macOS ABI） | **PASS** | 输出含真实 PID/CPU/RSS；逻辑与 Windows 共用 AmCore |
| 5 | Windows 主机生成 `jnative-task-monitor.exe` | **SKIP** | 无 Windows 主机 |
| 6 | Windows 启动 GUI / 表格 / 搜索 / 排序 | **SKIP** | WINDOWS_E2E_UNVERIFIED |
| 7 | Windows 真实 CPU/内存刷新 | **SKIP** | WINDOWS_E2E_UNVERIFIED |
| 8 | Windows TerminateProcess | **SKIP** | WINDOWS_E2E_UNVERIFIED |
| 9 | 30 分钟稳定性 | **SKIP** | WINDOWS_E2E_UNVERIFIED |

## 复现命令

```bash
# ABI 交叉（macOS）
./examples/windows-task-monitor/scripts/build.sh

# 托管逻辑 smoke（macOS，非 Windows EXE）
# （开发用）merge TaskMonitor+AmCore → -L build/activity-monitor -l jn_am

# Windows 主机（待执行）
mvn -DskipTests package
powershell -File examples/windows-task-monitor/scripts/build.ps1
.\build\windows-task-monitor\jnative-task-monitor.exe --dump
.\build\windows-task-monitor\jnative-task-monitor.exe
```

## EXE 大小

Windows EXE：**未生成**（本环境）。  
macOS smoke 二进制约 **1.3 MB**（参考，非交付物）。

## 修改文件摘要

- `src/main/java/io/jnative/backend/NativeRuntimeSources.java` — Windows 启用 GC/MT/tz  
- `src/main/java/io/jnative/backend/Toolchain.java` — Windows 链接库  
- `src/main/resources/native/windows/jnative_ui_win32.c` — ListView/布局/剪贴板/定时器/缺失符号  
- `examples/activity-monitor/native/jn_am.h`、`jn_am_windows.c`、`jn_am_macos.*`  
- `examples/activity-monitor/core/AmCore.java` — Windows 保护进程 / 错误码  
- `examples/activity-monitor/scripts/build_native.sh`  
- `examples/windows-task-monitor/**`  
- `docs/windows-task-monitor-*.md`  

## 下一阶段建议

1. 在 `windows-latest` CI 跑 `build.ps1` + `--dump` + 最短 GUI 冒烟。  
2. 补 LLVM `x86_64-pc-windows-gnu` 交叉目标（可选）。  
3. Win32 `TrendChart` 真实绘制、DPI/深色主题完善。  
4. Windows GC stress / 多线程回归测试。  
