# 平台文件/分享能力验收

作者：张晓达  
日期：2026-10-13

## 前置验收（移动运行链路）

| 项 | 结果 |
|----|------|
| `doctor --mobile` | Web/Android/iOS available |
| `mobile build --target web` | **已验证** |
| Android debug APK | **已验证**（含新 BridgeHost） |
| iOS 模拟器构建 + 安装启动 | **已验证** |
| VS Code 扩展编译 | 上一轮 0.8.1 保留 |

## 本轮能力

| 场景 | Web | Android | iOS |
|------|-----|---------|-----|
| 能力发现 `queryCapabilities` | **已验证**（脚本+UI） | 构建含实现 | 构建含实现 |
| 选择并读取文件 | **已验证**（浏览器 input） | 代码实现；**真机未验证**（无设备） | 代码实现；构建后可点 |
| 导出文件 | **已验证**（download / save picker） | SAF CreateDocument | UIDocumentPicker export |
| 系统分享 | **已验证**（share 或 fallback-download） | ACTION_SEND；送达=unknown | UIActivity；送达=unknown |
| 中文/空格文件名 | **已验证**（单元） | 依赖系统选择器 | 依赖系统选择器 |
| 损坏 JSON / 超限 | **已验证** `verify_platform_backup.mjs` | 同格式校验在 JS | 同左 |
| 取消 | Web 选择取消路径 | USER_CANCELLED | USER_CANCELLED |
| 导入预览+追加/替换 | **已验证**（逻辑+UI） | 同 Web 壳 | 同 Web 壳 |
| 备份往返（导出字段） | **已验证** | — | — |
| 页面销毁清理 | `platform.destroy` on pagehide | BridgeHost.destroy | BridgeHost.destroy |

## 工具版本

- Java 21 · Xcode 26.6 · WABT 1.0.39 · jnative.jar（本仓库 package）

## 截图

- iOS 启动：[`screenshots/guide-tasks-file-share-ios.png`](./screenshots/guide-tasks-file-share-ios.png)
- Web「备份与分享」面板：在设置页操作导出/导入时人工截取（自动化未点选文件框）。

## 未解决问题

1. Android 真机/模拟器安装与 SAF 交互 **未验证**（本机无在线 device）。
2. iOS 文档选择器/分享 UI **需人工点选确认**（自动化受限）。
3. 系统分享无可靠“已送达”信号 — 产品文案已标明。
4. 相机/定位/推送未做（刻意排除）。
