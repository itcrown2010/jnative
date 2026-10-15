# JNative Archive（阶段 B）

## 包结构（Java 风格 import）

- `src/java/util/zip/` — JDK 兼容子集：`ZipEntry`、`ZipFile`、`ZipInputStream`、`ZipOutputStream`、`CRC32`、`ZipException`
- `src/org/jnative/archive/` — 任务/密码/覆盖策略/`ZipExtensions`（AES 等扩展）
- `src/org/jnative/archive/internal/ZipNative.java` — native 绑定（内部）
- 构建：`scripts/build.sh` 多源码文件编译，**不再拼接**源码

示例：`examples/ZipStyleDemo.java`（`scripts/build-zip-style-demo.sh`）


跨平台压缩客户端：原生 `jn_zip`（minizip-ng 4.0.8），无 JVM，支持普通 ZIP 与 WinZip AES。

```bash
scripts/build.sh
scripts/regress-security.sh
scripts/build-api-probe.sh && ./build/archive-api-probe
scripts/smoke-phase-b.sh
scripts/accept-core.sh          # 目录/冲突/取消/互操作/性能
./build/jn-archive version
# 文件 + 目录（保留相对结构；符号链接默认拒绝）
./build/jn-archive --profile standard create /tmp/a.zip testdata/hello.txt some-dir/
./build/jn-archive list /tmp/a.zip
./build/jn-archive extract /tmp/a.zip /tmp/out
# 冲突：fail|skip|replace|rename|ask（--batch 禁止 ask 等待）
./build/jn-archive --overwrite skip --batch extract /tmp/a.zip /tmp/out
# AES（密码仅经 fd / TTY，禁止 argv；TTY 创建时确认两次）
printf 'secret' | ./build/jn-archive --password-fd 0 --encrypt create /tmp/a-aes.zip testdata/hello.txt
printf 'secret' | ./build/jn-archive --password-fd 0 test /tmp/a-aes.zip
```

业务接口（CLI/GUI 共用）：`ArchiveOps` + `ArchiveTask`（见 `src/ArchiveOps.java`）。  
界面只调用这些接口，不在按钮回调里直接读写文件或跑压缩循环。

### 桌面 GUI（macOS 已实测启动）

```bash
scripts/build-gui.sh
./build/jn-archive-gui
# 或
open ./build/JNativeArchive.app
# 验收：打开示例包并截图 + CLI 回归
scripts/accept-gui.sh
```

截图见 `build/gui-shots/`。Windows GUI：**WINDOWS_GUI_UNVERIFIED**。

### Finder / Explorer 右键集成

系统集成层只写 JN1 长度前缀 job 文件并 `open -a` 启动 `.app`；压缩解压仍走 `ArchiveOps` / `ArchiveTask`。密码仅在客户端窗口输入，**永不写入 job / argv**。

**macOS**

- 菜单位置：Finder **右键 → 快速操作** → `JNative Archive …`（也可在 **Finder → 服务**；不是顶层全局菜单）
- 最低系统：macOS 11.0+；依赖：系统 `libz` / `libiconv` / Cocoa（`jn_zip` 静态链接）
- **签名/公证：未完成** — 仅本地/侧载，Gatekeeper 可能提示无法验证开发者
- 构建：`scripts/build-gui.sh` → `build/JNativeArchive.app`
- 分发包：`scripts/dist-macos.sh` → `build/dist/JNativeArchive-macos-*.zip`
- 安装到 `~/Applications` 并注册菜单：

```bash
JN_ARCHIVE_INSTALL_YES=1 ./integration/macos/install.sh
# 或分发包内: JN_ARCHIVE_INSTALL_YES=1 ./install-from-dist.sh
```

- 卸载（只删本项目 App + Quick Actions；默认保留用户设置）：

```bash
JN_ARCHIVE_INSTALL_YES=1 ./integration/macos/uninstall.sh
# 连设置/inbox 一起清: JN_ARCHIVE_PURGE_USER_DATA=1 JN_ARCHIVE_INSTALL_YES=1 ./integration/macos/uninstall.sh
```

- 不接管系统默认 ZIP 关联（`LSHandlerRank=Alternate`）
- Finder 原生菜单：右键 → **JNative** → 压缩/解压（Finder Sync `.appex`，见 `integration/macos/FINDER_SYNC.md`）
- 可选旧 Automator：`JN_ARCHIVE_INSTALL_AUTOMATOR=1`
- 验收截图：`build/finder-sync-verify/`；目录覆盖：`integration/macos/DIRECTORY_COVERAGE.md`

**Windows**

| 能力 | 状态 |
|------|------|
| 经典右键菜单（HKCU） | 脚本已提供：`integration/windows/install-classic-context-menu.ps1` — **WINDOWS_E2E_UNVERIFIED** |
| Windows 11 现代顶层菜单 | **FUTURE**（未实现，不用经典菜单冒充） |

见 `integration/windows/README.md`。

文档：

- [阶段 B 报告](../../docs/jnative-archive-phase-b.md)
- [阶段 A 报告](../../docs/jnative-archive-phase-a.md)
- [阶段 B–E 计划](../../docs/jnative-archive-phases-b-e.md)
- [第三方说明](../../docs/jnative-archive-THIRD_PARTY.md)

Windows：`scripts/build.ps1`（**WINDOWS_E2E_UNVERIFIED**）。
