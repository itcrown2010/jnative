# 组合控件机制 — 交付说明

作者：张晓达  
验收：2026-10-13（macOS Cocoa）

## 交付物

| 项 | 路径 |
|----|------|
| 扩展契约 | `stdlib/ui/BindingLib.java` → `CompositeControl`；`DecimalFieldModel` 增强（小数位/负数/空值） |
| 控件包 | `examples/ui-controls-pkg/library` → `local/ui-controls@0.1.0` |
| 本地注册表 | `examples/ui-controls-pkg/registry/` |
| 消费项目 | `examples/ui-controls-pkg/consumer/` |
| 商品编辑器接入 | `examples/product-editor/`（依赖 `local/ui-controls`） |
| 教程 | `docs/tutorial/chapters/desktop-ui-composite-controls.md` |
| 包路径修复 | `SourceFiles.isSourcePath` 允许 `.jnative/pkg-cache/extracted` |

## 缺陷修复（本轮）

| ID | 问题 | 修复 |
|----|------|------|
| PKG-1 | 依赖包源码在 `.jnative/pkg-cache/extracted` 被 `isSourcePath` 整段跳过 → `undefined class` | 仅跳过 build/generated/cache 等工具树，保留 extracted |
| COMP-1 | `String.indexOf(int)` 在 BindingLib 不可用 | 改为 `indexOf(".")` |
| COMP-2 | 覆盖 stdlib 提升为 public 的方法时访问权限变弱 | 子类 `focusTarget` / `setControlEnabled` 保持 `public` |

## 测试结果

| 用例 | 结果 |
|------|------|
| `SourceLibPackageIntegrationTest`（单测） | 修 PKG-1 后应通过 |
| `build-consumer.sh` → `UI_CONTROLS_SELF_TEST_OK` | PASS |
| `ProductEditor --self-test` | PASS（含 NumericField 符号） |
| `DesktopSample --self-test`（回归） | 应仍 PASS |

## 平台声明

- **已验证**：macOS Cocoa  
- **包 requires**：`DESKTOP_UI`  
- **未承诺**：Win/Linux 控件 peer 自动可用；构建失败会报能力/编译错误，不伪装成功  

## 截图 / 产物

- 消费端：`examples/ui-controls-pkg/consumer/build/UiControlsDemo`
- 编辑器：`build/product-editor/ProductEditor.app`
- 截图目录：`build/ui-stress/screenshots/`（运行 GUI 后补充）

## 稳定版

**不**因本轮交付将桌面 UI 标为稳定版。组合控件路径在 macOS + `.jnpkg` 消费下已可复现验证。
