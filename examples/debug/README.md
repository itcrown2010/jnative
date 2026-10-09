# JNative 源码调试示例（macOS arm64）

作者：张晓达

验证：`--debug` 编译后，LLDB / VS Code CodeLLDB 能在**原始** `.java` 上断点、单步、查看调用栈、基础局部量，以及对象 / 字符串 / 数组。

## 平台

- **已验证**：macOS arm64 + clang + LLDB（下列脚本）
- **未验证**：Linux/Windows、远程调试
- **不覆盖**：Web/WASM、移动容器

## 脚本一览

| 脚本 | 内容 |
|------|------|
| `lldb_verify.sh` | 基础断点 / 单步 / int / 栈 |
| `multifile/lldb_verify.sh` | 跨文件行号 |
| `lldb_object_verify.sh` | 对象 / 继承 / String / 数组 |
| `lldb_gc_verify.sh` | `System.gc()` 前后引用 |
| `lldb_exception_verify.sh` | 未处理异常停靠 |

## 单文件：DebugDemo

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar --debug examples/debug/DebugDemo.java -o examples/debug/DebugDemo
./examples/debug/lldb_verify.sh
```

## 对象 / 字符串 / 数组：ObjectDebugDemo

```bash
./examples/debug/lldb_object_verify.sh
```

VS Code：选择配置 **ObjectDebugDemo (CodeLLDB)**（自动加载 `tools/lldb/jnative_formatters.py`）。

## GC / 异常

```bash
./examples/debug/lldb_gc_verify.sh
./examples/debug/lldb_exception_verify.sh
```

未处理异常：`breakpoint set --name jnative_debugger_on_uncaught`  
用户栈：在 `jnative_exception_throw` 查看（见 `docs/jnative-native-debug-objects.md`）。

## 跨文件：multifile/

```bash
./examples/debug/multifile/lldb_verify.sh
```

## VS Code

1. 安装 `tools/vscode-jnative` 与 CodeLLDB  
2. 配置 `jnative.jarPath`  
3. 信任工作区 → 设断点 → F5 / **JNative: 调试项目**

教程：`docs/jnative-native-debug-tutorial.md`  
对象与 ERP：`docs/jnative-native-debug-objects.md`

## ERP

```bash
./examples/erp/scripts/lldb_purchase_debug_verify.sh
```

使用独立 SQLite 练习库，勿连正式库。
