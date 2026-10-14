# Types

**Status:** Implemented for the listed primitives and reference types.

## Primitives

`byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char`

```java
int n = 42;
double x = 3.5;
boolean ok = true;
char c = 'A';
```

Conversions: primitive casts `(int)x` for numeric types; boolean casts rejected. Float/double NaN rules follow the float contract docs.

## References

- Class types, interface types, arrays
- `String` (managed)
- `null` for references

```java
String s = null;
Object o = s;
```

See [arrays.md](arrays.md), [strings.md](strings.md), [generics.md](generics.md).
