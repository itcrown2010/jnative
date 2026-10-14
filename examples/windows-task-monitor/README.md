# JNative Task Monitor（Windows 11 任务管理器对齐）

目标：用 JNative AOT + Win32 UiLib 复现 **Windows 11 任务管理器** 浅色 **进程页** 的信息结构、交互与真实采样口径。应用保留 **JNative** 名称与自身图标；进程图标从映像路径提取。

**本环境状态：`WINDOWS_E2E_UNVERIFIED`** — 完整 EXE / 叠图 / 录屏须在 Windows 10/11 x64（如 Parallel）构建并对照验收。见 [视觉验收](../../docs/windows-task-monitor-visual-qa.md)。

## 本轮进程页

- 左侧导航：进程 / 性能 / 应用历史记录 / 启动应用 / 用户 / 详细信息 / 服务；**设置**固定底部  
- 顶区居中搜索（名称 / 发布者 / PID）  
- 白内容面板；命令栏：运行新任务、结束任务、效率模式、更多  
- 列：名称、状态、CPU、内存、磁盘、网络；资源列上方整机摘要（伪双层表头）  
- 应用 / 后台 / Windows 分区；应用组按路径折叠（非官方 AUMID）  
- CPU/内存热度；磁盘/网络显示 `—`（诚实缺口，不伪造）  
- 其他导航页：说明面板，**不用空表/假数据冒充**

## 构建（Windows 主机）

```powershell
mvn -DskipTests package
.\examples\windows-task-monitor\scripts\build.ps1
.\build\windows-task-monitor\jnative-task-monitor.exe
```

产物：`build\windows-task-monitor\jnative-task-monitor.exe`

## 源码布局

```
examples/windows-task-monitor/src/
  TaskMonitor.java    # 入口、TmUi、壳、列渲染
  TmTokens.java       # 临时 DIP token
  TmViewModel.java    # 分区/分组虚拟行
  TmActions.java      # 采样、表重建、导航
  TmCommands.java     # 结束/效率/运行新任务/菜单
examples/activity-monitor/
  core/AmCore.java
  native/jn_am.h / jn_am_windows.c
```

## 文档

| 文档 | 内容 |
|------|------|
| [w11-parity](../../docs/windows-task-monitor-w11-parity.md) | 功能对照清单与指标口径 |
| [layout-tokens](../../docs/windows-task-monitor-layout-tokens.md) | 布局 token / 参考机模板 |
| [visual-qa](../../docs/windows-task-monitor-visual-qa.md) | 叠图验收流程 |
| [audit](../../docs/windows-task-monitor-audit.md) | 能力审计 |
| [implementation](../../docs/windows-task-monitor-implementation.md) | 实现说明 |

视觉脚本：`scripts/visual-qa.ps1` → `docs/screenshots/w11-process/`

## 已知差异（相对参考）

1. 双层表头为摘要条 + 单行 ListView，非原生双行 Header。  
2. 分组启发式 ≠ AUMID。  
3. 磁盘/网络未采集。  
4. 「运行新任务」为确认建议命令（notepad），非完整运行对话框。  
5. 深色/高对比度未做。  
6. 视觉像素级一致 **未验证**。

推进顺序（固定）：进程 → 性能 → 详细信息 → 服务/用户 → 启动/历史 → 设置。
