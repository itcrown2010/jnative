# Constructors

## Model

Constructors are first-class `ConstructorSymbol`s, not string-special methods.

Native ABI:

```text
void _JN_Person_init_i32(ptr this, i32 value)
```

## `new` lowering (IR)

```text
allocate (most-derived size)
zero initialize
store ClassMetadata* in header
ConstructorCall(this, args)
return reference
```

IR keeps `NewObject` and `ConstructorCall` separate. The LLVM backend does not
resolve constructors.

## Implicit default

If a class declares no constructor, the compiler synthesizes an implicit
no-arg constructor (calls `super()` when a superclass exists).

## Overloading

Resolved via `CallableResolver` (shared with future method overloading).

## `this(...)` / `super(...)`

- Must be the first statement in a constructor body.
- Delegation cycles are semantic errors.
- `super(...)` runs the superclass constructor on the same allocated object
  (no second allocation).

## Initialization order

```text
allocate most-derived object
→ zero init
→ metadata / vtable via ClassMetadata*
→ super constructor
→ subclass constructor
```
