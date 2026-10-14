# Exceptions

**Status:** Implemented

```java
try {
    throw new RuntimeException("boom");
} catch (RuntimeException e) {
    System.out.println(e.getMessage());
} finally {
    System.out.println("done");
}
```

- `try` / `catch` / `finally` / `throw`
- Try-with-resources **declaration** form supported
- Multi-catch **not supported**
- Checked `throws` **not enforced** (not SE)

Hierarchy docs: [`../../exceptions.md`](../../exceptions.md).
