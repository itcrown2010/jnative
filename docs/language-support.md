# JNative Supported Language Profile (1.0 Audit)

作者：张晓达

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
| multi-file / third-party (multi-CU) | SUPPORTED | L2 | Independent parse + project symbols; see `docs/jnative-multi-cu-architecture.md` |
| `.jn` / `.java` suffixes | SUPPORTED | L2 | Same language; `jnative new` defaults to `.jn` |
| `final` locals / params / class / methods | SUPPORTED | L2 | DA + constraints; see `jnative-final-da-foreach.md` |
| `final` fields | **NOT SUPPORTED IN 1.0** | L0 | Hard error (not silently ignored) |
| method overload | SUPPORTED | L2–L3 | Ambiguity diagnosed; `OverloadEndToEndTest` |
| constructor overload | SUPPORTED | L2 | |
| `override` (virtual dispatch) | SUPPORTED | L3 | Dynamic dispatch e2e |
| arrays | SUPPORTED | L3 | Runtime + e2e; 1-D only (no `int[][]`) |
| array initializers `{1, 2}` / `new T[] {..}` | SUPPORTED | L2 | 1-D, trailing comma ok; nested `{{..}}` diagnosed; `ArrayInitEndToEndTest` (diff vs javac) |
| instance field initializers (`int[] f = {..}; List<T> l = new ArrayList<>();`) | SUPPORTED | L2 | Run before constructor body; `ArrayInitEndToEndTest` |
| `Object.toString()` / `println(obj)` / `"s" + obj` | SUPPORTED | L2 | Wrapper/boolean/null/object (`Cls@hex` default); user `toString` virtual dispatch; `ArrayInitEndToEndTest#toStringAndConcatMatchJava` |
| `Integer/Long/Boolean/Float/Double/Character` stdlib APIs | SUPPORTED | L2 | `MAX_VALUE`, `parseInt/parseLong/parseDouble`, `toString(radix)`, `toHexString`, `compare`, `Character.isDigit/isLetter/...`, `NumberFormatException`; `StdlibNumbersEndToEndTest` (diff vs javac) |
| `double`/`float` printing | SUPPORTED | L2 | Java `Double.toString` algorithm (shortest round-trip, `E` notation) |
| generics (basic / specialized collections) | SUPPORTED | L2–L3 | `GenericsEndToEndTest` |
| wildcards `?` / `? extends` / `? super` | EXPERIMENTAL | L1 | Parsing exists; **no native wildcard e2e** |
| lambda / SAM | SUPPORTED | L2–L3 | `CallableEndToEndTest` |
| method reference `::` | SUPPORTED | L2–L3 | Same |
| annotations (compile-time / meta) | SUPPORTED | L2 | Meta032 processors; limited native app loop |
| `enum` | SUPPORTED | L2 | Basic constants + name/ordinal/values/valueOf; see `jnative-switch-enum.md` |
| `record` | **NOT SUPPORTED IN 1.0** | L0 | |
| pattern matching / switch patterns | **NOT SUPPORTED IN 1.0** | L0 | Classic `instanceof` only |

---

## 3. Control flow & exceptions

| Feature | Status | Level | Evidence / Notes |
|---|---|---|---|
| `if` / `else` | SUPPORTED | L3 | |
| `while` / `do`/`while` / `for` | SUPPORTED | L2–L3 | |
| enhanced `for` (arrays) | SUPPORTED | L2 | Index-loop lowering; `FinalDaForeachEndToEndTest` |
| enhanced `for` (Iterable) | SUPPORTED | L2 | `iterator`/`hasNext`/`next`; `GenericsIterableEndToEndTest` |
| local definite assignment | SUPPORTED | L2 | CFG merge; backend zero-init ≠ DA |
| generics (erase + primitive specialize) | SUPPORTED | L2 | See `generics.md` / `jnative-generics-iterable.md` |
| `switch` / `case` | SUPPORTED | L2 | Classic statement; int + enum; `SwitchEnumEndToEndTest` |
| `break` / `continue` | SUPPORTED | L2–L3 | `LoopControlEndToEndTest`; O0/O2/O3 |
| labeled break/continue | **NOT SUPPORTED IN 1.0** | L0 | |
| `return` | SUPPORTED | L3 | |
| `try` / `catch` / `finally` | SUPPORTED | L2–L3 | Abrupt completions; see `exceptions.md`; `ExceptionEndToEndTest` |
| try-with-resources | SUPPORTED | L2–L3 | Decl form; close-before-catch; suppressed; `ResourceEndToEndTest` |
| `addSuppressed` / `getSuppressed` | SUPPORTED | L2 | On `Throwable`; TWR wiring |
| `throw` | SUPPORTED | L3 | `throw null` → NPE |
| multi-catch | **NOT SUPPORTED IN 1.0** | L0–L1 | Parse error |
| checked `throws` enforcement | **NOT SUPPORTED IN 1.0** | L0 | `throws` not parsed |
| TWR existing-variable form | **NOT SUPPORTED IN 1.0** | L0 | Decl form only |
| WASM exceptions | SUPPORTED | L2 | Same pending+CFG as native; see `wasm32-browser-exceptions.md` |
| ternary `?:` (expression) | SUPPORTED | L2 | `PrimitiveOpsEndToEndTest`; unused branch not evaluated |
| `async` / `await` | SUPPORTED | L2 | Thin e2e; dual Future debt |

---

## 4. Operators & conversions

| Feature | Status | Level | Evidence / Notes |
|---|---|---|---|
| arithmetic `+ - * / %` | SUPPORTED | L2–L3 | |
| char literals `'a'` `'\n'` `'\u0041'` `'\101'` | SUPPORTED | L2 | Type `char`; usable as `case` labels; `"x" + 'a'` and `String.valueOf(char)` yield the character (not the code). `LiteralEndToEndTest` vs javac |
| hex / binary int literals `0xFF` `0b1010`, `L` suffix | SUPPORTED | L2 | Two's complement wrap like javac (`0xFFFFFFFF` = -1); range-checked |
| digit separators `1_000_000` | SUPPORTED | L2 | Only between digits; misplaced `_` is a diagnostic |
| `-2147483648`, `-9223372036854775808L` | SUPPORTED | L2 | Literal only valid as operand of unary minus; other overflow is a diagnostic (no compiler crash) |
| string escapes `\n \t \r \b \f \s \" \' \\ \uXXXX \0..\377` | SUPPORTED | L2 | Unknown escapes are now an error (previously silently the escaped char) |
| comparison / logical `&& \|\| !` | SUPPORTED | L3 | |
| bitwise `& \| ^ ~` | SUPPORTED | L2 | Integer + boolean non-short-circuit; `PrimitiveOpsEndToEndTest` |
| shifts `<< >> >>>` | SUPPORTED | L2 | Distance masked 0x1f / 0x3f; ashr/lshr | 
| compound assignment `+= -= *= /= %= &= \|= ^= <<= >>= >>>=` | SUPPORTED | L2 | Desugared in the parser to `a = (T)(a op b)` with Java's implicit narrowing (`char c += 2`, `int i += 2.9`, `byte b += 10`; `String +=`). The target is evaluated twice, so side-effecting targets (e.g. `a[i++] += 1`) are rejected with a diagnostic. `StdlibLangEndToEndTest` vs javac |
| varargs `...` | **NOT SUPPORTED IN 1.0** | L0 | |
| primitive casts `(int)x` | SUPPORTED | L2 | Integer + float/double (NaN→0/saturate); boolean rejected; char zext |
| reference cast / `instanceof` | SUPPORTED | L2–L3 | RuntimeError + Interface e2e |
| string `+` | SUPPORTED | L2–L3 | |

### Numeric semantics (1.0 expectations)

| Topic | Status |
|---|---|
| integer overflow (wrap) | Assumed two's complement via LLVM — **no dedicated suite** |
| integer division / modulo | Used in e2e — **no edge corpus** |
| float NaN / Infinity / `-0.0` | Verified — `FloatEndToEndTest` + `docs/jnative-float-double.md` |
| shift masking / signedness | Java-compatible masks; verified vs javac in `PrimitiveOpsEndToEndTest` |

---

## 5. String contract (Stable rules)

| API / rule | Semantics |
|---|---|
| `length()` | UTF-16 **code unit** count (Java-compatible) |
| `utf8Length()` | UTF-8 **byte** length (protocols / Content-Length) |
| `charAt` / `substring` / `indexOf` | UTF-16 code unit indices |
| `getBytes` / `fromBytes` | UTF-8 encode / strict decode |
| `split(delimiter)` | **Literal** delimiter (NOT regex) |
| empty delimiter | `[this]` |
| `"".split(",")` | `[""]` |
| consecutive / trailing empties | kept |
| null delimiter | NPE |
| case folding | ASCII only (`toLowerCase` / `equalsIgnoreCase`) |
| `StringBuilder` | Level **L2**, differential-tested vs javac (`Sb1`): ctors `()`/`(int)`/`(String)`; `append` for String/char/int/long/float/double/boolean/char[]/Object (null → `"null"`); `insert` (String/char/int/long/double/boolean), `replace`, `delete`, `deleteCharAt`, `reverse`, `setLength`, `setCharAt`, `charAt`, `indexOf`/`lastIndexOf(String)`, `substring`, `length`, `capacity` (hint only). Not provided: `append(CharSequence)`, `chars()`, `compareTo` |
| Details | See `docs/string.md` |

### 5.1 Collections (`java.util` subset, CollectionsLib)

Differential-tested against real Java (`Col1`, -O0 and -O2): `HashMap`/`HashSet` iteration order and `toString` formats match Java's.

| API | Notes |
|---|---|
| `ArrayList<T>` | `add/addAll/get/set/remove(int)/remove(Object)/indexOf/lastIndexOf/contains/containsAll/removeAll/retainAll/removeIf/sort/subList/forEach`, `toString/equals/hashCode`. `ArrayList<int>` etc. stay primitive-specialised (no boxing in `add/get`) |
| `HashMap<K,V>` | Java-compatible bucket table (hash spreading, load factor 0.75, resize order preserved, null keys). `put/get/getOrDefault/putIfAbsent/merge/computeIfAbsent/computeIfPresent/replace/remove/containsKey/containsValue/putAll/forEach/keySet/values/entrySet` (live views, `Entry.setValue`) |
| `HashSet<T>` | Backed by `HashMap`; `add/remove/contains/addAll/removeIf/...`, same iteration order as Java |
| `LinkedHashMap<K,V>`, `LinkedHashSet<T>` | Insertion-ordered `HashMap`/`HashSet` (the parser rewrites the type name and `new LinkedHashMap<>(args)` to `new HashMap<>(true, args)`); same API as above, re-`put` keeps the original position. Access-order mode (`new LinkedHashMap<>(n, lf, true)`) is not supported |
| `Queue<T>`, `Deque<T>`, `ArrayDeque<T>`, `LinkedList<T>` | One ring-buffer class implements `List` and `Deque`; the parser maps `LinkedList` to it. `add/offer/poll/peek/remove()/element`, `addFirst/addLast/offerFirst/offerLast/removeFirst/removeLast/pollFirst/pollLast/getFirst/getLast/peekFirst/peekLast/push/pop`, `iterator/descendingIterator`, and the full `List` API (`get/set/add(i,x)/remove(i)/remove(Object)/indexOf/sort/subList/...`) with `toString/equals/hashCode`. O(1) at both ends and for `get(i)`, O(n) for insertion in the middle. Deviation: `ArrayDeque` also accepts `null` and index operations |
| `PriorityQueue<T>` | The JDK's binary heap (`siftUp/siftDown`, same array order for iteration and `toString`); natural ordering or a `Comparator` (lambda or `Collections.reverseOrder()`); `add/offer/poll/peek/remove()/remove(x)/element/contains/removeIf/iterator`, collection constructor. No `PriorityQueue(PriorityQueue)` fast path |
| `Stack<T>` | Parser maps `Stack` to `ArrayList` plus `push/pop/peek/empty/search`; `EmptyStackException` on an empty stack. Iteration order is bottom to top as in Java |
| Array type arguments | `List<int[]>`, `PriorityQueue<int[]>`, `Map<String, String[]>` etc. (one-dimensional array as a type argument; multi-dimensional arrays are still unsupported) |
| `Objects` | `equals/hashCode/hash` (1–6 args; no varargs)/`toString`(1–2 args)`/isNull/nonNull/requireNonNull`(1–2 args)`/requireNonNullElse/checkIndex` |
| `Optional<T>` | `empty/of/ofNullable/isPresent/isEmpty/get/orElse/orElseGet/orElseThrow()/ifPresent/map/filter/or`, `equals/hashCode/toString`. `map` infers its result type from an expression lambda body (also available for user generic methods taking `Function<T,R>`); no `flatMap`, `OptionalInt` & co |
| `Random` | The JDK's 48-bit LCG: a seeded `Random` yields the identical sequence (`nextInt()/nextInt(bound)/nextInt(origin,bound)/nextLong/nextDouble/nextFloat/nextBoolean`); no `nextGaussian`/streams |
| `List.of/copyOf`, `Set.of/copyOf`, `Map.of/entry/copyOf`, `Arrays.asList` | Desugared by the parser (0–10 elements/pairs). Results are **mutable**; `Set.of`/`Map.of` use insertion-bucket order, not Java's randomised order |
| `Arrays` | `copyOf/copyOfRange/fill/equals/toString/hashCode/sort` (primitives, `Object[]`, `T[]` + `Comparator`, ranges), `asList(T[])` |
| `Collections` | `sort/reverse/swap/max/min/reverseOrder/frequency/binarySearch/emptyList` |
| Functional interfaces | `Comparator`, `Predicate`, `Consumer`, `Function`, `BiConsumer`, `BiFunction` (lambdas and method refs) |

Documented deviations from Java: `List.subList` is a **copy** (not a view); no tree bins in `HashMap`; `Map.Entry` is spelled `Entry` internally (the `Map.Entry` form is accepted); `import java.util.*;`, `import java.util.function.*;` and single-type imports from those packages are accepted and ignored (the supported subset is built in); other wildcard imports are still an error. Methods taking `Collection<T>`/`Iterator<T>`/`Predicate<T>` on primitive-specialised collections box the element (erased generic interfaces).

Not yet provided: `TreeMap`/`TreeSet`, `iterator.remove`, `Collections.shuffle`.

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

1. switch expressions / arrow `case ->`  
2. string switch / pattern matching  
3. enum bodies, ctor args, user members  
4. hex floating-point literals (`0x1.0p0`) — hard error  
5. octal integer literals (`017` is still read as decimal 17 — **differs from Java**)  
6. varargs / records  
7. labeled break  
8. compound assignment on side-effecting targets (`a[i++] += 1`) — diagnostic  
9. `final` fields (blank or initialized)  
10. Wildcard capture / multi-bound generics / full Stream & concurrent collections  
    (boxing `Integer`/`Long`/`Boolean` + `ArrayList`/`HashMap`: see `docs/jnative-boxing-collections.md`)  


---

## 8. Profile stability statement

For 1.0 RC, freeze this profile:

- **Stable API surface** = SUPPORTED rows at ≥ L2 with native e2e  
- Raising SUPPORTED → Stable product tier requires ≥ **L3** (real apps + failure paths)  
- Adding new syntax after RC1 requires a language RFC + e2e + opt differential  

**Current product truth:** language core is a **usable subset**, not Java SE.
