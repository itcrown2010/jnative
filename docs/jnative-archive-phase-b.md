# JNative Archive — 阶段 B 报告

作者：张晓达

## 目标

在阶段 A 引擎之上完成：**ZIP AES、ZIP64/大条目数、解压临时发布、任务进度/取消、错误分类与解压安全**。运行时仍为随应用静态链接的 `jn_zip`（minizip-ng **4.0.8**），不依赖 JVM，不 `exec` 系统 zip/unzip/7z。

## 阶段 A 复核（本轮实测）

| 检查 | 结果 |
|------|------|
| CLI create/list/test/extract | 通过 |
| SHA-256 本程序自压自解 | 一致 |
| `unzip -t` 读本程序包 | 通过 |
| 系统 `zip` → CLI extract + SHA-256 | 一致 |
| `otool`/`nm` 含 `jn_zip_*` | 确认原生引擎 |

## 源码与构建

| 组件 | 路径 |
|------|------|
| C ABI | `examples/jnative-archive/native/jn_zip.h`, `jn_zip.c` |
| ZIP API | `examples/jnative-archive/src/ZipLib.java` |
| 任务模型 | `examples/jnative-archive/src/ArchiveTask.java` |
| CLI | `examples/jnative-archive/src/JnArchiveCli.java` |
| 构建 | `scripts/build.sh` / `build.ps1` / `fetch-deps.sh` |
| 验收 | `scripts/smoke-phase-b.sh` |
| 依赖 | minizip-ng 4.0.8，`MZ_WZAES=ON`，`MZ_PKCRYPT=OFF` |

```bash
examples/jnative-archive/scripts/build.sh
examples/jnative-archive/scripts/smoke-phase-b.sh   # 默认含 70000 条目
# 可选 >4GiB：ZIP64_LARGE=1 examples/jnative-archive/scripts/smoke-phase-b.sh
```

## 加密与兼容（非界面口号）

| 项 | 实际 |
|----|------|
| 变体 | **WinZip AES**（minizip-ng `mz_strm_wzaes`，AE-1/AE-2） |
| 默认强度 | **AES-256**（`MZ_AES_STRENGTH_256`）；可选 128/192（`--aes`） |
| 密码编码 | **UTF-8**（FFI CString） |
| ZipCrypto | **关闭**（构建 `MZ_PKCRYPT=OFF`；设密码时强制 AES，禁止降级） |
| 文件名保密 | **ZIP 模式不支持**；中央目录条目名可见。文件名加密留给经验证的 7z（阶段 E） |
| 空密码 | **拒绝**（不静默生成明文包） |
| CLI 密码通道 | `--password-fd N` 或交互 TTY（`getpass`）；**禁止 argv / 日志 / 历史** |
| 互操作 | 本程序 AES ↔ 同版本 `minizip -s -p` 双向通过 |

错误密码与认证/完整性失败：引擎常无法区分 → 统一报告 **`password or integrity/auth verification failed`**（`JN_ZIP_ERR_PASSWORD`），不伪造精确诊断。

## API / 契约

### 加密扩展（不改变无密码调用语义）

- `ZipOutputStream.setPassword` / `setAesStrength`
- `ZipInputStream` / `ZipFile.setPassword`
- `ZipEntry.isEncrypted` / `getAesStrength` / `needsPassword` / `isSymlink`
- `ZipFile.extractAllEx(dest, overwrite, limits)`

### 流式认证

- `read()` 得到的部分数据 **不等于** 验证成功。
- 条目认证/CRC 在 `closeEntry` / `extract_entry_ex` 发布前完成；`ZipFileEntryStream.close()` 传播 CRC/认证失败。
- 解压：`mkdtemp` 独占 staging + `mkstemp`/`O_NOFOLLOW` 写临时文件 → `close_entry` 成功 → `renamex_np(RENAME_EXCL)` / 原子 rename 发布；失败删除临时、**不先删已有目标**（`OVERWRITE_FAIL`）。
- `ZipFile` 单游标：同时只允许一个 entry stream；打开第二个会被拒绝且不破坏第一个。
- `ZipOutputStream.finish()`：写入中央目录后文件即为完整 ZIP；后续 `close()` 不再重复写 CD。

### 覆盖策略

`fail` | `skip` | `replace` | `rename`（CLI `--overwrite`）。

### 任务模型（GUI 将复用）

`ArchiveTask`：`WAITING → PREPARING → RUNNING → COMPLETED|FAILED|CANCELLED`  
取消请求 ≠ 已取消：须引擎停写、句柄收尾、临时清理后才报 `CANCELLED`。  
进度：当前文件、bytes in/out、entries、速度、总量已知时的百分比（完成前不超过 99%）。通知建议限频（`snapshot` 100ms）。

### 错误码（稳定 ABI）

`NEED_PASSWORD` / `PASSWORD`（含认证） / `FORMAT` / `CRC` / `PATH` / `LINK` / `LIMIT` / `SPACE` / `SOURCE` / `TARGET` / `CANCELLED` / `UNSUPPORTED` / …

异常不跨 C ABI；仅错误码 + `jn_zip_strerror`。

## ZIP64 / 大文件

- 全链路尺寸与偏移为 **int64**（JNative `long`、C `int64_t`、进度统计）。
- 流式缓冲固定 **64KiB**，禁止整文件载入。
- **已测**：70000 空条目 ZIP64 EOCD，`test` 约 1.7s，峰值 RSS ≈ **28MB**（macOS `/usr/bin/time -l`）。
- **已测**：2MiB 高可压缩 + 2MiB 确定性低可压缩，压缩/解压 `diff` 一致。
- **>4GiB 单文件**：脚本支持 `ZIP64_LARGE=1`；本轮默认跳过（磁盘/时间），标记为可选验收。
- 归档符号链接条目：**拒绝**（`JN_ZIP_ERR_LINK`）；目标路径上已有 symlink 组件亦拒绝。

## 验收摘要（macOS arm64，本轮审查修复后复测）

| 项 | 结果 |
|----|------|
| 普通 ZIP 双向哈希 | PASS |
| AES-256 双向 ↔ minizip CLI | PASS |
| 错误/空密码 | PASS（无成功产物） |
| `../` 路径逃逸 | PASS |
| 覆盖 fail 保留原文件 | PASS |
| symlink staging 劫持 | PASS（`regress-security.sh`） |
| 密码选项脱敏 / 空密码清理临时 | PASS |
| Zip API finish / 单流限制 / CRC close | PASS（`archive-api-probe`） |
| 2MiB LCG 噪声压缩比 ≈ 1.0 | PASS（非 256 字节周期） |
| 截断归档 | FAIL（预期） |
| 70000 条目 ZIP64 | PASS（峰值 RSS ≈ 28MB） |
| 写中途取消（spike） | PASS |
| >4GiB 单文件 | **SKIPPED**（`ZIP64_LARGE=1` 可选） |
| Windows 主机 smoke | **WINDOWS_E2E_UNVERIFIED** |

## 剩余限制

- 交互密码依赖 TTY/`getpass`；无图形密码框（阶段 C / GUI）。
- `ZipFile` 尚不支持交错双 entry stream（明确拒绝第二流）。
- 后台线程池与 UI 投递尚未接 GUI；进度/取消以 native 互斥统计为准，CLI 为单线程驱动。
- 磁盘满用 `ENOSPC` 映射；受控注入需 `ulimit`/专用卷，未填满用户磁盘。
- 重复条目/大小写冲突策略：依赖覆盖策略；未做完整大小写折叠表。
- Windows：`build.ps1` 已纳入 `ArchiveTask`；主机端到端未跑，不得写成“Windows 运行通过”。
- 本轮未做 Finder/Explorer 右键菜单与 7z。

## 阶段 C 门槛

阶段 B 任务模型与 `ZipLib`/`jn_zip` 为唯一压缩逻辑；GUI 只绑定 `ArchiveTask` 进度/取消，不再另写引擎。
