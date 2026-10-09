# API：移动能力

移动当前是 **WASM UI + 系统 WebView/WKWebView 容器**，不是原生控件 UI。

## CLI

| 命令 | 说明 | 状态 |
|------|------|------|
| `jnative doctor --mobile` | 检查 SDK/模拟器等（不下载、不改签名） | 本机曾全绿 → 实验性/环境相关 |
| `jnative mobile build\|run\|…` | 通用移动子命令 | 以 `--help` 为准；示例多用仓库脚本 |

## 示例工程

| 工程 | 说明 | 验证 |
|------|------|------|
| `examples/guide-tasks/mobile/` | 任务管理器触屏壳 + 容器 | assets 同步 OK；Android APK OK；iOS Simulator 构建 OK；设备交互未全验 |
| `examples/sudoku/mobile/` | 数独触屏 + 桥接 | 见 PLATFORM.md |

## 容器职责（非 JNative 语言 API）

- 加载本地 `www/`（自定义 scheme / AssetLoader）  
- 可选 JS Bridge（存储、生命周期）  
- 安全区与 WebView 配置  

## 不包含

- 商店发布 API  
- 原生控件绑定生成器  

## 相关教程

[移动任务管理器](/tutorials/mobile/)
