# HTTP API

**Status:** Experimental · **Source:** `src/main/resources/stdlib/http/HttpLib.java`

## Overview

HTTP/1.x client and server built on TCP (and TLS helpers). Not a framework with middleware plugins.

## Types

| Type | Role |
|------|------|
| `HttpRequest` | Outgoing request builder |
| `HttpResponse` | Status, headers, body |
| `HttpClient` | Static `get` / `send` |
| `HttpServer` | `listen` / `accept` |
| `HttpConnection` | Per-accepted connection; `readRequest` / `send` |
| `HttpServerRequest` | Parsed inbound request |

## Methods (selected public)

### `HttpRequest`

| Name | Signature | Notes |
|------|-----------|-------|
| `create` | `static HttpRequest create(String method, String url)` | |
| `header` | `void header(String name, String value)` | |
| `bodyString` | `void bodyString(String text)` | |
| `bodyBytes` | `void bodyBytes(byte[] data)` | |
| `insecure` | `HttpRequest insecure(int v)` | TLS related flag |

### `HttpResponse`

| Name | Signature | Notes |
|------|-----------|-------|
| `text` | `static HttpResponse text(String body)` | 200 text |
| `textStatus` | `static HttpResponse textStatus(int status, String body)` | |
| `bytesStatus` | `static HttpResponse bytesStatus(int status, byte[] body)` | |
| `json` | `static HttpResponse json(String body)` | Sets JSON content-type; **does not** serialize objects |
| `fileStream` | `static HttpResponse fileStream(int status, String path, long start, long endInclusive, long total)` | Range/stream |

### `HttpClient`

| Name | Signature |
|------|-----------|
| `get` | `static HttpResponse get(String url)` |
| `send` | `static HttpResponse send(HttpRequest request)` |

Static timeout fields exist (`connectTimeoutMs`, `requestTimeoutMs`, `tlsHandshakeTimeoutMs`) — see source.

### `HttpServer`

| Name | Signature |
|------|-----------|
| `listen` | `static HttpServer listen(String host, int port)` |
| `listenAny` | `static HttpServer listenAny(int port)` |
| `accept` | `HttpConnection accept()` |
| `close` | (see source) |

## Examples

- Server: `examples/http-server/`, `examples/HttpHelloServer.java`  
- Client: `examples/HttpDownload.java`  
- Recipes: [http-get](../../recipes/http-get.md), [http-post](../../recipes/http-post.md)

## Error Handling

Network/parse failures throw; check response status codes explicitly. Redirects handled inside `sendRedirects` paths—read `HttpLib` before relying on edge cases.

## Thread Safety

Typical servers accept in a loop; multi-thread accept is possible with native threads but **experimental**. Connection objects are not documented as shareable across threads.

## Platform Notes

Native targets only. Not available inside wasm32-browser user code (use JS `fetch` adapters for browser apps).

## Known Limitations

- Not a complete servlet container  
- `HttpResponse.json` does not call `Json.stringify` for you  
- Pipelining flag `PIPELINING_SUPPORTED = 0`  
