# Stack Allocation (0.8)

```java
@Stack
Point p = new Point();
```

Requires `@NativeStruct`. Managed classes (`@Stack Person`) are rejected in 0.8.

## Lowering

```text
@Stack Point p = new Point();
  → IR:  stackalloc Point
  → LLVM: alloca %struct.Point + memset zero
```

**Zero GC allocation.** No Object header, ClassMetadata, or vtable.

## Lifetime

Valid for the current function invocation only. Leaving the function invalidates the storage.

## AddressOf

```java
NativePtr<Point> ptr = Native.addressOf(p);
```

Produces a `STACK` provenance pointer. Passing it to a native call is allowed (non-capturing contract). Returning it or storing it into a managed field is a **compile-time error**.

## Exceptions

On `throw` / unwind, stack frames disappear with the function — no manual cleanup.
