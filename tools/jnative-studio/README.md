# JNative Studio

用 JNative 自己写的 JNative 集成开发环境（IDE）：新建项目 → 拖拽设计界面 → 绑定事件 → 写代码 → 生成并运行，
全部在一个窗口里完成。作者：张晓达

Studio 本身就是一个 JNative 原生应用（`tools/jnative-studio`），也是对 UI / Graphics / Editor / IO 标准库的一次大规模实战。

## 启动

```sh
cd tools/jnative-studio
./studio.sh                 # 首次会自动编译 Studio，随后启动
./studio.sh --open <项目目录> # 启动并打开已有的 JNative 项目
./studio.sh --rebuild       # 强制重新编译 Studio
```

Studio 通过 JNative 编译器生成用户项目，编译器按以下顺序查找：`$JNATIVE_COMPILER`（`jnative.jar` 或 `target/classes`）→
`$JNATIVE_HOME/target/...` → 当前目录向上查找 `target/classes`、`target/jnative.jar`。找不到时在「设置」中手动指定。

## 功能

| 能力 | 说明 |
| --- | --- |
| 项目 | 新建项目（Hello 模板：窗体 + 事件处理 + `Main`）、打开项目、最近项目、资源管理器（新建窗体 / 类、删除、在 Finder 中显示） |
| 组件库 | 基本控件（Label / Button / TextField / TextArea / CheckBox / RadioButton / ComboBox / ListView / SegmentedControl / ProgressBar / SearchField / Canvas）和布局（VBox / HBox / GridPane / StackPane / Panel / ScrollView / SplitPane / TabPane）；搜索过滤，单击选取后点击画布放置，双击添加到当前容器，或直接拖到设计画布 |
| 设计画布 | 所见即所得：画布里就是真实的 JNative 控件，布局由真实的 VBox/HBox/GridPane 计算；选择、拖动、缩放（8 个手柄）、对齐吸附与辅助线、容器插入线、网格单元高亮、方向键微调、Delete 删除、右键菜单、撤销/重做（100 步） |
| 属性面板 | 按类型生成的分组编辑器（文本 / 数值 / 布尔 / 枚举 / 颜色 / 多行），实时写回；父容器相关的附加属性（Grid 的行列/跨度、绝对布局的 x/y、Tab 标题）自动出现；可滚动 |
| 文档大纲 | 控件树，与画布、属性面板三方同步选择 |
| 事件绑定 | 属性面板「事件」区为每个事件填写处理方法名并点 `↵`，或在画布上双击控件 / 右键「编写事件处理」，自动在代码里生成 `@Override void onXxx(...)` 并跳转 |
| 设备预览 | 桌面（窗体尺寸）/ 笔记本 / 平板 / 手机 / 小窗 预设尺寸；画布可滚动查看超出部分 |
| 实时预览 | 单独窗口按**同一份构建器**显示运行时效果（启用/可见属性生效），随每次编辑即时刷新 |
| 代码跳转 | 设计器 ⇄ 代码（⌘E）；事件 → 处理方法所在行；编译错误 → 问题列表 → 定位到文件行列 |
| 代码编辑 | 多标签 CodeEditor（语法高亮、行号、诊断下划线、清晰的中英文等宽字体、Retina） |
| 生成与运行 | ⌘B 生成 / ⌘R 生成并运行 / 停止；输出面板实时流式显示编译器与程序输出，问题面板解析 `file:line:col: error:` |

## 设计器如何工作

```
MainForm.jnui        设计文档（JSON，可读、对 diff 友好）— 设计器读写
MainFormBase.java    设计器生成（保存时覆盖）：字段、buildUi()、事件接线、空的虚方法
MainForm.java        你的代码（只生成一次）：extends MainFormBase，重写事件处理方法
Main.java            入口
```

- 控件字段（如 `btnLogin`、`txtUser`）在 `XxxBase` 中声明，业务代码直接使用。
- 事件在 `XxxBase` 里接线到同名的空方法；你在 `Xxx` 里 `@Override` 即可，永远不用改生成文件，重新设计不会丢失代码。
- 保存 `.jnui`（⌘S）或点「生成/运行」时会自动保存所有文档并重新生成 `XxxBase.java`。

## 目录

```
src/Ds*.java       设计器：DsMeta（组件目录）DsDoc（文档/撤销）DsNode DsGen（代码生成）DsBuild（运行时构建）
                   DsSurface（画布）DsPalette（组件库）DsProps（属性面板）DsDesigner（组合）DsPreview（实时预览）
src/Ide*.java      IDE 外壳：IdeApp（主窗口）IdeProject IdeProc（子进程流式输出）IdeTree IdeTabs IdeDialog
src/Ide.java       主题、字体、矢量图标、绘制辅助
studio.sh          启动脚本
```

## 已知限制

- 目前仅 macOS（Cocoa）完整可用；Windows 后端缺少抗锯齿系统字体与 Panel 裁剪/滚轮，Linux 无 GUI 后端。
- macOS 原生 `TabPane` 不显示标签条，设计器中的 TabPane 只能通过大纲选择各页；没有 Slider / Spinner / Image 控件。
- 设计画布是 1:1 渲染，没有缩放。
- 代码编辑器是基础版：无自动补全 / 重构 / 调试器；中文输入法组合输入未经验证。
- 生成按钮调用外部 `java` 运行编译器，需要本机可用的 JDK 和 clang 工具链。
