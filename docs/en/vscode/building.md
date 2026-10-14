# Building from VS Code

Use **JNative: Build**. Failures must not re-run stale binaries (extension policy). Artifacts depend on target:

- native → host executable / `.app` packaging helpers where configured
- wasm32-browser → `dist/` style outputs for web/mobile shells
