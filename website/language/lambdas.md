# Lambda、闭包与方法引用

## 概念

支持面向 SAM 接口的 lambda / 闭包 / 方法引用（见仓库 `docs/callable.md`、示例 `examples/LambdaClosure.java`）。可用于异步回调与 UI 事件。

## 最小示例

参考仓库示例（请在本机编译）：

```bash
jnative examples/LambdaClosure.java -o /tmp/LambdaClosure
/tmp/LambdaClosure
```

状态：示例路径存在；请以本机输出为准（**实验性**）。

## 明确语义

- 捕获变量规则以实现为准（注意可变捕获限制）。
- `Native.callback()` 可将托管 SAM 导出为 C 函数指针（FFI 章）。

## 常见错误

- 在对象销毁后仍调用捕获了 UI 控件的闭包。
- 假设完整 Java 流式 `stream().map().filter()` API。

## 支持限制

完整 Stream API：**未支持** 或仅部分。状态见 API 提取与 [限制](/language/limits)。

## 相关

- `docs/callable.md` · [异步](/language/async) · 桌面教程（阶段 2）事件节
