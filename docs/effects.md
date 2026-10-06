# Effects (0.11)

JNative tracks **function effects** as a bitset on every callable:

| Flag | Meaning |
|------|---------|
| `ALLOCATES` | May allocate managed heap objects |
| `SAFEPOINT` | May trigger GC safepoint |
| `THROWS` | May throw (null check, bounds, explicit throw, …) |
| `BLOCKS` | May block the thread (sleep, I/O, lock, …) |
| `NATIVE_CALL` | Calls native/FFI code |
| `WRITES_GLOBAL` | Writes static fields |
| `UNKNOWN` | Opaque (callbacks, untrusted native) |
| `PURE` | No flags set |

Effects are **inferred** by `EffectAnalyzer` after semantic analysis and propagated across the call graph (fixed-point). LLVM must not invent effects.

## Contracts

Annotations on methods declare contracts; violations are compile errors **only for annotated methods**:

- `@NoGC` — no allocate / safepoint / unknown
- `@NoThrow` — no throw / unknown
- `@NoBlock` — no block / unknown
- `@Deterministic` — implies all three above; must be fully deterministic

Use `--emit-effects` to print the inferred report.

See also: [no-gc.md](no-gc.md), [deterministic-code.md](deterministic-code.md), [native-effect-contracts.md](native-effect-contracts.md).
