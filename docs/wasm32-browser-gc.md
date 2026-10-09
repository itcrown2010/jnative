# wasm32-browser 托管内存与 GC

作者：张晓达

单线程、非移动、精确根的 mark-sweep。不扫描整段线性内存猜指针；不引入并发/分代。

## 内存分区

| 区域 | 地址范围（常量见 `WasmLayout`） | GC？ |
|------|--------------------------------|------|
| 保留 | 0..255 | 否 |
| DOM 回调表 | `DOM_CB_BASE`（id→callable） | 表内 ptr 作根，表本身非堆对象 |
| 宿主根句柄表 | `HOST_ROOT_BASE`（gen+ptr × 128） | 槽内 ptr 作根 |
| 常量字符串 data | `DATA_MIN`..`DATA_MAX` | 否 |
| Shadow stack | `SHADOW_BASE`..+64KiB | 否（LIFO 帧，存根槽） |
| Mark stack | 有界条目 | 否；溢出 `unreachable` |
| 托管堆 | `HEAP_BASE` 起 bump | 是 |

对象布局：`[GcHeader 16 | payload…]`。Java/WASM 引用指向 **payload**（`typeId` @0）。  
`GcHeader`：`next`、`size`（payload 字节）、`flags`、`free_next`。

标志：`MARK` / `FREE` / `ARRAY` / `REF_ELEMS` / `OPAQUE`（长度前缀 UTF-8 等，不扫字段）。

## 分配与回收

1. 阈值触发或 bump 失败时 `jn_gc_collect`。
2. 清 MARK → 从根标记 → 有界 mark stack 传播 → sweep 建空闲链表。
3. 分配：精确尺寸 free-list 复用 → 否则 bump；必要时 `memory.grow` 后调用 `js_memory_grew`。
4. 失败返回 0，并递增 `jn_stat_alloc_fail`。

根集合：shadow 帧槽、静态引用全局、`jn_pending_exc`、宿主根表、DOM 回调表、类型元数据中的引用字段偏移与引用数组元素。

## 精确根与 safepoint

每个含引用参数/局部/临时的函数：

- `jn_gc_frame_push(n)` → 写入 `__jn_frame`
- 参数立即 `jn_gc_frame_set_root`
- 每次对 rooted local/temp 的 `local.set` 后 `emitRootPublish`
- 正常返回路径 `jn_gc_frame_pop`（exit 前）

覆盖：参数、局部、跨分配存活的临时、`this`/receiver、返回前仍存活的中间值、异常对象全局、静态字段、闭包 env。

GC 仅在已定义安全点：`jn_gc_safepoint`、`jn_gc_alloc` 内阈值/失败路径、显式 `jn_gc_collect`。

## JS 互操作契约

**DOM 句柄**（`retain` / `release` / `getHandle`）：宿主 JS 对象，与托管 GC 无关。

**托管根句柄**（`jn_host_root_retain/release/get`，胶水 `retainManaged` / `releaseManaged` / `getManaged`）：

- 打包：`((gen & 0xFFFF) << 16) | (slot+1)`；`0` 无效。
- 长期持有 WASM 对象必须用根句柄；裸地址不可作长期 JS 引用。
- 释放后 gen 递增；旧句柄 `get` 得 0。
- 回调：WASM 侧 DOM 表持有 callable；`unregister_callback` / `jn_release_callback` / `releaseCallback` 成对释放。
- 页面销毁：`dispose()` 取消回调并 `jn_gc_collect`。
- `writeWasmString`：临时 opaque 分配；长期用 `writeWasmStringRooted`。
- `memory.grow` 后宿主必须刷新 TypedArray（胶水每次 `u8`/`view` 重绑 `memory.buffer`）。

## 统计（开发用）

`jn_gc_stats()` → `(allocBytes, liveBytes, freeBytes, heapCap, gcCount, hostRoots, allocFail)`。

- `allocBytes`：累计分配的 payload 字节（含已回收）。
- `liveBytes` / `freeBytes`：最近一次 collect 后存活/空闲 payload。
- `heapCap`：当前线性内存字节数（可只增不减）。
- UI 不应展示这些内部指标。

## 字段偏移

IR 的 `fieldIndex` 是 LLVM GEP 下标（header=0，首字段=1）。WAT 侧必须按 `FieldLayout.index()` 查字节偏移，不能用 `fields().get(fieldIndex)`，否则会把引用字段写到 typeId 上，破坏虚调用。

## 验证

```bash
mvn -DskipTests package
examples/browser/float-calc/scripts/verify.sh
examples/browser/order-list/scripts/verify.sh
examples/browser/gc-stress/scripts/verify.sh
```

实测（本轮）：`gc-stress` 预热后 8 轮 churn 的 `liveBytes` 恒为 44；循环引用 drop 后 live 56→32；`order-list` / `float-calc` Node 验收 PASS。

压力用例允许线性内存容量保留；以 **liveBytes 有界** 与重复负载不无界增长为准。

## 尚未支持

- 多线程 / 共享内存 / 并发 GC
- 移动式压缩 GC
- 原生（非 browser）目标的此套 WAT GC（原生走 LLVM GC runtime）
- try/catch 与异常根的完整语言语义（当前 pending 异常全局 + trap）
