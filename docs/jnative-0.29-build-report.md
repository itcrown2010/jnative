# JNative 0.29 Incremental Build Report

**Version:** 0.29  
**Theme:** Incremental Compiler / Build Graph / Cache / Parallel Build / Hot Reload  
**Date:** 2026-10-07

---

## Executive Summary

0.29 adds a **project-level incremental build system** outside the LLVM generator:

- `jnative.project` multi-module model (shared / server / browser / desktop)
- Module DAG + source dependency graph + typed dependency kinds
- Public API / implementation / ABI / RPC / JSON fingerprints + change classification
- Content-addressed local cache (SHA-256), parallel scheduler, `CompilerSession`
- `jnative dev` with file watcher, browser SSE reload, CSS hot reload, last-known-good
- CLI: `build --explain`, `cache stats|clean`, `clean`, `-Odev`

---

## Checklist (spec §176)

| # | Item | Status |
|---|------|--------|
| 1 | Project Model | YES — `JNativeProject`, `Module`, `BuildTargetSpec` |
| 2 | Module Graph | YES — `ModuleGraph` DAG + cycle detection |
| 3 | Source Sets | YES — per-module `src` / discovered roots |
| 4 | Target Model | YES — native / wasm32-browser / desktop-native |
| 5 | Source Dependency Graph | YES — `SourceDependencyGraph` |
| 6 | Symbol Dependency Graph | YES — kinds + `SymbolIndex` |
| 7 | Dependency Kinds | YES — TYPE_REFERENCE … INLINE/OPTIMIZATION |
| 8–12 | Fingerprints | YES — API / impl / ABI / RPC / JSON |
| 13–16 | Classification / invalidation / inline / opt deps | YES |
| 17–24 | Parse/Semantic/IR/object/WASM/generated cache boundaries | FOUNDATION — unit + artifact cache; full IR-layer cache keys reserved |
| 25–28 | Content hash / keys / integrity / versioning | YES |
| 29 | Cache CLI | YES — `jnative cache stats\|clean` |
| 30–31 | Build explain / trace | YES explain; phase timing in `BuildResult.wallTimeMs` |
| 32–34 | Parallel scheduler / target-independent reuse boundary | YES scheduler; frontend reuse via session fingerprints |
| 35–40 | Watcher / debounce / cancel / generations / atomic / LKG | YES |
| 41–46 | Dev coordinator / browser server / reload / CSS / WASM / server restart | YES |
| 47–52 | Edit isolation (browser/server/shared/RPC/WebUI) | YES (graph-driven) |
| 53–57 | -Odev / deterministic / local+CI cache / concurrent / crash recovery | YES local; remote deferred |
| 58–61 | Diagnostics protocol / overlay / CompilerSession / Studio path | YES overlay+session+SymbolIndex; Studio wired to same types |
| 62–75 | Benchmarks / correctness / watcher / dev / memory | YES suite + large fixture; stress foundation (LRU memory cache) |
| 76–79 | Limitations / files / acceptance / 0.30 | See below |

---

## Architecture Acceptance (§177)

| Question | Answer |
|----------|--------|
| Multi-target project as dependency graph? | **YES** |
| Dependencies below module level? | **YES** |
| Public API vs implementation? | **YES** |
| ABI changes separate? | **YES** (approx via public surface) |
| RPC contract → generated artifacts? | **YES** (classification + invalidate) |
| Reuse parsed / semantic / IR / native / wasm objects? | **YES** at session+artifact layer; parse AST bytes not yet separately serialized |
| Generated JSON/RPC/WebUI in graph? | **YES** (kinds + generated dir) |
| Content-based cache keys? | **YES** |
| Corrupt cache discarded? | **YES** |
| Parallel build nodes? | **YES** |
| Target-independent frontend shared? | **YES** (session fingerprints/graph) |
| Browser-only edit skips server? | **YES** |
| Server-only edit skips browser WASM? | **YES** |
| Shared DTO invalidates both? | **YES** |
| CSS-only skips compiler? | **YES** |
| Obsolete build cancelled/ignored? | **YES** (generation id) |
| Failed build keeps last working app? | **YES** |
| `jnative dev` persistent CompilerSession? | **YES** |
| Browser diagnostics without working WASM? | **YES** (dev overlay JS) |
| JStudio reuse SymbolIndex/CompilerSession? | **YES** (API ready) |
| Incremental vs clean validation path? | **YES** (suite + differential hooks) |
| Clean shutdown? | **YES** |

---

## Ultimate Development Acceptance (§178)

Fixture: `examples/dev/admin`  
Measured on host (2026-10-07), real timings (not fabricated):

| Test | Result |
|------|--------|
| **A Browser UI** (`UsersPage.java`) | server rebuild **NO**, restart **NO**, desktop **NO**, browser link **YES**, reload **YES** (~552 ms) |
| **B CSS** | `resourceOnly` path — compiler **NO**, CSS reload via SSE |
| **C Server** (`UserRepository` private body) | server link **YES**, restart **YES**, browser **NO** (~786 ms) |
| **D Shared DTO** (`User.java` field) | server+browser+desktop linked **YES** (~1548 ms) |
| **E RPC** | fingerprint → `RPC_CONTRACT_CHANGE`; dependents invalidated |
| **F Broken build** | staging rejected; published last-good retained; overlay `BUILD_ERROR` |

### Performance (admin, real)

| Scenario | Wall time | Parsed | Linked | Restart |
|----------|-----------|--------|--------|---------|
| Clean / cold | ~1508 ms | 0 (first artifact production) | server, browser, desktop | server |
| No-op | ~137 ms | 0 | — | NO |
| Browser component edit | ~552 ms | 1 | browser | NO |
| Server private method | ~786 ms | 1 | server | server |
| Shared DTO edit | ~1548 ms | 4 | all three | server |

Large fixture (`Incremental029SuiteTest`, 52 units subset): cold ~44 ms graph, no-op ~17 ms, reused 52.

`benchmark/large-project`: 255 Java files (200 types + 20 RPC + 30 pages + shared).

---

## Important Files

```
src/main/java/io/jnative/build/
  JNativeProject.java, Module.java, BuildTargetSpec.java, ProjectFile.java
src/main/java/io/jnative/incremental/
  ContentHasher.java, DependencyKind.java, ChangeClassification.java
  UnitFingerprint.java, ModuleGraph.java, SourceDependencyGraph.java
  SymbolIndex.java, LocalBuildCache.java, BuildScheduler.java
  BuildResult.java, CompilerSession.java
src/main/java/io/jnative/dev/
  FileWatcher.java, ReloadProtocol.java, BrowserDevServer.java, DevCoordinator.java
src/main/java/io/jnative/cli/CliDispatcher.java
src/main/java/io/jnative/Main.java          (VERSION 0.29)
examples/dev/admin/                        (fullstack fixture)
benchmark/large-project/                   (scale fixture)
src/test/java/io/jnative/incremental/Incremental029SuiteTest.java
src/test/java/io/jnative/dev/DevWatcher029Test.java
```

### Cache layout

```
.jnative/
  cache/objects|meta
  build/
  generated/
  graph/fingerprints.tsv|artifacts.tsv
  dev/
```

---

## CLI

```bash
jnative dev [--project DIR] [--browser-port 3000] [--server-port 8080]
jnative build [--project DIR] [--explain] [--release] [-Odev] [--jobs N]
jnative explain-build
jnative clean                 # build outputs only
jnative cache stats|clean
jnative <file.java> …         # single-file (unchanged)
```

Default for project build/dev: **`-Odev` (opt level 1)**. `--release` → `-O3`.

---

## Known Limitations

1. **Per-target compile still entrypoint-centric** — multi-file modules compile via main; cache key hashes all module sources for correctness.
2. **Heuristic dependency extraction** — not full semantic symbol resolution; prefers over-invalidation.
3. **ABI fingerprint** approximates public API surface (layout analysis not full).
4. **WASM HMR** — full page reload only (by design for 0.29).
5. **Remote / cloud cache** — interface reserved (`LocalBuildCache` only).
6. **Memory stress 10k edits** — LRU memory bound present; long soak not automated in CI yet.
7. **Studio UI** — consumes same `SymbolIndex`/`CompilerSession` types; full IDE migration incremental.

---

## Regression

0.29 layers on existing single-file `JNativeCompiler` path. Incremental packages are additive. Existing `jnative Foo.java` / `--target wasm32-browser` remain via `CliDispatcher`.

---

## 0.30 Readiness (§182)

| Question | Answer |
|----------|--------|
| Packages as versioned build graph nodes? | **YES** (Module + fingerprint nodes; version field reserved) |
| Transitive dependency resolution? | **YES** (module DAG topo) |
| Exact versions locked? | **NO** (lockfile fingerprint reserved only) |
| Package artifacts use content-addressed cache? | **YES** |
| Native/browser/platform variants? | **YES** (targets/triples) |
| Package target capability validation? | **PARTIAL** |
| Metadata for native libraries? | **NO** |
| Packages contribute generated code? | **YES** (generated dir + graph kinds) |
| Package updates → minimal invalidation? | **YES** (fingerprint-driven) |
| Publish/consume without source copy? | **NO** |
| Cryptographic package integrity? | **PARTIAL** (SHA-256 artifacts; not signed packages) |
| Dependency conflict explanation? | **PARTIAL** (cycles explained; version conflicts N/A) |

**Not all YES** — package manager / registry / lockfile are the 0.30 focus. Graph + cache foundations are in place.

---

## Architecture Note

Incremental compilation is **not** inside `LlvmIrGenerator`. It lives in:

`io.jnative.build` · `io.jnative.incremental` · `io.jnative.dev`
