# Generics

| Area | Status |
|------|--------|
| Basic type parameters / specialized collections | Implemented |
| Wildcards `?`, `? extends`, `? super` | Experimental |
| Full Java generic reflection | Not supported |

```java
ArrayList<String> list = new ArrayList<String>();
list.add("a");
```

Compiler performs erase + specialize lowering (`GenericLowering`) before IR. Prefer concrete usages covered by e2e (`GenericsEndToEndTest`).
