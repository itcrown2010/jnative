# Arrays

**Status:** Implemented

```java
int[] xs = new int[3];
xs[0] = 1;
int n = xs.length;

String[] names = new String[2];
names[0] = "a";
```

Bounds checks at runtime (subject to optimizer elimination when proven safe). Multidimensional arrays follow Java-like `new int[2][3]` forms supported by the parser/runtime path used in tests.
