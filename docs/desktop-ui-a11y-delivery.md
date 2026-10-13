# 桌面 UI：高 DPI / 键盘 / 无障碍 — 交付说明

作者：张晓达  
验收平台：macOS Cocoa（首期）

## 交付物

| 项 | 路径 |
|----|------|
| 焦点 / Tab / 默认按钮 | `UiLib.java`：`FocusTraversal`、`Window.setDefaultButton/setCancelButton` |
| Cocoa 焦点与窗口键 | `jnative_ui_cocoa.m`：`becomeFirstResponder`、`JnUiWindow.keyDown` |
| 无障碍 | `setAccessibleName/Description`、`associateErrorLabel`、tooltip→AX |
| 系统外观查询 | `Theme.prefersReducedMotion/IncreasedContrast` |
| DPI | 既有 `Dpi.scalePercentForId`；图表语义色 |
| 可重复测试 | `ProductEditor --self-test`、`scripts/ui/run-desktop-a11y-smoke.sh` |
| 教程 | `docs/tutorial/chapters/desktop-ui-a11y-keyboard.md` |

## 验证结果（2026-10-13，本机）

| 项 | 结果 |
|----|------|
| `ProductEditor --self-test` | PASS（`PE_UI_INTEGRATION_OK` / `PE_SELF_TEST_OK`） |
| Headless DPI 查询 | `PE_DPI_SCALE_PERCENT=100`（headless；实机 Retina 见 `Dpi.scalePercentForId`） |
| Reduced motion / contrast（headless） | `-1`（未知）；实机 Cocoa 读 `NSWorkspace` |
| AM `run_verify.sh` | PASS |
| `ProductEditor.app` 短时启动 | PASS |
| VoiceOver 手工 | 需在系统开 VoiceOver 后按教程表核对（自动化不替代） |

## 剩余限制

- VoiceOver：表格单元格细粒度、自定义 role 枚举未做
- 减少透明度：无独立 API（依赖系统窗口效果）
- Win32：无障碍 / 真实 DPI 未验收
- 截图不能代替键盘与 VoiceOver 交互
- Headless 下系统外观偏好返回 `-1`
