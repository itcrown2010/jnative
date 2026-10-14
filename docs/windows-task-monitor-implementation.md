# JNative Task Monitor — 实现说明（W11 对齐）

## 产品

**JNative Task Monitor** — 对齐 Windows 11 任务管理器浅色进程页；非 Activity Monitor 仿制壳。

## 目录

```
examples/windows-task-monitor/
  README.md
  src/TaskMonitor.java TmTokens.java TmViewModel.java TmActions.java TmCommands.java
  scripts/build.ps1 build.sh visual-qa.ps1

examples/activity-monitor/
  core/AmCore.java
  native/jn_am.h jn_am_windows.c jn_am_macos.c
```

## 架构

```
UI Thread (Win32)
  ← invokeLater ← Sampler Thread (jn_am_snapshot, single-flight)
TmViewModel (section/group/proc) → TableView
TmCommands → jn_am_signal / set_efficiency / run_task / open_file_location
```

关闭窗口：`alive` / `sampleToken` / `refreshGen` 停止回写并清理。

## 采样扩展（Windows P 行 extras）

`win_count` `group_class` `status_bits` `eco` `publisher`  
磁盘/网络：**不**用进程 I/O 冒充；列显示 `—`。

## UiLib / Win32

- 列 meta 可选 `##role`；标题嗅探 CPU/内存/磁盘/网络  
- 分区行 `jn:kind:section`；箭头 `jn:icon:chevron-*`  
- 行悬停 / 选中 custom draw；`TrackPopupMenu` 右键/更多  
- 伪双层表头：表格上方摘要 `HBox`

## 构建

**Windows：** `mvn -DskipTests package` → `scripts/build.ps1` → `jnative-task-monitor.exe`  
**macOS：** 仅交叉 `jn_am` + merge；逻辑可用 macOS `jn_am` smoke `--dump`。

## 验收标记

`WINDOWS_E2E_UNVERIFIED` 直至 Parallel 同条件叠图与交互录屏。
