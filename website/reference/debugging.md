# 调试、性能与故障排查

## 编译错误定位

1. 阅读 `file:line:col: error:`  
2. VS Code Problems 来自 `--format json`  
3. 若标 `[stdlib]`，先怀疑用法与版本，再怀疑用户行映射  
4. 最小化复现文件；数独相关可对照 `examples/sudoku/docs/repro/`

## 语言服务 / 「没有补全」

**预期**：当前扩展 **不提供补全**。只有高亮 + 诊断（LSP/构建）。

## 原生断点

```bash
jnative My.jn --debug -O0 -o /tmp/my
lldb /tmp/my
```

- VS Code：CodeLLDB +「调试项目」  
- 已验证（macOS arm64 CLI）：断点/单步/栈/部分标量  
- 未做：对象字段可视化、异步栈、优化 `-O2` 变量恢复

## WASM

| 手段 | 说明 |
|------|------|
| 浏览器 DevTools | 调试 JS 壳；WASM 源映射视产物而定（实验性） |
| `--keep-temps` | 查看 `app.wat` 函数名 `_JN_Class_method` |
| Node 冒烟 | 见 `verify_web.sh`（instantiate + 导出调用） |

完整 DWARF→浏览器：**未完成**。

## 移动容器

| 平台 | 调试 |
|------|------|
| Android | `chrome://inspect` 附加 WebView；`adb logcat` |
| iOS | Safari → 开发 → 模拟器/设备 → 页面 |
| 壳代码 | Android Studio / Xcode |

`jnative mobile logs` 以 CLI 帮助为准。

## GC / 内存 / 资源

- 泄漏：未关闭 IO、未 cancel 的 UI 订阅、窗口销毁后仍 `invokeLater`  
- 长暂停：大分配 + 持锁  
- WASM：注意 `writeWasmString` 短期指针；host roots 见生成 `app.js`  
- **不要**建议「关闭 GC」作常规手段

## async/await 不恢复

- 原生：检查是否回到正确调度/UI 线程  
- wasm32-browser：async/await **未接通**（编译失败或不可用）— 用事件/回调  
- 移动：后台 `bgToken` 是否作废了回调

## 网络 / TLS / 数据库

记录完整错误与复现命令；核对证书路径。文档默认解法 **不是** 关闭全局 TLS 验证。

## 构建缓存与依赖

```bash
jnative clean
jnative doctor
# 移动：重新 sync_mobile_assets 再打包，避免旧 wasm
```

优先清洁构建产物与包缓存；**不要**不加说明地删除无关用户数据或整个 git 历史。

## 性能（入门级）

| 场景 | 建议 |
|------|------|
| 桌面列表刷新 | 避免无变更时整树重建（guide-tasks 教学实现偏简单） |
| WASM | 减小导出面；热路径少分配；勿在 JS 重写核心 |
| 移动 | 大图/大 CSS 少放 www；冷启动先保证 wasm 缓存 |

深度 profiler 集成：**未验证**。
