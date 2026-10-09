# 常见安装问题

## `Unable to access jarfile target/jnative.jar`

**症状**：在错误工作目录执行 `java -jar target/jnative.jar`。  
**处理**：`cd` 到仓库根，或使用绝对路径 / `./scripts/jnative.sh`。先 `mvn -DskipTests package`。

## `doctor` 报 clang / LLVM 失败

**检查**：

```bash
which clang
clang --version
jnative doctor
```

安装 Xcode CLT（macOS）或发行版 `clang`/`llvm` 包（Linux，**未验证**）。不要关闭 SIP 或乱改系统安全设置。

## Java 版本过低

编译器需要 **Java 21+**。用 `JAVA_HOME` 指向正确 JDK，并确认 `jnative.javaPath` 一致。

## VS Code 不把 `.java` 当 JNative

确认目录有 `jnative.project`，或 `jnative.toml` + `src/`；或使用 **「将当前文件作为 JNative 打开」**。多根工作区按文件所属项目识别。

## 扩展命令找不到 jnative

配置 `jnative.jarPath` + `jnative.javaPath`，再运行 **检查工具链**。扩展不会自动跑 Maven。

## 构建成功但「运行」仍是旧程序（VS Code）

扩展设计：构建失败时 **不** 启动旧产物。若仍异常，打开 JNative 输出通道查看实际 artifact 路径。

## 移动 doctor / 真机

见阶段 3 移动教程。iOS 真机还需在设备上 **信任开发者证书**；失败时优先检查 Apple ID 登录与签名团队 ID，而不是重置整机。

## 仍失败时

收集：`jnative --version`、`doctor` 完整输出、复现用的最小命令与源文件。查阅 [调试与故障排查](/reference/debugging)。
