# Recipe: Synchronize threads

```java
static Mutex lock = new Mutex();
// inside run():
lock.lock();
// critical section
lock.unlock();
```

See `ThreadEndToEndTest` and `examples/multithreading/`.
