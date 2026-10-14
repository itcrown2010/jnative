# Documentation & Versioning

## Current version

| Channel | Value | Source |
|---------|-------|--------|
| CLI `--version` | `JNative 0.32` | `java -jar target/jnative.jar --version` |
| Help banner | `JNative 1.0-dev` | `--help` |
| Maven `pom.xml` | may differ | Prefer CLI for user docs |
| VS Code extension | `0.8.1` | `tools/vscode-jnative/package.json` |

## Stable features (docs may call “Stable”)

- Native console Hello World on **macOS arm64**  
- Single-file / project compile to host native binary  
- `jnative doctor` (desktop toolchain) on verified hosts  
- Basic language: classes, methods, control flow, strings, arrays  

## Experimental features

Most stdlib (HTTP, JSON, threads, UI, wasm, mobile containers), LSP, package manager, desktop GUI, TLS.

## Compatibility policy

- **Docs track the repository tip** with maturity labels—not multiple full doc trees.  
- Breaking changes must appear in release notes under **Breaking Changes**.  
- Experimental APIs may change without a long deprecation cycle; Stable ones should note migration.  

## Breaking changes

Record in changelog: removed APIs, changed signatures, target triple behavior, and doc URL moves.

## Single tree

Avoid maintaining parallel complete documentation versions unless a release train requires it. Prefer:

```text
docs/en/ + maturity labels + versions.md + CHANGELOG
```
