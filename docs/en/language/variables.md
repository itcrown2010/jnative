# Variables

**Status:** Implemented (locals / fields). **`final` fields: Not supported.**

```java
int count = 0;
String name = "task";
final int max = 10; // local final OK
```

Locals participate in definite-assignment analysis. Fields are zero/null-initialized per type rules.

`var` local-variable type inference is **not** documented as a Stable 1.0 feature in the language audit—prefer explicit types.
