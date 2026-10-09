# 安装并配置 VS Code 插件

扩展源码：`tools/vscode-jnative/`（`package.json` 版本 **0.3.0**）。  
仓库内可能带有较旧的 `.vsix`（如 `jnative-0.2.3.vsix`）；**建议重新打包**后再安装。

> 状态：扩展逻辑与单元测试路径存在；**VS Code UI 端到端**在无 `code` CLI / 宿主时标为 **未验证**。命令行工具链仍可独立使用。

## 1. 构建 CLI

```bash
mvn -q -DskipTests package
```

## 2. 打包扩展

```bash
cd tools/vscode-jnative
npm install
npm run package
```

会生成带依赖的 VSIX（勿使用会丢掉 `vscode-languageclient` 的错误打包方式）。

## 3. 安装 VSIX

命令行（macOS 常见路径）：

```bash
"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --install-extension tools/vscode-jnative/jnative-0.3.0.vsix
```

或：扩展视图 → `…` → **Install from VSIX…**。  
若文件名版本不同，以 `npm run package` 实际产出为准。

## 4. 配置工具链

工作区或用户 `settings.json` 推荐：

```json
{
  "jnative.jarPath": "/绝对路径/jnative/target/jnative.jar",
  "jnative.javaPath": "java"
}
```

优先级：`jnative.executablePath` → `jarPath`+`javaPath` → PATH 中的 `jnative` → 向上查找的 `scripts/jnative.sh`。

扩展 **不会** 后台自动下载软件或跑 Maven。

## 5. 项目如何被识别

- 目录含 `jnative.project`，或同时含 `jnative.toml` + `src/`
- 仅在识别到的源码根内，打开 `.java` 才会切到语言 ID `jnative`
- **不会**因仓库某处有 toml 就接管所有 Java 文件

## 6. 调试扩展（可选）

原生断点需另行安装 [CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb)。详见 [构建、运行与调试](/guide/build-run-debug)。

## 下一步

[创建第一个项目](/guide/first-project)。
