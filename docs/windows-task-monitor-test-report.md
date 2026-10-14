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

## 2026-10-14 稳定性修补（待 Windows 复验）

| 项 | 改动 |
|----|------|
| 每秒排序卡顿 | `TaskMonitor` 气泡排序 → 堆排序 O(n log n) |
| 图标闪烁 / ExtractIcon 过重 | `list_clear` 不再清空 HIMAGELIST 与路径缓存 |
| 刷新闪白 | `WM_SETREDRAW` 包住 clear+insert，结束后再 thaw |
| 滚动跳动 | `table_set_scroll_y` 去掉 `EnsureVisible`，仅 `ListView_Scroll` |
| 选中丢失 | 行仍在视图内则 `selectByRowId` 重绑，不因 native 事件丢失清 sel |
| 详情内存文案 | 专用字节 vs 工作集区分 |

Mac 侧：`jn_am` + TaskMonitor 合并 smoke/`--dump` **PASS**。完整 GUI：**WINDOWS_E2E 待 Parallel 复验**。

## 下一阶段建议

1. 在 Parallel Windows 上 `git pull` + `build.ps1` 复验上表项。  
2. 在 `windows-latest` CI 跑 `build.ps1` + `--dump` + 最短 GUI 冒烟。  
3. 补 LLVM `x86_64-pc-windows-gnu` 交叉目标（可选）。  
4. Windows GC stress / 多线程回归测试。  
