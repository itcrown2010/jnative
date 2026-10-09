# wasm32-browser float / double contract

Status: **available** (verified with Node WebAssembly + `examples/browser/float-calc`).

## Mapping

| JNative | WAT / WASM |
|---------|------------|
| `float` | `f32` |
| `double` | `f64` |
| params / returns / locals / statics | same typed locals/globals |
| field / array element | `f32.load/store`, `f64.load/store` |

Array layout (browser): `meta i32 @0` (unused/0), `length i32 @8`, payload `@16` (matches native data offset).

## Arithmetic

Supported: `+ - * / %`, unary `-`, comparisons `== != < <= > >=`.

- No fast-math, no FMA fusion, no reassociation beyond what IR already emits.
- Unary `-` lowers as `(-0) - x` so IEEE sign of zero is preserved.
- `%` is Java remainder (truncating quotient) via `f32.rem` / `f64.rem` — same family as LLVM `frem`, **not** IEEE 754 remainder.
- Comparisons: WASM `eq`/`ne`/`lt`… match Java (NaN: `==` false, `!=` true; ordered relations false).

## Conversions

| Direction | Instruction |
|-----------|-------------|
| int/long → float/double | `f*.convert_i*_s` |
| f32 ↔ f64 | `promote_f32` / `demote_f64` |
| float/double → int/long | `i*.trunc_sat_f*_s` |

`trunc_sat` contract (aligned with JLS numeric conversion / native helpers):

- NaN → 0
- toward zero
- out-of-range → type min/max (no trap)

Requires the WebAssembly **nontrapping float-to-int** feature (default in modern `wat2wasm` / engines).

IR emits `Cast` for fp→int; WASM lowers with `trunc_sat`. Native LLVM lowers the same Cast via `jnative_f*_to_i*` (do not call those builtins from WASM).

## Literals

- Decimal / exponent forms; suffixes `f`/`F`, `d`/`D` supported.
- Hex floats and digit separators: compile-time errors (language-wide).

## JS interop

- Exported `f64` params/results map to JS `Number`.
- Exported `f32` is rounded at the boundary by the engine.
- Exported `i64` may appear as JS `BigInt`.
- Host may parse/format decimal text; **must not** reimplement arithmetic in JS.
- Input parsing for the sample UI: ASCII digits, optional sign/exponent, `.` decimal only (no locale commas).
- Display: `Object.is` for `-0`, `Number.isNaN` for NaN; not bit-identical to `Double.toString`.

## Frontend notes

- `JType.binaryArithmeticResult` / IR `promote` follow Java order: double > float > long > int.

## Verify

```bash
mvn -DskipTests package
./examples/browser/float-calc/scripts/verify.sh
# optional UI: cd examples/browser/float-calc/dist && python3 -m http.server 8766
```

Recorded check environment: Node v24.1.0, wat2wasm 1.0.39 (Darwin).
Native float suite: `FloatEndToEndTest` (separate; do not infer WASM from native).

## Remaining limits

- No claim of full Java `Math.*` or boxed `Float`/`Double` on the browser path
- Enhanced-for not lowered in IR yet
- Full `-O0` rebuild of larger unrelated programs may still fail outside this float slice
