# Classes

**Status:** Implemented

```java
public class Task {
    String title;
    int status;

    Task(String title) {
        this.title = title;
        this.status = 0;
    }

    void complete() { this.status = 2; }
}
```

- `new Task("x")`
- Fields, constructors (`this` / `super` delegation), instance & static members
- Object lifecycle: managed heap (GC) unless stack/native allocation paths apply for native structs

**Not supported:** `record`, `final` fields.
