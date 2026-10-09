# 第 30 章：综合项目与后续学习路径

## 本章目标

把前面章节串成可维护的命令行任务管理器；明确输入模型与进程边界；知道下一步学什么。

## 前置知识

第 1–23 章；能阅读 `ArrayList`、异常与文件示例。

## 概念：为什么用参数而不是交互输入

**已验证**：`main(String[] args)`。  
**未作主线**：交互式 `System.in` 行编辑。

因此：

- 每次命令是一次进程：`add` 后内存状态在进程结束时消失。
- 教学用各 stage 的 **`demo`**：在同一进程内串联多条命令。
- 实用流程：`add`… → `save path` → 下次 `load path` → `list`。

不要用 shell 脚本实现业务规则来「假装」程序会记状态。

## 阶段与源码

| 阶段 | 技能 | 路径 | 运行 |
|------|------|------|------|
| 1 | 数组、方法 | `examples/tutorial/task-manager/stage1/Main.jn` | `/tmp/tm1 demo` |
| 2 | 对象、封装字段 | `stage2/` | `demo` |
| 3 | 集合、搜索 | `stage3/` | `demo` |
| 4 | 异常、文件 | `stage4/` | `demo` |
| 5 | 终稿 | `stage5/`（同 stage4） | `demo` |

### 运行命令（阶段 1）

```bash
java -jar target/jnative.jar examples/tutorial/task-manager/stage1/Main.jn -o /tmp/tm1 -O0
/tmp/tm1 demo
```

### 精确预期输出（阶段 1）

```text
added 0
added 1
0: Buy milk
1: Write docs
total=2
```

其余阶段预期见 `examples/tutorial/_expected/tm-stage*-demo.txt`。

## 代码关键部分解释

- `joinFrom`：把 `add Buy milk` 的多个参数拼成标题。
- `parseInt`：手写数字解析（无 varargs、教程不引入未讲 API）。
- `encode`/`decode`：简单文本协议；失败返回 false，不崩。
- `removeAt`：ArrayList 按索引删除的正确方法名。

## 常见错误及实际诊断

| 症状 | 原因 |
|------|------|
| `add` 后另开终端 `list` 为空 | 新进程；需 `demo` 或 `save`/`load` |
| `remove` 编译失败 | 使用 `removeAt` |
| 存档加载失败 | 检查 `V1` 头与 `0\|title` 行格式 |

## 练习

1. 增加 `undone <id>`。  
2. 拒绝空标题（`add` 后 title 长度为 0）。  
3. （选做）把 Task 挪到 `Task.jn` 并用 `jnative.project` 多文件构建。

## 参考答案或可运行解法

- 练习 1–2：改 stage4 后自建 expected。  
- 练习 3：对照 `examples/tutorial/17-multifile/`。

## 本章完成检查

- [ ] 四个 stage 的 `demo` 均通过  
- [ ] 能解释进程边界与 save/load  
- [ ] 知道后续：桌面/Web 见开发者文档站 `guide-tasks`，不是本语言教程范围强制项  

## 后续学习路径

1. [开发者文档站](/) 入门 / API / 桌面·Web·移动教程  
2. `examples/sudoku/` 跨平台进阶  
3. `docs/language-support.md` 官方能力档案  

## 下一章

返回 [教程目录](/tutorial/)。
