# 活动监视器 UI 说明（交付）

## 布局（公共 API）

三区自适应（默认约 1100×720，最小 720×420 逻辑点）：

1. **顶栏** `Toolbar`：结束/详情（SF Symbol）｜`SegmentedControl`｜搜索｜说明｜溢出菜单  
2. **中部** `TableView`：稳定行 ID（pid:startSec:startUsec）；列宽/对齐经 `addColumnDef`  
3. **底栏** 指标文案 + **`TrendChart` 真实采样曲线** + 状态  

`Window.setMinSize` 已落到 Cocoa `contentMinSize`。

## 相对旧版的框架化改动

| 旧做法 | 现公共能力 |
|--------|------------|
| 应用层 ASCII sparkline | `TrendChart` |
| 应用层按行号重绑选中 | `TableRowIdProvider` + `selectByRowId` |
| Dialog 冒充「更多」 | `Toolbar.addOverflowMenu`（Cocoa context menu） |
| 列宽仅靠 Cocoa 标题启发式 | `TableColumnDef`（`title##w##align##flex`） |

采样 / 杀进程仍在 `jn_am_macos.c`（非 UI）。

## CLI

```bash
./build/activity-monitor/ActivityMonitor
./build/activity-monitor/ActivityMonitor --appearance dark
./build/activity-monitor/ActivityMonitor --view-mode mem
./build/activity-monitor/ActivityMonitor --window-size 780 480
./build/activity-monitor/ActivityMonitor --demo-detail --appearance light
```

## 截图

| 文件 | 说明 |
|------|------|
| `activity-monitor-light.png` | 浅色 + 趋势图 |
| `activity-monitor-dark.png` | 深色 |
| `activity-monitor-detail.png` | 详情 |
| `activity-monitor-cpu.png` / `mem.png` | 分段页 |

## 验证

`scripts/run_verify.sh`：采样、身份、SIGTERM/SIGKILL、自保护 — **已通过**。  
GUI 需人工确认缩放 / 深浅色 / Retina / 选中稳定（非仅 headless）。
