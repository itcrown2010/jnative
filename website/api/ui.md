# API：桌面 UI

**源码**：`src/main/resources/stdlib/ui/UiLib.java`  
**版本**：0.32  
**平台**：macOS arm64 **实验性**（guide-tasks / sudoku 已构建）；Linux/Windows **未验证**。

签名提取：`website/api/_generated/ui.txt`（`./scripts/docs/extract-api.sh`）。

## Application

| 项 | 说明 |
|----|------|
| 签名 | `Application()`；`void run()`；`static void invokeLater(Runnable r)`；`static void checkUiThread(String)` |
| 线程 | 构造线程即 UI 线程；`run()` 进入原生循环 |
| 用法 | 见 `examples/guide-tasks/desktop/TasksDesktop.java` |
| 状态 | 实验性（macOS） |

## Window

| 项 | 说明 |
|----|------|
| 签名 | `Window(String title, int w, int h)`；`setContent`；`show`；`onClose(Runnable)` |
| 资源 | 关闭时应取消订阅并停止后台回调 |
| 异常 | 创建失败可抛 `UiNativeException` |
| 状态 | 实验性 |

## 常用控件

| 类型 | 要点 |
|------|------|
| `VBox` / `HBox` | `spacing` / `padding` / `add` |
| `Label` / `Button` / `TextField` | `setText` / `getText` / `onClick` / `subscribeClick` |
| `UiSubscription` | `cancel()` 取消点击订阅 |

逐方法行为未全部人工验收的标 **待完善**；以 guide-tasks 用到的路径为准。

## 可运行用法

```bash
./examples/guide-tasks/scripts/build_desktop.sh
./build/guide-tasks/TasksDesktop
```
