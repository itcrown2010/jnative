# 教程：在 VS Code 中运行与排错移动任务应用

作者：张晓达

## 你将得到什么

打开 `examples/guide-tasks`，用 JNative 扩展选择 Web / Android / iOS，完成构建、查看分层日志，并生成 release 配置产物（不上传商店）。

## 前置

1. 已 `mvn -DskipTests package` 得到 `target/jnative.jar`。
2. 安装并启用本仓库 `tools/vscode-jnative` 扩展；工作区受信任。
3. Web：WABT（`wat2wasm`）。Android：SDK + 设备。iOS：Xcode + 模拟器。

## 步骤

1. 用 VS Code 打开 `examples/guide-tasks`（含 `jnative.toml`）。
2. 命令面板 → **JNative: 检查移动工具链**。
3. **JNative: 运行** → 选择 **Web** → **debug** → 等待预览 URL。
4. 故意在 `core/TaskCore.java` 末尾加入非法文本 → 再构建 → 输出中应出现 `error:` 与 `web_build_script failed — refusing stale dist`；Problems 可能显示合并文件位置（不会伪造不存在的行号）。
5. 撤销错误后选 **iOS**（多模拟器时必须点选 UUID）→ 构建安装启动。
6. 调试构建下用 Safari「开发」菜单检查 WKWebView（见开发者文档）。
7. **JNative: 打包** → release → 阅读输出中的产物清单；确认 `store_ready=false`。
8. 可选：`./scripts/verify_upgrade_retention.sh` 验证升级保留语义。

## 分层日志怎么读

输出形如 `[m-xxx] [jnative-compile] …` / `[wasm]` / `[javascript]` / `[bridge]` / `[install]`。  
浏览器控制台同样有 `[eventId] [layer]`（`web/lib/diag.js`）。

## 不要做的事

- 不要对 Web/移动按 F5 期望 JNative 源码断点（扩展会拒绝）。
- 不要把未签名模拟器包当作商店分发包。
- 不要用清空 IndexedDB 掩盖迁移失败。

更多结构说明：[`../../developer/MOBILE_PLATFORM_STRUCTURE.md`](../../developer/MOBILE_PLATFORM_STRUCTURE.md)、[`../../developer/VSCODE_MOBILE_RUN.md`](../../developer/VSCODE_MOBILE_RUN.md)。
