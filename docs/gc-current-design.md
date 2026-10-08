# JNative GC — Current Design (code truth)

**Document role:** single source of truth for the **shipping** collector.  
**Evidence:** `jnative_gc2.c` / `jnative_gc2.h` / `GcRuntimeEmitter.java` / `NativeRuntimeSources.java`.  
**Not evidence:** roadmaps, historical “0.5 non-moving” blurbs, aspirational docs.

**Last audited:** 2026-10-08 (RH-1).

---

## 1. Default / production collector

| Item | Value |
|---|---|
| **Name** | **GC2** (generational) |
| **Implementation** | `src/main/resources/native/posix/jnative_gc2.c` |
| **Header** | `jnative_gc2.h` |
| **Linked on** | POSIX (macOS/Linux) via `NativeRuntimeSources` |
| **Windows** | **Not linked** — no `jnative_gc*` under `native/windows/` |
| **Selection** | Runtime `getenv("JNATIVE_GC")` only (not a compile flag) |

### Selection rules (`jn_gc2_init`)

| `JNATIVE_GC` | Behavior |
|---|---|
| unset / `""` / `generational` | **GC2 enabled** (`g_enabled = 1`) |
| `marksweep` | GC2 disabled; LLVM IR legacy mark-sweep used |
| any other string | **GC2 enabled** (default) |

LLVM entry: `jnative_gc_alloc` / `jnative_gc_collect` branch on `jn_gc2_enabled()`.

> **Note:** Older comments in `jnative_gc2.h` that said “unset = marksweep” were **wrong** and must not be trusted. Code wins.

---

## 2. Moving vs non-moving

| Region | Moving? | Mechanism |
|---|---|---|
| **Young** | **YES — moving** | Semi-space Cheney copy; forwarding pointers (`FLAG_FORWARDED`) |
| **Old** | **NO** | malloc-backed mark-sweep; free unreached |
| **Large** | **NO** | Separate malloc list; mark-sweep |
| **Legacy marksweep** (`JNATIVE_GC=marksweep`) | **NO** | Intrusive list + mark/sweep in LLVM IR |
| **`@Stack` / stack-managed** | **NO** | Not on GC heap |

**Default production heap therefore relocates young objects.** Managed references must be kept in **updateable slots** (shadow stack / field slots). Raw addresses into young objects are invalid across safepoints / collections.

---

## 3. Generations & compaction

| Feature | Present? |
|---|---|
| Young generation | YES — dual semi-space + TLAB bump |
| Old generation | YES — `GEN_OLD` |
| Large objects | YES — `GEN_LARGE` |
| Young compaction | YES — Cheney packing into to-space |
| Old compaction / sliding | **NO** |
| Concurrent / incremental STW-free | **NO** — stop-the-world |

Promotion: survivors age; when `age >= JNATIVE_PROMOTION_AGE` (default 2) → old.

---

## 4. Pinning / GcHandle

| Claim | Reality |
|---|---|
| `FLAG_PINNED` | Defined in C; **never set; never checked** in `forward_ref` |
| `GcHandle` / `PinnedRef` public API | **Does not exist** |
| How UI/FFI avoid raw managed pointers | **Indirection** (e.g. `UiHandleRegistry` int ids; callback trampoline + callable table) |

**There is no pinning that prevents young relocation.** Do not retain managed object addresses in native code across GC.

---

## 5. Roots (updateable slots)

| Root class | Mechanism |
|---|---|
| Temporary / locals | Shadow `GcFrame` slots (`protectRef` / `jnative_gc_frame_set_root`) |
| Thread exception | `JNThreadState.ex_current` slot visitor |
| Thread object | `JNThreadState.thread_obj` slot visitor |
| Static fields | `jnative_visit_static_root_slots` registered at `main` |
| Native transition | `jn_enter_native` / `jn_leave_native` + safepoint; **no extra root bag** |
| Async frames | Heap objects; rely on ordinary precise scanning / live slots (no separate AsyncFrame registry) |

Moving GC **rewrites** slot contents when young objects relocate.

---

## 6. Stable identity

| API | Behavior under GC2 |
|---|---|
| `Object.hashCode` / identity | Stable field (`jn_gc2_identity_hash`) — **not** raw address |
| `jn_gc2_debug_address` | Returns current `(intptr_t)obj` — **test/debug only**; may change after minor GC |

---

## 7. Managed pointer contract (FFI)

**Allowed across safepoints:**

- `CString` / `NativeMemory` / `@NativeStruct` native buffers  
- Callback trampoline code pointers  
- Integer handle ids into GC-rooted tables  

**Forbidden:**

- Long-lived raw pointers into managed young/old objects for FFI  
- Interior pointers into managed arrays/strings retained past a safepoint  
- Assuming `memory-model.md` historical “non-moving” text (removed; see that file)

---

## 8. Env knobs (current)

| Variable | Role |
|---|---|
| `JNATIVE_GC` | `generational` (default) / `marksweep` |
| `JNATIVE_GC_STRESS` | Force frequent collection |
| `JNATIVE_YOUNG_SIZE` | Semi-space size |
| `JNATIVE_PROMOTION_AGE` | Promote threshold |
| `JNATIVE_GC_VERIFY` | Heap verify |
| `JNATIVE_GC_LOG` / `JNATIVE_GC_STATS` | Diagnostics |

---

## 9. Related docs (authority order)

1. **This file** — current truth  
2. `gc-invariants.md` — invariants / stress expectations  
3. `gc2.md` — operational overview (must not contradict this file)  
4. Historical build reports — archival only  

If any doc says “GC is non-moving” without qualifying **legacy marksweep / old-gen / stack**, it is **wrong** for the default collector.
