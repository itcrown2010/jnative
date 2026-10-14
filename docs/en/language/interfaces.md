# Interfaces

**Status:** Implemented with limitations

```java
interface Greeter {
    void greet();
}
class Hello implements Greeter {
    public void greet() { System.out.println("hi"); }
}
```

- Multiple `implements` allowed
- Dispatch via interface tables
- **No** default methods, **no** static interface methods
- Functional interfaces used by lambdas (SAM)
