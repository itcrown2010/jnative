# GC root audit (0.13)

| Root class | Updateable slot? | Notes |
|------------|------------------|-------|
| Local managed PTR alloca | YES | Shadow stack |
| `protectRef` temps | YES | `gctmp.*` alloca |
| Parameters / `this` | YES | Entry alloca |
| Static managed fields | YES | `jnative_visit_static_root_slots` |
| Exception current | YES | `jn_foreach_ex_root_slot` |
| `Thread` object | YES | `jn_foreach_thread_obj_root_slot` |
| Stack-managed object ref fields | YES | Field GEPs as roots |
| Closure env (heap) | via object scan | After forwarding |
| NativeCallback retained callable | via handle fields | Object scan / roots |
| NativePtr | N/A | Never a managed ref |

Moving GC rule: any live managed reference the collector must update must be discoverable as a **slot address**.
