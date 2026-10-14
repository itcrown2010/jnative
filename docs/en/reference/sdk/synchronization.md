# Synchronization API

**Status:** Experimental · **Source:** `Mutex`, atomics used in `ThreadEndToEndTest`

## Overview

`Mutex` lock/unlock around critical sections; `AtomicInt` for counters.

## Example

`examples/multithreading/` · [synchronize-threads](../../recipes/synchronize-threads.md)

## Thread Safety

These primitives exist to build thread-safe user code; they are not a full `java.util.concurrent` port.

## Platform Notes

Native threads only.
