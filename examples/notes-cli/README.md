# notes-cli

Minimal **JNative Notes** CLI: create / read / list / delete notes persisted as `note-<id>.txt` files.

## Build

```bash
java -jar ../../target/jnative.jar src/Main.jn -o ../../build/notes-cli
```

## Run

```bash
cd /tmp   # or any writable directory
/path/to/jnative/build/notes-cli create hello "first note"
/path/to/jnative/build/notes-cli list
/path/to/jnative/build/notes-cli read hello
/path/to/jnative/build/notes-cli delete hello
```

Expected after create + read:

```text
created hello
hello
first note
```

(`list` prints the id lines from `note-index.txt`.)

## What it demonstrates

- `File.writeString` / `readString` / `exists` / `delete`
- CLI `args` handling
- Small persistent utility without a database
