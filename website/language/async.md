# async/await、取消与线程

## 概念

- **线程**：`Thread`、`Mutex`、`Condition`、原子类型等（见 0.9+ 说明与 stdlib）。
- **异步**：`stdlib/async`（`AsyncLib.java` 等）；async/await 为 **实验性**。
- **GC**：STW GC 与多线程根扫描（原生）；WASM 模型不同。

## 最小示例

优先运行仓库中已有功能示例（如 `examples` 下 async 相关、或 `build/functional` 历史产物对应源）。阶段 1 不强制引入复杂 async Hello。

```bash
# 示例：以仓库当前存在的 async 示例文件名为准
ls examples | head
```

## 明确语义

- UI 更新必须回到 UI 线程约定（桌面指南）。
- 取消：若 API 提供取消令牌，必须在文档/API 页写明；无依据则标 **待完善**。

## 常见错误

- await 之后假设一定恢复（见 [调试：async 不恢复](/reference/debugging)）。
- 在 WASM 使用阻塞线程 API。

## 支持限制

| 目标 | 异步/线程 |
|------|-----------|
| 原生 | 实验性 |
| wasm32-browser | 受限 / 实验性 |
| 移动容器 | 跟随 WASM/JS 事件循环 |

## 相关

- [API：并发与异步](/api/async) · `docs/jnative-functional-callbacks.md`
