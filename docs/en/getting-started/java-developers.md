# Guide for Java developers

## Familiar

You already know: `class`, `main`, `static`, objects, `extends`, `implements`, basic generics, `try`/`catch`, lambdas for SAM types, packages with explicit imports.

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("same shape as Java");
    }
}
```

## Different

| Topic | JNative |
|-------|---------|
| Runtime | Native runtime + GC2 — **no JVM** when running apps |
| Standard library | JNative stdlib modules, not the full JDK |
| JARs | **Not** a goal — cannot consume arbitrary Maven artifacts |
| `final` fields | **Not supported** (hard error) |
| Default interface methods | **Not supported** |
| Wildcards / records / pattern matching | Experimental or not supported — see language guide |
| Distribution | Native executable (or wasm32-browser for some samples) |

## Side by side

**Java (JVM):** `javac` → bytecode → `java` launcher.  
**JNative:** `jnative.jar` → LLVM → `clang` → `./app`.

Memory: precise GC with shadow stacks; blocking native calls enter a NATIVE state for STW. See [../architecture/gc.md](../architecture/gc.md).

Next: [jnative-vs-java.md](jnative-vs-java.md) · [../language/overview.md](../language/overview.md)
