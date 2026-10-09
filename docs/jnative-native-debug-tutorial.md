# JNative 原生调试教程（VS Code + CodeLLDB）

作者：张晓达

面向 **macOS arm64**，使用 LLDB / [CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb)。  
本教程不覆盖 Web/WASM、移动容器或独立 JNative Studio。

## 1. 安装 JNative 插件与调试适配器

在仓库根目录构建工具链与扩展：

```bash
mvn -q -DskipTests package
cd tools/vscode-jnative
npm install
npm run compile
npm run package
```

安装 VSIX（任选其一）：

- VS Code：扩展视图 → `…` → Install from VSIX → 选择 `tools/vscode-jnative/jnative-0.2.1.vsix`
- 命令行（若本机有 `code`）：

```bash
code --install-extension tools/vscode-jnative/jnative-0.2.1.vsix
```

再安装 **CodeLLDB**（`vadimcn.vscode-lldb`）。JNative 扩展**不会**自动安装适配器；缺失时会提示打开市场。

## 2. 配置工具链

在工作区设置（受信任后生效）：

```json
{
  "jnative.jarPath": "/绝对路径/jnative/target/jnative.jar",
  "jnative.javaPath": "java"
}
```

执行命令 **「JNative: 检查工具链」**，确认 `jnative --version`、Java、clang 可用。

未信任工作区不会启动编译器或调试器。

## 3. 打开示例

推荐：

| 示例 | 路径 | 说明 |
|------|------|------|
| 单文件 | `examples/debug/DebugDemo.java` | 最小断点 / 单步 |
| 跨文件项目 | `examples/debug/multifile/` | `DebugMulti` + `MathUtil` |

用 VS Code / Cursor 打开仓库根或该示例文件夹，并**信任工作区**。

## 4. 设置断点

打开用户源码（项目内 `.java` 会切到语言 ID `jnative`），在行号旁点击断点，例如：

- `DebugDemo.java`：`int sum = add(x, y);`
- `multifile/src/DebugMulti.java`：调用 `MathUtil.add`
- `multifile/src/MathUtil.java`：`int result = a + b;`

断点必须落在**原始用户文件**上，而不是 `build/units/*-unit.java`。

## 5. 按 F5 / 调试项目

任选：

1. 命令面板 → **「JNative: 调试项目」**（先 `--debug` 构建，失败则不启动）
2. **「JNative: 生成调试配置 (launch.json)」**（仅当不存在时写入，不覆盖），再按 **F5**
3. 无 `launch.json` 时 F5：扩展会尝试合成 CodeLLDB 配置并先做调试构建

构建失败时：**不会**启动旧可执行文件。

## 6. 单步并查看变量

命中断点后：

| 操作 | 说明 |
|------|------|
| Continue | 继续 |
| Step Over / Into / Out | 单步跳过 / 进入 / 退出 |
| VARIABLES | 查看 `int` / `long` / `boolean` 局部量与参数 |
| CALL STACK | 用户方法帧应显示源文件与行号 |

跨文件示例预期：在 `DebugMulti` 看到 `x=10`、`y=20`、`ready=true`；进入 `MathUtil.add` 后 `a`/`b`，加法后 `result=30`。

若同时使用 `--debug` 与显式 `-O2` 等，优化可能影响单步与变量可见性（编译器会警告）。

## 7. 停止调试

调试工具栏 **Stop**，或断开会话。CodeLLDB 会结束本次启动的被调试进程。
命令 **「JNative: 停止运行」** 仅终止「运行项目」启动的普通进程，与调试会话分开。

重新调试会重新 `--debug` 构建；缓存键包含调试标志，不会误复用无符号产物。

## 8. 对象 / 字符串 / 数组与异常

见 **`docs/jnative-native-debug-objects.md`**（含 LLDB 格式化器、GC、未处理异常、ERP 练习库采购链）。

推荐先跑：

```bash
./examples/debug/lldb_object_verify.sh
```

再在 VS Code 中打开 `examples/debug`，对 `ObjectDebugDemo` 按 F5，在变量面板核对中文 `String`、`Dog` 字段与数组。

## 命令行对照验证

```bash
./examples/debug/lldb_verify.sh
./examples/debug/multifile/lldb_verify.sh
```

## 相关文档

- 支持矩阵与实测结果：`docs/jnative-native-debug-report.md`
- 示例说明：`examples/debug/README.md`
