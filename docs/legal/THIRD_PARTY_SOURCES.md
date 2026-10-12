# 第三方来源清单（审阅稿）

分类：**开发时** / **运行时链接** / **随产物分发（源码或目标码）**。  
调用 `clang`、本机 JDK、Xcode **不等于**其代码被纳入仓库或用户产物。

未知来源项标为 **发布阻断候选**，不得猜测许可或删除他人声明。

---

## A. 仓库内嵌 / 随发行物打包

| 名称 | 版本/标识 | 来源 | 许可证 | 位置 | 是否修改 | 类别 | 需保留声明 |
|------|-----------|------|--------|------|----------|------|------------|
| Gradle Wrapper 脚本 | 随 Android 示例 | Gradle 项目 | Apache-2.0 | `examples/**/mobile/android/gradlew`（及 sudoku/guide-tasks/mobile calculator 等） | 未改许可头 | 开发/示例构建 | 保留文件内 Apache 头；可附 `third_party_licenses/Apache-2.0.txt` |
| Gradle Wrapper JAR | wrapper | Gradle | Apache-2.0 | `**/gradle/wrapper/gradle-wrapper.jar` | 二进制 | 开发/示例 | 同上 |
| vscode-languageclient（及传递依赖） | ^9.0.1 | Microsoft / npm | MIT | `tools/vscode-jnative/node_modules/`（打进 VSIX） | 未改源 | **随 VSIX 分发** | VSIX/NOTICE 中保留 Microsoft MIT 声明 |
| VitePress | ^1.6.3 | Vue 生态 | MIT（以 npm 包为准） | `website/` 开发依赖 | 未 vendoring 进源码树为主 | **网站构建开发时** | 文档站构建说明中注明；静态站是否再打包依赖需发布时核对 |

## B. 系统 / 包管理器库（链接，非源码拷贝）

| 名称 | 如何使用 | 许可证（库本体） | 位置/证据 | 类别 | 说明 |
|------|----------|------------------|-----------|------|------|
| OpenSSL | `#include <openssl/...>`，链接 `-lssl` 等 | OpenSSL / Apache-2.0（视版本） | `jnative_tls.c`；`JNativeCompiler` Homebrew 探测 | **运行时动态/静态链接进启用 TLS 的用户程序** | 仓库**未** vendoring OpenSSL 源码；分发含 TLS 的二进制时需遵守 OpenSSL 归属要求 |
| zlib | `#include <zlib.h>` | zlib License | `jnative_graphics.c` | **链接进启用 graphics 的用户程序** | 未 vendoring zlib 源 |
| 系统 Security.framework / BCrypt | CSPRNG | 平台专有 | `jnative_crypto.c` | 运行时 API | 非开源拷贝 |
| SQLite | 包元数据/FFI 演示；驱动为 JNative 侧封装 | 通常 public domain / blessing | `packages/sqlite-native`、`SqliteDriverLib.java` | 运行时依赖本机/外部 lib | **阻断候选**：需确认是否随包分发 sqlite 二进制；当前未见完整 sqlite 源码树 |
| clang / LLVM | 主机工具链 | Apache-2.0 with LLVM exceptions 等 | doctor/PATH | **仅开发/编译工具** | 不纳入用户产物源码 |

## C. 可能源自规范或其它实现、需人工核对

| 名称 | 说明 | 位置 | 风险 |
|------|------|------|------|
| HPACK Huffman 表与编解码 | 标注为 RFC 7540/7541 自包含测试辅助；表数据常见于多种实现 | `src/test/java/io/jnative/http2/Hpack031.java` | **待确认**是否仅依 RFC 独立编写；若移植自某项目需补来源与许可 |
| PNG 编码器逻辑 | 自写 raster + 调 zlib | `jnative_graphics.c` | 确认未嵌入 libpng/stb 源 |
| HTTP/2、TLS 协议逻辑 | 自研 Java/C 封装 | stdlib / native | 协议本身非版权对象；实现需保持自有或标明移植 |

## D. 示例与数据

| 名称 | 说明 | 类别 |
|------|------|------|
| ERP 演示数据 / seed | `examples/erp/**` | 示例；确认无真实个人数据后再公开 |
| HTTPS 演示证书 | `examples/https/certs/server.crt`（已跟踪）；`server.key`（工作区存在、**未**见入 Git 历史） | 演示用；私钥不得入库 |
| Docker Compose 演示口令 | `examples/erp/docker-compose.yml`、`examples/music/docker-compose.yml` 含明文演示口令 | 开发示例；公开前应改为占位符并文档说明 |

## E. 明确排除

| 项 | 原因 |
|----|------|
| `tools/vscode-jnative/.vscode-test/**` 内 VS Code 应用与字体 | 本地测试宿主，**不应**随仓库公开或进源码发行包 |
| `website/node_modules`、`target/`、各类 `build/` | 构建产物 / 依赖缓存 |
| 调用 clang 编译用户代码 | 工具调用，非拷贝 LLVM 源码 |

---

## 维护规则

1. 新增第三方拷贝必须更新本清单与 `THIRD_PARTY_NOTICES.md`。  
2. 修改第三方文件须保留原版权头，并在清单「是否修改」栏记录。  
3. 不明项进入 `quarantine/` 说明，**禁止**猜测 SPDX。
