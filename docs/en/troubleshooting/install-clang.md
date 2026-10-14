# Missing clang

## Symptom
`jnative doctor` reports Linker/clang failure.

## Cause
`clang` not on `PATH`.

## Fix
Install Xcode CLT (macOS) or clang package (Linux). Re-run `doctor`.
