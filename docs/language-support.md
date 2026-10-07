# JNative Supported Language Profile

**Profile:** Language Core 0.x (Round 1.1)  
**Evidence basis:** compiler + native e2e on macOS arm64  
**Maturity of this profile:** **L2** (implemented + unit/negative/native tests for declared features)

This document defines what is **officially supported**. Features not listed as SUPPORTED must not be assumed present.

| Feature | Status | Level | Notes |
|---|---|---|---|
| `byte` `short` `int` `long` `float` `double` `boolean` `char` | SUPPORTED | L2 | Happy + e2e coverage via existing suites |
| `class` / `interface` / `abstract class` | SUPPORTED | L2 | |
| inheritance / override | SUPPORTED | L2 | |
| method overload | SUPPORTED | L2 | Fixed Round 1 (semantic + IR) |
| constructor / `this` / `super` | SUPPORTED | L2 | |
| `static` | SUPPORTED | L2 | |
| `null` | SUPPORTED | L2 | |
| arrays | SUPPORTED | L2 | |
| generics (basic) | SUPPORTED | L2 | Primitive specialization exists for some collections |
| lambda / method reference | SUPPORTED | L1–L2 | See callable e2e |
| `if` / `if-else` | SUPPORTED | L2 | |
| `while` | SUPPORTED | L2 | |
| `do` / `while` | SUPPORTED | L2 | Round 1.1 |
| `for` | SUPPORTED | L2 | |
| `break` | SUPPORTED | L2 | Round 1.1; BreakTarget model (loop today; switch later) |
| `continue` | SUPPORTED | L2 | Round 1.1; `for` continues to update |
| `return` | SUPPORTED | L2 | |
| `try` / `catch` / `finally` | SUPPORTED | L2 | |
| `break`/`continue` × `finally` | SUPPORTED | L2 | Finally runs; abrupt completion kinds |
| `throw` / exceptions | SUPPORTED | L2 | |
| `enum` | NOT YET SUPPORTED | L0 | |
| `switch` | NOT YET SUPPORTED | L0 | |
| labeled break/continue | NOT YET SUPPORTED | L0 | |
| try-with-resources | SUPPORTED | L2 | AutoCloseable or void close(); reverse-order cleanup |
| `final` (fields/locals) | NOT YET SUPPORTED | L0 | Partial / unverified |
| varargs | NOT YET SUPPORTED | L0 | |
| bitwise `& \| ^ ~ << >> >>>` | NOT YET SUPPORTED | L0 | |
| ternary `?:` (expr) | NOT YET SUPPORTED | L0 | `?` used in wildcards |
| primitive casts `(int)` etc. | NOT YET SUPPORTED | L0 | |
| enhanced for | NOT YET SUPPORTED | L0 | |

## Control-flow model

- Semantic validation uses **break-target depth** (loops today).
- IR uses **LoopFrame / BreakTarget** with exit + continue blocks.
- Abrupt completion through `finally` uses TLS completion kinds: `return=1`, `break=2`, `continue=3`.
- LLVM lowering never pattern-matches AST `break`; it only emits CFG branches.

## String contract (core)

| API | Semantics |
|---|---|
| `length()` | UTF-8 **byte** count (not Unicode code points) |
| `split(String delimiter)` | **Literal** delimiter (NOT Java regex) |
| empty delimiter | returns single-element array `[this]` |
| empty input `"".split(",")` | `[""]` |
| consecutive / trailing empties | **kept** |
| null delimiter | NPE |
| `String.join(delim, String[])` | single-pass join; null element → `"null"` |

## Collections core (1.0 minimum)

See Round 1.1 report matrix. Planned post-core: LinkedList, LinkedHashMap, LinkedHashSet, PriorityQueue.
