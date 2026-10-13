# 统一平台能力接口（文件 / 分享）

作者：张晓达

## 原则

- 应用通过**能力名**发现功能，不按 `android` / `ios` / `web` 猜。
- 每项能力有状态：`available` | `unsupported` | `denied`。
- 错误码统一：`USER_CANCELLED`、`PERMISSION_DENIED`、`UNSUPPORTED`、`SIZE_LIMIT`、`BAD_PARAMS`、`IO_ERROR`、`DESTROYED`、`TIMEOUT`、`BUSY`。

## 能力列表（首轮）

| 能力 | 含义 |
|------|------|
| `platform.queryCapabilities` | 返回名称与状态 |
| `file.pick` | 系统选择器选文件，返回 `handleId`（非普通路径） |
| `file.read` | 按句柄读取 `text` 或 `bytesBase64`（有界） |
| `file.release` | 释放句柄 / 临时授权 |
| `file.export` | 导出到用户选定位置 |
| `share.system` | 系统分享面板；**不宣称送达** |

未包含：相机、定位、推送。

## 文件边界

| 项 | 值 |
|----|-----|
| 最大字节 | 1 MiB |
| 文件名 | 1..180，允许中文与空格，禁控制字符 |
| 文本编码 | UTF-8 |
| 二进制 | Base64 仅在桥接载荷内，不灌入 WASM |
| 取消 | `USER_CANCELLED`，不重试弹窗 |
| Android | `content://` URI + SAF |
| iOS | 安全作用域 / 文档选择器 URL |
| 临时文件 | 分享/导出后清理 |

## JS API

```js
import { createPlatform } from './lib/platform.js';
const platform = await createPlatform();
await platform.init();
const caps = await platform.queryCapabilities();
const picked = await platform.pickFile({ accept: ['.jnbackup', 'application/json'] });
const { text } = await platform.readFile(picked.handleId, { as: 'text' });
await platform.releaseFile(picked.handleId);
await platform.exportFile({ name: '任务备份.jnbackup', mimeType: 'application/json', text });
const share = await platform.share({ title: '任务备份', name: '...', text });
// share.status === 'unknown' | 'dismissed' | 'fallback-download'
platform.destroy(); // 页面销毁时
```

## 备份格式

见 `web/lib/backup.js`：`format = jnative.guide-tasks.backup`，`version = 1`。  
**不含**同步令牌、待同步队列、serverVersion。导入后以本地 `create` 入队，避免错误版本与重复上传旧版本。

## 权限

- 仅在用户点击导出/导入/分享时触发系统选择器或分享面板。
- 优先 SAF / UIDocumentPicker，避免宽泛存储权限。
- `PERMISSION_DENIED` 后 `queryCapabilities` 可将相关能力标为 `denied`，应用应说明而非循环申请。

## 桥接安全

- 方法白名单；参数类型/长度校验；请求 ID 一次完成。
- 仅授权应用页面（同源 / 打包 WebView）。
- 容器 `destroy` 取消未完成选择并释放句柄。
- 不执行传入脚本或系统命令。
