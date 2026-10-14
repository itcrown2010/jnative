# Project setup in VS Code

1. **JNative: Create project** or open an existing folder with `jnative.project` / `jnative.toml`+`src/`.
2. Open `.jn` / project `.java` sources (Java files switch to `jnative` only inside recognised project roots).
3. Select target (`native` vs `wasm32-browser`) when relevant.
4. Build / Run from the command palette.

Terminal workflow remains valid:

```bash
java -jar $JNATIVE_JAR build --project .
```
