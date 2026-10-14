# Concurrency

| Feature | Status |
|---------|--------|
| `Thread`, mutexes, atomics | Implemented (POSIX) |
| STW GC with safepoints | Implemented |
| `async` / `await` | Experimental (thin e2e) |

```java
Thread t = new Thread();
// use Thread subclass / start APIs as in threading docs & e2e
```

See [../sdk/threading.md](../sdk/threading.md) and [`../../threading.md`](../../threading.md).
