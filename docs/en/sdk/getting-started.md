# SDK getting started

**Status:** Experimental overall (console output Stable)

```java
public class Main {
    public static void main(String[] args) {
        File.writeString("out.txt", "hi");
        System.out.println(File.readString("out.txt"));
    }
}
```

1. Prefer APIs listed in the [capability matrix](../reference/capability-matrix.md).  
2. Run an example under `examples/` before adopting an API in production.  
3. Check platform: File/TCP/HTTP/Threads are **native**; wasm differs.

Next: [filesystem](filesystem.md) · [cookbook](../cookbook/index.md)
