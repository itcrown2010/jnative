# Reproducible Bootstrap

## Requirements (Bootstrap 0 / Stage 1)

| Item | Value |
|------|-------|
| Host JDK | 21+ |
| Build | Maven 3.9+ |
| Native | clang on `PATH` |
| Compiler artifact | `mvn -DskipTests package` → `target/jnative.jar` |
| Verify | `java -jar target/jnative.jar doctor` |

## Stage 1 verification

```bash
./scripts/bootstrap-stage1.sh
```

Records conceptually:

- Source revision (`git rev-parse HEAD` when available)  
- `jnative --version`  
- Pass/fail of lexer golden compare + tool builds  

Full stage checksums of compiler binaries are optional until Bootstrap 2+.

## Not yet

Reproducing a self-built compiler that rebuilds itself from a clean machine.
