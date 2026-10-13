# 桌面应用壳交付说明（菜单 / 多窗口 / 打包）

作者：张晓达  
验收平台：macOS Cocoa（首期）

## 交付物

| 项 | 路径 |
|----|------|
| 命令 / 菜单 / 生命周期 | `stdlib/ui/UiLib.java`（`Command.bindMenuItem`、`AppMenus`、`Application.requestQuit`、`Window.setOwnerWindow`） |
| Cocoa 菜单加速键 / App 菜单 | `native/posix/jnative_ui_cocoa.m` |
| 打包脚本 | `scripts/macos/pack-app.sh` |
| 中文教程 | `docs/tutorial/chapters/desktop-ui-app-shell.md` |
| 商品编辑器 | `examples/product-editor/` + `ProductEditor.app` |
| UI 工作台 | `examples/ui-workbench/` |
| 活动监视器 | `examples/activity-monitor/` + `ActivityMonitor.app` |
| VS Code 打包 | `tools/vscode-jnative/src/runner.ts` → `packageProject` |

## 生命周期契约（选定）

1. **关最后一窗默认退出**（`quitOnLastWindowClose=1`）；可改为 0 保留进程。  
2. **Quit / Cmd+Q** → `requestQuit` → 退出钩子 → 关闭剩余窗口 → `quitNative`。  
3. **辅助窗**挂 `setOwnerWindow`；主窗关闭时一并关闭。  
4. **文本编辑快捷键**（C/V/X/Z/A）在 TextField/TextArea 焦点下不交给 `ShortcutManager`。  

## 测试结果（本机，2026-10-13）

| 项 | 结果 |
|----|------|
| ProductEditor `--self-test` | PASS `PE_SELF_TEST_OK` |
| AM `run_verify.sh` | PASS `ALL VERIFY CHECKS PASSED` |
| `ProductEditor.app` / `UiWorkbench.app` / `ActivityMonitor.app` | PASS（`pack-app.sh` + `open` 拉起进程） |
| 构建前删除旧 `.app` | PASS（脚本 `rm -rf`） |
| 浅色菜单截图 | `examples/product-editor/docs/screenshots/` |

## 签名与分发

本轮**不**包含 Developer ID 签名、公证、stapler。未签名 `.app` 仅适已验证本机；外发需单独流程。
