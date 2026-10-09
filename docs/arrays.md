# Arrays

作者：张晓达

## Syntax

```java
int[] a = new int[10];
Person[] people = new Person[2];
a[0] = 1;
int n = a.length;
```

Multidimensional arrays (`int[][]`) are not required in 0.4; `ArrayType` nesting
is allowed architecturally.

## Runtime layout

```text
┌─────────────────────┐
│ ClassMetadata*      │
├─────────────────────┤
│ length (i32)        │
├─────────────────────┤
│ elements...         │
└─────────────────────┘
```

Not a bare C pointer. Length, bounds checks, future GC scanning, and array RTTI
all depend on this header.

## IR

- `NewArray`
- `LoadElement` / `StoreElement`
- `ArrayLength`

Bounds and negative size checks call runtime helpers and abort with:

```text
JNative runtime error: array index out of bounds
JNative runtime error: negative array size
```

## Zero initialization

Matches Java: `0` / `0L` / `0.0` / `false` / `null`.

## Reference arrays

Store references (pointers), not inline object structs.
