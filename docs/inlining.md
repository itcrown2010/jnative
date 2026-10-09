# Inlining

作者：张晓达

`InlinePass` runs at `-O2`+.

## Constraints (0.12)

First version is intentionally conservative for IR correctness:

- Single basic-block callees only (no CFG merge / phi)
- No `Alloca` / `StackAlloc` / stack `NewObject` in the callee (inlined allocas mid-block break entry GC-frame dominance)
- Instruction budget ≤ 20; module inline budget 64
- Prefer `PURE` / `@Deterministic`; otherwise skip allocating bodies
- Do not inline recursive self-calls or `main`

## Remapping

Callee temps and locals are remapped to fresh identities (`IrUtil.mapValues` maps destinations) so LLVM SSA names do not collide.
