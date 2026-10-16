# Windows Calculator（JNative AOT）

用 **JNative** 写的 Windows 风格计算器：源码是 Java 子集语法（`.java`），经 `jnative.jar` **AOT 编译成原生可执行文件**。  
运行时 **不需要 Java / JRE**——和 `jnative-task-monitor.exe` 一样。

| 阶段 | 要不要 Java |
|------|-------------|
| 编译（`java -jar target/jnative.jar …`） | 需要（只作编译器宿主） |
| 运行 `Calculator.exe` / `Calculator` | **不需要** |

## Windows 上编出 `.exe`

```powershell
# 仓库根目录
mvn -DskipTests package
powershell -File examples/windows-calculator/scripts/build.ps1
.\build\windows-calculator\Calculator.exe
```

产物：`build\windows-calculator\Calculator.exe`（Win32 GUI，`-Wl,--subsystem,windows`）。

状态：**WINDOWS_E2E_UNVERIFIED**（需在真实 Windows 10/11 上验收）。

## macOS 上的代理构建（本机迭代 UI）

```bash
mvn -DskipTests package
./examples/windows-calculator/scripts/build.sh
./build/windows-calculator/Calculator
```

`file build/windows-calculator/Calculator` 应为 `Mach-O 64-bit executable`，链接 Cocoa/AppKit，**不是** JAR。

## 无头引擎测试

```bash
S=examples/windows-calculator/src
java -jar target/jnative.jar \
  $S/WcNum.java $S/WcFmt.java $S/WcMath.java $S/WcEngine.java $S/WcTests.java \
  -o build/windows-calculator/WcTests
./build/windows-calculator/WcTests
```

## 源码

| 文件 | 作用 |
|------|------|
| `WcNum` / `WcFmt` / `WcMath` / `WcEngine` | 十进制引擎（标准 / 科学 / 程序员） |
| `WcTheme` / `WcUi` / `WcApp` | Win11 风格 UI + 入口 |
| `scripts/build.ps1` | Windows → `Calculator.exe` |
| `scripts/build.sh` | macOS 代理 + MinGW UI 编译检查 |
