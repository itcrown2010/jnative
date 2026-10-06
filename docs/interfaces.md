# Interfaces (0.14)

JNative 0.14 adds a first-class **InterfaceSymbol** (distinct from ClassSymbol), ITable dispatch, abstract classes, and functional-interface lambda conversion.

## Syntax

```java
interface Animal {
    void speak();
}

class Dog implements Animal {
    void speak() { System.out.println("woof"); }
}

Animal a = new Dog();
a.speak();
```

- Classes: single inheritance + `0..N` interfaces
- Interfaces: multiple `extends`
- No default methods yet (explicit diagnostic)
- No interface instance fields / constructors

## Dispatch

```text
InterfaceCall → ClassMetadata → itable lookup(ifaceId) → slot → call
```

Managed references stay single pointers (no fat `{obj, itable}`). Moving GC updates interface locals like any managed root.

## Functional interfaces

SAM detection (inheritance-aware, Object methods ignored). Lambda targets reuse 0.10 `MakeCallable` / closure env via a synthetic adapter class that implements the interface.

## Optimizer

`DevirtualizePass` lowers monomorphic `InterfaceCall` (exact type / MakeCallable / closed-world single implementor) to direct `Call`.
