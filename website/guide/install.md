# 安装 SDK

以下命令均使用仓库内真实 CLI。默认假设你在 **macOS arm64** 上从源码安装（本环境已验证）。其他平台步骤相同思路，但执行结果标为 **未验证**，除非你本地复跑通过。

## 方式 A：从本仓库构建（推荐开发者）

### 1. 克隆并进入仓库

```bash
git clone <你的 jnative 仓库 URL>
cd jnative
```

### 2. 确认 Java 与 Maven

```bash
java -version    # 需要 21+
mvn -version     # 建议 3.9+
which clang
```

### 3. 打包编译器

```bash
mvn -DskipTests package
```

成功后应存在：`target/jnative.jar`。

### 4. 验证版本

```bash
java -jar target/jnative.jar --version
# 期望类似：JNative 0.32
```

### 5. 可选：包装脚本

仓库提供：

```bash
./scripts/jnative.sh --version
```

脚本在缺少 JAR 时会尝试 `mvn -q -DskipTests package`，再 `exec java -jar …`。

可将别名写入 shell 配置（示例）：

```bash
alias jnative='/绝对路径/jnative/scripts/jnative.sh'
```

下文若写 `jnative …`，等价于 `java -jar /绝对路径/target/jnative.jar …` 或上述脚本。

## 方式 B：仅使用已构建的 JAR

若他人提供了 `jnative.jar`：

```bash
java -jar /path/to/jnative.jar --version
java -jar /path/to/jnative.jar doctor
```

仍需本机 **clang / LLVM** 等才能完成原生链接（见 doctor）。

## 平台备注

| 平台 | 安装步骤适用性 |
|------|----------------|
| macOS arm64 | **稳定**（本交付已验证构建与 Hello） |
| macOS x86_64 | 步骤相同，**未验证** |
| Linux | 步骤预期相同，**未验证** |
| Windows | **未验证**；可能需要额外原生工具链配置 |

## 下一步

[检查工具链](/guide/doctor)。
