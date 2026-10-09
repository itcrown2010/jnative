# @NoGC (0.11)

作者：张晓达

`@NoGC` methods must not allocate, safepoint, or call opaque (`UNKNOWN`) code.

## Compile time

`EffectAnalyzer` rejects violations and prints an **effect path**, e.g.:

```text
@NoGC function 'foo' may allocate

effect path:
  C.foo()
  → C.bar()
  → new Person
```

## IR

Methods with `@NoGC` (or inferred non-safepoint effects) omit loop-backedge `safepoint.poll` IR.

## Runtime

- Entry: `jn_nogc_enter()` raises thread-local `no_gc_depth`
- Exit: `jn_nogc_leave()` before each return
- `jnative_gc_alloc` aborts if `jn_nogc_depth() > 0`

This catches allocation bugs that slip past static analysis.
