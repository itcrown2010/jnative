# JNative in 30 minutes: CLI Todo

**Goal:** go from clone → native executable of a **real** small app (not another Hello World).

You will use: project layout ideas, variables, methods, `ArrayList`, `File` I/O, simple error paths, and the compile pipeline.

**Status of APIs used:** ArrayList / File = **Experimental**; console `main` = **Stable** on verified hosts.

## 0. Prerequisites (2 min)

- Java 21+, Maven 3.9+, `clang` on `PATH`
- Clone this repository

```bash
cd jnative
mvn -q -DskipTests package
java -jar target/jnative.jar doctor
```

## 1. Hello once (2 min)

Confirm the toolchain:

```bash
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
```

## 2. Build the Todo app (5 min)

Source of truth: `examples/tutorials/cli-todo/TodoApp.java`

```bash
java -jar target/jnative.jar examples/tutorials/cli-todo/TodoApp.java -o todo
```

## 3. Use it (5 min)

```bash
./todo add milk
./todo add eggs
./todo list
./todo done milk
./todo list
```

Expected:

```text
added
added
milk
eggs
done
eggs
```

Persistence file: `todo-store.txt` in the current working directory.

## 4. What the code teaches (8 min)

| Step | Language / SDK |
|------|----------------|
| `main(String[] args)` | entry + CLI |
| `ArrayList<String>` | generics + collections |
| `File.readString` / `writeString` / `exists` | filesystem SDK |
| `equals` / loops | control flow |
| missing title → `missing` | simple error handling |

Open `TodoApp.java` and skim `load` / `save` / `add` / `removeTitle`.

## 5. Optional: VS Code (5 min)

Install the extension VSIX from `tools/vscode-jnative`, trust the workspace, open the repo, build `cli-todo`. See [../vscode/index.md](../vscode/index.md).

## 6. What the compiler did (3 min)

```text
TodoApp.java
    ↓  lexer / parser / semantic analysis
JNative IR
    ↓  optimize (optional -O)
LLVM IR
    ↓  clang + JNative runtime
native executable `todo`
```

No JVM is required to **run** `./todo`.

## Next

- Expand persistence: [build-your-first-application](build-your-first-application.md) (Notes)  
- HTTP: [http-server](http-server.md)  
- Language tour: [../language/index.md](../language/index.md)  
- Larger app: [building-an-application](building-an-application.md)  
