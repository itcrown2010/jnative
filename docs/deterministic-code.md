# @Deterministic (0.11)

`@Deterministic` is sugar for `@NoGC @NoThrow @NoBlock`: the method must be pure from the runtime's perspective.

Allowed:

- Arithmetic, local variables, control flow
- Calls to `@Native(effects = "PURE")` functions
- `Atomic*` operations

Rejected:

- Allocation, I/O, sleep, println, throws, capturing lambda creation

Use for hot loops, signal handlers, and native callbacks where GC pauses or blocking are forbidden.

Example:

```java
@Deterministic
static int sum(int n) {
    int s = 0;
    for (int i = 0; i < n; i = i + 1) {
        s = s + i;
    }
    return s;
}
```
