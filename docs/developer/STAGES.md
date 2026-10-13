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

## Web / 移动闭环（2026-10-13 转向）

| 项 | 状态 | 证据 |
|----|------|------|
| 核实 WASM 用户代码 / 存档 / JS 壳 | 完成 | `verify_web.sh`；`storage_get` 已修 |
| M1 Web 本地任务 + 持久化 | 完成 | Chrome restore |
| M2 最小网络 + 生命周期 | 完成 | hint.json；AbortController |
| M3 移动触屏 assets | 完成（资产） | `verify_mobile_assets.sh`；Sim/APK 构建 |
| 多页面导航 + 表单 v3 | 完成 | Chrome `invalid-route…bad-date…` |
| 开发预览可靠刷新 | 完成 | `verify_dev_web.mjs`（非 HMR） |
| 离线队列 + SQLite 同步 | 完成 | `verify_sync` / `verify_idb_sync` |
| 版本冲突 UI | 完成 | 记录级；非字段合并 |
| SW 离线壳 | 完成（基础） | `sw.js` 整包版本；不缓存 API |
| 桌面控件扩展 | **暂停** | 不在本阶段扩大 |

## 验收标准（最终）

开发者无需阅读编译器内部实现，仅按文档完成：安装 → 学习 → 查 API → 在已标注支持的平台上构建与调试 sample。未验证平台不得写成「已支持」。
