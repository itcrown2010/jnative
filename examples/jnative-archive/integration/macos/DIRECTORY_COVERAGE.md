# JNative Finder Sync — 目录覆盖说明

## 机制

扩展通过 `FIFinderSyncController.directoryURLs` 声明监视根目录。  
**不**注册 `$HOME` 或启动盘根 `/`（Apple 明确不建议；在本机还会导致菜单不出现）。

路径使用 `getpwuid` 得到的真实用户主目录拼接，避免沙盒把 `NSSearchPath` 指到容器内假 Desktop。

## 已注册（本机实测会注入菜单）

| 范围 | 说明 |
|------|------|
| `~/Desktop` | 本地桌面及子目录（含验收目录） |
| `~/Documents` | 文稿 |
| `~/Downloads` | 下载 |
| `~/Movies` `~/Music` `~/Pictures` `~/Public` | 常见用户目录 |
| `~/JNativeFinderMenuTest` 等白名单名 | `$HOME` 下已知工程/测试目录（不枚举整个 $HOME） |
| `~/Library/Application Support/JNativeArchive/finder-sync-roots.txt` | 主应用可写入的额外根（每行一个绝对路径） |
| `/Volumes/*`（非启动卷） | 外接磁盘等已挂载卷 |

本机验收：`~/JNativeFinderMenuTest`、`~/Desktop/JNativeFinderSyncTest` 右键均出现「JNative」。

## 明确不覆盖 / 可能失效

| 位置 | 原因 |
|------|------|
| 启动盘根 `/` 及整盘任意路径 | 未注册；避免性能问题与系统限制 |
| `$HOME` 本身作为根 | 刻意不注册 |
| `$HOME/Library` | 未注册（系统区域） |
| iCloud「桌面与文稿」同步后的云端视图 | 由其他扩展/系统接管；`menuForMenuKind` 可能不被调用（见 FB13109005 同类问题） |
| 其他 App 沙盒容器内部 | 非用户可见目录，不注册 |

**不要**简单声称「全盘支持」。未在上表的路径：把绝对路径写入 `finder-sync-roots.txt` 后重启扩展，或把文件放到已覆盖目录。

## 沙盒与通信

- Appex **必须**沙盒。JN1 任务写入 appex 容器内  
  `~/Library/Containers/io.jnative.examples.archive.findersync/Data/Library/Application Support/JNativeArchive/shell-inbox/`  
  （temporary-exception 写真实 `~/Library/...` 在本机不可靠）。
- 主应用（非沙盒）轮询：主 inbox + 容器 inbox + `/Users/Shared/JNativeArchive/shell-inbox`。
- 密码只在主应用窗口收集，不进入 job / 参数 / 日志。

## 签名与启用

- 开发签名：`Apple Development`；appex 与 host 同 Team。
- 启用：`pluginkit -e use -i io.jnative.examples.archive.findersync`  
  或 系统设置 → 通用 → 登录项与扩展 → 文件提供程序 → JNative。
- 应用内一次性引导，写 `finder-extension-guide-shown` 后不再弹。
