# First project

**Status:** Implemented

## Create

```bash
java -jar target/jnative.jar new demo
cd demo
```

Typical layout:

```text
demo/
├── jnative.toml
├── jnative.project
├── .gitignore
└── src/Main.jn   # or Main.java depending on template
```

## Build

```bash
java -jar target/jnative.jar build --project .
```

Binary under `build/` (exact name depends on the template).

## Edit in VS Code

Open the folder, set `jnative.jarPath` to your `target/jnative.jar`, then use **JNative: Build / Run**. Details: [../vscode/project-setup.md](../vscode/project-setup.md).

## Troubleshooting

[troubleshooting.md](troubleshooting.md)
