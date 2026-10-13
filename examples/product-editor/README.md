# 商品编辑器

演示可观察状态、双向绑定、表单校验与 JSON 持久化；单价/数量使用 `NumericField`，分类使用 `SearchableSelect`（依赖 `local/ui-controls` 源码包，不手拷控件源码）。

## 构建与运行（macOS）

```bash
mvn -q -DskipTests package
examples/product-editor/scripts/build.sh   # 会先 publish ui-controls 到示例 registry
./build/product-editor/ProductEditor --self-test
./build/product-editor/ProductEditor --appearance light --data build/product-editor/product.json
open build/product-editor/ProductEditor.app
```

菜单：文件（打开/保存/另存为/退出）、编辑、窗口、帮助；Cmd+S / Cmd+O；未保存关闭走「保存/放弃/取消」。用户数据写在 `--data` 路径或所选文件，不写入 `.app`。

## 文档

- 组合控件：`docs/tutorial/chapters/desktop-ui-composite-controls.md`
- 绑定：`docs/tutorial/chapters/desktop-ui-binding.md`
- 应用壳：`docs/tutorial/chapters/desktop-ui-app-shell.md`
