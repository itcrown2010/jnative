# JNative for Java developers

## Core message

> JNative uses familiar **Java-like syntax** while targeting **native executable** applications.

It is **not** positioned as “Java but faster” unless you measure a specific workload yourself. The architectural difference is the product.

## What feels familiar (when supported)

- Classes, fields, constructors, methods  
- `extends` / `implements`  
- Basic generics, lambdas (SAM), exceptions  
- `main`, `System.out.println`  
- Packages / imports (project-dependent)

## What is different

| Topic | Java (typical) | JNative |
|-------|----------------|---------|
| Compilation | bytecode / JVM | AOT → LLVM → native binary |
| Runtime | HotSpot / other JVM | JNative runtime + GC2 |
| Libraries | JDK + Maven JARs | JNative SDK modules |
| Distribution | JAR + JRE | Host executable (or wasm sample) |
| FFI | JNI | JNative `@Native` / FFI (experimental) |
| Memory | JVM GC | GC2 + native sections for blocking FFI/IO |

## What JNative is NOT

- Not a drop-in Java SE replacement  
- Not a guarantee that any Maven library works  
- Not “install JDK and run `.class` files”  
- Not a production native mobile UI toolkit (see mobile status)

## Next

[basics](basics.md) · [30-minute tutorial](../tutorials/30-minutes.md) · [jnative-vs-java](../getting-started/jnative-vs-java.md)
