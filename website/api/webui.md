# API：浏览器与 WebUI

**源码**：`stdlib/web`、`webui`、`browser`  
**目标**：`wasm32-browser`

## 两条路径

### A. WASM 导出 + JS 壳（本阶段教学采用）

| 项 | 说明 |
|----|------|
| 模式 | 编译单个核心类 → `app.wasm` + 生成 `app.js`；UI 用 JS |
| 验证 | `examples/guide-tasks` / `examples/sudoku` |
| 状态 | **实验性**（已有 `verify_web.sh`） |

### B. WebUI 组件模型（`WebUI.mount` / `Html.*`）

| 项 | 说明 |
|----|------|
| 示例 | `examples/webui/counter/App.java` |
| 本机 0.32 构建 | **失败**（全量 stdlib 进 WAT 后类型校验错误）；曾对 `getenv`/Println 做软处理仍不足 |
| 状态 | **实验性 / 当前不可用于新教程** |

## 浏览器 imports（生成 app.js）

含 `console_log`、DOM、`register_callback` / `unregister_callback` 等。完整列表以生成文件为准。

## 可运行用法（路径 A）

```bash
./examples/guide-tasks/scripts/verify_web.sh
```
