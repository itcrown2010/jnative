# Build and run in VS Code

1. Trust the workspace.  
2. Open a folder with `jnative.project` or `jnative.toml` + `src/`.  
3. Command Palette → **JNative: Build Project**.  
4. **JNative: Run Project**.  

CLI equivalent:

```bash
java -jar /path/to/target/jnative.jar build
```

Failures should surface in Problems from JSON diagnostics. Do not run stale binaries after a failed build.
