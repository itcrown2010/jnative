# Errors and exceptions (language)

**Status:** Experimental · **Tests:** `ExceptionEndToEndTest`

## Language exceptions

`try` / `catch` / `finally` and throwable hierarchies exist. Declaration-form try-with-resources is implemented (see language-support / exceptions guide).

```java
try {
    // work
} catch (Exception e) {
    System.out.println(e.getMessage());
}
```

## Compiler vs runtime

| Kind | When | What you see |
|------|------|----------------|
| Syntax / semantic | compile | CLI JSON / VS Code Problems |
| Runtime exception | run | process abort / message (native) |
| Link / clang | compile late | linker errors |

## Related

[exceptions.md](exceptions.md) · [sdk/error-handling.md](../sdk/error-handling.md) · [troubleshooting](../troubleshooting/index.md)
