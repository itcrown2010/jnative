# 桌面 UI 能力与验证清单

作者：张晓达  
验收日期：2026-10-13（本机）  
验收主机：macOS arm64（Cocoa）。**不得**将 macOS 结果推断为 Windows / Linux 已支持。

状态含义：

| 标记 | 含义 |
|------|------|
| 已实现 | 公共 API / 后端存在 |
| 已验证 | 本机有可重复脚本或 `--self-test` / verify 通过 |
| 部分支持 | API 或后端有限；或仅手工可测 |
| 未支持 | 无实现或未验收 |

## 1. 布局 / 工具栏 / 表格 / 图表

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| VBox/HBox、padding、spacing、flex、minSize | 已验证 | 部分支持 | 未支持 | 已验证 | AM / PE / UiWorkbench |
| 窗口缩放、最小尺寸 | 已验证 | 部分支持 | 未支持 | 部分支持 | `Window.setMinSize` |
| 合成工具栏（按钮+分段+搜索） | 已验证 | 部分支持 | 未支持 | 已验证 | AM |
| 真·NSToolbar | 未支持 | — | — | — | `FEAT_NATIVE_TOOLBAR=0` |
| TableView + 稳定行 ID | 已验证 | 部分支持 | 未支持 | 部分支持 | AM `run_verify.sh` |
| TrendChart | 已验证 | 未支持 | 未支持 | stub | AM；Win/Linux 抛能力错误 |
| SegmentedControl / SearchField 样式 | 已验证 | 部分支持 | 未支持 | 已验证 | AM |

## 2. 菜单 / 快捷键 / 多窗口

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| MenuBar / MenuItem / AppMenus | 已验证 | 部分支持 | 未支持 | 部分支持 | PE / desktop-sample |
| Command + KeyCombination + ShortcutManager | 已验证 | 部分支持 | 未支持 | 部分支持 | Cmd+S；文本框内不抢 C/V/X/Z/A |
| 多窗口 + owner + 关主关辅 | 已验证 | 未支持 | 未支持 | 部分支持 | PE About / AM 详情 |
| Quit / requestQuit / veto / onQuit | 已验证 | 部分支持 | 未支持 | 已验证 | PE confirmQuit；退出清队列 |

## 3. 状态绑定 / 表单校验

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| ObservableValue / ObservableInt / BindingScope | 已验证 | 同左（逻辑） | 同左 | 已验证 | BindingLib + PE `--self-test` |
| bindTextTwoWay / 批量 beginUpdate | 已验证 | 同左 | 同左 | 已验证 | PE |
| DecimalFieldModel / FormModel 校验 | 已验证 | 同左 | 同左 | 已验证 | PE `validateSubmit` |
| 保存失败保留输入 | 已验证 | — | — | 已验证 | PE serverError 路径 |

## 4. UI 线程 / 后台任务

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| `Application.checkUiThread` / `UiThreadViolationException` | 已验证 | 同左 | 同左 | 已验证 | 控件 mutate 路径 |
| `invokeLater` / `UiTaskQueue` | 已验证 | 同左 | 同左 | 已验证 | AM 采样回写 |
| 退出时 `UiTaskQueue.clear` + 丢弃 late invokeLater | 已验证 | 同左 | 同左 | 已验证 | `requestQuit` |
| gen/alive 守卫关闭后回调 | 已验证 | 同左 | 同左 | 已验证 | AM / PE / desktop-sample |
| `UiCoalesce` / `UiThread.coalesce` 高频合并 | 已实现 | 同左 | 同左 | 已实现 | UiLib；sample 演示 |
| 任务取消令牌（通用 Task API） | 部分支持 | — | — | — | 应用层 gen；无统一 CancelToken |

## 5. 控件与回调生命周期

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| 父子 `Container.dispose` 递归 | 已验证 | 同左 | 同左 | 已验证 | 关窗释放 content 树 |
| `ensureAlive` → `DisposedComponentException` | 已验证 | 同左 | 同左 | 已验证 | 已销毁句柄不可静默成功 |
| 订阅随 dispose / BindingScope 取消 | 已验证 | 同左 | 同左 | 已验证 | PE scope + Component.liveSubscriptions |
| 重复 close / 重复 dispose 幂等 | 已验证 | 同左 | 同左 | 已验证 | `disposed` 早退 |
| GC 后原生回调不撞坏堆 | 已验证 | 未支持 | 未支持 | 已验证 | registry 查不到则忽略事件 |

## 6. 深色 / 高 DPI / 键盘 / 无障碍

| 能力 | macOS | Windows | Linux | Headless | 证据 |
|------|-------|---------|-------|----------|------|
| Theme 外观 MODE_SYSTEM/LIGHT/DARK | 已验证 | 部分支持 | 未支持 | 部分支持 | AM `--appearance` |
| Dpi.scalePercentForId | 已验证 | 未支持 | 未支持 | stub（常 100） | PE 打印 scale |
| FocusTraversal / 默认·取消按钮 | 已验证 | 部分支持 | 未支持 | 部分支持 | PE / sample |
| accessibleName / Description / 错误关联 | 已验证 | 未支持 | 未支持 | 部分支持 | PE；VoiceOver 需手工 |
| prefersReducedMotion / IncreasedContrast | 部分支持 | 未支持 | 未支持 | `-1` 未知 | Cocoa 可读；headless 未知 |

## 7. .app 打包

| 能力 | macOS | Windows | Linux | 证据 |
|------|-------|---------|-------|------|
| `scripts/macos/pack-app.sh` | 已验证 | — | — | PE / AM / desktop-sample / UiWorkbench |
| 构建失败不保留旧 .app | 已验证 | — | — | 各 `build.sh` `rm -rf *.app` |
| 代码签名 / 公证 | 未支持 | — | — | 仅本机未签名验证 |
| Win 安装包 / Linux desktop 文件 | 未支持 | 未支持 | 未支持 | — |

## 8. VS Code 构建 / 运行 / 调试

| 能力 | macOS | 状态 | 证据 |
|------|-------|------|------|
| 扩展识别 `jnative.toml` / `jnative.project` | macOS | 已验证（扩展套件） | `tools/vscode-jnative` |
| 补全 / 诊断 / 跳转 | macOS | 已验证（LSP smoke） | 扩展 verify-artifacts |
| 构建与运行任务 | macOS | 已验证 | JNative: Build / Run |
| CodeLLDB 源码断点 | macOS | 部分支持 | 需本机 CodeLLDB；见新鲜环境报告 |
| Package Project → .app | macOS | 已验证 | 同 pack-app.sh |

## 稳定版判定

当前：**不得**标记为桌面 UI「稳定版」。理由：Windows/Linux 未验收；VoiceOver 细粒度与通用取消令牌未完成；新鲜环境断点若缺扩展则未通过。已开放的 macOS 路径在真实应用压力与 self-test 下应可靠，见 `docs/desktop-ui-stability-delivery.md`。
