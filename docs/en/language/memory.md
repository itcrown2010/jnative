# Memory

**Status:** Experimental (GC2) · Docs: [architecture/gc.md](../architecture/gc.md)

## What it is

Managed objects live on the JNative heap and are reclaimed by a generational GC (GC2) with precise roots / shadow stacks.

## Why it exists

Native executables still need automatic memory management for Java-like objects—without a JVM heap.

## Mental model

```text
Your objects  →  managed heap (GC)
Native/FFI    →  outside managed heap (careful ownership)
```

## Common mistakes

- Calling into blocking native I/O without understanding NATIVE/GC interaction (runtime enters NATIVE for STW safety).  
- Assuming `finalize` / JVM soft references.

## Limitations

wasm32-browser uses a constrained GC model (single-threaded). Do not port thread-heavy designs there.

## Related

[concurrency](concurrency.md) · [native-interoperability](native-interoperability.md)
