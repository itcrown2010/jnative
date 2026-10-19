# Windows Explorer（JNative AOT）

用 **JNative** 重写的 Windows 11 风格文件资源管理器。源码是 Java 子集（`.java`），经 `jnative.jar` **AOT 编译成原生 EXE**，运行时 **不需要 Java / JRE**。

界面由 Java 在双缓冲画布（`PaintView` + GDI/GDI+）上自绘，复刻 Win11 的圆角 Fluent 外观（支持深色）；文件图标、回收站、剪贴板、右键菜单、属性等系统能力通过一个小的原生库 `jn_fm`（`native/jn_fm_windows.c`）调用 Shell API。

| 阶段 | 要不要 Java |
|------|-------------|
| 编译（`java -jar target/jnative.jar …`） | 需要（只作编译器宿主） |
| 运行 `WindowsExplorer.exe` | **不需要** |

## 已实现（第一版 core）

- 导航窗格：主页 / 常用文件夹 / 此电脑 / 驱动器，懒加载展开，拖动分隔条改宽度
- 地址栏：面包屑（点击跳转、`›` 弹出子文件夹菜单）+ 点击空白处进入文本编辑（`Ctrl+L` / `Alt+D` / `F4`）
- 后退 / 前进 / 上一级 / 刷新，导航历史
- 详细信息列表：名称 / 修改日期 / 类型 / 大小，点击列头排序，拖动列边框调宽；图标视图（`Ctrl+滚轮` 切换）
- 此电脑 / 主页：磁盘与文件夹的**磁贴**布局（容量条，低于 10% 变红）
- 多选（Ctrl / Shift / 框选）、拖放移动 / Ctrl 拖放复制到文件夹或导航项
- 命令栏：新建 / 剪切 / 复制 / 粘贴 / 重命名 / 删除 / 排序 / 查看 / 更多
- 行内重命名（`F2`，非法字符与重名提示），新建文件夹 / 文件后直接进入重命名
- 复制 / 剪切 / 粘贴使用系统 `CF_HDROP` 剪贴板（可与系统资源管理器互通），删除进回收站（`Shift+Del` 永久删除），由 Shell 显示进度与冲突对话框
- 右键菜单：Win11 风格常用项 + “显示更多选项”（调出完整的系统经典菜单）；属性（`Alt+Enter`）
- 搜索框（`Ctrl+E` / `Ctrl+F`）：在当前文件夹递归增量搜索（文件名包含关键字，不区分大小写）
- 状态栏：项目数、选中数与大小；目录变化监视自动刷新
- 隐藏项 / 扩展名选项
- 主题：跟随系统 / 浅色 / 深色，另有 6 种强调色（蓝 / 紫 / 绿 / 橙 / 红 / 粉）；入口在命令栏 `…` 菜单的“主题”“强调色”，也可按 `Ctrl+Shift+T` 循环切换主题
- 设置自动保存到 `%APPDATA%\JNativeExplorer\settings.ini`（主题、强调色、隐藏项、扩展名、视图模式），下次启动自动恢复

## 快捷键

| 操作 | 快捷键 |
|------|--------|
| 后退 / 前进 / 上一级 | `Alt+←` / `Alt+→` / `Alt+↑`，`Backspace` 后退 |
| 编辑地址 | `Ctrl+L` `Alt+D` `F4` |
| 搜索 | `Ctrl+E` `Ctrl+F` |
| 全选 / 复制 / 剪切 / 粘贴 | `Ctrl+A` `Ctrl+C` `Ctrl+X` `Ctrl+V` |
| 复制路径 | `Ctrl+Shift+C` |
| 重命名 / 删除 / 永久删除 | `F2` / `Del` / `Shift+Del` |
| 打开 / 属性 | `Enter` / `Alt+Enter` |
| 新建窗口 / 新建文件夹 / 关闭 | `Ctrl+N` / `Ctrl+Shift+N` / `Ctrl+W` |
| 刷新 | `F5` `Ctrl+R` |
| 详细信息 / 图标视图 | `Ctrl+Shift+6` / `Ctrl+Shift+2` |
| 切换主题（跟随系统 → 浅色 → 深色） | `Ctrl+Shift+T` |
| 更多选项（系统菜单） | `Shift+F10` |
| 按名称前缀跳转 | 直接键入字符 |

## 构建

Windows（需 Java 17+、LLVM `clang`/`ar`、`target\jnative.jar`）：

```powershell
mvn -DskipTests package
powershell -File examples/windows-explorer/scripts/build.ps1
.\build\windows-explorer\WindowsExplorer.exe [文件夹]
```

状态：**WINDOWS_E2E_UNVERIFIED**。开发机是 macOS，没有 PE 交叉链接，也无法在 Windows 上运行：Windows 路径只做过 MinGW 编译检查（`jn_fm_windows.c`、`jnative_ui_win32.c`）。以下行为必须在真实 Windows 10/11 上验收：GDI/GDI+ 绘制与 DPI、Shell 图标、`IContextMenu` 右键菜单、`CF_HDROP` 剪贴板互通、回收站与进度对话框、长路径、目录监视。

macOS / Linux（本机迭代）：

```bash
mvn -DskipTests package
./examples/windows-explorer/scripts/build.sh      # MinGW 编译检查 + 用 POSIX 替身构建代理程序
```

代理程序用 `tests/jn_fm_posix.c` 代替 `jn_fm`，把 `C:\a\b` 映射到 `$JNFM_ROOT/a/b`，`GDI` 相关绘制在 Cocoa 上是空实现，仅用于逻辑验证。

## 测试

```bash
bash examples/windows-explorer/tests/run.sh
```

无头驱动真实的应用逻辑（`FmApp.setup`），覆盖：格式化 / 路径 / 历史 / 排序 / 选择、搜索语法、导航与面包屑、新建与重命名（含非法和重名）、复制 / 剪切 / 粘贴 / 拖放、回收站、导航树、列表鼠标键盘与网格 / 磁贴几何、绘制冒烟。当前 234 项全部通过。

## 目录结构

| 文件 | 内容 |
|------|------|
| `src/FmCore.java` | 路径、格式化、条目、历史、文件夹模型（排序 / 选择） |
| `src/FmModel.java` | 本地化名称、导航树、与 `jn_fm` 的绑定 |
| `src/FmTheme.java` | 颜色（浅/深主题、强调色预设）、字体、尺寸、命中表 |
| `src/FmSettings.java` | 用户设置的读取与保存 |
| `src/FmList.java` | 列表控件：详细信息 / 图标 / 磁贴、鼠标键盘、框选、拖放 |
| `src/FmChrome.java` | 导航栏、命令栏、地址栏、导航窗格、状态栏的绘制 |
| `src/FmApp.java` | 应用状态、命令、菜单、输入分发 |
| `native/jn_fm_windows.c` | Shell 层（图标类型名、文件操作、剪贴板、IContextMenu、搜索、监视） |
| `tests/` | POSIX 替身、测试用例与运行脚本 |
