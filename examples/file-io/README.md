# file-io

**What this demonstrates:** `File.writeString` / `File.readString` / `File.delete`.

## Prerequisites

`target/jnative.jar`, `clang`.

## Build

```bash
java -jar ../../target/jnative.jar FileHello.java -o FileHello
```

## Run

```bash
./FileHello
```

## Expected output

```text
Hello file
```

## Notes

Builtin `File` API — `docs/en/sdk/files.md`, `docs/file-io.md`.
