# Mac Calculator

用 **JNative Studio** 开发的 macOS 风格计算器：界面在设计器里搭好（`src/MainForm.jnui`），
按钮事件由设计器绑定并生成到 `MainFormBase.java`，计算逻辑写在 `MainForm.java`。

```sh
# 在 Studio 中打开
../../tools/jnative-studio/studio.sh --open .
# 或命令行
./package.sh && open MacCalculator.app
./.jnative/build/MacCalculator --selftest         # 逻辑自测（不需要鼠标）
./.jnative/build/MacCalculator --snapshot "12+" /tmp/calc.png   # 输入按键序列并截图
```

功能：加减乘除、连续运算、连续按 “=” 重复上次运算、正负号、百分号、退格、千位分隔、
运算符反白（与 macOS 计算器一致）、键盘输入（数字 / + - * / = Enter Esc Backspace）。
数值使用标准库 `Decimal`（十进制定点），所以 `0.1 + 0.2 = 0.3`，`1 ÷ 3` 最多显示 10 位小数；
超过 15 位提示“数值太大”，除以零提示“无法除以零”。

文件：

| 文件 | 说明 |
| --- | --- |
| `MainForm.jnui` | 设计器文档（界面） |
| `MainFormBase.java` | 设计器生成，请勿手改 |
| `MainForm.java` | 业务逻辑 |
| `SelfTest.java` | 无鼠标逻辑自测 / 截图 |
| `Main.java` | 入口 |
| `package.sh` | 打包成 `MacCalculator.app` |
