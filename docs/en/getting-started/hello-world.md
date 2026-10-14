# Hello World

**Status:** Implemented

## Concept

The smallest JNative program is a class with `public static void main(String[] args)` that prints to stdout—same shape as Java, compiled to a native binary.

## Source

`examples/guide-hello/src/Main.jn`:

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

## Build & run

```bash
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
```

Expected output: `Hello, JNative!`

Single-file variant: `examples/Hello.java` prints `Hello JNative`.

## What the compiler did

Lex → parse → semantic check → IR → (optional optimize) → LLVM IR → `clang` + runtime → native executable. No JVM is required to run `./Hello`.

See [../architecture/overview.md](../architecture/overview.md).
