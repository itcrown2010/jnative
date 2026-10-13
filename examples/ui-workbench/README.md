# JNative UI 工作台

展示桌面 UI 公共能力：布局、工具栏、表格（稳定行 ID）、趋势图、对话框、后台→UI 线程更新。

## 构建与运行（macOS）

```bash
mvn -q -DskipTests package
examples/ui-workbench/scripts/build.sh
./build/ui-workbench/UiWorkbench
open build/ui-workbench/UiWorkbench.app
```

演示菜单/快捷键、辅助窗口、文件打开保存、未保存关闭确认。

截图：`docs/screenshots/workbench-light.png`

教程：`docs/tutorial/chapters/desktop-ui-framework.md`、`desktop-ui-app-shell.md`
