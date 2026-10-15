# JNative Archive — 阶段 A 报告

作者：张晓达

## 目标（本轮）

可运行的 **ZIP 引擎 + JDK 风格子集 API + CLI**，原生链接 minizip-ng，不依赖运行时 JVM，不调用用户机器上的 `zip`/`unzip`/`7z` 作为实现（仅用系统 `unzip`/`zip` 做互操作证据）。

## 源码路径

| 组件 | 路径 |
|------|------|
| C ABI | `examples/jnative-archive/native/jn_zip.h`, `jn_zip.c` |
| 引擎 spike | `examples/jnative-archive/native/jn_zip_spike.c` |
| JNative ZIP API | `examples/jnative-archive/src/ZipLib.java` |
| CLI | `examples/jnative-archive/src/JnArchiveCli.java` |
| 依赖 | `examples/jnative-archive/third_party/minizip-ng`（**4.0.8**） |
| 构建 | `examples/jnative-archive/scripts/{fetch-deps,build,smoke-phase-a}.sh`, `build.ps1` |
| 夹具 | `examples/jnative-archive/testdata/` |
| 后续计划 | `docs/jnative-archive-phases-b-e.md` |
| 第三方说明 | `docs/jnative-archive-THIRD_PARTY.md` |

## 构建与运行（macOS，已验证路径）

```bash
# 编译器宿主（仅构建期需要 JVM）
mvn -q -DskipTests package

# 依赖 + jn_zip + CLI
examples/jnative-archive/scripts/build.sh

# 功能烟雾 + 与系统 unzip/zip 互操作
examples/jnative-archive/scripts/smoke-phase-a.sh
```

产物：`examples/jnative-archive/build/jn-archive`、`libjn_zip.a`、`jn_zip_spike`。

Windows：`scripts/build.ps1`（**WINDOWS_E2E_UNVERIFIED**，直至在 Windows 主机跑通 smoke）。

## 引擎验证（spike）

`jn_zip_spike` 覆盖：

- 普通 ZIP 创建（Deflate + Store 流式条目）
- 读取、CRC 测试、解压；Unicode 条目名
- 系统 `unzip -t` 互操作
- ZIP AES 加解密烟雾（密码不进 argv；错误密码失败）——能力留给阶段 B 产品化

构建配置（`fetch-deps.sh`）：`MZ_WZAES=ON`，`MZ_PKCRYPT=OFF`（禁止 ZipCrypto 静默降级），静态库，系统 zlib；macOS 使用 Apple CommonCrypto。

## 已实现 API（兼容子集，非完整 JDK）

| 类型 | 能力 | 限制 |
|------|------|------|
| `CRC32` | `update` / `getValue` / `reset` | 经 `jn_zip_crc32`；非整文件假流式 |
| `ZipEntry` | name, size, compressedSize, crc, method, dir/encrypted | 时间字段预留 |
| `ZipOutputStream` | `putNextEntry` / `write` / `closeEntry` / `finish` / `close` | Store/Deflate；`finish`≠`close`（close 写中央目录）；close 幂等 |
| `ZipInputStream` | `getNextEntry` / `read` / `closeEntry` / `close` | 顺序读；EOF→null；条目结束 read→-1 |
| `ZipFile` | `entries` / `getEntry` / `getInputStream` / `extractAll` / `test` / `close` | 随机定位靠 minizip locate |
| `ZipFileEntryStream` | 读当前条目；close 只关条目 | **不**关闭父 `ZipFile` |

扩展：`setPassword`（null 清除；空串拒绝）。加密产品语义见阶段 B。

### 语义约定（阶段 A）

- **EOF**：`ZipInputStream.getNextEntry`→`null`；`read` 在条目末尾→`-1`（`JN_ZIP_ERR_EOF=-9`）。
- **空文件 / 目录条目**：支持；目录经 `is_dir`；解压路径安全拒绝 `..`、绝对路径、盘符、UNC。
- **所有权**：`ZipOutputStream`/`ZipInputStream`/`ZipFile` 拥有归档句柄；`ZipFileEntryStream` 不拥有父句柄。
- **finish vs close**：finish 关闭未完条目；close = finish + 写中央目录 + destroy（幂等）。
- **Store**：由引擎写局部头/描述符；应用层无需预填 CRC（与严格 JDK Store 预声明不同——已文档化差异）。
- **ZIP64 / 非寻址流**：引擎侧 ZIP64 随 minizip-ng；阶段 A CLI 未做 >4GiB 专项验收（阶段 B）。
- **不支持方法**：非 Store/Deflate → `JN_ZIP_ERR_UNSUPPORTED`。

CLI 创建走 `writer_add_file`（引擎内流式读盘），输出先写 `*.jn-tmp.zip` 再 `rename` 发布。

## 限制（诚实）

- 未宣称完整 `java.util.zip` 兼容；无独立 Deflater/Inflater 公有类（压缩在引擎内）。
- 阶段 A 产品面为无密码 ZIP；AES 仅 spike/ABI 就绪。
- GUI、右键、7z、任务取消 UI：**未做**（见 B–E）。
- **Windows 运行证据未在本机取得**；不得把「Mac 编译成功」写成 Windows 已验证。
- 互操作证据使用系统 `unzip`/`zip`，**运行时实现不依赖它们**。

## 证据（macOS arm64，本轮实测）

| 检查 | 结果 |
|------|------|
| `jn_zip_spike` | `SPIKE_PASS`（plain ZIP、Unicode、Store 流式、`unzip -t`、AES 正误密码） |
| CLI `version` | `minizip-ng 4.0.8` |
| CLI create/list/test/extract | 空文件 + 中文名 + 文本；内容 `diff` 一致 |
| 外部 `unzip -t` 读本程序包 | No errors detected |
| 系统 `zip` 包 → CLI test/extract | `PHASE_A_SMOKE_PASS` |
| Windows EXE | **未验证**（`WINDOWS_E2E_UNVERIFIED`） |

产物目录：`examples/jnative-archive/build/phase-a-smoke/`
