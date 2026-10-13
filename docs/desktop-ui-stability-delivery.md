# 桌面 UI 稳定性收敛 — 交付说明

作者：张晓达  
验收平台：macOS Cocoa（首期）  
能力矩阵：`docs/desktop-ui-capability-matrix.md`

## 交付物

| 项 | 路径 |
|----|------|
| 生命周期 / 退出 / 队列 | `stdlib/ui/UiLib.java`（`Container.dispose`、`requestQuit`、`UiTaskQueue.clear`、`UiCoalesce`） |
| 绑定与表单 | `stdlib/ui/BindingLib.java` |
| 参考 sample | `examples/desktop-sample/` |
| 压力脚本 | `scripts/ui/stress-desktop.sh` |
| 中文教程 | `docs/tutorial/chapters/desktop-ui-stability.md` |
| 能力矩阵 | `docs/desktop-ui-capability-matrix.md` |
| 新鲜环境记录 | `docs/desktop-ui-fresh-env-acceptance.md` |

## 缺陷与修复清单

### P0（本轮已处理）

| ID | 问题 | 修复 | 回归 |
|----|------|------|------|
| P0-1 | `Container.dispose` 不递归子控件 → 原生句柄泄漏 | 子树先 `dispose`，再 `disposeSelf`（无 `super.dispose`） | PE / DS `--self-test` 句柄下降 |
| P0-2 | Quit 与 `vetoClose` 死锁/无法退出 | `quitting` 期间忽略 veto；确认在 `requestQuit` 前 | PE `confirmQuitThenExit` |
| P0-3 | 退出后 `invokeLater` 仍触达已毁控件 | `quitting` 丢弃；`clear` 队列；应用 `alive`/`gen` | AM soak 退出；sample |
| P0-4 | 已销毁句柄静默操作 | `ensureAlive` → `DisposedComponentException` | 诊断可读，非随机崩溃 |
| P0-5 | 嵌套 notify 吞掉异常 | 超 `MAX_NESTING` 抛 `UiException` | PE self-test |
| P0-6 | AM 刷新约 20–30s 后 SIGSEGV（`jnative_thread_entry` / `jn_str_len`） | 年轻代移动 GC 后 `thread_entry` 从 TLS **重载** Thread；`string_concat` 遇 null 不落到 `jn_str_len`；UI 日志容忍 null message | 45s AM soak 存活；`stress-desktop.sh` |

### P1（部分）

| ID | 问题 | 状态 |
|----|------|------|
| P1-1 | 高频刷新排队膨胀 | **已修**：`UiCoalesce` / `UiThread.coalesce` |
| P1-2 | 刷新丢选择/焦点 | AM 稳定行 ID 已有；压力中需持续观察 |
| P1-3 | 打包缺资源 | 示例无包内资源依赖；用户数据在 `build/` |
| P1-4 | VoiceOver 单元格细粒度 | 未做 |
| P1-5 | Win/Linux 对等 | 未验收 |

## UI 线程约定

**必须在 UI 线程：**

- 所有控件创建 / mutate / dispose
- `Observable*.set`、绑定、对话框、`requestQuit`
- `Command` 执行体（由 ShortcutManager / 菜单在 UI 线程触发）

**可在后台线程：**

- 纯计算、文件读（注意与 UI 共享状态需拷贝）
- 完成后：`Application.invokeLater` 或 `UiThread.coalesce`，并检查 `alive`/`gen`/`disposed`

```java
int token = gen;
Thread t = new Thread(() -> {
    // work…
    UiThread.coalesce(() -> {
        if (alive == 0 || token != gen || window.disposed != 0) return;
        status.setText("ok");
    });
});
t.start();
```

## 压力测试结果（2026-10-13 本机）

```bash
STRESS_SECONDS=30 ./scripts/ui/stress-desktop.sh
# 另：手工 45s soak → SOAK_OK rss_min≈32KB(启动瞬态) rss_max≈136MB
```

| 项 | 结果 |
|----|------|
| `DesktopSample --self-test` | PASS（句柄 19→9） |
| PE `--self-test` ×5 + leak probe | PASS |
| AM `run_verify.sh` | PASS |
| AM GUI soak 30s | PASS；RSS ≈111–129 MB；CPU avg ≈6.3%；退出无残留 |
| 进程信号范围 | 仅脚本拉起的 AM PID |

日志：`build/ui-stress/latest-run.txt`、`stress-*.log`。

手工补充（无法由 headless 完全替代）：AM 搜索排序、CPU/内存切换、详情开关、暂停恢复、改窗、关开；PE 多窗切换与保存失败确认。

## 可运行产物

| 产物 | 路径 |
|------|------|
| 生命周期 sample | `build/desktop-sample/DesktopSample` / `.app` |
| 商品编辑器 | `build/product-editor/ProductEditor` / `.app` |
| 活动监视器 | `build/activity-monitor/ActivityMonitor` / `.app` |
| 截图（本机） | `build/ui-stress/screenshots/desktop-sample-screen.png` |
| 压力日志 | `build/ui-stress/latest-run.txt` |

## 稳定版声明

**未标记稳定版。** 本轮闭合了 macOS 上已开放路径的若干 P0（含 GC×Thread）；Windows/Linux、VoiceOver 细粒度与完整 IDE 新鲜环境断点未全部通过前，不宣称「稳定版桌面 UI」。