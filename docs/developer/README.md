# 开发者文档维护说明

- **对外英文权威文档**：[`../en/`](../en/README.md)（GitHub 国际读者优先；先更新英文）
- **中文导航**：[`../zh-CN/`](../zh-CN/README.md)
- **对外文档站源码**：`website/`（VitePress，中文为主）
- **事实与阶段元数据**：本目录（`FACT_INVENTORY.md`、`CAPABILITY_MATRIX.md`、`STAGES.md`、`DIAGNOSTICS.md`、`BUILD_ARTIFACTS.md`）
- **Web / 移动能力（guide-tasks）**：[`WEB_MOBILE_CAPABILITY_MATRIX.md`](WEB_MOBILE_CAPABILITY_MATRIX.md)、[`../tutorial/chapters/web-mobile-tasks.md`](../tutorial/chapters/web-mobile-tasks.md)、[`../tutorial/chapters/web-mobile-sync.md`](../tutorial/chapters/web-mobile-sync.md)、[`../../examples/guide-tasks/docs/SYNC_CONTRACT.md`](../../examples/guide-tasks/docs/SYNC_CONTRACT.md)
- **VS Code 移动运行 / 排错 / 打包**：[`MOBILE_PLATFORM_STRUCTURE.md`](MOBILE_PLATFORM_STRUCTURE.md)、[`VSCODE_MOBILE_RUN.md`](VSCODE_MOBILE_RUN.md)、[`WASM_SOURCE_DEBUG.md`](WASM_SOURCE_DEBUG.md)、[`VSCODE_MOBILE_ACCEPTANCE.md`](VSCODE_MOBILE_ACCEPTANCE.md)、[`../tutorial/chapters/vscode-mobile-toolchain.md`](../tutorial/chapters/vscode-mobile-toolchain.md)
- **统一平台能力（文件/分享）**：[`PLATFORM_FILE_SHARE_ACCEPTANCE.md`](PLATFORM_FILE_SHARE_ACCEPTANCE.md)、[`../../examples/guide-tasks/docs/PLATFORM_CAPS.md`](../../examples/guide-tasks/docs/PLATFORM_CAPS.md)、[`../tutorial/chapters/platform-file-share.md`](../tutorial/chapters/platform-file-share.md)
- **公开前许可材料**：[`../legal/`](../legal/)（不替代正式法律意见）
- **桌面 UI 框架**：[`../desktop-ui-delivery.md`](../desktop-ui-delivery.md)、[`../desktop-ui-platform-matrix.md`](../desktop-ui-platform-matrix.md)、[`../tutorial/chapters/desktop-ui-framework.md`](../tutorial/chapters/desktop-ui-framework.md)
- **本地预览**：`cd website && npm install && npm run dev`
- **静态构建**：`cd website && npm run build` → `website/.vitepress/dist`
- **不自动公开部署**；需要时由维护者手动发布静态产物
- **API 签名提取**：`scripts/docs/extract-api.sh`
- **验证**：`scripts/docs/verify-phase1.sh` → `verify-phase2.sh` → `verify-phase3.sh`（阶段 3 含 Android/iOS 构建，较慢）

写作规则：

1. 状态必须落到「功能 × 平台」。
2. 未跑通的步骤标 **未验证**，不可用的标 **未支持**。
3. 不承诺 Java SE / JAR 兼容。
4. Tutorial 提供起点/终点完整代码，禁止省略关键实现。
