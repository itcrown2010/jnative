# JNative Studio

**Status:** Experimental — **CLI Core MVP** shipped; GUI deferred.

## Why it exists

Prove that JNative can build a useful developer tool — not to compete with VS Code.

```text
JNative language + SDK
        ↓
JNative Studio Core (native executable)
        ↓
opens / edits / builds / runs JNative projects
```

## Architecture

```text
examples/jnative-studio/
├── src/CoreFs.java          FileSystemService
├── src/CoreProject.java     Project model (jnative.project)
├── src/CoreBuild.java       BuildService / ProcessService
├── src/CoreDiag.java        Diagnostics parse
└── src/StudioMain.java      CLI shell
```

UI must not embed compiler logic: Studio invokes `java -jar <jnative.jar> build --project <dir>`.

## Current capabilities

| Feature | Status |
|---------|--------|
| Open project | Yes |
| List tree | Yes |
| Read / write source | Yes |
| Build via real toolchain | Yes |
| Run native binary | Yes |
| Parse PROBLEMS from compiler output | Yes |
| GUI explorer / CodeEditor | Blocked (stdlib type clash) |
| Recent projects DB | Session file only (`.studio-session`) |
| Integrated terminal | Not Supported (OUTPUT via stdout) |

## Dogfood workflow

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/jnative-studio
STUDIO=examples/jnative-studio/build/StudioMain
JAR=$PWD/target/jnative.jar

$STUDIO --jar $JAR open examples/hello-app
$STUDIO --jar $JAR tree
$STUDIO --jar $JAR read src/Main.jn
$STUDIO --jar $JAR build
$STUDIO --jar $JAR run
```

Expected run line: `Hello from JNative Studio!`

## Limitations

- CLI only for this MVP  
- Compiler host still requires JVM to run `jnative.jar` (Studio itself does not)  
- Do not name types `StudioDiagnostic` in app sources — token pulls StudioLib/UI  

## Roadmap

1. Fix UiLib/GraphicsLib `Point`/`Size`/`Rect` clash  
2. Wire GUI shell using StudioLib patterns  
3. Shortcuts Cmd/Ctrl+S/B/R when UI ready  
4. Level 4–5 self-hosting  

## Contribute

See [feature-completion](../contributing/feature-completion.md) · [feasibility](../architecture/jnative-studio-feasibility.md) · [release notes](../releases/jnative-studio-mvp.md)
