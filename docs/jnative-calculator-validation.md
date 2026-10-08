# JNative Calculator Validation Report

**Stamp:** `JNATIVE_CALCULATOR_PARTIAL`  
**Date:** 2026-10-08  
**Primary platform:** macOS arm64  
**Sources:** `examples/calculator/` · Decimal core: `stdlib/math/DecimalLib.java`

Does **not** claim `JNATIVE_CALCULATOR_PASS` (Windows/Linux unverified; keyboard/theme automation incomplete).

---

## Capability matrix (audited against real code + runtime)

| # | Capability | Status | Evidence |
|---|------------|--------|----------|
| 1 | Native Window | **VERIFIED** | `Window` + Cocoa/`--app-type gui`; Mach-O links AppKit; smoke launch alive |
| 2 | Button | **VERIFIED** | Mode keys + digit grid; click → `CalculatorActions.press` |
| 3 | Label | **VERIFIED** | Expression / result / error labels update via `setText` |
| 4 | GridLayout | **VERIFIED** | `GridPane.addAt` for keypads (std/sci/prog) |
| 5 | Keyboard Event | **PARTIAL** | `window.onKeyDown` wired (digits, numpad, Enter, Esc, BS, ops, Cmd/Ctrl+C/V); no automated key-injection e2e |
| 6 | Focus | **PARTIAL** | Window-level key focus works; per-control focus API exists (`UiNative.focus`) but not deeply exercised |
| 7 | Clipboard | **PARTIAL** | `ClipboardLib` + Cmd/Ctrl+C/V handlers; not automated in headless CI |
| 8 | Theme | **PARTIAL** | Default dark appearance + filled button colors; runtime light/dark toggle still blocked by `getAppearanceMode` LLVM SSA bug |
| 9 | Math | **VERIFIED** | `MathLib` sin/cos/… used by scientific expression evaluator |
| 10 | Decimal / BigDecimal | **VERIFIED** (Decimal) | New reusable `Decimal` (base-10 string digits); **no** JVM `BigDecimal` |
| 11 | Integer / Long | **VERIFIED** | Programmer mode uses `long` + width masks |
| 12 | Bitwise Operations | **VERIFIED** | `BitOps` int + `and64`/`xor64`/`shl64`/`ushr64` natives in crypto runtime |

“API exists” alone was **not** treated as VERIFIED; GUI smoke + engine tests required.

---

## Architecture

```
Calculator
 ├── CalculatorApp          (JNativeCalculator.main)
 ├── CalculatorWindow        (Native UI)
 ├── CalculatorEngine        (façade; no UI types)
 ├── DecimalEngine           (standard)
 ├── ScientificEngine        (expression buffer)
 ├── ProgrammerEngine        (bases / bitwise)
 ├── CalculatorState
 ├── HistoryManager          (in-memory)
 └── CalculatorTests        (headless engines)
```

Expression path is independent: `ExpressionTokenizer` → `ExpressionParser` → `ExpressionEvaluator` (no `eval`, no JS).

---

## Decimal policy

| Policy | Value |
|--------|-------|
| Representation | `sign * digits / 10^scale` (decimal digit string) |
| Precision cap | `MAX_DIGITS = 128` |
| Rounding | `ROUND_HALF_UP` |
| Division scale | `DEFAULT_DIV_SCALE = 32` |
| Overflow | digit-length guard / `DecimalException` (not silent wrap) |

Precision checks: `0.1+0.2==0.3`, `1/3` → `0.333…`, `999999999999+1` → `1000000000000`.

---

## Feature completion

| Area | Status | Notes |
|------|--------|-------|
| Standard ops `0-9 . + − × ÷ = AC CE BS +/- % 1/x x² √x` | **DONE** | Continuous ops + repeat `=` |
| Scientific funcs + DEG/RAD + `()` + precedence | **DONE** | `^` right-assoc (`2^3^2=512`) |
| Programmer BIN/OCT/DEC/HEX + AND/OR/XOR/NOT + shifts | **DONE** | 8/16/32/64-bit; signed/unsigned |
| History (memory) | **DONE** | Click recall + clear; persistence deferred |
| Native UI layout | **DONE** | Mode bar · display · grid · history side |
| macOS `.app` | **DONE** | `build/calculator/Calculator.app` |
| Windows / Linux packages | **NOT RUN** | Charter allows deferral |

---

## Native compile result (macOS arm64)

```text
java -jar target/jnative.jar examples/calculator/JNativeCalculator.java \
  -o build/calculator/Calculator --app-type gui

file: Mach-O 64-bit executable arm64
links: Cocoa, AppKit, Foundation, libz, libSystem  (NO libjvm / JavaVM)
smoke: process stays alive ≥2s after launch (CalculatorNativeE2E)
bundle: build/calculator/Calculator.app (NSHighResolutionCapable)
```

Pipeline exercised: **JNative Source → Compiler → LLVM → clang → Native Binary → Calculator Window**.

---

## Tests

| Suite | Count (this run) | Result |
|-------|------------------|--------|
| DecimalTests | 3 | PASS |
| ArithmeticTests | 3 | PASS |
| ExpressionTests | 3 | PASS |
| ScientificTests | 1 | PASS |
| ProgrammerTests | 2 | PASS |
| PercentTests | 1 | PASS |
| CalculatorStateTests | 4 | PASS |
| **Total headless** | **17** | **PASS 17 / FAIL 0** |
| CalculatorNativeE2E | script | PASS (binary + no-JVM + GUI smoke + `.app`) |

```bash
java -jar target/jnative.jar examples/calculator/CalculatorTests.java \
  -o build/calculator/CalculatorTests
./build/calculator/CalculatorTests

./examples/calculator/scripts/native-e2e.sh
```

---

## UI Layout Audit (redesign)

### Root causes (pre-fix)

| Symptom | Root cause |
|---------|------------|
| Large blank regions / clipped keypad | `NSWindow setFrame` treated content size as frame size; nested `VBox`/`StackPane` + Cocoa Y-flip |
| History squeezed / Clear clipped | History always in side `HBox`, not overlay |
| Buttons not filling cells | Cocoa `fittingSize` shrunk filled keys; `GridPane` ignored parent width |
| Invisible white text | Visual API used `-1` as KEEP sentinel, colliding with opaque white `0xFFFFFFFF` |

### Generic UI fixes (not Calculator-specific)

1. **`jnative_ui_set_visual` / filled buttons** — bg/fg/radius/font/align; hover/pressed on `JnUiButton`
2. **`StackPane`** + `stackPlacement` / `vgrow` — overlay + explicit vertical grow (replaces `preferredHeight>=80` heuristic)
3. **`GridPane`** — expand column width to parent; keep row height at `cellH` (avoid Y-flip gaps)
4. **`Window.setContentMinSize`** + Cocoa **`setContentSize`** for sizing
5. **Label/Button style helpers** — `setDisplayStyle` / `setFilledStyle` / `flashPressed`
6. **Explorer / Notepad / Workbench** — set `vgrow=1` on stretch targets after heuristic change

### Calculator UI redesign

- Default **dark** theme (`#1C1C1E` / digit `#3A3A3C` / func `#636366` / op `#FF9F0A`)
- Compact mode pills + History toggle
- 4-column filled keypad (radius 14, height 56)
- History via **`PopupWindow`** overlay (does not squeeze keypad)
- Mode window width: Standard ~420 / Scientific ~680 / Programmer ~760; height fits content

### Screenshots (real native window)

| Shot | Path |
|------|------|
| Standard | `build/calculator/screenshots/01-standard.png` |
| Scientific | `build/calculator/screenshots/02-scientific.png` |
| Programmer | `build/calculator/screenshots/03-programmer.png` |
| History | `build/calculator/screenshots/04-history.png` |

### Visual verification

| Check | Result |
|-------|--------|
| Dark theme + orange operators | **PASS** (screenshots) |
| Mode pills + selected state | **PASS** |
| Full standard keypad visible | **PASS** |
| Scientific / Programmer grids | **PASS** |
| History overlay (popup) | **PARTIAL** (popup works; not in-window drawer) |
| Display vertical tightness | **PARTIAL** (still some air above result) |
| Keyboard pressed flash | **PARTIAL** (wired via `flashPressed`) |

## UI validation

| Check | Result |
|-------|--------|
| Window opens | PASS (smoke) |
| Mode switch rebuilds grid without reparent crash | PASS |
| Mouse button press | PASS (screenshots / AX click) |
| Keyboard | Wired; no automated injection |
| Error / meta label | PASS |
| High DPI | Bundle flag set |

### Regression

| Target | Result |
|--------|--------|
| `CalculatorTests` | **17 PASS / 0 FAIL** |
| Explorer GUI build | **PASS** (`build/explorer/JNativeExplorer`) |
| UiWorkbench GUI build | **PASS** (`build/ui/UiWorkbench`) |

---

## Platform validation

| Platform | Compile | Link | Run | Tests | Mark |
|----------|---------|------|-----|-------|------|
| macOS arm64 | YES | YES | YES | YES | **VERIFIED** |
| Windows x64 | — | — | — | — | not verified |
| Linux x64 | — | — | — | — | not verified |

---

## JNative defects found

| Defect | Impact | Fix / status |
|--------|--------|--------------|
| UI `Container.add` rejects reparent without `remove` | Calculator constructor called `syncLayout` and re-added live widgets → `UiException` / instant exit | Calculator fixed: single tree; `rebuildGrid` removes old `GridPane`; history uses `clear()` + add |
| LLVM IR: duplicate `%t0` when compiling `Theme.getAppearanceMode()` static field load in a small method | Dark/light toggle crashed compile | Workaround: `setAppearanceMode` at startup only. **Compiler SSA bug remains** |
| Visual KEEP sentinel `-1` == opaque white | Labels rendered black-on-dark | KEEP sentinel changed to `1` |
| Window `setFrame` vs content size | Layout Y-flip gaps / clipping | Cocoa uses `setContentSize` |
| Engine sources duplicated in `CalculatorTests.java` | Drift risk | Acceptable for single-file AOT |

No Calculator-specific compiler / LLVM hacks were added.

---

## Stdlib / runtime work used (generic)

1. **`DecimalLib.java`** — reusable decimal core appended via `StdlibIncluder` with Math.
2. **`ClipboardLib.java`** — text clipboard without pulling full Editor/Graphics.
3. **`BitOps` long64 natives** — programmer calculator shifts/masks.

---

## Final stamp

```
JNATIVE_CALCULATOR_PARTIAL
```

Reasons not PASS:

1. Only macOS arm64 verified.
2. Keyboard / clipboard automation incomplete.
3. Residual compiler defect on `Theme.getAppearanceMode`.
4. History is popup overlay (not in-window drawer); persistence stage-2.
5. Display band still has leftover vertical air vs Apple Calculator density.
