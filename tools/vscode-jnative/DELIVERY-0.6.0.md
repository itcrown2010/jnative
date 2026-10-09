# JNative VS Code 插件 0.6.0 交付说明（稳定性）

作者：张晓达

## 问题与修复清单（基于实际代码审计）

| 问题 | 根因 | 修复 |
|------|------|------|
| 每次按键全量 `invalidate` + 项目 analyze | `scheduleAnalyze` 盲目清缓存 | 去掉无效 invalidate；缓存键含 overlay 哈希 + **磁盘指纹**（源码/清单/锁 mtime） |
| 外部改文件/新建删除后符号错乱 | 未处理 `workspace/didChangeWatchedFiles` | LSP 处理监视事件并按路径失效；客户端监视 `.jn/.java` + `jnative.project/toml/lock` |
| 多根工作区串项目 overlay | `openOverlays` 混入全部打开文件 | `overlaysForScope` 按项目根过滤 |
| 重命名可能用过期快照 | 缓存命中后直接改 | `requireFresh` + 作用域锁；文本对不齐则失败 |
| 重启重复订阅 LanguageClient | 每次 `context.subscriptions.push(client)` | 单次 disposable，重启先 dispose |
| 崩溃自动重启循环 | 无限制 | 60s 内最多 3 次；故意 stop 不触发自动重启 |
| 日志无限增长 / 可能泄密 | stderr 无级别 | `LspLog` 级别 + redact；客户端日志不倾倒 env |
| 构建超时显示含糊 | 无 timedOut | `runCapture.timedOut` → 明确「超时」，不成功、不跑旧产物 |
| 多项目误选 | QuickPick 可取消但提示弱 | 取消明确提示；标出当前文件所属项目 |

## 三类项目验收（独立目录，不改业务树）

根目录：`/tmp/jnative-acceptance-20261009-132524`  
环境：darwin arm64，Java 21.0.6，`target/jnative.jar`  
脚本：`node tools/vscode-jnative/scripts/acceptance-e2e.mjs`  
报告：`verify-artifacts/acceptance-e2e/report.json`

| 项目 | 规模 | 冷启动 init | 未保存补全 | 重命名 | 格式化 | 构建 |
|------|------|-------------|------------|--------|--------|------|
| 01-minimal（guide-hello 副本） | 小 | **98–100 ms** | **120–124 ms** | **18–22 ms** | **5–6 ms** | **~192–197 ms** success |
| 02-multicu（多文件+pkg-cache） | 中 | **125–135 ms** | **120–432 ms** | **29–62 ms** | **4–5 ms** | 上游示例构建失败（Util/依赖）；**LSP 跳转/编辑通过** |
| 03-order（order-processing） | ~9 文件 / 424 行 | **119–171 ms** | **367–497 ms** | **185–197 ms** | **5 ms** | 上游示例构建失败（MoneyCents/依赖）；**LSP 编辑通过** |

协议回归 `lsp-smoke.mjs`：**24 passed / 0 failed**。  
编辑链路（打开→未保存补全→重命名→格式化）三类项目均通过；原生构建在依赖示例上与仓库主线一致地失败，已记入 knownIssue，**故不宣称无阻断稳定版**。

调试：依赖 CodeLLDB，本 harness 未自动拉起调试器（已标注）。

## 性能说明

上表数字为**本机实测**（acceptance-e2e），非估计。  
补全走 `CompletionEngine` 单文件快路径；项目级 `analyzeUnits` 在诊断/导航/fresh 重命名路径，有 debounce（350ms）与按 URI 合并。

## 安装 / 升级

```bash
cd /Users/zhangxiaoda/jnative && mvn -q -DskipTests package
cd tools/vscode-jnative && npm install && npm run package

code --uninstall-extension jnative.jnative
code --install-extension ./jnative-0.6.0.vsix --force
```

保留用户 `settings.json` / `launch.json`（VSIX 不覆盖）。  
确认扩展 **0.6.0**，`jnative.jarPath` = 本仓库 `target/jnative.jar`。

未信任工作区：不启动 LS/构建/运行。  
普通 Java 项目：不会仅因 `.java` 被 LSP 接管。

## 尚未完成 / 未验证

- Extension Development Host 内完整 UI 点击调试（需本机 CodeLLDB + `code` CLI）
- `examples/multi-cu-resolve` **构建期** Util 解析（仓库现状；LSP 索引可用）
- 范围格式化、可覆盖方法重命名（仍明确拒绝）
- 超大项目（≫数百文件）缓存策略仅做了 8 scope 上限，未压测

**因此不将本版标为「无阻断稳定版」**：多文件第三方示例的**原生构建**仍有上游问题；编辑/LSP 路径三类项目已通过独立目录验收。

## 故障排查

见 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)。
