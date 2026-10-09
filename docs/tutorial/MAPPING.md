# 教程章节—实现—测试对应表

适用工具链：**JNative 0.32**（`java -jar target/jnative.jar --version`）  
验证主机：macOS arm64（其他平台标未验证）  
源码后缀：`.jn` / `.java` 均可；教程示例统一 `.jn`。

| 章 | 主题 | 示例路径 | 实现/证据 | 教程状态 |
|----|------|----------|-----------|----------|
| 01 | 定位与编译运行 | — | CLI `--help`；非 Java SE | 已写 |
| 02 | 安装与 VS Code | — | `website/guide/*`；扩展 README | 已写（链到入门指南） |
| 03 | Hello World | `examples/tutorial/01-hello/` | Hello e2e | 已写 |
| 04 | 项目与入口 | `examples/tutorial/04-project/` | `jnative new` 结构 | 已写 |
| 05 | 读懂编译错误 | `examples/tutorial/05-errors/`（负例） | 编译器诊断格式 | 已写 |
| 06 | 变量与作用域 | `examples/tutorial/06-vars/` | DA；language-support | 已写 |
| 07 | 数值与边界 | `examples/tutorial/07-numbers/` | 算术 e2e；溢出假定补码 | 已写 |
| 08 | 字符串 | `examples/tutorial/08-strings/` | String 契约 | 已写 |
| 09 | 运算符与转换 | `examples/tutorial/09-ops/` | PrimitiveOps | 已写 |
| 10 | 控制流 | `examples/tutorial/10-control/` | if/while/for/switch | 已写 |
| 11 | 方法与递归 | `examples/tutorial/11-methods/` | 方法；递归实测 | 已写 |
| 12 | 数组与增强 for | `examples/tutorial/12-arrays/` | arrays + foreach | 已写 |
| 13 | class/对象 | `examples/tutorial/13-objects/` | OOP L3 | 已写 |
| 14 | 封装 | `examples/tutorial/14-access/` | visibility L2 | 已写 |
| 15 | 继承与接口 | `examples/tutorial/15-inherit/` | Interface e2e | 已写 |
| 16 | enum/final | `examples/tutorial/16-enum-final/` | enum L2；**final 字段不支持** | 已写 |
| 17 | 多文件 | `examples/tutorial/17-multifile/` | multi-CU | 已写 |
| 18 | 第三方源码包 | — | CLI `add` **实验性/未端到端验收** | 限制章 |
| 19 | 泛型 | `examples/tutorial/19-generics/` | Generics e2e；通配符实验性 | 已写 |
| 20 | 集合 | `examples/tutorial/20-collections/` | ArrayList 等 | 已写 |
| 21 | Lambda | `examples/tutorial/21-lambda/` | Callable e2e；WASM 拒绝 | 已写 |
| 22 | 异常与资源 | `examples/tutorial/22-exceptions/` | Exception/Resource e2e | 已写 |
| 23 | 文件 | `examples/tutorial/23-files/` | File.read/writeString | 已写 |
| 24 | async/线程 | — | 有能力但主线不依赖；限制说明 | 限制章 |
| 25 | FFI | — | 实验性；限制说明 | 限制章 |
| 26 | VS Code LSP | — | 无补全；有诊断 | 实践章 |
| 27 | 调试 | — | LLDB macOS 实验性 | 实践章 |
| 28 | 测试与复现 | — | 方法说明 | 实践章 |
| 29 | GC/资源 | — | 语义说明 | 实践章 |
| 30 | 综合与后续 | `examples/tutorial/task-manager/` | 分阶段 CLI 任务管理器 | 已写 |

## 任务管理器输入方式

**已验证**：`main(String[] args)` 命令参数。  
**未作为主线**：交互式 `System.in` 行编辑（stdlib 有流类型，教程不依赖未验收的控制台 REPL）。

命令形如：`add` / `list` / `done` / `delete` / `search` / `save` / `load`。
