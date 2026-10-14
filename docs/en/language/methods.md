# Methods

**Status:** Stable (core) · **See also:** [functions.md](functions.md)

## What it is

Instance and static methods with Java-like signatures. Overloading is supported (e2e).

## Syntax

```java
class Counter {
    int value;
    void inc() { this.value = this.value + 1; }
    static int add(int a, int b) { return a + b; }
}
```

## Common mistakes

- Treating `varargs` / default methods as available — verify before use.  
- Assuming reflection (`Method.invoke`) — **Not Supported**.

## Related

[classes](classes.md) · [lambdas](lambdas.md)
