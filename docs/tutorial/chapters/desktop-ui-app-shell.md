# 桌面应用壳：菜单、命令、多窗口与打包

作者：张晓达

目标：用公共 API 构建带原生菜单、快捷键与明确生命周期的 macOS 桌面应用，并生成可本机双击运行的 `.app`。

## 1. 命令与菜单

同一业务动作由 `Command` 执行；菜单项与工具栏按钮绑定到同一命令。

```java
Application.setAppName("商品编辑器");

Command save = new Command("file.save", "保存");
save.setShortcut(KeyCombination.of(83).withPrimary()); // Cmd+S
save.setExecute(() -> { /* ... */ });

MenuItem mi = new MenuItem("保存");
save.bindMenuItem(mi);   // 同步快捷键到原生 keyEquivalent
fileMenu.add(mi);

ShortcutManager.installOn(window); // 窗口级兜底；文本框内 C/V/X/Z/A 不拦截
```

应用菜单（About / Quit）由 Cocoa 根据 `Application.setAppName` 生成。**Quit 走 `Application.requestQuit()`**，不直接 `terminate:`，以便运行退出钩子。

标准结构可用 `AppMenus.fileMenu` / `editMenu` / `windowMenu` / `helpMenu`。编辑菜单仅占位；文本控件的复制粘贴撤销优先走原生响应链。

## 2. 多窗口契约

| 概念 | API |
|------|-----|
| 主窗口 | `Window` 默认 `ROLE_PRIMARY` |
| 辅助窗口 | `aux.setOwnerWindow(primary)` |
| 激活 | `window.activate()` |
| 关闭取消 | `onClose` 中 `vetoClose()` |
| 未保存 | `Dialogs.askSave` → 1 保存 / 2 放弃 / 0 取消 |

**macOS 契约（本轮选定）：**

- 默认 `Application.setQuitOnLastWindowClose(true)`：关闭最后一个托管窗口即 `requestQuit()`。
- 设为 `false` 时，关窗不退出；Dock 仍在，需菜单 Quit / Cmd+Q。
- 主窗口关闭时自动关闭其 `setOwnerWindow` 的辅助窗口。

## 3. 退出清理

```java
Application.onQuit(() -> {
    alive = 0;
    scope.dispose();
    // 停采样 / 关文件；失败只打日志
});
Application.requestQuit();
```

钩子有界执行；失败记日志，不默认 `kill -9` 代替清理。

## 4. 文件对话框

```java
String p = FileDialog.open(window, "json"); // null = 用户取消
String s = FileDialog.save(window, "json"); // 覆盖确认由系统面板处理
FileDialog.writeTextSafe(s, text);          // tmp 再写目标，避免半截文件
```

用户数据请用 `Files.configDir("AppName")`，**不要写入 `.app` 包内**。

## 5. 打包 .app

```bash
# 通用
scripts/macos/pack-app.sh \
  --bin build/product-editor/ProductEditor \
  --name ProductEditor \
  --id io.jnative.examples.product-editor \
  --out build/product-editor

# 示例脚本（编译失败会删掉旧 .app）
examples/product-editor/scripts/build.sh
examples/ui-workbench/scripts/build.sh
examples/activity-monitor/scripts/build_desktop.sh
```

未签名、未公证：仅本机验证。外部分发需另行签名与公证。

VS Code：`JNative: Package Project` 先构建再调用同一 `pack-app.sh`；构建失败不打包、不启动旧产物。

## 6. 示例

- `examples/product-editor/` — 菜单、快捷键、辅助「关于」窗、打开/另存为、未保存 askSave
- `examples/ui-workbench/` — 同上 + 辅助窗口
- `examples/activity-monitor/` — 详情窗归属主窗、退出停采样、`.app` 打包
