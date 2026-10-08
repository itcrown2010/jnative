# JNative Calculator

Native desktop calculator (Standard / Scientific / Programmer).  
**No** JVM / Swing / JavaFX / Electron / WebView at runtime.

Validation report: [`docs/jnative-calculator-validation.md`](../../docs/jnative-calculator-validation.md)  
Stamp: **`JNATIVE_CALCULATOR_PARTIAL`** (macOS arm64 verified).

## Build & run (GUI)

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar examples/calculator/JNativeCalculator.java \
  -o build/calculator/Calculator --app-type gui

./build/calculator/Calculator
# or: open build/calculator/Calculator.app
```

## Headless engine tests

```bash
java -jar target/jnative.jar examples/calculator/CalculatorTests.java \
  -o build/calculator/CalculatorTests
./build/calculator/CalculatorTests
```

## Native e2e (Mach-O, no JVM link, smoke)

```bash
./examples/calculator/scripts/native-e2e.sh
```

## Modes

| Mode | Notes |
|------|--------|
| Standard | `Decimal` arithmetic, `%` / `1/x` / `x²` / `√x`, continuous `=` |
| Scientific | Expression parser (`+ - * / ^`, functions, `pi` / `e`), DEG/RAD |
| Programmer | BIN/OCT/DEC/HEX, AND/OR/XOR/NOT/`<<`/`>>`/`>>>`, bit width & signed |

## Percent semantics

- **Unary:** `50%` → `0.5` (`value / 100`)
- **Binary with pending `+` / `-`:** second operand becomes `a * (b / 100)`

## Shortcuts

- Digits / operators / Enter / Escape (AC) / Backspace
- Cmd/Ctrl+C copy display · Cmd/Ctrl+V paste into entry
