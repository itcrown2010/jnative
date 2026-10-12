# 开发者文档维护说明

- **对外文档站源码**：`website/`（VitePress，中文为主）
- **事实与阶段元数据**：本目录（`FACT_INVENTORY.md`、`CAPABILITY_MATRIX.md`、`STAGES.md`、`DIAGNOSTICS.md`、`BUILD_ARTIFACTS.md`）
- **公开前许可材料**：[`../legal/`](../legal/)（不替代正式法律意见）
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
