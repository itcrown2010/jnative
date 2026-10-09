# 文档分阶段交付

## 第一阶段（本轮优先完成）

| 项 | 状态 | 证据 |
|----|------|------|
| 文档站脚手架（VitePress） | 完成 | `website/` |
| 事实清单 / 能力矩阵 | 完成 | `docs/developer/` |
| 入门：是什么、依赖、安装、doctor | 完成 | `website/guide/*` |
| VS Code 配置 | 完成 | `website/guide/vscode.md` |
| Hello 项目与第 0 课 | 完成 | `examples/guide-hello/` + tutorials |
| 语言基础章节（有限制声明） | 完成 | `website/language/*` |
| API 索引 + 提取脚本 | 完成 | `website/api/` + `scripts/docs/extract-api.sh` |
| 验证脚本 phase1 | 完成 | `scripts/docs/verify-phase1.sh` → `PHASE1_VERIFY_OK`（含 guide-hello 与站点 build） |
| 多语言结构（英 stub） | 完成 | `website/en/` |
| 全新环境安装验收 | 部分 | 本机已有工具链；脚本可在干净机复跑 |

## 第二阶段

| 项 | 状态 | 证据 |
|----|------|------|
| `examples/guide-tasks` 共享核心 | 完成 | `verify_core.sh` → PASS |
| 桌面终稿 + 10 课文档 | 完成 | `build_desktop.sh`；`website/tutorials/desktop/` |
| Web WASM+JS 壳 + 10 课文档 | 完成 | `verify_web.sh` → GUIDE_TASKS_WASM_OK |
| WebUI `Html.*` 组件教程 | 未完成 | counter 全量 WAT 仍失败；文档已标明改走导出+壳 |
| 桌面/Web API 页 | 部分 | `api/ui.md` / `api/webui.md` |
| `verify-phase2.sh` | 完成 | → `PHASE2_VERIFY_OK` |

## 第三阶段

| 项 | 状态 | 证据 |
|----|------|------|
| guide-tasks 触屏壳 + 容器工程 | 完成 | `mobile/web` + android/ios；`verify_mobile_assets.sh` |
| Android `assembleDebug` | 完成（构建） | `build_android.sh` → APK；设备安装未宣称 |
| iOS Simulator 构建 | 完成（构建） | `build_ios.sh`；真机未宣称 |
| 移动 10 课文档 | 完成 | `website/tutorials/mobile/` |
| 数独跨平台文档 | 完成 | `website/samples/sudoku.md` + PLATFORM.md |
| 进阶调试与性能 | 完成 | `website/reference/debugging.md` 扩充 |
| `verify-phase3.sh` | 完成 | 含 APK + iOS sim（耗时） |

## 验收标准（最终）

开发者无需阅读编译器内部实现，仅按文档完成：安装 → 学习 → 查 API → 在已标注支持的平台上构建与调试 sample。未验证平台不得写成「已支持」。
