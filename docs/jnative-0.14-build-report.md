# JNative 0.14 Build Report — Interface System

作者：张晓达

## Summary

- Version **0.14**
- `InterfaceSymbol` distinct from `ClassSymbol`
- ITable + `InterfaceCall` IR + LLVM itable lookup
- Abstract classes / methods
- Functional interface → lambda via adapter + 0.10 callable runtime
- CHA / closed-world interface devirtualization

```text
mvn test: PASS
Total tests: 239
New 0.14 tests (InterfaceEndToEndTest): 13
Failures: 0
Errors: 0
```

| Check | Result |
|-------|--------|
| Interface parsing | PASS |
| Multiple interfaces | PASS |
| Interface inheritance | PASS |
| ITable | PASS |
| Interface dispatch | PASS |
| instanceof interface | PASS |
| checked cast interface | PASS |
| Abstract class / method | PASS |
| Functional interface / lambda | PASS |
| Capturing lambda + GC stress | PASS |
| InterfaceCall in IR | PASS |
| 0.1–0.13 regression | PASS |
| Native compilation | PASS |

## Architecture Acceptance

| Requirement | Status |
|-------------|--------|
| InterfaceSymbol ≠ ClassSymbol | **YES** |
| Multi interface extends / implements | **YES** |
| instanceof / checked cast interfaces | **YES** |
| Explicit InterfaceCall IR | **YES** |
| Stable interface slots + ITables | **YES** |
| Inherited class methods satisfy interfaces | **YES** |
| Abstract classes / methods | **YES** |
| Functional interfaces + capturing lambdas | **YES** |
| Effect contracts on interface impls | **YES** |
| GC2 treats interface refs as managed | **YES** |
| Interface calls / lambda path devirtualizable | **YES** |

## 0.15 Readiness

| Question | Answer |
|----------|--------|
| InterfaceSymbol can carry type parameters? | **YES** (API room) |
| ClassSymbol / methods type parameters? | **YES** |
| Parameterized types in Type model? | **YES** (extend JType) |
| Erasure → managed refs / ITable reuse? | **YES** |
| GC2 unchanged for erased refs? | **YES** |

## Known Limitations

- No default interface methods (diagnostic)
- No interface constants / static interface methods
- Method references → user interfaces optional / limited
- Inline cache (PIC) not required; AOT closed-world first
- Builtin SAM types remain ClassSymbol adapters for Thread/HTTP compatibility

## Important Files

- `semantic/InterfaceSymbol.java`, `InterfaceType.java`, `InterfaceCallTarget.java`
- `vtable/ITable*.java`, `iface/InterfaceSlotBuilder.java`, `FunctionalInterfaceAnalyzer.java`
- `IrInstruction.InterfaceCall`, `LlvmIrGenerator` itable emit + lookup
- `opt/DevirtualizePass.java` (interface)
- `e2e/InterfaceEndToEndTest.java`
