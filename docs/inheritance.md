# Inheritance (JNative 0.3)

## Syntax

```java
class Dog extends Animal {
}
```

Parser stores `superClassName` as a string; Semantic Analyzer resolves it to `ClassSymbol`.

## Validation

- Unknown superclass → error
- `class A extends A` → error
- Inheritance cycles → `cyclic inheritance involving 'A'` (DFS color marking, not stack overflow)

## Subtyping

`Animal.isAssignableFrom(Dog)` is true when `Dog` extends `Animal` (transitively).

```java
Dog d = new Dog();
Animal a = d;   // OK
Dog x = a;      // FAIL without cast
Dog y = (Dog) a; // OK with checked cast (0.4)
```

`null` remains assignable to any class type. `null instanceof T` is false; `(T) null` is null.

## Object prefix layout

```text
Animal:  [vtable*][age]
Dog:     [vtable*][age][weight]
```

`offset(Dog.age) == offset(Animal.age)`.

A `Dog*` may be used as an `Animal*` for field access through the shared prefix.

## Override

Same name + same parameter types + same return type → override (reuses vtable slot).
Incompatible signature → semantic error.

## Method lookup

Walk: declared methods → superclass → … until found.

## Not in 0.3

`super`, casts, `instanceof`, interfaces, constructor chaining.
