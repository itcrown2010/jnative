# Escape analysis

作者：张晓达

`EscapeAnalysisPass` classifies each `NewObject` temp intraprocedurally:

`NO_ESCAPE`, `ARG_ESCAPE`, `RETURN_ESCAPE`, `GLOBAL_ESCAPE`, `THREAD_ESCAPE`, `NATIVE_ESCAPE`, `UNKNOWN`.

## Alias tracking

Allocation identity is tracked through local `store` / `load` aliases so that:

```java
Box b = new Box(1);
return b;   // RETURN_ESCAPE on the allocation, not only on the load temp
```

Closure environments linked from `MakeCallable` follow the callable’s escape through the same alias chain.

## Escalation rules

| Use | Effect |
|-----|--------|
| Returned | `RETURN_ESCAPE` |
| Stored into field of receiver `R` | value escapes as much as `R` (not `R` itself) |
| Stored to static | `GLOBAL_ESCAPE` |
| Passed as call / virtual / native arg | `ARG_ESCAPE` / `NATIVE_ESCAPE` |
| Constructor `this` (arg 0) | not an escape |
| Captured by escaping callable / native callback | env follows callable |

Storing into a receiver does **not** make the receiver escape.

## Consumers

- `ScalarReplacementPass` — `NO_ESCAPE` primitive-only
- `StackAllocationPass` — remaining `NO_ESCAPE`
- `--emit-escape-analysis` / `--explain-opt` — diagnostics
