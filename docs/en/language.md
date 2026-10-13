# Language overview

JNative accepts Java-like source (`.java` or `.jn`). This page summarizes what is **supported** for contributors and early adopters.

**Authoritative audit:** [`../language-support.md`](../language-support.md) (feature × maturity levels).  
**Rule:** If a feature is not listed as supported there, do not rely on it.

---

## Entry point

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

`main(String[] args)` receives a real managed argv array on native targets.

---

## Supported (high level)

| Topic | Notes |
|-------|--------|
| Primitives | `byte` `short` `int` `long` `float` `double` `boolean` `char` |
| Classes | Fields, methods, `static`, constructors, `this` / `super` |
| Inheritance | `extends`, virtual dispatch |
| Interfaces | `implements`; **no** default/static interface methods |
| Visibility | `public` / `private` / `protected` / package |
| Packages / imports | Explicit type imports; no wildcard / static import |
| Arrays | Runtime-checked |
| Generics | Basic + specialized collections; wildcards experimental |
| Control flow | `if`, loops, classic `switch`, `break`/`continue` (no labeled) |
| Exceptions | `try`/`catch`/`finally`, `throw`; try-with-resources **declaration** form |
| Lambdas / `::` | SAM callables |
| `enum` | Basic constants + `name`/`ordinal`/`values`/`valueOf` |
| `final` | Locals/params/class/methods; **final fields not supported** (hard error) |
| Operators | Arithmetic, logical, bitwise, shifts, casts, `instanceof`, string `+` |
| Annotations | Compile-time / meta subset (`@Native`, effects, …) |

---

## Explicitly not Java SE / not 1.0

Examples of **not supported** (or not shippable as Stable) — see the audit for the full list:

- `record`, pattern matching / switch patterns  
- Multi-catch, checked `throws` enforcement  
- Varargs `...`  
- Full reflection / arbitrary JAR execution  
- Complete collections/`java.*` compatibility  

---

## Strings

UTF-16 code-unit indexing for `length` / `charAt` / `substring` (Java-compatible). Encoding helpers for UTF-8 exist for protocols. `split` uses a **literal** delimiter (not regex). Details: [`../string.md`](../string.md).

---

## Concurrency & async

- **Threads:** `Thread`, mutex/condition/atomics — implemented with OS threads and STW GC.  
- **`async`/`await`:** present with thin e2e; treat carefully (experimental maturity).  

---

## Examples

| Path | Purpose |
|------|---------|
| `examples/guide-hello/` | Minimal project / Hello |
| `examples/Hello.java` | Single-file sample |
| `examples/LambdaClosure.java` | Lambdas / closures |
| `examples/tutorial/` | Tutorial companion sources |

Chinese narrative tutorial: [`../tutorial/index.md`](../tutorial/index.md).
