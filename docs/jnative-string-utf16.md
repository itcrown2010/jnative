# JNative — String UTF-16 契约交付

作者：张晓达

## 公开契约

- `length()` / 索引 API → UTF-16 码元  
- `utf8Length()` / `getBytes()` → UTF-8 字节  
- `"中文😀"` → length **4**，utf8Length **10**

## 内部表示

UTF-16 内联：`{ meta*, utf16Len@8, uint16 data@16 }`（`jnative_string.c`）。

## 兼容迁移

| 原用法 | 新用法 |
|--------|--------|
| `s.length()` 当字节数 | `s.utf8Length()` 或 `s.getBytes().length` |
| File/TCP `writeString` 直读 payload | 已改为 UTF-8 编码写出 |
| PG `body.length()+16` | `body.utf8Length()+16` |
| Studio 按字节截断 | 按 `utf8Length` 循环裁剪 |

HTTP Content-Length 本就走 `byte[]`，无需改。

## 修改要点

- `native/posix/jnative_string.{c,h}` — 核心运行时  
- `StringRuntimeEmitter` / File / Network / FFI / Io println  
- `SemanticAnalyzer` + `IrGenerator`：`utf8Length`  
- `PgDriverLib` / `StudioLib`  
- `docs/string.md`、`language-support.md`  
- `StringEndToEndTest`

## 编码策略

- `fromBytes`：严格 UTF-8  
- 孤立代理：UTF-16 可存；编码 UTF-8 时 → U+FFFD  
- 嵌入 NUL：String/`getBytes` 保留；CString 拒绝  
- 大小写：仅 ASCII  

## WASM

浏览器路径仍为 UTF-8 字节布局；**未**对齐 UTF-16 `length`。原生后端为本轮验收目标。

## 测试

见 `StringEndToEndTest`（UTF-16 长度、代理对 substring、equals/hashCode、getBytes 往返、嵌入 NUL、GC）。
