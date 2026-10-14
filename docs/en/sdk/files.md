# Files

**Status:** Experimental / Implemented path via `IoLib` + runtime emitters

## Concept

File I/O is provided by the stdlib and native runtime—not by exposing `FILE*` to user code.

## API sketch

Use types from the IO stdlib (`FsPath`, streams, helpers). Authoritative deep dive: [`../../file-io.md`](../../file-io.md).

## Example

See `examples/file-io/` (verified README) and File I/O e2e tests.

## Errors / threads / platforms

IO failures surface as IO-related exceptions. Blocking I/O enters NATIVE for GC. Windows filesystem support is incomplete relative to POSIX.
