# Architecture

This document describes the **current** compiler and runtime as implemented in the repository.  
Future ideas are listed separately under [Planned architecture](#planned-architecture).

For a longer technical narrative, see also [`../architecture.md`](../architecture.md).

---

## Current architecture

### Pipeline

```text
Source (.java / .jn)
  → Lexer → Parser → AST
  → Semantic analysis (types, members, packages, effects, …)
  → High-level IR
  → Optimization pipeline (-O0 … -O3; default typically -O2)
  → LLVM IR emission
  → clang → native object code → native executable
```

Generated programs link against JNative’s **native runtime** (memory/GC, exceptions, I/O helpers). They do **not** embed a JVM.

### Frontend

| Stage | Role |
|-------|------|
| **Lexer / Parser** | Produce AST from Java-like source |
| **AST** | Structural program representation |
| **Semantic analysis** | Types, overload resolution, inheritance, constructors, arrays, String, RTTI, exception CFG, effect annotations |
| **Multi-CU** | Project / multi-file compilation with independent parse + shared symbols (see `docs/jnative-multi-cu-architecture.md`) |

### Middle end (IR + optimization)

High-level IR lives in the compiler before LLVM. Optimization passes (escape analysis, scalar replacement, stack allocation for managed objects where safe, CHA/devirtualization, inlining, constprop/DCE, check/safepoint elimination, …) are documented in [`../optimizer.md`](../optimizer.md).

### Backend

| Piece | Role |
|-------|------|
| **LLVM emission** | Lower IR with class metadata, shadow stack, GC and exception runtime hooks |
| **clang** | Optimize/link to a host **native executable** |
| **wasm32-browser** | Alternate experimental target for browser WASM cores (not a full browser JDK) |

LLVM does **not** re-resolve Java method lookup, catch matching, or `@Native` semantics — those are fixed earlier in the pipeline.

### Runtime

| Component | Current behavior (summary) |
|-----------|----------------------------|
| **GC** | Default **GC2**: generational (TLAB, young copy, old mark-sweep). Legacy mark-sweep selectable via env in some builds. Precise roots (shadow stack + updatable slots). |
| **Exceptions** | Explicit pending-exception propagation; IR encodes dispatch CFG |
| **Threading** | OS threads (`Thread`, locks); multi-thread STW with safepoint polls |
| **FFI** | `@Native` / native methods → `NativeCall` + target ABI; `@NativeStruct` / `NativePtr` outside the managed heap |
| **I/O** | File and TCP builtins via runtime emitters; HTTP as **stdlib** on sockets |

### Package manager

CLI commands (`add` / `remove` / `deps` / …) exist. Treat package management as **experimental** until more hosts are gated. Language `package` declarations are separate from package-manager coordinates.

### Invariants (current)

1. Managed layout uses `ClassLayout`; native structs use ABI layouts — GC ignores unmanaged targets.  
2. All managed allocations are GC-tracked and enumerable.  
3. Roots are precise (not conservative whole-stack scans).  
4. Exception objects are ordinary GC-managed instances.  
5. Blocking native work enters a NATIVE state so STW does not wait on it indefinitely.

---

## Planned architecture

These are **goals or gaps**, not present as production claims:

- Multi-platform CI matrices (Linux / Windows / macOS x64) as release gates  
- Completing Linux epoll / Windows runtime stubs where still partial  
- Broader package ecosystem and registry workflow  
- Deeper IDE/LSP (today: analyze-oriented; limited navigation)  
- Expanding `wasm32-browser` glue (some browser host APIs still stubbed)

See [roadmap.md](roadmap.md) and [`../1.0-release-blockers.md`](../1.0-release-blockers.md).
