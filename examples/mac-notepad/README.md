# Mac 记事本（JNative Studio 开发）

用 JNative Studio 的窗体设计器开发的 macOS 记事本：`src/MainForm.jnui` 是设计器文件，
`MainFormBase.java` 由 Studio 生成（不要手改），`MainForm.java` 是手写逻辑。

## 功能

- 新建 / 打开 / 存储 / 存储为（⌘N ⌘O ⌘S ⇧⌘S），未保存时关闭窗口、退出（⌘Q）都会询问
- 保留文件原有的 CRLF 换行与 UTF-8 BOM，保存时原样写回；含 NUL 字节的文件打开前会确认
- 原生文本视图：撤销/重做、拼写检查、输入法、系统服务全部可用
- 查找 / 替换栏（⌘F 展开，⌘G / ⇧⌘G 上下查找，⌥⌘F 替换，Esc 关闭），区分大小写，匹配计数 `i / n`，到头自动回绕
- 转到行（⌘L）、转大写 / 小写、排序所选行
- 格式：自动换行、等宽字体、放大 / 缩小 / 实际大小（⌘= ⌘- ⌘0），偏好自动记忆
- 状态栏：行/列、选中字符数、总字符/行数、编码与换行格式

## 构建与运行

```sh
java -cp ../../target/classes io.jnative.Main build --project .
./.jnative/build/MacNotepad [文件]
./package.sh          # 生成 MacNotepad.app
```

## 测试

```sh
./.jnative/build/MacNotepad --selftest                 # 无鼠标自测，输出 ALL OK
./.jnative/build/MacNotepad --snapshot 文件 out.png    # 打开文件、展开查找栏并截图
```
