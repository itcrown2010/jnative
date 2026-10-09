# JNative optimizer (0.12)

作者：张晓达

JNative IR is optimized after semantic analysis and before LLVM lowering.

## Usage

```bash
jnative App.java -O2              # default
jnative App.java -O0              # no IR optimizations (reference behavior)
jnative App.java -O2 --opt-report
jnative App.java -O2 --emit-ir-before-opt --emit-ir-after-opt
jnative App.java -O2 --verify-ir
```

| Flag | Description |
|------|-------------|
| `-O0` … `-O3` | Optimization level (default **-O2**) |
| `--opt-report` | Print pass counters |
| `--emit-ir-before-opt` / `--emit-ir-after-opt` | IR dumps around the pipeline |
| `--emit-escape-analysis` | Escape notes in the report |
| `--explain-opt` | Verbose pass notes |
| `--verify-ir` | Run `IrVerifier` after optimization |

`--emit-ir` prints IR after optimization when `-O>0`.

## Pipeline (`io.jnative.opt`)

| Level | Passes |
|-------|--------|
| O0 | (skipped) |
| O1+ | escape analysis, constant propagation, scalar replacement, devirtualization (CHA/exact-type), null-check elimination, DCE |
| O2+ | inline (single-block safe), re-escape, scalar, BCE, safepoint elim, **stack allocation** of `NO_ESCAPE` managed objects, whole-program DCE |

Allocation decision after opt: scalar replace → stack allocate → heap (TLAB young / large). Write barriers on managed ref stores must not be incorrectly DCE'd or reordered past the store (0.13 GC2).

Clang also receives `-O0`…`-O3` matching the JNative level.

## Package layout

- `OptimizationPipeline`, `PassManager`, `OptimizationContext`, `OptimizationReport`
- `cfg/` — CFG, def-use, loop backedge detection
- Transform passes under `io.jnative.opt.*`

See also: [escape-analysis.md](escape-analysis.md), [stack-allocation.md](stack-allocation.md), [devirtualization.md](devirtualization.md), [inlining.md](inlining.md), [check-elimination.md](check-elimination.md).
