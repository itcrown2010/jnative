# JNative Runtime Stability Audit — Phase 0.8

作者：张晓达

**Date:** 2026-10-08  
**Host audited:** macOS arm64 (darwin), clang `/usr/bin/clang`, lldb present, Homebrew `llvm-as` available  
**Rule:** Code is authoritative; prior docs may be stale.

---

## Module status (actual)

| Subsystem | Status | Evidence |
|-----------|--------|----------|
| Compiler → LLVM IR | EXISTS | `LlvmIrGenerator.java` |
| Mid-IR verifier | EXISTS | `opt/IrVerifier.java` (`--verify-ir`) |
| LLVM `.ll` verifier | **ADDED 0.8** | `LlvmIrFileVerifier` (`llvm-as` or `clang -c`) |
| Native link (clang) | EXISTS | `Toolchain` / `NativeLinker` |
| Debug symbols (`-g`) | **ADDED 0.8** | default on; `--no-debug-symbols` to disable |
| Build profiles | **ADDED 0.8** | `--debug` / `--release` |
| Sanitizers | **ADDED 0.8** | `--sanitize=address\|undefined\|address,undefined\|thread` |
| GC2 (default) | EXISTS | `jnative_gc2.c` — **young gen MOVING (Cheney)** |
| Legacy mark-sweep | EXISTS | `JNATIVE_GC=marksweep` |
| Threads + STW | EXISTS | `jnative_mt.c` (POSIX only) |
| FFI enter/leave | EXISTS | `jn_enter_native` / `jn_leave_native` |
| Exception model | EXISTS | TLS pending bit — **not** C++/DWARF unwind across C ABI |
| Crash diagnostics | **ADDED 0.8** | `jnative_crash.c` (async-signal-safe) |
| AsyncTcp true async | BLOCKED | blocking-adapter / Experimental |
| Linux epoll | STUB/PARTIAL | |
| Windows sockets/GC/MT | MISSING/STUB | |
| PostgreSQL SCRAM | BLOCKED | MD5/trust only |
| Browser WASM GC | N/A | bump heap, not GC2 |

---

## GC truth

- Young: Cheney semi-space **moves** objects (`forward_ref` / `FLAG_FORWARDED`).
- Old / Large: malloc mark-sweep (non-moving).
- Header: **32 bytes** (`JN_GC_HEADER_SIZE`).
- Roots: shadow frames, exception roots, thread objects, statics.
- Safepoints: alloc / method entry / loop backedge.
- `FLAG_PINNED`: defined but **not enforced** in `forward_ref` — FFI must not retain managed raw pointers across safepoints.
- **0.8 fix:** `jn_gc2_verify_heap` fills TLAB waste before scan; small TLAB tails (`< header`) no longer advance `g_eden_high` into uninitialized memory.

---

## Crash classification (diagnostic paths)

| Class | Detection | Diagnostics |
|-------|-----------|-------------|
| Normal exit (0) | process status | logs |
| Non-zero exit | `WEXITSTATUS` | stderr + `jn_ex_print_stack` if throw path |
| Uncaught exception | runtime abort/print | TLS stack capture + `-rdynamic` / `-g` |
| SIGSEGV / SIGABRT / SIGBUS / SIGILL | `jn_crash_handler` | build_id + signal; then OS reporter / core |
| Illegal instruction | SIGILL handler | same |
| OS terminate/kill | SIGTERM/SIGKILL | OS tools |
| Timeout | harness | `waitFor` in e2e / soak script |
| Deadlock | NOT auto-detected | manual / TSan (partial) |
| WASM trap | browser console | WASM path separate; not GC2 |

Signal handlers only use `write(2)` + re-raise — no malloc/printf in handler.

---

## Existing stability-related tests (pre-0.8)

GC: `GcEndToEndTest`, `Gc2EndToEndTest`, `GcRelocationEndToEndTest`, `GcNativeValidationEndToEndTest`  
Threads: `ThreadEndToEndTest`  
FFI: `FfiEndToEndTest`, `NativeCallbackEndToEndTest`  
Network+GC: `NetworkEndToEndTest.gcStressNetwork`, `HttpEndToEndTest.gcStressHttp`, `AsyncIoRound4EndToEndTest`  
Exceptions: `ExceptionEndToEndTest`

---

## Gaps closed in 0.8

1. Debug/Release + sanitizer CLI flags  
2. DWARF `-g` by default  
3. LLVM IR file verify before link  
4. RuntimeCrashDiagnostics  
5. RuntimeStabilitySuite + TLAB verify regression  
6. GC verify false-positive under live TLAB  

## Gaps remaining

1. Long soaks 10m / 30m / 2h — **NOT_RUN** unless `JNATIVE_STABILITY_SOAK` set  
2. Every-allocation GC stress (`JNATIVE_GC_STRESS=1`) still harsh; soft stress is the gated path  
3. No managed-object pin API  
4. Windows runtime / epoll / true AsyncTcp / PG SCRAM unchanged  

---

## Forbidden practices (compliance)

This phase did **not**: disable GC, disable threads, swallow non-zero exits, skip failing tests as PASS, or invent soak results.
