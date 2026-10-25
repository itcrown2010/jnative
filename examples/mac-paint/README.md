# Mac 画图（JNative Studio 开发）

仿 Windows「画图」的 macOS 版本，用 JNative Studio 的窗体设计器开发：`src/MainForm.jnui` 是设计器文件，
`MainFormBase.java` 由 Studio 生成（不要手改），其余为手写逻辑。画布是纯软件位图（`Image`/`Graphics`），
所有绘图算法（直线、曲线、多边形、椭圆、喷枪、填充…）都在 `Painter.java` / `Doc.java` 中。

## 功能

- 工具：矩形选择、橡皮、颜色填充（油漆桶）、取色、放大镜、铅笔、画笔（多种笔刷）、喷枪、文字、直线、曲线、
  矩形、多边形、椭圆、圆角矩形；形状支持“轮廓 / 轮廓+填充 / 填充”，6 档线宽，按住 ⇧ 画正圆 / 正方形 / 水平垂直线
- 左键用前景色、右键用背景色；28 色标准调色板 + 10 个自定义色 + “编辑颜色…”（系统取色板）
- 选区：移动、⌥ 拖动复制、⌘X/⌘C/⌘V、删除、全选，粘贴的图片可继续移动
- 图像：翻转（水平/垂直）、旋转（⌘R / ⇧⌘R 90°、180°）、反色（⌘I）、画布大小（⌥⌘E）、缩放图像（⌥⌘R）、裁剪到选区
- 视图：放大 / 缩小 / 适应窗口 / 实际大小（⌘= ⌘- ⌘0，⌘9 适应窗口），滚轮平移、⌘滚轮缩放，网格（高倍率时），状态栏显示坐标与画布大小
- 撤销 / 重做（⌘Z / ⇧⌘Z，多级），新建 / 打开 / 存储 / 存储为（⌘N ⌘O ⌘S ⇧⌘S），PNG / BMP，未保存时询问

## 构建与运行

```sh
java -cp ../../target/classes io.jnative.Main build --project .
./.jnative/build/MacPaint [图片]
./package.sh          # 生成 MacPaint.app
```

## 测试

```sh
./.jnative/build/MacPaint --selftest                  # 无鼠标自测，输出 ALL OK
./.jnative/build/MacPaint --snapshot [文件] out.png   # 画示例图（或打开文件）并截图
```
