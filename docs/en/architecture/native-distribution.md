# Native executable distribution

```text
Source (.jn / .java)
  ↓  jnative.jar (JVM host compiler)
IR → LLVM IR
  ↓  clang + JNative runtime objects
Native executable (Mach-O / ELF / …)
  ↓
Distribution
```

## What we observe (macOS arm64)

- Output is a host native binary (`file` reports Mach-O arm64)  
- Runtime does **not** require a JRE to execute the app  
- The **compiler** still runs on JVM when you invoke `java -jar target/jnative.jar`  
- Do not advertise “single static binary with zero deps” unless `otool -L` / `ldd` evidence is recorded per release  

## Studio implication

Studio Core is a native binary that **spawns** `java -jar jnative.jar` to build projects. That is toolchain client usage, not a JVM sidecar inside the Studio process heap.
