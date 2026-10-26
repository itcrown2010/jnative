# JNative Studio Core

**Experimental** CLI development environment written in JNative.

> Not a VS Code competitor. Proves open → edit → build → run on real projects.

## Build

```bash
# from repository root
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/jnative-studio
```

Binary: `examples/jnative-studio/build/StudioMain`

## Dogfood

```bash
STUDIO=examples/jnative-studio/build/StudioMain
JAR=$PWD/target/jnative.jar
$STUDIO --jar $JAR open examples/hello-app
$STUDIO --jar $JAR tree
$STUDIO --jar $JAR build
$STUDIO --jar $JAR run
```

## Commands

| Command | Action |
|---------|--------|
| `open <dir>` | Load `jnative.project` |
| `tree` | List project files |
| `read <rel>` | Print file |
| `write <rel> <text>` | Save file |
| `build` | Invoke toolchain |
| `run` | Run output binary |
| `problems` | Re-print last diagnostics |

## Architecture

See `docs/en/projects/jnative-studio.md` and feasibility audit.

## GUI

Deferred until UiLib/GraphicsLib compile cleanly. Historical entry: `examples/studio/JNativeStudio.java`.
