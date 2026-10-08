# JNative Supported Language Profile (1.0 Audit)

**Profile ID:** `jnative-lang-1.0-candidate`  
**Audit date:** 2026-10-09  
**Maturity of profile as a whole:** **L2** (not L3 — missing optimizer identity, fuzz, multi-OS)  
**Rule:** Features not listed SUPPORTED must be treated as **NOT SUPPORTED IN 1.0**.

Evidence basis: compiler sources + `src/test` (especially `e2e/`, `PackageAndAccessTest`, `MultipackageResolveIntegrationTest`). Host for e2e: macOS arm64 unless noted.

---

## 1. Official status legend

| Status | Meaning |
|---|---|
| **SUPPORTED** | In language profile; has implementation + tests at stated Level |
| **NOT SUPPORTED IN 1.0** | Must not be used; reject or absent — do not ship half-implemented |
| **EXPERIMENTAL** | Exists but API/semantics may change; not Stable |

Levels: L0–L4 per readiness audit.

---

## 2. Types & OOP

| Feature | Status | Level | Evidence / Notes |
|---|---|---|---|
| `byte` `short` `int` `long` `float` `double` `boolean` `char` | SUPPORTED | L2–L3 | Widespread e2e |
| `class` / fields / methods | SUPPORTED | L3 | All apps |
| `interface` | SUPPORTED | L2–L3 | `InterfaceEndToEndTest`; **no** default/static interface methods |
| `abstract class` | SUPPORTED | L2–L3 | Same |
| inheritance / `extends` | SUPPORTED | L3 | Inheritance + vtable e2e |
| `implements` | SUPPORTED | L2–L3 | Interface e2e |
| constructor / `this` / `super` | SUPPORTED | L2–L3 | Constructor semantic + e2e |
| `static` | SUPPORTED | L3 | |
| visibility (`public`/`private`/`protected`/package) | SUPPORTED | L2 | Enforced for types/members; `PackageAndAccessTest` |
| `package` declaration | SUPPORTED | L2 | Language package ≠ pkg-manager coordinate |
| explicit type `import` | SUPPORTED | L2 | No wildcard / static import (hard error) |
| same-package + FQN type refs | SUPPORTED | L2 | Expression-position dotted FQN receivers not yet |
| multi-file / third-party source merge | SUPPORTED | L2 | Concat unit + per-type package/import; line maps |
| `.jn` / `.java` suffixes | SUPPORTED | L2 | Same language; `jnative new` defaults to `.jn` |
| `final` (fields/locals) | **NOT SUPPORTED IN 1.0** | L0–L1 | Unverified / incomplete |
| method overload | SUPPORTED | L2–L3 | Ambiguity diagnosed; `OverloadEndToEndTest` |
| constructor overload | SUPPORTED | L2 | |
| `override` (virtual dispatch) | SUPPORTED | L3 | Dynamic dispatch e2e |
| arrays | SUPPORTED | L3 | Runtime + e2e |
| generics (basic / specialized collections) | SUPPORTED | L2–L3 | `GenericsEndToEndTest` |
| wildcards `?` / `? extends` / `? super` | EXPERIMENTAL | L1 | Parsing exists; **no native wildcard e2e** |
| lambda / SAM | SUPPORTED | L2–L3 | `CallableEndToEndTest` |
| method reference `::` | SUPPORTED | L2–L3 | Same |
| annotations (compile-time / meta) | SUPPORTED | L2 | Meta032 processors; limited native app loop |
| `enum` | **NOT SUPPORTED IN 1.0** | L0 | No language keyword |
| `record` | **NOT SUPPORTED IN 1.0** | L0 | |
| pattern matching / switch patterns | **NOT SUPPORTED IN 1.0** | L0 | Classic `instanceof` only |

---

## 3. Control flow & exceptions

| Feature | Status | Level | Evidence / Notes |
|---|---|---|---|
| `if` / `else` | SUPPORTED | L3 | |
| `while` / `do`/`while` / `for` | SUPPORTED | L2–L3 | |
| enhanced `for` | **NOT SUPPORTED IN 1.0** | L0 | |
| `switch` / `case` | **NOT SUPPORTED IN 1.0** | L0 | No tokens/AST |
| `break` / `continue` | SUPPORTED | L2–L3 | `LoopControlEndToEndTest`; O0/O2/O3 |
| labeled break/continue | **NOT SUPPORTED IN 1.0** | L0 | |
| `return` | SUPPORTED | L3 | |
| `try` / `catch` / `finally` | SUPPORTED | L2–L3 | `ExceptionEndToEndTest` |
| try-with-resources | SUPPORTED | L2–L3 | `ResourceEndToEndTest` |
| `throw` | SUPPORTED | L3 | |
| multi-catch | **NOT SUPPORTED IN 1.0** | L0–L1 | |
| checked `throws` enforcement | **NOT SUPPORTED IN 1.0** | L0 | |
| ternary `?:` (expression) | **NOT SUPPORTED IN 1.0** | L0 | `?` is wildcard-only |
| `async` / `await` | SUPPORTED | L2 | Thin e2e; dual Future debt |

---

## 4. Operators & conversions

| Feature | Status | Level | Evidence / Notes |
|---|---|---|---|
| arithmetic `+ - * / %` | SUPPORTED | L2–L3 | |
| comparison / logical `&& \|\| !` | SUPPORTED | L3 | |
| bitwise `& \| ^ ~` | **NOT SUPPORTED IN 1.0** | L0 | No tokens |
| shifts `<< >> >>>` | **NOT SUPPORTED IN 1.0** | L0 | |
| varargs `...` | **NOT SUPPORTED IN 1.0** | L0 | |
| primitive casts `(int)x` | **NOT SUPPORTED IN 1.0** | L0 | Profile: not supported; use typed APIs |
| reference cast / `instanceof` | SUPPORTED | L2–L3 | RuntimeError + Interface e2e |
| string `+` | SUPPORTED | L2–L3 | |

### Numeric semantics (1.0 expectations)

| Topic | Status |
|---|---|
| integer overflow (wrap) | Assumed two's complement via LLVM — **no dedicated suite** |
| integer division / modulo | Used in e2e — **no edge corpus** |
| float NaN / Infinity / `-0.0` | **NOT VERIFIED** systematically |
| shift masking / signedness | N/A (shifts unsupported) |

---

## 5. String contract (Stable rules)

| API / rule | Semantics |
|---|---|
| `length()` | UTF-8 **byte** count (not Unicode code points) |
| `split(delimiter)` | **Literal** delimiter (NOT regex) |
| empty delimiter | `[this]` |
| `"".split(",")` | `[""]` |
| consecutive / trailing empties | kept |
| null delimiter | NPE |
| `StringBuilder` | Present in CollectionsLib — Level **L2** (not full Java parity) |

---

## 6. Compiler correctness gates (profile)

| Gate | Status |
|---|---|
| Illegal source → diagnostic (no NPE/crash) | PARTIAL — many rejects; **no fuzz corpus** |
| O0 / O2 / O3 same semantics | PARTIAL — `DifferentialOptTest` O0↔O2 only; LoopControl runs three opts |
| Overload vertical e2e | PASS — must remain regression |
| Lexer/parser fuzz | **MISSING** |

---

## 7. Explicit 1.0 exclusions (do not half-implement)

Ship as hard errors or absence — **never** silent wrong codegen:

1. `enum`  
2. `switch`  
3. ternary expressions  
4. bitwise / shift operators  
5. varargs  
6. records / pattern matching  
7. labeled break  
8. primitive cast syntax (until implemented + tested)  

---

## 8. Profile stability statement

For 1.0 RC, freeze this profile:

- **Stable API surface** = SUPPORTED rows at ≥ L2 with native e2e  
- Raising SUPPORTED → Stable product tier requires ≥ **L3** (real apps + failure paths)  
- Adding new syntax after RC1 requires a language RFC + e2e + opt differential  

**Current product truth:** language core is a **usable subset**, not Java SE.
