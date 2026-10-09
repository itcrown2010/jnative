# API 参考 · 索引与提取流程

## 原则

1. **签名从源码提取**（`src/main/resources/stdlib/`）。
2. **行为说明人工核实**；无依据标 **待完善**。
3. **不因写文档批量改名** 现有 API。
4. 每个条目尽量包含：全名与签名、参数/返回、异常、null/边界、同步异步与线程、资源所有权、目标与验证状态、可运行用法、所属版本（`0.32`）。

## 可重复提取

```bash
# 在仓库根执行
./scripts/docs/extract-api.sh
```

输出目录：`website/api/_generated/`（生成物可提交或 CI 生成；本阶段脚本写入文本索引）。

## 模块导航

| 页面 | stdlib 目录 |
|------|-------------|
| [基础与字符串](/api/basics) | collections（StringBuilder 等）+ 语言内建 |
| [数值与集合](/api/collections) | `collections/`、`math/` |
| [文件与流](/api/io) | `io/` |
| [时间](/api/time) | `time/` |
| [网络与 HTTP](/api/net-http) | `net/`、`http/`、`tls/` |
| [数据库](/api/db) | `db/` |
| [并发与异步](/api/async) | `async/`、collections 线程池 |
| [桌面 UI](/api/ui) | `ui/` |
| [浏览器与 WebUI](/api/webui) | `web/`、`webui/`、`browser/` |
| [移动能力](/api/mobile) | 移动构建器 + 容器桥接（非完整 stdlib） |
| [FFI](/api/ffi) | 编译器原生绑定 / 示例 |

## 状态图例

页面内接口若仅有提取签名、无行为验收 → **待完善** + 能力 **未验证**。
