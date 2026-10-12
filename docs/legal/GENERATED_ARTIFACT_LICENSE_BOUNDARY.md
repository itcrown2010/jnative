# 生成程序与发行包的许可边界

## 编译器如何带入代码

`NativeRuntimeSources` 将仓库内 `src/main/resources/native/**` 的 C 源拷贝到临时目录，并由 `clang` **编译链接进**用户原生可执行文件（按是否启用 UI/TLS/graphics 等裁剪）。  
标准库 Java 源进入前端分析与 IR，其逻辑可体现在最终机器码中。  
**clang/LLVM 工具本身**不会把 LLVM 源码拷进用户程序。

WASM 路径生成 `app.wasm` / `app.js` 等；一般不链接整份 POSIX runtime，但仍可能包含 JNative 运行时语义。

## 随用户应用可能分发的组件

| 组件 | 何时进入用户产物 | 许可影响（当前理解） |
|------|------------------|----------------------|
| JNative 自有 runtime C（gc、fs、socket、mt…） | 几乎所有原生链接 | 若项目整体为 MIT，用户分发闭源二进制通常仍须满足 MIT 对**你再分发 JNative 源码树**的要求；**用户自己的应用源码**不因「用了 MIT 编译器」而必须开源 |
| UI / graphics C | 启用桌面 UI / graphics | 另可能 **链接 zlib** → 遵守 zlib 归属 |
| TLS C | 启用 TLS | **链接 OpenSSL** → 遵守对应 OpenSSL 版本归属（见 `OpenSSL-NOTE.txt`） |
| 标准库逻辑 | 被引用的 API | 视为项目自有实现的一部分（待权属确认） |
| OpenSSL / zlib / SQLite **库文件** | 动态或静态链接 | **不是** JNative MIT「重新授权」这些库；须各自合规 |

## 常见误解（明确否定）

| 说法 | 本项目立场（技术/许可边界说明，非正式法律意见） |
|------|--------------------------------------------------|
| 「编译器开源，所以所有生成程序必须开源」 | **不成立**。MIT 编译器不自动把 GPL 式 copyleft 传给用户应用。 |
| 「因此所有产物都可无条件闭源分发、无需任何声明」 | **也不成立**。若分发中包含或静态链接了需归属的第三方库（OpenSSL、zlib 等），或再分发了 JNative 源码，仍有义务。 |

## 发行形态差异

| 形态 | 应附带的材料（建议） |
|------|----------------------|
| 源码 SDK / Git 树 | `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md`、`third_party_licenses/` |
| `jnative.jar` | 在发行说明或 jar 旁附上述文件；勿声称 jar 内已含全部系统库许可 |
| VSIX | 含扩展依赖的 MIT 声明（languageclient）；附 `LICENSE` / notices |
| 用户原生可执行文件 | 由**用户**负责其应用与所链库；文档应提示 TLS/graphics 场景 |
| WASM + JS 壳 | 分发 `app.wasm`/`app.js`/HTML 时附项目许可；JS 壳若含第三方再列 notices |

## 当前无法确认的兼容性

1. 启用 TLS 的闭源应用 + OpenSSL 版本组合的归属是否已在文档中写清。  
2. `packages/openssl-tls` 元数据写 `Apache-2.0` 与 OpenSSL 本体许可易混淆。  
3. SQLite 是系统库还是随包二进制（见阻断项）。  
4. HPACK 测试表来源（见 quarantine）。
