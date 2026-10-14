# JNative vs Java (honest comparison)

| Area | JNative | Java (HotSpot) |
|------|---------|----------------|
| Syntax | Java-like subset | Java language |
| App runtime | Native executable + JNative runtime | JVM |
| Native executable | Yes (AOT via LLVM/clang) | Usually needs JVM (or separate AOT products) |
| JAR / Maven ecosystem | No arbitrary JAR consumption | Yes |
| Garbage collection | JNative GC2 (generational) | JVM GC |
| LLVM backend | Yes | No (bytecode VM) |
| Platform maturity | Experimental / uneven CI | Mature |
| Reflection / dynamic proxies | Not a SE substitute | Core platform feature |

Use JNative when you want Java-shaped source and a **native** binary you control. Use Java when you need the JDK ecosystem.
