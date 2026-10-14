# Troubleshooting

| Symptom | Check |
|---------|-------|
| `clang: command not found` | Install Xcode CLT (macOS) or clang (Linux) |
| Link fails after JAR builds | `jnative doctor`; SDK/sysroot |
| Version confusion | Trust `jnative --version` |
| Expecting Java SE / JARs | Not supported — [java-developers.md](java-developers.md) |
| VS Code commands missing | Install VSIX; see [../vscode/troubleshooting.md](../vscode/troubleshooting.md) |
| Linux / Windows issues | Incomplete CI — [../roadmap.md](../roadmap.md) |
