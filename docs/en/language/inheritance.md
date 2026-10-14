# Inheritance

**Status:** Implemented

```java
class Animal {
    void speak() { System.out.println("..."); }
}
class Dog extends Animal {
    void speak() { System.out.println("woof"); }
}
```

Virtual dispatch via vtables. `super` for parent construction/calls. Abstract classes supported.

See e2e examples under `examples/Polymorphism.java`, `DynamicDispatch.java`.
