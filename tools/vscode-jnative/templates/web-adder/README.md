# {{APP_NAME}} — JNative Web 加法器

业务主题与桌面/移动模板相同：**两个整数相加**。计算在 JNative（WASM）中完成，本目录 HTML 只做 DOM 宿主。

## 所需工具

- JNative SDK（CLI / JAR）
- `wat2wasm`（WASM 工具链；`jnative doctor --mobile` 会检查 Web 相关项）
- 本地静态文件服务（插件可用 Python `http.server`；也可自行用其他服务器）

## 项目结构

| 路径 | 说明 |
|------|------|
| `src/App.java` | **修改这里**：`add` / `compute` / 输入校验 |
| `index.html` | 页面壳（输入框与按钮 ID 勿随意改） |
| `dist/` | 构建产物（`app.wasm`、`app.js`、复制后的 `index.html`） |
| `jnative.project` | 目标 `wasm32-browser` |

## 如何运行

在 VS Code：

1. **JNative: 构建**（目标选 `wasm32-browser` 或本模板默认 Web）
2. **JNative: 运行** → 启动本地预览并打开页面

命令行：

```bash
jnative build --target wasm32-browser src/App.java -o dist
cp index.html dist/index.html
cd dist && python3 -m http.server 8765
# 浏览器打开 http://127.0.0.1:8765/
```

## 如何验证修改生效

1. 打开 `src/App.java`，把 `add` 改成例如 `return a + b + 1;`
2. 重新构建并刷新页面
3. 输入 `12` 与 `30`，点击「相加」应得到 `43`（若改了 +1）

## 如何查看错误

- 构建失败：VS Code **Problems** 面板（点击定位源码）
- 运行时：浏览器开发者工具 Console
- 插件 **JNative** / **JNative Language Server** 输出通道

## 调试方式

当前 Web/WASM **无** CodeLLDB 源码级调试。请用编译诊断、页面 Console 与 `Browser.console().log`。

## 下一步

- 教程：仓库 `docs/tutorial/`（欢迎页「打开教程」）
- 更丰富的浏览器示例：`examples/browser/order-list`
- 移动容器：用「创建移动项目」模板（同一加法业务）
