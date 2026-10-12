# 桌面教程：小型任务管理器

业务：本地任务列表。共享规则在 `examples/guide-tasks/core/TaskCore.java`，UI 在 `desktop/TasksDesktop.java`。

## 平台状态

| 平台 | 状态 |
|------|------|
| macOS arm64 | **实验性**（`build_desktop.sh` 已通过） |
| macOS x64 / Linux / Windows | **未验证** |

## 课表

| 课 | 主题 | 终点代码 |
|----|------|----------|
| [01](./lessons/01) | 创建窗口 | `desktop/lessons/01/` |
| [02](./lessons/02) | 布局与基础控件 | 见终稿 `TasksDesktop` 表单区 |
| [03](./lessons/03) | 表单输入与校验 | `TaskCore.addTaskString` 错误码 |
| [04](./lessons/04) | 列表增删改 | 终稿 `refreshList` / `makeRow` |
| [05](./lessons/05) | 事件与闭包 | `onClick` / `subscribeClick` |
| [06](./lessons/06) | 本地存储 | `prepareSave` + `File.writeString` |
| [07](./lessons/07) | 后台任务与 UI 更新 | `Thread` + `Application.invokeLater` |
| [08](./lessons/08) | 异常与错误展示 | status 标签 / try-catch |
| [09](./lessons/09) | 调试 | `--debug` / LLDB / CodeLLDB |
| [10](./lessons/10) | 打包与运行 | `desktop/lessons/10/` = 终稿 |

## 必读概念

- **UI 线程**：控件创建与更新须在 `Application` 线程；后台用 `invokeLater` 回投。
- **事件取消**：`subscribeClick` 返回 `UiSubscription`，窗口 `onClose` 时 `cancel`。
- **销毁后**：`TasksUi.alive = 0` 且递增 `bgToken`，忽略过期回调。

## 构建终稿

```bash
./examples/guide-tasks/scripts/build_desktop.sh
./build/guide-tasks/TasksDesktop
```

验证核心：`./examples/guide-tasks/scripts/verify_core.sh`
