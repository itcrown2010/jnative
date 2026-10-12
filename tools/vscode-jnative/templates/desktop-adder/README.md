# {{APP_NAME}} — JNative 桌面加法器

业务主题与 Web/移动模板相同：**两个整数相加**。计算在 JNative 原生程序中完成。

## 所需工具

- JNative SDK
- clang / LLVM（`jnative doctor`）
- macOS：系统窗口工具链；Linux/Windows 桌面 UI 支持因平台而异（见能力矩阵）

## 项目结构

| 路径 | 说明 |
|------|------|
| `src/Main.java` | **修改这里**：`add` / `compute` / UI 事件 |
| `jnative.project` | 原生桌面目标 |
| `.jnative/build/app` | 构建产物（可执行文件） |

## 如何运行

VS Code：**JNative: 构建** → **JNative: 运行**（启动原生窗口）。

```bash
jnative build --project .
jnative run --project .
```

## 如何验证修改生效

1. 编辑 `src/Main.java` 的 `add`（例如改为 `return a + b + 1;`）
2. 重新构建并运行
3. 输入 `12`、`30`，点击「相加」

## 如何查看错误

- **Problems** 面板（构建 JSON 诊断）
- **JNative** 输出通道
- 构建失败时插件**不会**启动旧可执行文件

## 调试方式

**JNative: 调试**（需安装 CodeLLDB）。仅适用于原生目标。

## 下一步

- 教程：`docs/tutorial/`
- 更完整桌面示例：`examples/guide-tasks/desktop`
