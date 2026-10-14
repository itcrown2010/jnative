# Installation

**Status:** Implemented (verified on macOS arm64)

## Requirements

| Tool | Role |
|------|------|
| Java 21+ | Runs the compiler JAR |
| Maven 3.9+ | Builds the compiler |
| clang on PATH | Links native executables |
| Git | Clone the repository |

Optional: VS Code + [JNative extension](../vscode/installation.md); Android SDK / Xcode for mobile samples.

## Build from source

```bash
git clone https://github.com/itcrown2010/jnative.git
cd jnative
mvn -q -DskipTests package
java -jar target/jnative.jar --version
java -jar target/jnative.jar doctor
```

Expect `JNative 0.32` (or newer). Prefer CLI `--version` over `pom.xml`.

## Sanity check

```bash
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
# Hello, JNative!
```

Next: [hello-world.md](hello-world.md) · [first-project.md](first-project.md) · [../vscode/overview.md](../vscode/overview.md)
