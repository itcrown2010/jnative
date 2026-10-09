# Native effect contracts (0.11)

作者：张晓达

`@Native` methods declare trusted effects via `effects=`:

| Contract | Inferred effects |
|----------|------------------|
| `PURE` | none |
| `NO_BLOCK` | `NATIVE_CALL` only |
| `MAY_BLOCK` | `NATIVE_CALL \| BLOCKS` |
| `NO_CALLBACK` | `NATIVE_CALL` |
| `MAY_CALLBACK` | `NATIVE_CALL \| UNKNOWN` |
| default / omitted | `UNKNOWN` (conservative) |

Example:

```java
@Native(symbol = "abs", effects = "PURE")
static native int abs(int x);
```

`@Deterministic` managed code may call `PURE` natives. Untrusted or blocking natives require ordinary (non-contract) callers.
