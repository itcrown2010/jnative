# VS Code Tutorial (empty directory → run)

Follow on **macOS** with Java 21+, Maven (to build the compiler JAR), clang, and VS Code.

## 1. Install VS Code

Install from [https://code.visualstudio.com/](https://code.visualstudio.com/).

## 2. Install JNative compiler

From a clone of this repository:

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar doctor
```

Optional: put `java -jar /absolute/path/to/target/jnative.jar` behind a shell alias named `jnative`.

## 3. Install the JNative extension

```bash
cd tools/vscode-jnative
npm install
npm run compile
npm run package   # produces a .vsix
```

In VS Code: **Extensions → … → Install from VSIX…** and select the generated VSIX.

Set `jnative.compilerPath` (or equivalent setting from the extension README) to your `target/jnative.jar` / launcher if auto-detect fails.

## 4. Create a project

**Option A — CLI**

```bash
mkdir ~/jn-first && cd ~/jn-first
java -jar /path/to/target/jnative.jar new hello
cd hello
```

**Option B — VS Code command**

Command Palette → **JNative: Create Project** (or Create Desktop/Web variants).

## 5. Open the project

`File → Open Folder…` → select the project root (must contain `jnative.project` or `jnative.toml` + `src/`).

Trust the workspace when prompted (required for build/LSP).

## 6. Edit `src/Main.jn`

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

## 7. Build

Command Palette → **JNative: Build Project**, or terminal:

```bash
java -jar /path/to/target/jnative.jar build
```

## 8. Run

**JNative: Run Project**, or run the binary under `build/`.

## 9. Debug (if supported)

Install **CodeLLDB**. Use **JNative: Debug Project** / generate `launch.json` from the extension. Source-level debugging is an **MVP** on macOS arm64—see [debugging](debugging.md).

## 10. Release build

```bash
java -jar /path/to/target/jnative.jar build -O2
```

(Exact flags: `jnative --help` / [cli-reference](../reference/cli-reference.md).)

No screenshots are embedded here; UI chrome varies by VS Code version.
