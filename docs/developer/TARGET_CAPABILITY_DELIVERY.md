# 目标能力 / 诊断 / 可重复构建 — 交付说明

## 本轮完成

1. **统一目标能力模型**（`io.jnative.target`）
   - `TargetId`：`NATIVE_HOST` / `WASM32_BROWSER` / `MOBILE_CONTAINER`
   - `TargetProfiles`：能力证据（IMPLEMENTED / VERIFIED / UNSUPPORTED / UNVERIFIED）
   - `backend` 与 `pkg` 的 `TargetCapability.forTriple` 均委托同一提供集合
   - **修正**：wasm/mobile **不再**错误提供 `THREADS`
   - 移动容器明确为 wasm 编译壳，非完整原生移动运行时

2. **构建前检查**（`TargetCapabilityChecker`）
   - 包 `requires` vs 目标提供
   - 源码 API 模式（Files / Thread / Socket / Sqlite / @Native / Document…）
   - 失败于链接/运行前，带文件位置与 hint

3. **统一诊断**
   - `Diagnostic` 增加 `code` / `hint` / `related`
   - JSON 与文本同源；见 `DIAGNOSTICS.md`

4. **产物管理**
   - staging → 成功发布；失败写 `build-result.json`（success:false）
   - 见 `BUILD_ARTIFACTS.md`

5. **VS Code 0.7.0**
   - `jnative.target`（resource scope，多根不串）
   - 状态栏项目·目标；命令「选择编译目标」
   - 构建/运行传入 `--target`；Problems 显示 code/hint
   - 编辑时仍仅 LSP analyze

## 验证结果（本机）

| 项 | 结果 |
|----|------|
| `TargetCapabilityUnifiedTest` | 通过 |
| `WasmBrowserTargetTest` | 通过 |
| TlsAbstraction031Test.mainVersionIs031 | 预存在失败（期望 0.31，实际 0.32），与本轮无关 |
| 字节级可复现 | **未验证 / 不承诺** |

## 剩余限制

- API 检查为源码模式匹配，非完整语义依赖图；漏报可能存在。
- 增量与干净构建的系统性 diff 尚未自动化。
- 移动设备交互仍为未验证。
- VS Code 未重新打包 VSIX（需 `npm run package`）。
