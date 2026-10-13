# Roadmap

Statuses reflect repository evidence (tests, doctor, documented audits).  
Speculative items are labeled **planned**.

---

## Completed (0.x foundation)

Verified capabilities on at least one host (primarily macOS arm64):

- Full AOT pipeline: lexer → parser → AST → semantic → IR → LLVM → **native executable**
- Classes, inheritance, interfaces (without default methods), arrays, casts, overloads
- Generics (basic), lambdas / method references, enums (basic)
- Exceptions + try-with-resources (declaration form)
- IR optimizer (`-O0`…`-O3`)
- Generational GC2, precise roots, multi-thread STW
- Native FFI / native structs / effect annotations
- File I/O, TCP, HTTP stdlib, threading primitives
- Project CLI (`new`, `build`, `doctor`), `.jn` templates
- Experimental `wasm32-browser` cores and Web/mobile sample shells (guide-tasks / sudoku)

---

## Current

- Hardening toward a honest **1.0** language profile ([`../language-support.md`](../language-support.md))
- Closing **P0 release blockers** ([`../1.0-release-blockers.md`](../1.0-release-blockers.md)): CI matrix, incomplete Windows/Linux runtime pieces, security defaults where flagged
- English-first public documentation (this `docs/en/` tree)
- Web/mobile sample reliability (WASM + sync demos) without over-claiming store deployment

---

## Next

- Multi-platform CI (macOS arm64 + Linux x64 at minimum; Windows when runtime is honest)
- Package manager workflows beyond experimental CLI surface
- Stronger LSP/IDE experience (beyond analyze-oriented diagnostics)
- Broader e2e coverage for numeric edge cases and secondary OS

---

## Long-term vision (planned)

- Richer standard library maturity (collections, crypto, DB, UI) with per-API verification  
- Better cross-platform desktop/mobile story without “write once runs everywhere” marketing  
- Optional ecosystem / registry for reusable packages  

None of the long-term items are promised for a specific date.
