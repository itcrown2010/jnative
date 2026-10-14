# Control flow

**Status:** Implemented for classic forms.

```java
if (n > 0) { } else { }

while (n > 0) { n = n - 1; }

for (int i = 0; i < 3; i = i + 1) { }

for (String s : items) { } // arrays / Iterable

switch (n) {
    case 1: break;
    default: break;
}
```

`break` / `continue` supported; **labeled** break/continue **not supported**. Pattern switches **not supported**.
