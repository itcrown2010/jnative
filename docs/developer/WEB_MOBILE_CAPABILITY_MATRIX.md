# Web / 移动能力矩阵（guide-tasks 验收）

作者：张晓达  
快照：2026-10-13（多页面/表单轮）· macOS arm64 · JNative JAR `0.32`

状态：**稳定** | **实验性** | **未支持** | **未验证**  
规则：有代码 ≠ 已验收；不得用 mock / 固定模板冒充用户 WASM。

## 编译与运行时

| 能力 | wasm32-browser | mobile-container（WebView+WASM） | 证据 / 备注 |
|------|----------------|-----------------------------------|-------------|
| 执行普通用户代码（非固定模板） | **实验性 / 已验收** | 同左 | `TaskCore.java` → `verify_web.sh` → `GUIDE_TASKS_WASM_OK` |
| 对象 / 数组 / 静态集合状态 | **实验性 / 已验收** | 同左 | TaskCore 数组状态 + 存档往返 |
| 闭包 / callable | **实验性** | 同左 | TaskCore **刻意不用** lambda（WASM callable 拒绝风险） |
| GC / 宿主句柄 | **实验性** | 同左 | `docs/wasm32-browser-gc.md`；长期引用需根句柄 |
| 异常传播 | **实验性** | 同左 | pending+CFG；非 WASM EH 提案 |
| 线程 | **未支持** | **未支持** | TargetProfiles |
| async/await（语言） | **实验性** | 同左 | 本示例异步用 JS `setTimeout`/`fetch`+AbortController |
| JS 互操作（DOM/glue） | **实验性 / 部分验收** | 同左 | `app.js` glue；DOM 由 JS 壳驱动 |
| `Browser.localStorage`（WASM→host） | **实验性** | 同左 | `storage_set` 可用；**`storage_get` 已修复**（需重建 JAR） |
| `BrowserNative.fetch` | **未支持** | **未支持** | glue `fetch_get` 仍返回空；网络走 **JS 适配器 fetch** |

## Web 应用面（guide-tasks）

| 能力 | 状态 | 证据 |
|------|------|------|
| 多页面导航（list/detail/new/edit/settings） | **实验性 / 已验收** | hash + 导航栈；Chrome e2e |
| 无效路由 / 坏参数 | **实验性 / 已验收** | `#/task/not-a-number` → 无效页 |
| 未保存离开 | **实验性 / 已验收** | dirty confirm |
| 表单：备注/状态/日期 + 跨字段 | **实验性 / 已验收** | 进行中需备注；非法日期不改写 |
| 中文输入 / 静态表单 DOM | **实验性 / 已验收** | composition；不重建输入框 |
| 稳定业务 ID | **实验性 / 已验收** | TaskCore id |
| JS 壳生命周期 | **实验性** | `lib/lifecycle.js` mount/update/dispose |
| WebUI `unmount` | **实验性** | `WebUI.unmount`；全量 Html 应用仍可能 WAT 失败 |
| 真实持久化 | **实验性 / 已验收** | localStorage `jnative.guide-tasks.v3` |
| 开发预览（可靠刷新） | **实验性 / 已验收** | `dev_web.mjs`；`verify_dev_web.mjs`；**非 HMR** |
| 最小真实网络 | **实验性 / 已验收** | hint.json + AbortController |
| IndexedDB 事务队列 | **实验性 / 已验收** | 非 localStorage 队列 |
| SQLite 同步 API | **实验性 / 已验收** | `server/sync-server.mjs`；幂等+游标 |
| 冲突（记录级） | **实验性 / 已验收** | `verify_idb_sync` conflict-ui |
| Service Worker 整包 | **实验性** | `sw.js`；失败保留旧完整集 |
| 字段级自动合并 | **未支持** | 后续 |

## 移动容器

| 能力 | Android WebView | iOS WKWebView | 备注 |
|------|-----------------|---------------|------|
| 本地 assets 加载 | **实验性** | **实验性** | `sync_mobile_assets.sh` |
| 桥接白名单 / 超时 / 能力发现 | **实验性** | **实验性** | `BridgeHost` + `bridge.js` |
| 平台 KV 存储 | SharedPreferences | UserDefaults | **普通本地存储，非安全凭据库** |
| 触屏 UI | Web DOM 壳 | Web DOM 壳 | **不是**完整原生控件 |
| 安全区 / 键盘 | CSS + visualViewport | 同左 | `adjustResize` / safe-area |
| 前后台 | visibility + bridge event | 同左 | |
| 模拟器/设备构建 | **实验性 / 本机构建 OK** | **实验性 / Simulator 构建 OK** | `assembleDebug` / xcodebuild；**安装与触控交互未做自动化验收** |
| 远程网络 | Manifest 默认无 INTERNET | ATS 默认 | 打包 `api/hint.json` 可离线拉取 |

## VS Code

| 能力 | 状态 |
|------|------|
| 创建 web/mobile 项目 | 实验性（扩展 wizard / CLI template） |
| 目标 `wasm32-browser` 构建预览 | 实验性 |
| 移动 doctor / 设备选择 | 未验证（依赖本机 SDK） |
| 构建失败不启动旧产物 | 扩展 runner 有逻辑；guide-tasks 脚本以本次构建为准 |

## 验收命令

```bash
./examples/guide-tasks/scripts/verify_core.sh
./examples/guide-tasks/scripts/verify_web.sh
node examples/guide-tasks/scripts/verify_browser.mjs   # 需本机 Google Chrome
./examples/guide-tasks/scripts/verify_mobile_assets.sh
./examples/guide-tasks/scripts/build_android.sh        # 需 Android SDK
./examples/guide-tasks/scripts/build_ios.sh            # 需 Xcode
```

Chrome e2e 期望：`GUIDE_TASKS_BROWSER_OK edit,search,complete,restore,network-ok,network-fail`
