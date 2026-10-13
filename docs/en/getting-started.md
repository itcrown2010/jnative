# Getting Started

Goal: clone the repo, build the compiler, compile a tiny program, and run a **native executable**.

**Primary verification host:** macOS arm64 (Java 21, Maven 3.9+, system `clang`).  
**Other platforms:** may compile; they are **not** yet treated as CI-proven. See [roadmap.md](roadmap.md).

---

## Requirements

| Dependency | Why you need it |
|------------|-----------------|
| Git | Clone sources |
| **Java 21+** | Runs `jnative.jar` (the compiler is a JVM program) |
| **Maven 3.9+** | Builds that JAR |
| **clang** on `PATH` | Turns generated LLVM IR into a host binary |

```bash
java -version
mvn -version
clang --version
```

---

## Build the compiler

```bash
git clone https://github.com/itcrown2010/jnative.git
cd jnative
mvn -q -DskipTests package
java -jar target/jnative.jar --version
java -jar target/jnative.jar doctor
```

`doctor` should report Compiler / LLVM / clang / Linker / Runtime (and package cache) as healthy on a properly set-up machine.

> Version tip: trust `jnative --version` (currently **0.32**). `pom.xml` can lag behind the CLI banner.

---

## Your first program

Template source: `examples/guide-hello/src/Main.jn`

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

```bash
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
# prints: Hello, JNative!
```

The same language also accepts `.java` files (`examples/Hello.java` prints `Hello JNative`).

What just happened: the JAR parsed and type-checked the source, lowered it to IR, emitted LLVM IR, and linked a native binary with the JNative runtime — **no JRE** is required to run `./Hello`.

---

## Optional: project layout

```bash
java -jar target/jnative.jar new demo
cd demo
java -jar ../target/jnative.jar build --project .
```

Artifacts land under the project’s `build/` directory (exact layout depends on the template). Package-manager and mobile subcommands are available from `--help` but should be treated as **experimental**.

---

## Running tests while hacking

```bash
mvn test                                 # full suite (slow)
mvn -Dtest=ExceptionEndToEndTest test    # focused
```

Native e2e tests need `clang`.

---

## Troubleshooting

| Problem | Likely fix |
|---------|------------|
| `clang: command not found` | Install Xcode Command Line Tools (macOS) or a clang package |
| Link errors after a clean JAR build | Re-run `doctor`; confirm SDK / sysroot for `clang` |
| “It should work like full Java” | It won’t — see [language.md](language.md) |
| Linux / Windows surprises | Expected until release blockers clear |

---

## Next reading

1. [architecture.md](architecture.md) — accurate pipeline  
2. [language.md](language.md) — what you can write  
3. [compiler.md](compiler.md) — flags and modules  
4. Chinese tutorial (optional): [`../tutorial/index.md`](../tutorial/index.md)
