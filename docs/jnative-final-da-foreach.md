# Final, definite assignment, and array enhanced-for

**Date:** 2026-10-09  
**Scope:** Language core only (no ERP / mobile).

## Summary

| Feature | Status |
|---|---|
| `final` local variables / parameters | Supported |
| Blank final locals (assign once on all paths) | Supported |
| `final` class / instance method | Supported (no extend / no override) |
| `final` fields | **Rejected** with diagnostic |
| Local definite-assignment (DA) | Supported (CFG-style merge) |
| Array enhanced-for | Supported (lowered to index loop) |
| Iterable enhanced-for | Supported (see `jnative-generics-iterable.md`) |

## Definite assignment

Tracked with a per-method `DaEnv` (identity map of locals/params → assigned / unassigned).

- **Merge** = intersection (DA only if DA on every incoming path).
- **if/else**, **ternary**, **`&&` / `||`** short-circuit merges.
- **while / for**: body analyzed from pre-loop state; after-loop merges zero-trip / cond-false with `break` exits. `while (true)` without break → unreachable after.
- **do/while**: body always entered once.
- **switch**: fall-through keeps state; each case also intersects jump-from-entry (pre-switch); after merges breaks + fall-off + (no default ⇒ pre-switch).
- **return / throw / break / continue** mark the path unreachable.
- Backend zero-init of locals does **not** count as DA.

Illegal:

```java
int value;
if (condition) { value = 1; }
System.out.println(value); // error
```

Legal when both branches assign (or default + all cases, etc.).

## Final rules

- Initialized `final` local / parameter: no reassignment.
- Blank `final` local: assign only when definitely unassigned; must be DA before use.
- Outer blank `final` assignment inside a loop is rejected (stricter one-pass rule vs full JLS loop DU).
- Loop-local `final` (declared in loop body) may be assigned once per declaration.
- `final` reference: cannot rebind; object fields remain mutable.
- `final class`: cannot be extended.
- `final` instance method: cannot be overridden.
- `final` fields: explicit “not supported” error (no silent ignore).

Compile-time constants for `case` labels remain the existing literal/cast rules — **not** every `final` local.

## Array enhanced-for

```java
for (int value : numbers) {
    total = total + value;
}
```

- Array expression evaluated **once** (stored in a temp).
- Element variable: loop body scope; assigning it does not write the array.
- `null` → runtime null-check; empty array → zero iterations.
- `break` / `continue` / nesting / name shadowing supported.
- Safepoint on the index-loop back-edge (same as classic `for`).
- Non-array (including putative Iterable) → hard error.

## Files touched (core)

- Lexer: `TokenType.FINAL`
- AST: `VariableDeclaration.isFinal`, `Parameter.isFinal`, `MethodDeclaration.isFinal`, `ClassDeclaration.isFinal`, `EnhancedForStatement`
- Parser: modifiers, enhanced-for lookahead, reject final fields
- Semantic: `DaEnv`, DA/final checks, final class/method, array-only foreach
- IR: `lowerEnhancedFor` → index loop
- Tests: `FinalDaForeachEndToEndTest`
- Example: `examples/FinalDaForeachDemo.java`

## Limits

- No full JLS exception-path DA; try/catch uses simplified reachability.
- No final fields / static blank finals.
- No Iterable foreach / stream forEach.
- WASM: verify separately; native e2e does not imply WASM.
