# Devirtualization

`DevirtualizePass` (O1+) rewrites `VirtualCall` → `Call` when the receiver temp traces (via load/store aliases) to a `NewObject` of a known class and the vtable slot resolves to a single target.

Polymorphic calls through parameters or distinct subclasses are unchanged. Counters: `devirtualize` in `--opt-report`.
