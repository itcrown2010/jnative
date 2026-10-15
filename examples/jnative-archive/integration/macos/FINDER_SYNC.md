# Finder Sync「JNative」右键菜单

## 源码

| 路径 | 作用 |
|------|------|
| `integration/macos/FinderSync/JNativeFinderSync.m` | `menuForMenuKind`、选中分类、JN1 投递 |
| `integration/macos/FinderSync/Info.plist` | `CFBundleDisplayName=JNative`，`com.apple.FinderSync` |
| `integration/macos/FinderSync/*.entitlements` | 沙盒 + inbox 例外 |
| `integration/macos/FinderSync/build-appex.sh` | 编译 / 签名 / 嵌入 `PlugIns/` |
| `src/gui/AgShell.java` | 轮询主 inbox + appex 容器 inbox |
| `integration/macos/install.sh` | 安装 app、启用 appex、默认移除旧 Automator |

## 构建与安装

```bash
cd examples/jnative-archive
./scripts/build-gui.sh          # 打包 app + 嵌入 appex
JN_ARCHIVE_INSTALL_YES=1 ./integration/macos/install.sh
open -a JNativeArchive          # 若需一次性启用引导
```

可选兼容旧菜单：`JN_ARCHIVE_INSTALL_AUTOMATOR=1`。

卸载：`./integration/macos/uninstall.sh`（只删本项目 Automator 名与 appex）。

## 菜单行为

Finder 右键第一层：**JNative** → 子菜单：

- **压缩为 ZIP**：后台 standard 压缩（无主窗、无创建表单）
- **加密压缩…**：仅密码框
- **压缩选项…**：完整创建表单
- 全为 ZIP：打开压缩包（单选）、解压到当前位置 / 同名文件夹 / 解压到…
- 混合选中：仅显示可执行项（压缩始终可；解压仅全 ZIP）

快捷任务不抢 Finder 焦点；>1s 显示进度浮窗。详见 `build/finder-sync-verify/QUIET_SHELL.md`。

扩展只写 JN1 并唤醒主应用，不在 appex 内压缩或要密码。

## 目录覆盖

见同目录说明副本，以及验收目录  
`build/finder-sync-verify/DIRECTORY_COVERAGE.md`。
