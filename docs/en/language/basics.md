# Basics

**Status:** Stable (console `main` on macOS arm64 verified)

## What it is

A JNative program is one or more `.java` / `.jn` compilation units with a `public static void main(String[] args)` entry.

## Why it exists

Same entry shape as Java so beginners and Java developers can start quickly—then compile to a **native executable**.

## Syntax

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

## Minimal example

```bash
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
```

## Real-world example

CLI tools parse `args` (see Notes / Todo tutorials).

## Common mistakes

- Expecting a JVM (`java Main`) — run the native binary instead.  
- Assuming JDK packages (`java.nio.*`) exist — use JNative SDK.  

## Compiler/runtime behavior

Source → semantic analysis → IR → LLVM → clang → host binary linked with JNative runtime (GC, I/O).

## Limitations

Not an interpreter; no REPL product. File extension `.jn` is project convention; grammar matches `.java`.

## Related

[variables](variables.md) · [for-java-developers](for-java-developers.md)
