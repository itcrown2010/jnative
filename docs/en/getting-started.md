# Getting Started

This guide takes you from a clone to a running **native executable** on a supported host.

**Verified on:** macOS arm64 with Java 21, Maven 3.9+, system `clang`.  
**Not yet claimed as CI-gated:** Linux x64, Windows, macOS x86_64 (may work; treat as unverified).

---

## Requirements

| Tool | Role |
|------|------|
| **Git** | Clone the repository |
| **Java 21+** | Runs the JNative compiler JAR |
| **Maven 3.9+** | Builds the compiler (`mvn package`) |
| **clang** | Links LLVM output into a native executable |

Optional later: Android/iOS SDKs for mobile samples; Node for the VitePress site and some Web demos.

Check:

```bash
java -version
mvn -version
clang --version
```

---

## 1. Clone and build the compiler

```bash
git clone https://github.com/itcrown2010/jnative.git
cd jnative
mvn -q -DskipTests package
java -jar target/jnative.jar --version
# Expect: JNative 0.32  (or newer)
java -jar target/jnative.jar doctor
```

`doctor` reports compiler/LLVM/clang/runtime availability on your machine.

---

## 2. Compile and run Hello World

Source (project template): `examples/guide-hello/src/Main.jn`

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
# Hello, JNative!
```

Single-file `.java` examples also work, e.g. `examples/Hello.java`.

---

## 3. Project-style workflow (optional)

```bash
java -jar target/jnative.jar new demo
cd demo
java -jar ../target/jnative.jar build --project .
# Binary under build/ (layout depends on template)
```

Package-manager and mobile subcommands exist but are **experimental** — see CLI `--help` and [roadmap](roadmap.md).

---

## 4. Tests

```bash
mvn test
```

The suite is large (compiler + e2e). Prefer targeted tests while developing:

```bash
mvn -Dtest=SomeTestClass test
```

---

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| `clang: command not found` | Install Xcode CLT (macOS) or a clang package (Linux) |
| Compiler builds but link fails | `java -jar target/jnative.jar doctor`; ensure clang can find system SDK |
| Wrong version string | Prefer CLI `--version` over `pom.xml` |
| Expecting full Java SE | JNative is a **subset** — see [language.md](language.md) |
| Windows / Linux issues | Tracked as release blockers; do not assume parity yet |

---

## Next steps

- [Architecture](architecture.md) — pipeline overview  
- [Language](language.md) — what you can write today  
- Chinese tutorial track: [`../tutorial/index.md`](../tutorial/index.md)  
- Doc site: `cd website && npm install && npm run dev`
