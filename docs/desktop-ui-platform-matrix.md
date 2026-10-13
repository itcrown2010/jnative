# 桌面 UI 平台支持矩阵

作者：张晓达

本轮验收平台：**macOS（Cocoa）**。其他平台明确标注，不伪装为已实现。

| 能力 | macOS Cocoa | Windows | Linux/GTK | Headless |
|------|-------------|---------|-----------|----------|
| VBox / HBox / padding / spacing / flex / minSize | **已实现** | 布局逻辑可用；原生 peer 有限 | 未验收 | 模拟 peer |
| 窗口缩放 / 最小尺寸 | **已实现** | 部分 | 未验收 | 事件可注入 |
| 合成工具栏（按钮 + 分段 + 搜索） | **已实现** | 基础控件 | 未验收 | 有 |
| 真·NSToolbar | **未实现**（`FEAT_NATIVE_TOOLBAR=0`） | — | — | — |
| SegmentedControl | **已实现** | 不支持（位图无） | 不支持 | stub |
| SearchField 样式 | **已实现** | 基础 TextField | 未验收 | 有 |
| SF Symbol / Tooltip | **已实现** | 不支持 | 不支持 | no-op |
| 溢出菜单（context menu） | **已实现** | 不支持则抛错 | 不支持则抛错 | 无 |
| TableView（原生 NSTableView） | **已实现** | 有限 | 未验收 | 有 |
| 稳定行 ID / 滚动保留 | **已实现（Java+Cocoa）** | 滚动可能 -1 | — | 部分 |
| TrendChart 折线 | **已实现** | 不支持（位图无） | 不支持 | 空 |
| Dialogs / 文件选择 | **已实现** | 有限 | 未验收 | 可编程结果 |
| 语义 Theme 样式 | **已实现（跨平台逻辑色）** | 同左 | 同左 | 同左 |

查询运行时能力：

```java
UiPlatform.supportSummary();
UiPlatform.supports(UiPlatform.FEAT_TRENDCHART);
```

不支持的接口：**不得静默伪装成功**；例如 `Toolbar.addOverflowMenu` 在缺少 `FEAT_OVERFLOW_MENU` 时抛出 `UiNativeException`。

完整能力与验证状态（含菜单、生命周期、VS Code、打包）见 [desktop-ui-capability-matrix.md](./desktop-ui-capability-matrix.md)。
