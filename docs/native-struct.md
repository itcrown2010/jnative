# NativeStruct (0.8)

作者：张晓达

`@NativeStruct` declares an **ABI data layout**, not a managed JNative class.

```java
@NativeStruct
class Point {
    double x;
    double y;
}
```

## Rules

| Property | NativeStruct | Managed class |
|----------|--------------|---------------|
| Object / GC header | **No** | Yes |
| ClassMetadata / vtable | **No** | Yes |
| Inheritance | **No** | Yes |
| Virtual methods | **No** | Yes |
| Layout source | `NativeStructLayout` + `TargetAbi` | `ClassLayout` |
| Storage | `@Stack` or native heap | `new` → GC heap |

## Allowed fields

- `byte`, `short`, `int`, `long`, `float`, `double`, `char`
- `NativePtr<T>`
- nested `@NativeStruct` (**inline value**, not a pointer)

**Forbidden:** `boolean` (use a future `NativeBool`), managed references (`String`, user classes), arrays, recursive inline structs.

Recursive links must use pointers:

```java
@NativeStruct
class Node {
    NativePtr<Node> next;
}
```

## Storage

```java
@Stack
Point p = new Point();   // alloca, zero-init

NativePtr<Point> h = Native.allocate<Point>();  // malloc(sizeof)
NativeMemory.free(h);
```

Bare `Point p = new Point();` is a semantic error — storage must be explicit.

## Layout

`--emit-layout` prints size, alignment, field offsets, and padding. Layout is shared by stack, native heap, and FFI.
