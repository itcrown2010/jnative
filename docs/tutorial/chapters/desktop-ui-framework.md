# 桌面 UI 框架（公共 API）

作者：张晓达

目标：只用 JNative 公共 UI 接口构建结构清晰的原生桌面应用，无需为每个项目手写 Cocoa 控件与坐标布局。

## 1. 布局

```java
VBox root = new VBox();
root.setSpacing(8);   // < 0 抛 UiException
root.setPadding(12);

HBox bar = new HBox();
Label flex = new Label("");
flex.hgrow = 1;       // 或 prefWidth = 0：吃掉剩余宽度
table.vgrow = 1;      // 吃掉剩余高度

window.setMinSize(720, 420); // 逻辑点；会落到 Cocoa contentMinSize
```

隐藏控件会触发父容器 `layout()`，避免重叠空洞。非法 spacing/padding/minSize 会抛错，而不是静默重叠。

## 2. 工具栏

```java
Toolbar tb = new Toolbar();
tb.addPushToolButton("结束", "xmark.circle", "结束所选进程", () -> { ... });
tb.addSegmented(new SegmentedControl("CPU\t内存"));
tb.addFlexibleSpace();
tb.addSearch(search);
if (UiPlatform.supports(UiPlatform.FEAT_OVERFLOW_MENU) != 0) {
    tb.addOverflowMenu("更多", moreMenu);
}
```

macOS 使用原生按钮 / `NSSegmentedControl` / 搜索框样式。**不是** `NSToolbar`（见平台矩阵 `FEAT_NATIVE_TOOLBAR=0`）。

## 3. 表格

```java
table.setRowIdProvider(row -> row.stableId());
table.addColumnDef("名称", nameCol, 0, "L", 1);  // flex 列
table.addColumnDef("CPU %", cpuCol, 84, "R", 0);
table.setEmptyMessage("无数据");
table.setItems(rows);
table.selectByRowId(id); // 刷新后按身份恢复，勿用行号当业务 ID
table.onDoubleClick(ev -> { ... });
table.onHeaderClick(ev -> table.sortByColumn(ev.selectedIndex));
```

## 4. 主题

```java
Theme.setAppearanceMode(Theme.MODE_SYSTEM); // 或 LIGHT / DARK
Theme.applyStyle(label, Theme.STYLE_BODY);
Theme.applyStyle(hint, Theme.STYLE_SECONDARY);
int accent = Theme.colorOf(Theme.ACCENT);
```

应用不要硬编码字体名；颜色随浅色/深色解析。

## 5. 趋势图

```java
TrendChart chart = new TrendChart();
chart.setCapacity(32);          // 有界
chart.setYRange(0.0, 100.0);
chart.setSamplesFromMilli(hist, 0.01); // 真实数据；无内置假曲线
```

## 6. 对话框与生命周期

```java
Dialogs.info(window, "说明");
boolean ok = Dialogs.confirm(window, "继续？");
Dialogs.error(window, "失败");
String path = FileDialog.open(window, "");

UiSubscription sub = button.subscribeClick(() -> { ... });
window.onClose(() -> { sub.cancel(); alive = 0; });
Application.invokeLater(() -> { /* 后台完成后回 UI 线程 */ });
```

## 示例

- `examples/ui-workbench/UiWorkbench.java`
- `examples/activity-monitor/desktop/ActivityMonitor.java`
