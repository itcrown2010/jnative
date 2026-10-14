# Strings

**Status:** Implemented (UTF-16 code-unit model)

```java
String s = "Hello";
int n = s.length();
char c = s.charAt(0);
String t = s.substring(0, 2);
String u = s + "!";
```

- Indices are UTF-16 **code units** (Java-compatible)
- `utf8Length` / byte helpers exist for protocols
- `split(delimiter)` uses a **literal** delimiter, not regex

Details: [`../../string.md`](../../string.md).
