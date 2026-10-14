# Methods & functions

**Status:** Implemented (methods on classes/interfaces). No free-standing top-level functions outside types.

```java
class Calc {
    static int add(int a, int b) { return a + b; }
    int mul(int a, int b) { return a * b; }
}
```

- Overloading: supported (ambiguity diagnosed)
- Visibility: `public` / `private` / `protected` / package
- `static` vs instance methods
- **Varargs `...`:** Not supported in 1.0 profile
