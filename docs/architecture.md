# JNative Architecture

```text
.java
  → Lexer / Parser / AST
  → Semantic (types, constructors, arrays, String, RTTI, exceptions)
  → JNative IR (Throw, try CFG, GcCollect, PTR root allocas)
  → LLVM (ClassMetadata, shadow stack, GC + exception runtime)
  → clang → native executable
```

## Key packages

- `semantic` — `ClassMetadata` with flattened reference byte offsets; Throwable hierarchy
- `layout` — object payload layout (ClassMetadata* header)
- `backend.llvm.runtime` — Memory / Gc / **Exception** / Array / String / Type / Io emitters

## GC (0.5)

Precise STW mark-sweep; Java refs point past a 24-byte `GcHeader`; roots via shadow stack of PTR alloca addresses. Exception current pointer is also a precise GC root.

## Exceptions (0.6)

Explicit pending-exception propagation. See [exceptions.md](exceptions.md).
LLVM does not perform Java catch-resolution; IR already encodes dispatch CFG.

## Invariants

1. `ClassLayout` is the only field-layout source of truth
2. LLVM does not re-resolve Java methods, types, or catch matching
3. All managed allocations are GC-tracked and enumerable
4. Roots are precise (not conservative stack scans)
5. Exception objects are ordinary GC-managed instances
