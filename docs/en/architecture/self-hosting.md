# Self-Hosting Levels

**Rule:** Do not claim a level until it is demonstrated with evidence.

## Level 0 — External Compiler (current reference)

```text
Host JVM + jnative.jar (Java implementation)
     ↓
JNative source
     ↓
native executable
```

The reference compiler lives under `src/main/java/io/jnative/`.

## Level 1 — JNative Tools

JNative builds useful development tools written in JNative (CLI utilities, Studio Core, lex dumpers, project inspectors).

## Level 2 — JNative Build System

JNative-written tooling can load projects and drive the **reference** build (`java -jar jnative.jar build`). Studio Core already does this.

## Level 3 — JNative Compiler Components

Individual compiler pieces (e.g. lexer) are implemented in JNative and checked for compatibility against the reference.

## Level 4 — JNative Compiler Frontend

Substantial frontend (lexer + parser + semantic) in JNative, feeding the **existing** backend/IR path.

## Level 5 — Full Bootstrap

A JNative-built compiler can compile its own sources into a working compiler (reproducible loop).

```text
Compiler A → builds Compiler B → builds Compiler B
```

## Current claim (Phase 6)

**Self-hosting in progress** — Levels **1–2** demonstrated; Level **3** started (lexer + golden corpus). **Not** a self-hosted compiler.

See [self-hosting-status.md](../reference/self-hosting-status.md) · [bootstrap-roadmap.md](bootstrap-roadmap.md).
