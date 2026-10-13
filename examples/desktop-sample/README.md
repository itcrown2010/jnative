# Desktop Sample（生命周期参考）

规范结构的最小桌面应用：命令/菜单、双向绑定、后台任务（`UiThread.coalesce`）、未保存确认、`BindingScope` 释放、`.app` 打包。

## 构建

```bash
mvn -q -DskipTests package
examples/desktop-sample/scripts/build.sh
./build/desktop-sample/DesktopSample --self-test
open build/desktop-sample/DesktopSample.app
```

期望 self-test 输出含 `DS_SELF_TEST_OK`（关窗后原生句柄下降）。

用户数据：`build/desktop-sample/note.txt`（不在 `.app` 内）。

- 教程：`docs/tutorial/chapters/desktop-ui-stability.md`
- 能力矩阵：`docs/desktop-ui-capability-matrix.md`
- 交付：`docs/desktop-ui-stability-delivery.md`
