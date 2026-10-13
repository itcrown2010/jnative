# 桌面 UI：组合控件 → 打包 → 跨项目使用

作者：张晓达  
验证平台：macOS Cocoa  
示例：`examples/ui-controls-pkg/`、`examples/product-editor/`

## 0. 稳定性前提

上一轮稳定性路径（PE / AM / desktop-sample self-test）应先通过。本轮另修复：`.jnative/pkg-cache/extracted` 下的包源码曾被 `SourceFiles.isSourcePath` 误过滤（路径含 `.jnative`）——现已允许 extracted 源码参与编译。

**未标记桌面 UI 稳定版**；组合控件仅在已声明 `DESKTOP_UI` 且本机 Cocoa 验证。

## 1. 公共契约：`CompositeControl`

位于 stdlib `BindingLib`，组合控件继承它：

| 契约 | API |
|------|-----|
| 创建/挂载 | `root()` → 作为 VBox 加入布局 |
| 输入属性 | 子类字段 + `Observable*` / `DecimalFieldModel` |
| 事件订阅 | 现有 `subscribe` / `onClick` / `onKeyDown`（不另起事件体系） |
| 布局 | 自身为 `VBox`，参与父布局 |
| 启用/隐藏/焦点 | `setControlEnabled` / `setControlVisible` / `focusTarget()` |
| 销毁 | `dispose()` 幂等；`alive`/`gen` 作废晚到异步 |

## 2. 创建 NumericField

库源码：`examples/ui-controls-pkg/library/src/NumericField.java`

要点：

- 原始文本 `getRaw()` 与 `getParsed()`（`Decimal`）分离
- 非法输入保留文本，**不**静默变 0
- `setMaxFractionDigits` / `setAllowNegative` / `setAllowEmpty` / `setRange`
- 双向绑定用 `setTextIfChanged`，不强制挪光标

## 3. SearchableSelect

`SearchableSelect.java` + `SelectOption.java`：

- 搜索框 + `ListView` 展示（标签旁附稳定 id）
- 键盘 ↑↓ / Enter / Esc
- 同名项靠 `id` 区分
- 空列表 / 无结果 / 可选异步过滤（`setAsyncFilter` + `gen`）
- `dispose` 后晚到结果不更新 UI

限制：异步仅 `Thread` + `Application.invokeLater`；不引入未验证的高级 async API。

## 4. 测试

```bash
examples/ui-controls-pkg/scripts/build-consumer.sh
# UI_CONTROLS_SELF_TEST_OK
```

覆盖：非法文本、小数位、负数、空值、同名项稳定 id、dispose/gen。

## 5. 打包到本地注册表

```bash
examples/ui-controls-pkg/scripts/publish.sh
# → registry/local/ui-controls/0.1.0/package.jnpkg
```

清单声明：`type = "source"`、`requires = ["DESKTOP_UI"]`、`min_compiler`。

## 6. 另一项目依赖使用

消费端 `jnative.toml`：

```toml
[dependencies]
"local/ui-controls" = "0.1.0"
```

构建时 `--registry examples/ui-controls-pkg/registry`，**不要**把控件源码复制进 `app/src`。

商品编辑器已按此方式接入单价/数量 `NumericField` 与分类 `SearchableSelect`。

## 7. 下一步

- [应用壳](./desktop-ui-app-shell.md)
- [生命周期与稳定](./desktop-ui-stability.md)
