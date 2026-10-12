# 活动监视器 UI 说明（交付）

## 布局

三区自适应（默认约 1100×720，最小 720×420）：

1. **顶栏** `Toolbar`：结束进程 / 详情｜CPU·内存 模式｜搜索｜更多  
2. **中部** `TableView` + `NSScrollView`：进程表为主体（`vgrow`）  
3. **底栏**：CPU / 内存页合计 / ASCII 趋势 / 采样状态 + 指标脚注  

未实现的磁盘 / 网络 / 能耗页签不展示。

## 原生控件

基于现有 Cocoa/AppKit 后端（`jnative_ui_cocoa.m` + `UiLib`）：

- 工具栏按钮（chrome style）、`SearchField`、`NSTableView`、菜单栏、确认 Sheet（`Dialogs.terminateChoose`）
- 系统字体 / 标签色 / 交替行 / 选中与滚动条；`--appearance light|dark` 强制 `NSApp.appearance`
- 列头点击排序（`JN_UI_EVENT_HEADER`）；名称列自动伸展

业务采样与终止仍在 `AmCore` / `AmActions`，未另建平行 UI 运行时。

## 采样与刷新

- 后台线程 warmup：**连续两次** `jn_am_snapshot`，再 **一次** `invokeLater` 回写（避免第二次 TASK 丢失）
- 周期性刷新：`armRefresh` 短线程 sleep 后回 UI 再挂下一拍（长 `while` 循环在 Cocoa 下会提前退出）
- 首次 CPU 为「—」/「正在采样」；第二次起显示真实值
- 进程表在 `sampleCount >= 2` 后重建；刷新保持选中身份键

## 截图

| 文件 | 说明 |
|------|------|
| `docs/screenshots/activity-monitor-light.png` | 浅色主窗口 |
| `docs/screenshots/activity-monitor-dark.png` | 深色主窗口 |
| `docs/screenshots/activity-monitor-detail.png` | 进程详情 |

## 验证（本机）

- `run_verify.sh`：采样、身份、SIGTERM/SIGKILL、自保护 — **PASSED**
- GUI：默认尺寸、Retina、浅/深色、列排序、详情窗口、关闭后 `alive`/token 作废
- 终止仅对 `am_*` 测试进程验证
