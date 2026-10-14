# Lambdas

**Status:** Implemented (SAM)

```java
Runnable r = () -> System.out.println("hi");
r.run();
```

Lambdas target functional interfaces (single abstract method). Closures capture locals per callable runtime rules. Example: `examples/LambdaClosure.java`.
