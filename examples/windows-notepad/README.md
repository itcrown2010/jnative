# Windows Notepad（JNative AOT）

用 **JNative** 写的 Windows 记事本：源码是 Java 子集语法（`.java`），经 `jnative.jar` **AOT 编译成原生可执行文件**。
运行时 **不需要 Java / JRE**。界面是真正的 Win32 控件（原生菜单栏、`EDIT` 编辑框、通用文件对话框、TaskDialog、`ChooseFont`、打印对话框），行为对照系统自带的经典记事本（Windows 10 外观）。

| 阶段 | 要不要 Java |
|------|-------------|
| 编译（`java -jar target/jnative.jar …`） | 需要（只作编译器宿主） |
| 运行 `Notepad.exe` / `Notepad` | **不需要** |

## Windows 上编出 `.exe`

```powershell
# 仓库根目录
mvn -DskipTests package
powershell -File examples/windows-notepad/scripts/build.ps1
.\build\windows-notepad\Notepad.exe [文件]
```

状态：**WINDOWS_E2E_UNVERIFIED**（开发机是 macOS，Windows 路径只做过 MinGW 编译检查，需在真实 Windows 10/11 上验收）。

## macOS 上的代理构建（本机迭代）

```bash
mvn -DskipTests package
./examples/windows-notepad/scripts/build.sh
./build/windows-notepad/Notepad
```

产物是 `Mach-O` 原生可执行文件（Cocoa 后端代理 Win32 后端），**不是** JAR。字体对话框、编码下拉、打印仅 Windows 提供。

## 测试

```bash
S=examples/windows-notepad/src
java -jar target/jnative.jar $S/NpCodec.java $S/NpUi.java $S/NpWindow.java \
  $S/NpFindDialog.java $S/NpGotoDialog.java $S/NpTests.java -o build/windows-notepad/NpTests --app-type gui
./build/windows-notepad/NpTests        # 编码 / 换行 / 工具函数
./build/windows-notepad/NpTests ui     # 另外驱动真实窗口：打开、编辑、查找、替换、转到、缩放、保存
```

## 已实现的记事本功能

- **文件**：新建、新建窗口、打开、保存、另存为、页面设置、打印、退出；未保存时“保存 / 不保存 / 取消”提示；标题 `*文件名 - Notepad`；命令行参数打开文件（不存在时询问是否创建）。
- **编码**：ANSI、UTF-16 LE/BE、UTF-8、UTF-8 with BOM；打开时自动检测（BOM、无 BOM 的 UTF-16、合法 UTF-8，否则 ANSI）；另存为对话框里有“编码”下拉；ANSI 会丢字符时给出警告。
- **换行**：检测并保留 CRLF / LF / CR，状态栏显示 `Windows (CRLF)` / `Unix (LF)` / `Macintosh (CR)`。
- **编辑**：撤销、剪切、复制、粘贴、删除、全选、时间/日期（F5）、用 Bing 搜索（Ctrl+E）；菜单项随选区/剪贴板/撤销栈实时启用或灰显。
- **查找**：查找（Ctrl+F，含向上/向下、区分大小写、循环查找）、查找下一个（F3）、查找上一个（Shift+F3）、替换（Ctrl+H，含全部替换）、转到（Ctrl+G，超出行数提示）。
- **格式 / 查看**：自动换行、字体（系统字体对话框）、缩放（Ctrl+加号 / 减号 / 0、Ctrl+滚轮，10%–500%）、状态栏（`Ln, Col` | 缩放 | 换行符 | 编码；自动换行开启时隐藏，与 Windows 10 一致）。
- **帮助**：查看帮助、发送反馈、关于。
- **快捷键**：由 Win32 原生加速表处理，菜单里显示原生加速键文字。

## 已知差异 / 限制

- 对照的是经典（Windows 10）记事本，没有 Windows 11 的标签页、深色主题自动跟随。
- 字体、自动换行、状态栏开关、查找参数只在进程内保存，不写注册表。
- 切换自动换行会重建 `EDIT` 控件（Win32 的限制），撤销历史会清空。
- “新建窗口”在同一进程里开新窗口，而不是新进程。
- ANSI 代码页转换用“字节串”传递，文件中的 NUL 字节会被替换为空格显示（`EDIT` 本身也不能存 NUL）。
- 页眉只打印文件名、页脚只打印页码；页面设置对话框只有边距/纸张，没有页眉页脚模板。

## 源码

| 文件 | 作用 |
|------|------|
| `NpCodec` | 编码检测 / 编解码 / 换行规范化 |
| `NpUi` | 布局、URL 编码、数字解析等小工具 |
| `NpWindow` | 主窗口：菜单、编辑框、状态栏、文档状态、查找替换逻辑 |
| `NpFindDialog` / `NpGotoDialog` | 查找 / 替换 / 转到 对话框 |
| `NpApp` | 入口 |
| `NpTests` | 无头测试 + 窗口驱动测试 |
| `scripts/build.ps1` | Windows → `Notepad.exe` |
| `scripts/build.sh` | macOS 代理 + MinGW UI 编译检查 |

## 平台层新增能力

为了让记事本和系统自带的一致，补了 Win32 / Cocoa 后端和 `UiLib`：原生菜单栏 + 加速表（`MenuItem` 的 `shortcutLabelOnly`）、`FileDialogEx`（过滤器、默认文件名、编码下拉）、`FontDialog`、`Dialogs.show`（保存/不保存/取消、警告等）、`TextArea` 的选区/撤销/行/字体/自动换行接口、代码页转换、`Window.setDialogStyle`、页面设置与打印、Ctrl+滚轮缩放事件。
