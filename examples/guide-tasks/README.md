# guide-tasks：小型任务管理器

作者：张晓达

跨目标教学 sample：**共享 `TaskCore`** + 桌面 `jnative.ui` + Web（WASM 核心 + JS DOM 壳）。

| 路径 | 说明 | 状态（macOS arm64，2026-10-09） |
|------|------|----------------------------------|
| `core/TaskCore.java` | 规则 / 存档格式 | `scripts/verify_core.sh` **PASS**；WASM `verify_web.sh` **PASS** |
| `desktop/TasksDesktop.java` | 原生窗口 UI | `scripts/build_desktop.sh` **PASS** |
| `web/` | 浏览器壳 | 构建 **PASS**；浏览器点击可本地预览 |
| `mobile/` | 触屏壳 + WebView 容器 | assets OK；Android APK OK；iOS Simulator 构建 OK；设备安装另步 |

## 构建

```bash
./examples/guide-tasks/scripts/build_desktop.sh
./build/guide-tasks/TasksDesktop

./examples/guide-tasks/scripts/build_web.sh
cd examples/guide-tasks/web/dist && python3 -m http.server 8767

./examples/guide-tasks/scripts/verify_mobile_assets.sh
./examples/guide-tasks/scripts/build_android.sh
./examples/guide-tasks/scripts/build_ios.sh
```

## 重要边界

- **不是**「一份 UI 自动适配所有平台」。
- Web/移动 UI 为 **WASM + JS 壳 / WebView 容器**，不是原生控件 UI，也不是当前可用的 WebUI `Html.*` 全量路径。
- 桌面：仅 **macOS arm64** 已验证构建；Win/Linux **未验证**。
- 商店发布：**未支持**。

文档：`website/tutorials/desktop|web|mobile/`。
