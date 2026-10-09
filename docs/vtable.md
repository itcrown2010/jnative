# VTable (JNative 0.4)

作者：张晓达

## Model

```text
VTable
  entries: List<VTableEntry>

VTableEntry
  slot
  slotOwner        // method that introduced the slot
  implementation   // mangled function currently installed
```

Built by `VTableBuilder` in superclass-first order. Slots are fixed before LLVM.

## Slot rules

| Event | Action |
|-------|--------|
| New virtual method | append slot |
| Override | replace implementation, **same slot** |
| Inherited method | copy parent entry |
| `static` / `main` | not in vtable |

## Object header (0.4)

```text
object → ClassMetadata* → { vtable*, super*, size, flags, name* }
```

`new Dog()` stores `@meta.Dog` into the object header (not a bare vtable pointer).

## Virtual call

```text
nullcheck receiver
meta = load ClassMetadata* from object
vtable = load vtable* from meta field 0
fn = load vtable[slot]
indirect call fn(receiver, ...)
```

LLVM does **not** re-resolve Java methods.

## Future

- interface / itable alongside vtable
- GC maps on ClassMetadata
