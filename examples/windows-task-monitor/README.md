# JNative Task Monitor（Windows）

Windows 10/11 x64 原生任务监视器：真实进程枚举、CPU/内存/线程、搜索排序、详情、受控结束。  
**不依赖 JVM/JRE 运行**（由 JNative AOT 生成 EXE）。无 Electron / WebView。

> 当前仓库在 macOS 上完成了 ABI 交叉编译与托管逻辑 smoke；**Windows 主机 E2E 标记为 `WINDOWS_E2E_UNVERIFIED`**。见 [docs/windows-task-monitor-test-report.md](../../docs/windows-task-monitor-test-report.md)。

## 界面

参考 macOS Activity Monitor 三区：

1. **顶栏** — 详情 / 结束进程 / 搜索  
2. **中部** — 进程表（名称、PID、用户、CPU%、内存、线程）  
3. **底栏** — 系统 CPU、进程数、线程数、刷新状态  

截图：请在 Windows 主机跑通后放入 `docs/screenshots/`（本环境无 Windows GUI 截图）。

## 编译（Windows 主机）

依赖：JDK 17+、已构建的 `target/jnative.jar`、MinGW/LLVM `clang` + `ar`。

```powershell
mvn -DskipTests package
.\examples\windows-task-monitor\scripts\build.ps1
```

产物：`build\windows-task-monitor\jnative-task-monitor.exe`

## 运行

```powershell
.\build\windows-task-monitor\jnative-task-monitor.exe
.\build\windows-task-monitor\jnative-task-monitor.exe --dump
.\build\windows-task-monitor\jnative-task-monitor.exe --bench 8
```

## 功能

| 功能 | 说明 |
|------|------|
| 进程列表 | Toolhelp32，真实 PID/名称/线程数 |
| CPU % | `GetProcessTimes` 双采样差分；首次为「—」 |
| 内存 | WorkingSet / PrivateUsage；拒绝访问 → N/A |
| 用户 | Token SID → `LookupAccountSidW`；失败 → N/A |
| 路径 | `QueryFullProcessImageNameW` |
| 搜索 / 排序 | 名称与 PID；列头切换升降序；默认 CPU 降序 |
| 详情 | 双击或「详情」；复制到剪贴板 |
| 结束进程 | 确认框 → 身份校验 → `TerminateProcess`；禁杀自身与关键系统进程 |
| 刷新 | 默认 1s；后台线程采样，UI 线程更新 |

## Windows API 依赖

`kernel32` / `user32` / `comctl32` / `psapi` / `advapi32` / `shell32` / `gdi32` / `comdlg32`（见 `Toolchain` Windows 链接标志与 `jn_am_windows.c`）。

## 已知限制

- 需在 **Windows x64 + MinGW clang** 上构建完整 EXE；macOS 仅可交叉编译 `jn_am`。  
- Win32 UI 能力仍弱于 Cocoa（无完整 Toolbar/曲线对等）。  
- 结束进程无 POSIX 信号语义。  
- 无 Windows CI；请本地验收后更新测试报告。  

## 文档

- [审计](../../docs/windows-task-monitor-audit.md)  
- [实现](../../docs/windows-task-monitor-implementation.md)  
- [测试报告](../../docs/windows-task-monitor-test-report.md)  
- 共享核心：[../activity-monitor/](../activity-monitor/)  
