# JNative 0.9 Concurrent Memory Model

作者：张晓达

This is a **documented subset**, not a claim of full Java Memory Model compatibility.

> **LLVM `volatile` ≠ Java `volatile`.**  
> JNative lowers Java `volatile` to LLVM **atomic** loads/stores with acquire/release ordering.

## Ordinary fields

Unsynchronized concurrent access is a data race (undefined behavior for races), same spirit as Java: use `Mutex`, `volatile`, or `Atomic*`.

## Happens-before (minimum)

| Edge | Rule |
|------|------|
| `Thread.start()` | Writes before `start()` in the starter are visible to the new thread |
| Thread termination → `join()` return | Worker writes are visible to the joiner after `join()` returns |
| `Mutex.unlock` → later `lock` | Unlock happens-before subsequent lock acquisition |
| `volatile` write → later `volatile` read | Release store happens-before acquire load of the same field |
| `Atomic*` | Public ops use **seq_cst** by default (correctness first) |

## Atomics

Internal `MemoryOrder`: `RELAXED`, `ACQUIRE`, `RELEASE`, `ACQ_REL`, `SEQ_CST`.  
Public `AtomicInt`/`AtomicLong`/`AtomicBool` default to seq_cst.

## What we do not claim

- Full JMM (including final-field freeze, interruptible locks, etc.)
- Automatic race detection
- Lock-free collections / `java.util.concurrent`
