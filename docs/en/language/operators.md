# Operators

**Status:** Implemented

Arithmetic `+ - * / %`, comparison, logical `&& || !`, bitwise `& | ^ ~`, shifts `<< >> >>>` (distance masked like Java), string `+`, ternary `?:`, `instanceof`, casts.

```java
int a = 1 + 2;
boolean b = a > 0 && true;
String s = "n=" + a;
Object o = s;
if (o instanceof String) { }
```
