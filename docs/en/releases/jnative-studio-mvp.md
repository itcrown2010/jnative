# Release notes — JNative Studio Core MVP

**Label:** Experimental  
**Date:** 2026-10-13

## What works

- Native CLI `StudioMain` built entirely with JNative  
- Open `jnative.project` projects  
- Tree / read / write  
- Build via `java -jar jnative.jar build --project`  
- Run generated binary; capture stdout/stderr/exit code  
- Parse compiler diagnostics into PROBLEMS  
- Dogfood: Studio builds and runs `examples/hello-app`

## What does not work

- Native GUI IDE shell (compile blocked by duplicate Point/Size/Rect)  
- In-app terminal emulator  
- Language server inside Studio  
- Building `examples/studio/JNativeStudio.java` GUI entry

## Known issues

- Avoid identifier/comment tokens that auto-include StudioLib (e.g. the name used by stdlib diagnostic type)  
- Session state is a local `.studio-session` file  

## Platform support

Verified: **macOS arm64**. Linux/Windows: unverified in this release.

## How to build

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/jnative-studio
```

## How to run

```bash
examples/jnative-studio/build/StudioMain --jar $PWD/target/jnative.jar open examples/hello-app
examples/jnative-studio/build/StudioMain --jar $PWD/target/jnative.jar build
examples/jnative-studio/build/StudioMain --jar $PWD/target/jnative.jar run
```

## How to contribute

Fix the UI/graphics type clash, then reconnect StudioLib UI; keep Core services free of compiler internals.
