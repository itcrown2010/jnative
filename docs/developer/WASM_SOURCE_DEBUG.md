# WASM / JNative 源码调试评估

作者：张晓达

## 结论（本轮核实）

| 能力 | 状态 |
|------|------|
| 浏览器显示原始 `.jn` / `.java` 并断点 | **不支持** |
| 单步 / 变量 / 调用栈（JNative 源级） | **不支持** |
| CodeLLDB 调试 WASM 或 WebView 内模块 | **不支持**（仅桌面原生） |
| `app.js` 源映射到 JS 胶水 | 可能存在于浏览器 DevTools，**不等于** JNative 源码调试 |

原生后端在 `--debug` 时可发 DWARF；`wasm32-browser` 路径走 WAT/`wat2wasm`，**当前未生成浏览器可消费的、映射回 `.jn` 的 DWARF/source map**。

因此 VS Code **不会**为 Web/移动目标提供无效的「源码调试」按钮；F5 在这些目标上会明确拒绝并指向分层日志。

## 本轮交付的排错路径

1. **JNative 编译**：CLI / Problems 中带真实 `file:line:col` 的诊断可点击定位。
2. **WASM 加载**：`[wasm]` 层日志（magic、export 缺失、trap）。
3. **JavaScript**：`window.onerror` / `unhandledrejection` → `[javascript]`（guide-tasks `web/lib/diag.js`）。
4. **桥接 / 网络**：`[bridge]` / `[network]`，带 `eventId`；不打印完整令牌。
5. **容器**：`jnative mobile logs`；Android `chrome://inspect`；iOS Safari Web Inspector（仅调试构建）。

## 缺口清单（后续若要做真源码调试）

1. WASM 后端输出浏览器支持的 DWARF 或标准 source map，且路径指向用户 `.jn`。
2. 保留临时 `.wat` / 调试符号与构建产物的稳定关联。
3. VS Code / Chrome DevTools 集成验证：断点、单步、局部变量、调用栈。
4. 明确优化等级对变量可见性的影响（`-O0` 调试构建）。

在上述项完成前，任何「仅映射 JS 胶水」的方案都不得标为 JNative 源码调试。
