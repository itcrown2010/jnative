# Roadmap

Based on what is actually in the repository (compiler sources, e2e tests, release-blocker audits)—not on aspirations alone.

---

## Completed foundation (0.x)

On at least one verified host (primarily **macOS arm64**):

- AOT pipeline: lex → parse → semantic → IR → optimize → LLVM → **native executable**
- Core OOP: classes, inheritance, interfaces (no default methods), arrays, casts, overloads
- Generics (basic), lambdas / method references, basic enums
- Exceptions and declaration-form try-with-resources
- IR optimizer (`-O0`…`-O3`, single-file default `-O2`)
- Generational GC2, precise roots, multithreaded STW + safepoints
- Native FFI / structs / effect annotations
- File I/O, TCP, HTTP stdlib, threading APIs
- Project CLI (`new`, `build`, `doctor`) and `.jn` templates
- Experimental `wasm32-browser` cores used by sample apps

---

## Current focus

- Honest **1.0 language profile** discipline ([`../language-support.md`](../language-support.md))
- Clearing **P0 blockers** ([`../1.0-release-blockers.md`](../1.0-release-blockers.md)): missing multi-OS CI, incomplete Windows/Linux runtime pieces, unsafe crypto defaults where flagged
- Keeping **English-first** public docs (`docs/en/`) aligned with the implementation
- Stabilizing Web/mobile **samples** without claiming app-store pipelines

---

## Next

- CI matrices that fail the build when native e2e breaks on secondary OS
- Package-manager workflows beyond “CLI exists”
- Deeper editor support (today’s LSP/analyze path is intentionally limited)
- Broader numeric / concurrency edge-case coverage

---

## Longer term (planned only)

Richer stdlib maturity, cross-platform UI without “write once runs everywhere” marketing, and an optional package ecosystem. None of these have a committed ship date.
