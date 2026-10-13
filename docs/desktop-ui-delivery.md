# 桌面 UI 框架交付说明

作者：张晓达  
验收平台：macOS Cocoa（首期）

## 交付物

| 项 | 路径 |
|----|------|
| 公共 API + Cocoa | `stdlib/ui/UiLib.java`，`native/posix/jnative_ui*.{h,c,m}` |
| 平台矩阵 | `docs/desktop-ui-platform-matrix.md` |
| 中文教程 | `docs/tutorial/chapters/desktop-ui-framework.md` |
| UI 工作台 | `examples/ui-workbench/UiWorkbench.java` |
| 工作台截图 | `build/ui-workbench/screenshots/workbench-light.png` |
| 活动监视器迁移 | `examples/activity-monitor/desktop/ActivityMonitor.java` |
| AM 验证 | `build/activity-monitor/verify-results.txt`（`ALL VERIFY CHECKS PASSED`） |

## 本轮能力摘要

1. **布局**：`setSpacing`/`setPadding` 校验；`hgrow`/`vgrow`；隐藏回流；`Window.setMinSize`→原生  
2. **工具栏**：推送按钮、分段、搜索、弹性间距、溢出菜单；不支持则抛错或位图为 0  
3. **表格**：稳定行 ID、列宽对齐、空态、滚动保留、双击、表头排序  
4. **主题**：`Theme.applyStyle` / `colorOf`  
5. **TrendChart**：有界历史、纵轴范围、系统色、无假数据  
6. **对话框**：既有 `Dialogs` / `FileDialog`  
7. **生命周期**：`UiSubscription` 取消；窗口关闭停采样  

## 实际验证

- [x] `run_verify.sh` 业务无回归  
- [x] 编译并通过 GUI 启动 Activity Monitor / UI 工作台  
- [x] 浅色截图（含趋势图）  
- [x] 深色截图  
- [ ] 长时间内存边界：需人工跑 ≥10 分钟观察（TrendChart capacity 有界）  
- [ ] Retina 双缩放人工核对  

## 后续（已交付）

状态绑定 / 表单校验见 [desktop-ui-binding-delivery.md](./desktop-ui-binding-delivery.md)。

## 剩余限制

- 非 `NSToolbar`（`FEAT_NATIVE_TOOLBAR=0`）；工具栏为原生控件合成  
- Windows / GTK：多数高级能力未验收；查询 `UiPlatform.supports`  
- 表增量 patch（逐行 diff）仍为全量 `setItems` + 身份重绑；滚动偏移在 reload 时尽量保留  
- `Dialogs.prompt` 仍为确认伪装  
- HPACK 等与 UI 无关的法律 quarantine 项另案  
