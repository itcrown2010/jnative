# 第三方纯源码库示例（`acme/math`）

作者：张晓达

本示例验证 JNative **现有**包管理机制：第三方作者发布 `.jnpkg` 源码包 → 本地文件注册表 → 锁文件 → 缓存 → 构建器把库源码并入编译单元 → 生成**无需 JVM** 的原生程序。

> 编译工具本身需要 Java（运行 `jnative` CLI）。最终应用是原生二进制，运行时不需要 JVM。  
> **不支持**任意 Java JAR / Maven 坐标；本示例只覆盖 JNative 源码包（`type = "source"`）。

## 目录

```text
examples/third-party-source-lib/
├── library/          # 第三方库 acme/math@1.0.0
│   ├── jnative.toml
│   └── src/MathUtils.java
├── app/              # 消费应用（不复制 MathUtils.java）
│   ├── jnative.toml  # 声明 "acme/math" = "1.0.0"
│   ├── jnative.project
│   └── src/Main.java
├── registry/         # 示例专用本地文件注册表（初始为空）
└── README.md
```

## 语言 / 命名空间说明

源码包机制仍会把依赖包的 `src/**/*.{jn,java}` **拼接进同一个编译单元**，再交给编译器；但类型按 **语言 package / import / FQN** 索引，并检查访问控制。

- 本示例为兼容旧路径，仍使用**默认包**简单类名（`MathUtils`、`Main`）。
- 带语言 package 的多包示例见 [`examples/multipackage-resolve/`](../multipackage-resolve/)。
- 库坐标 `acme/math` 是**包管理标识**（namespace/name），不是 Java 语言包名。

## 前置条件

在仓库根目录构建 CLI：

```bash
mvn -q -DskipTests package
alias jnative='java -jar target/jnative.jar'   # 或直接写完整命令
```

以下命令均在仓库根目录执行。示例缓存与注册表使用本目录下的路径，不依赖开发者本机已有包缓存。

## 1. 创建库清单与源码

已提供 `library/`。清单要点：

```toml
[package]
name = "math"
namespace = "acme"
version = "1.0.0"
license = "MIT"
type = "source"
```

源码见 `library/src/MathUtils.java`（`clamp` / `gcd`，无额外标准库依赖）。

## 2. 打包并发布到本地注册表

`jnative publish` 会打包项目为 `.jnpkg`（含 `jnative.toml` + 源码），并写入 `--registry` 指向的文件系统注册表：

```bash
rm -rf examples/third-party-source-lib/registry/acme
rm -rf examples/third-party-source-lib/app/.jnative \
       examples/third-party-source-lib/app/jnative.lock \
       examples/third-party-source-lib/app/build

java -jar target/jnative.jar publish \
  --project examples/third-party-source-lib/library \
  --registry examples/third-party-source-lib/registry
```

实际成功输出示例：

```text
publish acme/math@1.0.0 sha256:18a7d58d109de15f4cd643cd67ebc9dba4c4464519c8c11a8ce8531c47f03652
  registry: file://…/examples/third-party-source-lib/registry
  jnative.toml
  src/MathUtils.java
```

成功时注册表布局类似：

```text
registry/acme/math/1.0.0/
  package.jnpkg
  SHA256
  meta.toml
```

## 3. 应用声明依赖并解析

`app/jnative.toml` 已声明：

```toml
[dependencies]
"acme/math" = "1.0.0"
```

解析依赖并生成/更新 `jnative.lock`（也可在首次 `build` 时自动完成）：

```bash
java -jar target/jnative.jar deps \
  --project examples/third-party-source-lib/app \
  --registry examples/third-party-source-lib/registry
```

## 4. 构建与运行

构建器通过包管理器解析依赖，把缓存中解压出的库 `src/` 交给 `CompilerSession`（`setPackageSourceRoots`），与应用源码合并编译：

```bash
java -jar target/jnative.jar build \
  --project examples/third-party-source-lib/app \
  --registry examples/third-party-source-lib/registry
```

运行原生程序：

```bash
./examples/third-party-source-lib/app/build/math-app
```

**必须精确输出：**

```text
10
0
7
6
```

或一步构建并运行：

```bash
java -jar target/jnative.jar run \
  --project examples/third-party-source-lib/app \
  --registry examples/third-party-source-lib/registry
```

## 5. 离线重建（锁文件 + 缓存）

首次成功解析后，依赖归档在 `app/.jnative/pkg-cache/`，版本钉在 `app/jnative.lock`。

清空注册表后仍可用 `--offline` 构建（不得访问网络 / 注册表下载）：

```bash
# 模拟注册表不可用（勿删掉 lock 与 pkg-cache）
rm -rf examples/third-party-source-lib/registry/acme

java -jar target/jnative.jar build \
  --project examples/third-party-source-lib/app \
  --registry examples/third-party-source-lib/registry \
  --offline

./examples/third-party-source-lib/app/build/math-app
```

## 包管理器如何把库源码交给编译器

1. `PackageManager.resolve` 读 `jnative.toml` / `jnative.lock`，从注册表下载 `.jnpkg`，校验 `SHA256`，写入内容寻址缓存并解压。
2. `BuildGraphPackageIntegration.materialize` / `sourceRoots` 得到各包的 `src/` 路径（**不会**复制进 `app/src`）。
3. `ProjectCli.attachPackageSources` → `CompilerSession.setPackageSourceRoots`。
4. 编译前 `materializeModuleCompileUnit` 将包源码与项目源码拼接为临时编译单元，再 AOT 生成本地原生二进制。

## 相关测试

```bash
mvn -q -Dtest=SourceLibPackageIntegrationTest,Package030SuiteTest test
```

覆盖：临时注册表解析并编译运行、未声明依赖编译失败、注册表缺版本报错、包被篡改拒绝、锁文件钉版、离线构建。
