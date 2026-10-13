# 教程：统一平台能力 — 文件导入导出与分享

作者：张晓达

## 目标

在 `examples/guide-tasks` 中用**同一套 API**完成：导出任务备份、预览导入、确认导入、系统分享。不按平台名分支。

## 步骤（Web）

1. 构建：`java -jar target/jnative.jar mobile build --target web --project examples/guide-tasks`
2. 预览 `examples/guide-tasks/dist/`（或 VS Code「运行 → Web」）。
3. 打开**设置 → 备份与分享**，确认能力状态行（`file.pick` / `share.system`）。
4. 新建几条任务 → **导出备份** → 得到 `任务备份.jnbackup`。
5. **选择备份并预览** → 核对条数 → 选「追加」或「替换」→ **确认导入**。
6. **分享备份**：有 `navigator.share` 则打开系统面板；否则回退为下载。状态为 `unknown` 时表示**未确认送达**。

## 移动

```bash
java -jar target/jnative.jar mobile build --target ios --project examples/guide-tasks
java -jar target/jnative.jar mobile run --target ios --device <UDID> --project examples/guide-tasks
```

Android 使用系统文档选择器（SAF）；iOS 使用 `UIDocumentPicker` / 分享面板。调试构建可检查 WebView。

## 契约要点

- 备份不含同步令牌与队列；导入走本地 `create` 待同步。
- 取消 → `USER_CANCELLED`；超限 → `SIZE_LIMIT`；不支持 → `UNSUPPORTED`。
- 详情：[`../../examples/guide-tasks/docs/PLATFORM_CAPS.md`](../../examples/guide-tasks/docs/PLATFORM_CAPS.md)

## 自动校验

```bash
node examples/guide-tasks/scripts/verify_platform_backup.mjs
```
