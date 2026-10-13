# guide-tasks：多页面任务管理器

作者：张晓达

共享 **TaskCore（WASM）** + Web 多页面壳 + 移动触屏覆盖 + 桌面 UI（本轮不扩展桌面控件）。

## 验收（macOS arm64）

```bash
./examples/guide-tasks/scripts/verify_core.sh
./examples/guide-tasks/scripts/verify_web.sh
node examples/guide-tasks/scripts/verify_browser.mjs
node examples/guide-tasks/scripts/verify_sync.mjs       # 真实 SQLite API + 故障注入
node examples/guide-tasks/scripts/verify_idb_sync.mjs   # 离线队列→同步→冲突
node examples/guide-tasks/scripts/verify_dev_web.mjs
./examples/guide-tasks/scripts/verify_mobile_assets.sh
./examples/guide-tasks/scripts/verify_upgrade_retention.sh
node examples/guide-tasks/scripts/verify_platform_backup.mjs
```

## VS Code / CLI 移动入口

项目含 `jnative.toml`（`web_build_script` → 根目录 `dist/`）：

```bash
java -jar target/jnative.jar doctor --mobile
java -jar target/jnative.jar mobile build --target web --project examples/guide-tasks
java -jar target/jnative.jar mobile build --target ios --project examples/guide-tasks
java -jar target/jnative.jar mobile run --target ios --device <UDID> --project examples/guide-tasks
```

扩展：`tools/vscode-jnative` 0.8.1 — 选择目标 / 配置 / 设备 QuickPick / 分层日志 / 停止。  
说明：`docs/developer/VSCODE_MOBILE_RUN.md` · 验收：`docs/developer/VSCODE_MOBILE_ACCEPTANCE.md`

## 开发：同步服务 + 预览

```bash
node examples/guide-tasks/server/sync-server.mjs   # http://127.0.0.1:8780/  仅本机
node examples/guide-tasks/scripts/dev_web.mjs      # http://127.0.0.1:8767/
```

契约：`docs/SYNC_CONTRACT.md` · 教程：`docs/tutorial/chapters/web-mobile-sync.md`

## 页面

`#/list` · `#/task/:id` · `#/new` · `#/edit/:id` · `#/settings`

## 职责

| 层 | 职责 |
|----|------|
| TaskCore | 模型、校验、稳定 ID、存档 v3 |
| `web/lib/*` | 路由、表单、生命周期、存储适配 |
| Swift/Kotlin | WebView、桥接白名单、平台 KV |

文档：`docs/developer/WEB_MOBILE_CAPABILITY_MATRIX.md` · `docs/tutorial/chapters/web-mobile-tasks.md`
