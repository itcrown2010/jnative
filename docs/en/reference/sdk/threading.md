# Threading API

**Status:** Experimental · **Native only** · **Source:** builtin `Thread` + e2e `ThreadEndToEndTest`

## Overview

Subclass `Thread`, override `run()`, call `start()` / `join()`.

## Methods

| Name | Notes |
|------|-------|
| `run()` | Override |
| `start()` | Start once (double-start behavior covered in e2e) |
| `join()` | Wait |

## Example

See `examples/multithreading/` and [start-thread recipe](../../recipes/start-thread.md).

## Platform Notes

**Not supported** on wasm32-browser / mobile containers.

## Known Limitations

Not Java `ExecutorService` complete; prefer documented patterns.
