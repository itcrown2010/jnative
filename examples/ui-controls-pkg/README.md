# 组合控件源码包（`local/ui-controls`）

作者：张晓达

第三方风格桌面组合控件：`NumericField`、`SearchableSelect`，经 `.jnpkg` 发布到示例本地注册表，由消费项目与商品编辑器依赖使用（**不手拷源码**）。

## 布局

```text
examples/ui-controls-pkg/
├── library/                 # local/ui-controls@0.1.0
│   ├── jnative.toml         # requires = ["DESKTOP_UI"]
│   └── src/
│       ├── NumericField.java
│       ├── SearchableSelect.java
│       ├── SelectOption.java
│       └── ControlsPkgVersion.java
├── consumer/                # 依赖声明 + 演示表单
├── registry/                # publish 目标
└── scripts/publish.sh / build-consumer.sh
```

## 平台

| 项 | 声明 |
|----|------|
| 能力 | `requires = ["DESKTOP_UI"]` |
| 已验证 | macOS Cocoa |
| 未承诺 | Windows / Linux UI 后端自动兼容 |

## 构建

```bash
mvn -q -DskipTests package
examples/ui-controls-pkg/scripts/build-consumer.sh
# 期望：UI_CONTROLS_SELF_TEST_OK
```

商品编辑器：

```bash
examples/product-editor/scripts/build.sh
./build/product-editor/ProductEditor --self-test
```

## 公共契约

见 stdlib `CompositeControl`（`BindingLib`）：创建/挂载、`BindingScope`、启用/隐藏/焦点、幂等 `dispose`、`alive`/`gen` 守卫晚到异步。

教程：`docs/tutorial/chapters/desktop-ui-composite-controls.md`
