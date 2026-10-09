# API：网络与 HTTP

**源码**：`stdlib/net`、`stdlib/http`、`stdlib/tls`  
内部工程文档线索：`docs/http.md`、`docs/networking.md`（偏实现，可能过时——以源码与提取为准）。

| 目标 | 状态 |
|------|------|
| 原生 HTTP 服务/客户端 | 实验性 |
| WASM | 浏览器网络模型；勿假设服务端 socket API |
| 错误 / TLS | 见 [调试](/reference/debugging)；细节待完善 |
