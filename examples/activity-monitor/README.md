# JNative 活动监视器（macOS）

真实进程列表 + CPU/内存采样 + 自动刷新 + 详情 + 受控终止。  
**不是**待办事项应用。首版仅支持 macOS；数据来自 `libproc` / Mach / `sysctl`，不解析 `ps`/`top` 文本，无 mock。

## 构建与运行

```bash
# 依赖：已构建的 target/jnative.jar、clang、macOS
./examples/activity-monitor/scripts/build_desktop.sh

# GUI
./build/activity-monitor/ActivityMonitor

# 无界面采样 / 压测
./build/activity-monitor/AmCore --dump
./build/activity-monitor/AmCore --bench 8
./examples/activity-monitor/scripts/build_core.sh   # 仅 headless
```

产物：

| 路径 | 说明 |
|------|------|
| `build/activity-monitor/ActivityMonitor` | 原生桌面 GUI（`--app-type gui`） |
| `build/activity-monitor/AmCore` | 无 UI 采样 / 终止 CLI |
| `build/activity-monitor/libjn_am.a` | macOS C ABI 静态库 |
| `build/activity-monitor/am_*` | 专用测试进程 |

## 指标口径

见 [METRICS.md](./METRICS.md)。摘要：

- **CPU%**：两次采样的 `cpu_ns` 差 / 单调时钟差；**100% = 1 逻辑核心**；首次显示「等待采样」。
- **RSS**：`proc_taskinfo.pti_resident_size`。
- **物理占用**：`ri_phys_footprint`（`RUSAGE_INFO_V2`）。
- **系统内存页**：`HOST_VM_INFO64`（与苹果「活动监视器」不必一致）。

不可用字段显示「不可用」，不用 0 冒充。

## 权限与保护

- 无权限读字段时对应 flag 关闭 → UI 显示「不可用」。
- 终止使用 `kill(2)`（SIGTERM / SIGKILL），不拼 shell。
- 操作前确认；按 **PID + 启动时间** 校验身份，防 PID 复用。
- **不**自动提权；EPERM 等原样报告。
- 默认禁止：PID≤1、`kernel_task`、本监视器进程。无批量强制结束。
- 信号发送成功 ≠ 进程已退出；会再采样确认。

## 验证

```bash
./examples/activity-monitor/scripts/run_verify.sh
# 结果：build/activity-monitor/verify-results.txt
```

只终止专用测试进程（`am_idle` / `am_burn` / `am_ignore_term`）。

## 文档

- [METRICS.md](./METRICS.md) — 指标与系统 API
- [docs/tutorial.md](./docs/tutorial.md) — 使用与开发教程
- [docs/screenshots/](./docs/screenshots/) — 界面截图
- [docs/LIMITATIONS.md](./docs/LIMITATIONS.md) — 限制与后续

## 布局（GUI）

1. **顶栏** 紧凑工具栏：结束进程 / 详情｜CPU·内存 模式｜搜索｜更多（暂停/刷新见「显示」菜单）  
2. **中部** 进程表为主体（列头排序；名称 + 图标；CPU/内存页切换默认排序）  
3. **底栏** CPU / 内存页合计 / 趋势 / 采样状态；详情为独立窗口  

默认约 1100×720（最小 720×420）。默认中文；`LANG=en*` 英文化。  
`--appearance light|dark` 强制外观；`--demo-detail` 打开首行详情（截图用）。

**刷新策略：** warmup 双采样后填充表格；之后自动采样更新底栏概览，表格在搜索/排序/「立即刷新」时重建（见 `docs/LIMITATIONS.md`）。更多 UI 说明见 [docs/UI_NOTES.md](./docs/UI_NOTES.md)。
