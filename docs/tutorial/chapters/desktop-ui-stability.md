# 桌面 UI：生命周期、线程与稳定交付

作者：张晓达  
适用：JNative 当前主干 + macOS Cocoa  
示例：`examples/desktop-sample/`（本机验证后再写进此章）

## 1. 目标

做一个能**关窗、退出、后台回写**而不崩溃的最小桌面应用，并学会用 VS Code 构建、调试、打 `.app`。

## 2. 项目结构

```
examples/desktop-sample/
  jnative.toml
  README.md
  scripts/build.sh
  src/Main.java
```

```bash
mvn -q -DskipTests package
chmod +x examples/desktop-sample/scripts/build.sh
examples/desktop-sample/scripts/build.sh
./build/desktop-sample/DesktopSample --self-test
open build/desktop-sample/DesktopSample.app
```

用户数据在 `build/desktop-sample/note.txt`，**不要**写入 `.app` Bundle。

## 3. 公共 UI 接口（要点）

| 用途 | API |
|------|-----|
| 应用循环 | `Application` / `run` / `requestQuit` / `onQuit` |
| 线程 | `UiThread.assertCurrent` / `runLater` / `coalesce` |
| 绑定 | `ObservableValue` / `Bindings.bindTextTwoWay` / `BindingScope` |
| 命令 | `Command` / `ShortcutManager` / `AppMenus` |
| 未保存 | `Dialogs.askSave` + `vetoClose` |
| 安全写文件 | `FileDialog.writeTextSafe` |

## 4. 事件与状态绑定

```java
scope = new BindingScope();
note = ObservableValue.ofString(DsStore.load(path));
Bindings.bindTextTwoWay(field, note, scope);
scope.addObs(note.subscribe((ev) -> { markDirty(); }));
```

关闭时：

```java
scope.dispose(); // 取消所有订阅
alive = 0;
gen = gen + 1;   // 作废在途回调
```

## 5. 后台任务

```java
int token = gen;
new Thread(() -> {
    // 后台工作
    UiThread.coalesce(() -> {
        if (alive == 0 || token != gen || window.disposed != 0) return;
        status.setText("完成");
    });
}).start();
```

规则：

1. UI mutate 只在 UI 线程。
2. 关闭后回调必须用 `alive`/`gen`/`disposed` 守卫。
3. 高频刷新用 `UiThread.coalesce`，不要无限 `invokeLater` 堆积。
4. `Application.requestQuit()` 会清空任务队列；脏文档确认写在 **Quit 命令里**，不要依赖 veto 挡住 Quit。

## 6. 错误处理

- 已销毁控件：`DisposedComponentException`（可诊断）。
- 非 UI 线程：`UiThreadViolationException`。
- 保存失败：保留输入，状态栏 + `Dialogs.error`，不假装成功。

## 7. 调试与打包

1. VS Code 打开 `examples/desktop-sample`（或含 `jnative.toml` 的工程）。
2. 确认扩展与 `jnative.jar` 路径（见第 2 章安装）。
3. 故意写错类型 → Problems 定位。
4. 构建运行；需要断点时用「生成调试配置」+ CodeLLDB。
5. `scripts/build.sh` 或命令面板 **Package Project** → 双击 `.app`。
6. 退出后确认无残留专用测试进程（`ps` 查 `DesktopSample` / 压力脚本拉起的 AM）。

## 8. 压力与回归

```bash
./build/desktop-sample/DesktopSample --self-test
./build/product-editor/ProductEditor --self-test
examples/activity-monitor/scripts/run_verify.sh
STRESS_SECONDS=45 ./scripts/ui/stress-desktop.sh
```

手工清单（AM / PE）见 `docs/desktop-ui-stability-delivery.md`。

## 9. 相关章节

- [框架 API](./desktop-ui-framework.md)
- [绑定](./desktop-ui-binding.md)
- [应用壳](./desktop-ui-app-shell.md)
- [键盘与无障碍](./desktop-ui-a11y-keyboard.md)
