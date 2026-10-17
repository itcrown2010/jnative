# JNative 任务管理器（Windows 11 任务管理器重构版）

用 JNative（Java 子集 → LLVM → 原生 EXE）实现，对标 Windows 11 自带任务管理器。无 Swing/AWT/JRE：整个界面由 UiLib 的 PaintView 画布通过 GDI/GDI+ 自绘，数据来自 `jn_am`（进程快照）与 `jn_tm`（性能/服务/会话/启动项/进程操作）两个原生库。

## 页面

进程 · 性能（CPU/内存/磁盘/网络/GPU，60 秒图，按逻辑处理器视图）· 应用历史记录 · 启动应用 · 用户 · 详细信息 · 服务 · 设置（刷新速度、浅/深/系统主题、性能页选项、以管理员身份重启）

## 主要交互

- 进程分组（应用/后台进程/Windows 进程）、展开/折叠、svchost 显示为"服务主机: 服务名"
- 双行表头（CPU/内存/磁盘整机百分比）、列排序、列宽拖动、列显示菜单、热度底色
- 搜索（名称/发布者/PID）、右键菜单、结束任务/结束进程树、效率模式、优先级、挂起/恢复、转储、打开文件位置、属性、服务启停、启动项启用/禁用、用户断开/注销
- 刷新 1 s（可高/低/暂停，F5 手动）；刷新、排序、选中、滚动位置保持
- 快捷键：Ctrl+F 搜索，F5 刷新，Del 结束任务，Esc 清除搜索，方向键选择

## 构建（Windows 主机）

```powershell
mvn -DskipTests package
.\examples\windows-task-monitor\scripts\build.ps1
.\build\windows-task-monitor\jnative-task-monitor.exe
```

## 源码

```
src/TmTheme.java    主题色板/字体/格式化/稳定排序
src/TmData.java     jn_tm 绑定、数据模型与采样（TmStore）
src/TmGrid.java     自绘表格（表头、排序、滚动条、键盘）与 TmUi 绘制助手
src/TmBuild.java    各页数据 → 行（分组、热度）
src/TmPerf.java     性能页与设置页
src/TmDialogs.java  新建任务对话框
src/TaskMonitor.java 入口、窗口、导航、命令栏、菜单与动作
native/jn_tm_*.c/h  原生采样/操作库（含 jn_tm_test.c）
../activity-monitor/  AmCore + jn_am（进程快照）
```

## 已验证 / 未验证

已验证（ARM64 Windows 11，200% DPI，截图）：默认尺寸、最大化、900×600 DIP、搜索、深色主题、各页渲染、展开/键盘导航、深色流程重复 2 次无绘制异常（设置页诊断行：绘制异常 0 次）；刷新约 65 ms、绘制约 28 ms。

**UNVERIFIED**：150% DPI（本机 200%）、录屏（无 ffmpeg）、高对比度、x64 机器。

## 已知差异（诚实缺口）

1. 进程磁盘列为进程读写 I/O 计数；网络/GPU 无按进程数据（显示整机或 `—`）。
2. 应用历史记录为当前运行进程的累计值，无 30 天历史。
3. 启动影响未测量；未实现亲和性对话框、"分析等待链"、悬停提示。
4. 无自绘标题栏（系统标题栏按主题着色）；设置不持久化。
5. 详细信息列过多时无横向滚动，名称列会被压缩。
6. 像素级一致未验证；旧文档 `docs/windows-task-monitor-*.md` 描述的是上一版架构，仅供参考。
