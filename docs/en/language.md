# Language

JNative source looks like a familiar subset of Java. File extensions `.java` and `.jn` are the same language; `jnative new` prefers `.jn`.

This page is a **reader’s guide**. For the audited feature matrix (SUPPORTED / EXPERIMENTAL / NOT SUPPORTED), use [`../language-support.md`](../language-support.md). If something is missing there, do not depend on it.

---

## Mental model

Write ordinary classes with `main`, fields, methods, and `System.out.println`. The compiler ahead-of-time lowers that program to machine code (or WASM). There is **no** classpath full of `rt.jar`, **no** reflection-based frameworks, and **no** promise that an arbitrary Maven JAR will compile.

Stdlib that ships with JNative is included by the compiler frontend from `src/main/resources/stdlib/`.

---

## Hello World

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

On native targets, `args` is a real managed `String[]` built from process argv.

---

## What works well today

**Types & OOP** — primitives; classes with inheritance and virtual dispatch; interfaces (`implements`) without default or static interface methods; constructors with `this` / `super`; overloads; visibility modifiers; packages with explicit type imports (no `import foo.*` or static imports).

**Data** — arrays with runtime checks; basic generics (collections path specializes); `enum` with `name` / `ordinal` / `values` / `valueOf`; strings with UTF-16 code-unit indexing (Java-compatible `length` / `charAt`).

**Control & errors** — `if`, `while`, `for`, classic `switch`, `break` / `continue` (not labeled); `try` / `catch` / `finally`; try-with-resources in the **declaration** form; `throw`.

**Modern-ish Java surface (subset)** — lambdas and method references to SAM types; a limited annotation / processor story (`@Native`, effect annotations, builtin processors).

**`final`** — locals, parameters, classes, and methods are supported. **`final` fields are rejected** (hard error), not silently ignored.

---

## Deliberate non-goals (examples)

Do not expect:

- `record`, pattern matching, or switch patterns  
- multi-catch or checked `throws` enforcement  
- varargs (`...`)  
- full `java.*` compatibility or loading third-party bytecode  
- wildcards as a stable language feature (parsing exists; treat as experimental)

The long list lives in the language-support audit.

---

## Strings in one paragraph

`length`, `charAt`, `substring`, and `indexOf` count **UTF-16 code units**. Helpers such as `utf8Length` / `getBytes` exist for protocols. `split(delimiter)` splits on a **literal** delimiter, not a regex.

---

## Concurrency

OS threads (`Thread`, mutexes, atomics) are real and interact with a stop-the-world GC. `async` / `await` exists with limited e2e coverage — useful for experiments, not a claim of full structured concurrency.

---

## Where to look next

| Resource | Use |
|----------|-----|
| [`../language-support.md`](../language-support.md) | Normative support table |
| [architecture.md](architecture.md) | How the compiler implements the language |
| `examples/guide-hello/` | Minimal project |
| `examples/LambdaClosure.java` | Closures / callables |
| [`../tutorial/`](../tutorial/index.md) | Longer Chinese tutorial track |
