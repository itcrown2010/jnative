# Install the JNative VS Code extension

1. Build CLI: `mvn -q -DskipTests package`
2. Package extension:

```bash
cd tools/vscode-jnative && npm install && npm run package
```

3. Install VSIX (path may vary by version):

```bash
code --install-extension tools/vscode-jnative/jnative-0.8.0.vsix --force
```

4. Settings:

```json
{
  "jnative.jarPath": "/ABS/PATH/jnative/target/jnative.jar",
  "jnative.javaPath": "java"
}
```

Run **JNative: Check toolchain**. The extension does **not** auto-download JDKs or run Maven for you.
