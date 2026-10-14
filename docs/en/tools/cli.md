# CLI

**Status:** Implemented

```bash
java -jar target/jnative.jar --help
```

Common forms:

| Command | Purpose |
|---------|---------|
| `jnative <file.jn\|.java> -o out` | Compile single file |
| `jnative build --project DIR` | Project build |
| `jnative new NAME` | Scaffold project |
| `jnative doctor` | Toolchain check |
| `jnative analyze …` | Frontend diagnostics |
| `jnative run / test` | Project run/test |
| `jnative mobile …` | Mobile container helpers (experimental) |
| `jnative add/remove/deps/…` | Package manager (experimental) |

See [cli-reference](../reference/cli-reference.md).
