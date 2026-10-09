# 创建第一个 `.jn` 项目

## 使用 CLI（已验证）

```bash
jnative new my-app --dir ~/jnative-apps
cd ~/jnative-apps/my-app
```

生成结构（与 2026-10-09 CLI 实际输出一致）：

```text
my-app/
├── .gitignore
├── jnative.toml
├── jnative.project
├── src/
│   └── Main.jn
└── tests/
    ├── GreetingTest.jn
    ├── IdentityTest.jn
    ├── MathTest.jn
    └── StringTest.jn
```

`src/Main.jn` 内容：

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

`jnative.toml` 示例：

```toml
[package]
name = "my-app"
namespace = "local"
version = "0.1.0"
license = "MIT"
type = "source"
```

`jnative.project` 示例：

```json
{
  "name": "my-app",
  "source": ["src"],
  "main": "Main",
  "output": "build/my-app"
}
```

## 使用 VS Code

命令 **「JNative: 创建项目」** 调用：

```bash
jnative new <name> --dir <parent> --minimal
```

创建前检查冲突，不覆盖已有文件。

## 文档附带快照

仓库内固定快照：`examples/guide-hello/`（与模板等价，供教程第 0 课使用）。

## 构建与运行

```bash
cd my-app
jnative build
./build/my-app
# 期望输出：
# Hello, JNative!
```

本机验证：`Build succeeded`，产物 `build/guide-hello` 可执行。

## 下一步

[构建、运行与调试](/guide/build-run-debug)。
