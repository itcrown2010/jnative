# 桌面 UI：状态绑定与表单校验 — 交付说明

作者：张晓达  
验收平台：macOS Cocoa（首期）

## 交付物

| 项 | 路径 |
|----|------|
| 公共 API | `src/main/resources/stdlib/ui/BindingLib.java` |
| 中文教程 | `docs/tutorial/chapters/desktop-ui-binding.md` |
| 商品编辑器 | `examples/product-editor/ProductEditor.java` |
| 自测 | `./build/product-editor/ProductEditor --self-test` → `PE_SELF_TEST_OK` |
| 截图 | `examples/product-editor/docs/screenshots/` |
| AM 接入 | `examples/activity-monitor/desktop/ActivityMonitor.java`（`BindingScope` + 页面/搜索/选中/暂停/工具栏启用） |
| 上一轮 UI | `docs/desktop-ui-delivery.md`（布局/工具栏/表格/生命周期仍有效） |

## 能力摘要

1. **ObservableValue / ObservableInt**：读改、相等跳过、订阅取消、批量、嵌套上限、UI 线程、监听器在值提交后于无锁快照上回调  
2. **Bindings**：标签、文本单向/双向、勾选、分段、下拉稳定 id、启用态、显式 `bindComputed`  
3. **Form**：必填/长度/数值范围/自定义/跨字段；`serverError`；`FieldState`；提交再验  
4. **DecimalFieldModel**：原始文本与 `Decimal` 分离；解析失败不写成 0  
5. **生命周期**：`BindingScope.dispose`；控件 `disposed` / 窗口关闭后回调空操作；后台 `invokeLater` 带 alive 令牌  

## 实际测试结果

| 用例 | 结果 |
|------|------|
| 相等跳过 / 通知 / 取消 | PASS（`--self-test`） |
| 批量更新一次通知最终值 | PASS |
| 嵌套更新 + `MAX_NESTING` 抛错 | PASS |
| `BindingScope.dispose` 取消订阅 | PASS |
| 非法数字不 coerce 为 0，恢复可再解析 | PASS |
| 商品编辑器编译 + GUI 启动 | PASS（见截图） |
| 分类下拉显示标签、勾选同步原生 | PASS（修复 `combo_add_item`/`set_selected`→Cocoa） |
| AM `run_verify.sh` | PASS（`ALL VERIFY CHECKS PASSED`） |
| 浅色 / 深色外观 | PASS（截图 `product-editor-light.png` / `product-editor-dark.png`） |

## 生命周期责任（明确）

| 责任方 | 动作 |
|--------|------|
| 应用 | 窗口 `onClose` 调用 `scope.dispose()`；设 `alive=0` |
| 绑定 API | 登记到 scope；回调检查 disposed |
| 后台任务 | 仅经 `Application.invokeLater`；检查 alive / disposed |
| GC | **不**作为原生监听器释放手段 |

## 已知限制

- 无反射、无自动依赖追踪、无完整 MVVM  
- 完整 VoiceOver 角色/名称映射待平台扩展；当前靠字段旁错误标签 + `Theme.STYLE_ERROR`  
- 高频采样对象图不做可观察；AM 仅轻量 UI 状态 + 有界快照表格刷新  
- Windows / GTK 未在本轮验收  
