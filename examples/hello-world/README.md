# hello-world

**What this demonstrates:** smallest native Hello World.

## Prerequisites

Built `target/jnative.jar`, `clang` on `PATH`.

## Build

```bash
java -jar ../../target/jnative.jar src/Main.jn -o Hello
```

## Run

```bash
./Hello
```

## Expected output

```text
Hello, JNative!
```

## Source

`src/Main.jn` — `main` + `System.out.println`.
