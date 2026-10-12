/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import java.util.List;

/**
 * Emits wasm32-browser mark-sweep GC runtime into a WAT module body.
 * Non-moving, single-threaded, precise roots (shadow stack + statics + host handles).
 * @author 张晓达
 */
public final class WasmGcEmitter {

    private final WasmMetaRegistry meta;

    public WasmGcEmitter(WasmMetaRegistry meta) {
        this.meta = meta;
    }

    public void emitGlobals(StringBuilder wat) {
        wat.append("  (global $heap (mut i32) (i32.const ").append(WasmLayout.HEAP_BASE).append("))\n");
        wat.append("  (global $jn_heap_head (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_free_head (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_shadow_sp (mut i32) (i32.const ").append(WasmLayout.SHADOW_BASE).append("))\n");
        wat.append("  (global $jn_frame (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_mark_sp (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_pending_exc (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_cb_next (mut i32) (i32.const 1))\n");
        wat.append("  (global $jn_host_next (mut i32) (i32.const 1))\n");
        wat.append("  (global $jn_stat_alloc_bytes (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_stat_live_bytes (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_stat_free_bytes (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_stat_gc_count (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_stat_alloc_fail (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_stat_host_roots (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_since_gc (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_gc_threshold (mut i32) (i32.const ")
                .append(WasmLayout.GC_THRESHOLD_DEFAULT).append("))\n");
        wat.append("  (global $jn_gc_collecting (mut i32) (i32.const 0))\n");
    }

    public void emitTypeAndStaticTracers(StringBuilder wat, List<String> staticRefGlobalWatNames) {
        wat.append("  (func $jn_gc_trace_type_fields (param $obj i32) (param $tid i32)\n");
        int max = meta.maxTypeId();
        for (int tid = 1; tid <= max; tid++) {
            List<Integer> offs = meta.refFieldOffsets(tid);
            if (offs.isEmpty()) {
                continue;
            }
            wat.append("    local.get $tid\n");
            wat.append("    i32.const ").append(tid).append("\n");
            wat.append("    i32.eq\n");
            wat.append("    if\n");
            for (int off : offs) {
                wat.append("      local.get $obj\n");
                wat.append("      i32.const ").append(off).append("\n");
                wat.append("      i32.add\n");
                wat.append("      i32.load\n");
                wat.append("      call $jn_gc_mark_obj\n");
            }
            wat.append("      return\n");
            wat.append("    end\n");
        }
        wat.append("  )\n");

        wat.append("  (func $jn_gc_trace_statics\n");
        for (String g : staticRefGlobalWatNames) {
            wat.append("    global.get $").append(g).append("\n");
            wat.append("    call $jn_gc_mark_obj\n");
        }
        wat.append("  )\n");
    }

    public void emitRuntime(StringBuilder wat) {
        int shadowEnd = WasmLayout.SHADOW_BASE + WasmLayout.SHADOW_CAPACITY;
        wat.append("  ;; ---- wasm32 mark-sweep GC (non-moving, precise roots) ----\n");

        // shadow stack
        wat.append("  (func $jn_gc_frame_push (param $n i32) (result i32)\n");
        wat.append("    (local $fp i32) (local $bytes i32) (local $end i32) (local $i i32)\n");
        wat.append("    local.get $n\n    i32.const 2\n    i32.add\n    i32.const 2\n    i32.shl\n    local.set $bytes\n");
        wat.append("    global.get $jn_shadow_sp\n    local.set $fp\n");
        wat.append("    local.get $fp\n    local.get $bytes\n    i32.add\n    local.set $end\n");
        wat.append("    local.get $end\n    i32.const ").append(shadowEnd).append("\n    i32.gt_u\n");
        wat.append("    if\n      unreachable\n    end\n");
        wat.append("    local.get $fp\n    global.get $jn_frame\n    i32.store\n");
        wat.append("    local.get $fp\n    i32.const 4\n    i32.add\n    local.get $n\n    i32.store\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $zdone\n      (loop $z\n");
        wat.append("        local.get $i\n        local.get $n\n        i32.ge_u\n        br_if $zdone\n");
        wat.append("        local.get $fp\n        i32.const 8\n        i32.add\n");
        wat.append("        local.get $i\n        i32.const 2\n        i32.shl\n        i32.add\n");
        wat.append("        i32.const 0\n        i32.store\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $z\n      )\n    )\n");
        wat.append("    local.get $end\n    global.set $jn_shadow_sp\n");
        wat.append("    local.get $fp\n    global.set $jn_frame\n");
        wat.append("    local.get $fp\n  )\n");

        wat.append("  (func $jn_gc_frame_set_root (param $frame i32) (param $i i32) (param $val i32)\n");
        wat.append("    local.get $frame\n    i32.const 8\n    i32.add\n");
        wat.append("    local.get $i\n    i32.const 2\n    i32.shl\n    i32.add\n");
        wat.append("    local.get $val\n    i32.store\n  )\n");

        wat.append("  (func $jn_gc_frame_pop\n");
        wat.append("    (local $fp i32)\n");
        wat.append("    global.get $jn_frame\n    local.set $fp\n");
        wat.append("    local.get $fp\n    i32.eqz\n    if\n      return\n    end\n");
        wat.append("    local.get $fp\n    global.set $jn_shadow_sp\n");
        wat.append("    local.get $fp\n    i32.load\n    global.set $jn_frame\n  )\n");

        // host roots
        emitHostRoots(wat);

        // mark / collect / alloc
        emitMarkCollectAlloc(wat);
    }

    /**
     * Host root handle packing: {@code ((gen & 0xFFFF) << 16) | (slot+1)}.
     * Slot table is fixed; free slots have ptr=0. Stale handles fail gen check.
     */
    private void emitHostRoots(StringBuilder wat) {
        int slots = WasmLayout.HOST_ROOT_SLOTS;
        int stride = WasmLayout.HOST_ROOT_STRIDE;
        int base = WasmLayout.HOST_ROOT_BASE;

        wat.append("  (func $jn_host_root_retain (param $ptr i32) (result i32)\n");
        wat.append("    (local $i i32) (local $b i32) (local $gen i32)\n");
        wat.append("    local.get $ptr\n    i32.eqz\n    if (result i32)\n      i32.const 0\n    else\n");
        wat.append("      i32.const 0\n      local.set $i\n");
        wat.append("      (block $found (result i32)\n");
        wat.append("        (loop $scan\n");
        wat.append("          local.get $i\n          i32.const ").append(slots).append("\n          i32.ge_u\n");
        wat.append("          if\n            i32.const 0\n            br $found\n          end\n");
        wat.append("          i32.const ").append(base).append("\n");
        wat.append("          local.get $i\n          i32.const ").append(stride).append("\n          i32.mul\n");
        wat.append("          i32.add\n          local.set $b\n");
        wat.append("          local.get $b\n          i32.const 4\n          i32.add\n          i32.load\n          i32.eqz\n");
        wat.append("          if\n");
        wat.append("            local.get $b\n            i32.load\n            i32.const 1\n            i32.add\n");
        wat.append("            i32.const 65535\n            i32.and\n            local.set $gen\n");
        wat.append("            local.get $gen\n            i32.eqz\n            if\n              i32.const 1\n              local.set $gen\n            end\n");
        wat.append("            local.get $b\n            local.get $gen\n            i32.store\n");
        wat.append("            local.get $b\n            i32.const 4\n            i32.add\n");
        wat.append("            local.get $ptr\n            i32.store\n");
        wat.append("            global.get $jn_stat_host_roots\n            i32.const 1\n            i32.add\n");
        wat.append("            global.set $jn_stat_host_roots\n");
        wat.append("            local.get $gen\n            i32.const 16\n            i32.shl\n");
        wat.append("            local.get $i\n            i32.const 1\n            i32.add\n");
        wat.append("            i32.or\n");
        wat.append("            br $found\n");
        wat.append("          end\n");
        wat.append("          local.get $i\n          i32.const 1\n          i32.add\n          local.set $i\n");
        wat.append("          br $scan\n");
        wat.append("        )\n");
        wat.append("        i32.const 0\n");
        wat.append("      )\n");
        wat.append("    end\n  )\n");

        wat.append("  (func $jn_host_root_release (param $h i32)\n");
        wat.append("    (local $slot i32) (local $b i32) (local $gen i32)\n");
        wat.append("    local.get $h\n    i32.eqz\n    if\n      return\n    end\n");
        wat.append("    local.get $h\n    i32.const 65535\n    i32.and\n    i32.const 1\n    i32.sub\n");
        wat.append("    local.set $slot\n");
        wat.append("    local.get $slot\n    i32.const ").append(slots).append("\n    i32.ge_u\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $h\n    i32.const 16\n    i32.shr_u\n    local.set $gen\n");
        wat.append("    i32.const ").append(base).append("\n");
        wat.append("    local.get $slot\n    i32.const ").append(stride).append("\n    i32.mul\n    i32.add\n");
        wat.append("    local.set $b\n");
        wat.append("    local.get $b\n    i32.load\n    local.get $gen\n    i32.ne\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $b\n    i32.const 4\n    i32.add\n    i32.load\n    i32.eqz\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $b\n    i32.const 4\n    i32.add\n    i32.const 0\n    i32.store\n");
        wat.append("    local.get $b\n    local.get $gen\n    i32.const 1\n    i32.add\n");
        wat.append("    i32.const 65535\n    i32.and\n    i32.store\n");
        wat.append("    global.get $jn_stat_host_roots\n    i32.const 1\n    i32.sub\n");
        wat.append("    global.set $jn_stat_host_roots\n  )\n");

        wat.append("  (func $jn_host_root_get (param $h i32) (result i32)\n");
        wat.append("    (local $slot i32) (local $b i32) (local $gen i32)\n");
        wat.append("    local.get $h\n    i32.eqz\n    if (result i32)\n      i32.const 0\n    else\n");
        wat.append("      local.get $h\n      i32.const 65535\n      i32.and\n      i32.const 1\n      i32.sub\n");
        wat.append("      local.set $slot\n");
        wat.append("      local.get $slot\n      i32.const ").append(slots).append("\n      i32.ge_u\n");
        wat.append("      if (result i32)\n        i32.const 0\n      else\n");
        wat.append("        local.get $h\n        i32.const 16\n        i32.shr_u\n        local.set $gen\n");
        wat.append("        i32.const ").append(base).append("\n");
        wat.append("        local.get $slot\n        i32.const ").append(stride).append("\n        i32.mul\n");
        wat.append("        i32.add\n        local.set $b\n");
        wat.append("        local.get $b\n        i32.load\n        local.get $gen\n        i32.ne\n");
        wat.append("        if (result i32)\n          i32.const 0\n        else\n");
        wat.append("          local.get $b\n          i32.const 4\n          i32.add\n          i32.load\n");
        wat.append("        end\n");
        wat.append("      end\n");
        wat.append("    end\n  )\n");
    }

    private void emitMarkCollectAlloc(StringBuilder wat) {
        int hdr = WasmLayout.GC_HEADER_SIZE;
        int markBase = WasmLayout.MARK_STACK_BASE;
        int markCap = WasmLayout.MARK_STACK_CAP;
        int heapBase = WasmLayout.HEAP_BASE;
        int domBase = WasmLayout.DOM_CB_BASE;
        int hostBase = WasmLayout.HOST_ROOT_BASE;
        int hostSlots = WasmLayout.HOST_ROOT_SLOTS;
        int hostStride = WasmLayout.HOST_ROOT_STRIDE;

        wat.append("  (func $jn_gc_obj_to_hdr (param $obj i32) (result i32)\n");
        wat.append("    local.get $obj\n    i32.const ").append(hdr).append("\n    i32.sub\n  )\n");
        wat.append("  (func $jn_gc_hdr_to_obj (param $h i32) (result i32)\n");
        wat.append("    local.get $h\n    i32.const ").append(hdr).append("\n    i32.add\n  )\n");

        wat.append("  (func $jn_gc_mark_push (param $obj i32)\n");
        wat.append("    (local $sp i32)\n");
        wat.append("    local.get $obj\n    i32.eqz\n    if\n      return\n    end\n");
        wat.append("    global.get $jn_mark_sp\n    local.set $sp\n");
        wat.append("    local.get $sp\n    i32.const ").append(markCap).append("\n    i32.ge_u\n");
        wat.append("    if\n      unreachable\n    end\n");
        wat.append("    i32.const ").append(markBase).append("\n");
        wat.append("    local.get $sp\n    i32.const 2\n    i32.shl\n    i32.add\n");
        wat.append("    local.get $obj\n    i32.store\n");
        wat.append("    local.get $sp\n    i32.const 1\n    i32.add\n    global.set $jn_mark_sp\n  )\n");

        wat.append("  (func $jn_gc_mark_obj (param $obj i32)\n");
        wat.append("    (local $h i32) (local $f i32)\n");
        wat.append("    local.get $obj\n    i32.eqz\n    if\n      return\n    end\n");
        wat.append("    local.get $obj\n    call $jn_gc_obj_to_hdr\n    local.set $h\n");
        wat.append("    local.get $h\n    i32.const ").append(heapBase).append("\n    i32.lt_u\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $h\n    i32.const 8\n    i32.add\n    i32.load\n    local.set $f\n");
        wat.append("    local.get $f\n    i32.const 2\n    i32.and\n    if\n      return\n    end\n");
        wat.append("    local.get $f\n    i32.const 1\n    i32.and\n    if\n      return\n    end\n");
        wat.append("    local.get $h\n    i32.const 8\n    i32.add\n");
        wat.append("    local.get $f\n    i32.const 1\n    i32.or\n    i32.store\n");
        wat.append("    local.get $obj\n    call $jn_gc_mark_push\n  )\n");

        wat.append("  (func $jn_gc_trace_one (param $obj i32)\n");
        wat.append("    (local $tid i32) (local $f i32) (local $len i32) (local $i i32)\n");
        wat.append("    local.get $obj\n    call $jn_gc_obj_to_hdr\n");
        wat.append("    i32.const 8\n    i32.add\n    i32.load\n    local.set $f\n");
        // OPAQUE: length-prefixed strings / host blobs — never interpret payload as typed object
        wat.append("    local.get $f\n    i32.const ").append(WasmLayout.GC_FLAG_OPAQUE).append("\n    i32.and\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $obj\n    i32.load\n    local.set $tid\n");
        wat.append("    local.get $obj\n    local.get $tid\n    call $jn_gc_trace_type_fields\n");
        wat.append("    local.get $f\n    i32.const 8\n    i32.and\n    i32.eqz\n");
        wat.append("    if\n      return\n    end\n");
        wat.append("    local.get $obj\n    i32.const 8\n    i32.add\n    i32.load\n    local.set $len\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $adone\n      (loop $a\n");
        wat.append("        local.get $i\n        local.get $len\n        i32.ge_s\n        br_if $adone\n");
        wat.append("        local.get $obj\n        i32.const 16\n        i32.add\n");
        wat.append("        local.get $i\n        i32.const 2\n        i32.shl\n        i32.add\n");
        wat.append("        i32.load\n        call $jn_gc_mark_obj\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $a\n      )\n    )\n  )\n");

        // trace_roots
        wat.append("  (func $jn_gc_trace_roots\n");
        wat.append("    (local $fp i32) (local $n i32) (local $i i32) (local $base i32)\n");
        wat.append("    global.get $jn_frame\n    local.set $fp\n");
        wat.append("    (block $fdone\n      (loop $f\n");
        wat.append("        local.get $fp\n        i32.eqz\n        br_if $fdone\n");
        wat.append("        local.get $fp\n        i32.const 4\n        i32.add\n        i32.load\n        local.set $n\n");
        wat.append("        i32.const 0\n        local.set $i\n");
        wat.append("        (block $sdone\n          (loop $s\n");
        wat.append("            local.get $i\n            local.get $n\n            i32.ge_u\n            br_if $sdone\n");
        wat.append("            local.get $fp\n            i32.const 8\n            i32.add\n");
        wat.append("            local.get $i\n            i32.const 2\n            i32.shl\n            i32.add\n");
        wat.append("            i32.load\n            call $jn_gc_mark_obj\n");
        wat.append("            local.get $i\n            i32.const 1\n            i32.add\n            local.set $i\n");
        wat.append("            br $s\n          )\n        )\n");
        wat.append("        local.get $fp\n        i32.load\n        local.set $fp\n");
        wat.append("        br $f\n      )\n    )\n");
        wat.append("    global.get $jn_pending_exc\n    call $jn_gc_mark_obj\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $hdone\n      (loop $h\n");
        wat.append("        local.get $i\n        i32.const ").append(hostSlots).append("\n        i32.ge_u\n        br_if $hdone\n");
        wat.append("        i32.const ").append(hostBase).append("\n");
        wat.append("        local.get $i\n        i32.const ").append(hostStride).append("\n        i32.mul\n        i32.add\n");
        wat.append("        i32.const 4\n        i32.add\n        i32.load\n        call $jn_gc_mark_obj\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $h\n      )\n    )\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $cdone\n      (loop $c\n");
        wat.append("        local.get $i\n        i32.const 64\n        i32.ge_u\n        br_if $cdone\n");
        wat.append("        i32.const ").append(domBase).append("\n");
        wat.append("        local.get $i\n        i32.const 8\n        i32.mul\n        i32.add\n");
        wat.append("        i32.const 4\n        i32.add\n        i32.load\n        call $jn_gc_mark_obj\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $c\n      )\n    )\n");
        wat.append("    call $jn_gc_trace_statics\n  )\n");

        // collect
        wat.append("  (func $jn_gc_collect\n");
        wat.append("    (local $h i32) (local $next i32) (local $f i32) (local $live i32) (local $freeb i32)\n");
        wat.append("    global.get $jn_gc_collecting\n    if\n      return\n    end\n");
        wat.append("    i32.const 1\n    global.set $jn_gc_collecting\n");
        wat.append("    i32.const 0\n    global.set $jn_mark_sp\n");
        wat.append("    global.get $jn_heap_head\n    local.set $h\n");
        wat.append("    (block $clrdone\n      (loop $clr\n");
        wat.append("        local.get $h\n        i32.eqz\n        br_if $clrdone\n");
        wat.append("        local.get $h\n        i32.const 8\n        i32.add\n        i32.load\n        local.set $f\n");
        wat.append("        local.get $h\n        i32.const 8\n        i32.add\n");
        wat.append("        local.get $f\n        i32.const -2\n        i32.and\n        i32.store\n");
        wat.append("        local.get $h\n        i32.load\n        local.set $h\n");
        wat.append("        br $clr\n      )\n    )\n");
        wat.append("    call $jn_gc_trace_roots\n");
        wat.append("    (block $mdone\n      (loop $m\n");
        wat.append("        global.get $jn_mark_sp\n        i32.eqz\n        br_if $mdone\n");
        wat.append("        global.get $jn_mark_sp\n        i32.const 1\n        i32.sub\n        global.set $jn_mark_sp\n");
        wat.append("        i32.const ").append(markBase).append("\n");
        wat.append("        global.get $jn_mark_sp\n        i32.const 2\n        i32.shl\n        i32.add\n");
        wat.append("        i32.load\n        call $jn_gc_trace_one\n");
        wat.append("        br $m\n      )\n    )\n");
        wat.append("    i32.const 0\n    global.set $jn_free_head\n");
        wat.append("    i32.const 0\n    local.set $live\n");
        wat.append("    i32.const 0\n    local.set $freeb\n");
        wat.append("    global.get $jn_heap_head\n    local.set $h\n");
        wat.append("    (block $swdone\n      (loop $sw\n");
        wat.append("        local.get $h\n        i32.eqz\n        br_if $swdone\n");
        wat.append("        local.get $h\n        i32.load\n        local.set $next\n");
        wat.append("        local.get $h\n        i32.const 8\n        i32.add\n        i32.load\n        local.set $f\n");
        wat.append("        local.get $f\n        i32.const 1\n        i32.and\n");
        wat.append("        if\n");
        wat.append("          local.get $h\n          i32.const 8\n          i32.add\n");
        wat.append("          local.get $f\n          i32.const -2\n          i32.and\n          i32.store\n");
        wat.append("          local.get $live\n          local.get $h\n          i32.const 4\n          i32.add\n          i32.load\n");
        wat.append("          i32.add\n          local.set $live\n");
        wat.append("        else\n");
        wat.append("          local.get $h\n          i32.const 8\n          i32.add\n");
        wat.append("          local.get $f\n          i32.const 2\n          i32.or\n          i32.store\n");
        wat.append("          local.get $h\n          i32.const 12\n          i32.add\n");
        wat.append("          global.get $jn_free_head\n          i32.store\n");
        wat.append("          local.get $h\n          global.set $jn_free_head\n");
        wat.append("          local.get $freeb\n          local.get $h\n          i32.const 4\n          i32.add\n          i32.load\n");
        wat.append("          i32.add\n          local.set $freeb\n");
        wat.append("        end\n");
        wat.append("        local.get $next\n        local.set $h\n");
        wat.append("        br $sw\n      )\n    )\n");
        wat.append("    local.get $live\n    global.set $jn_stat_live_bytes\n");
        wat.append("    local.get $freeb\n    global.set $jn_stat_free_bytes\n");
        wat.append("    i32.const 0\n    global.set $jn_since_gc\n");
        wat.append("    global.get $jn_stat_gc_count\n    i32.const 1\n    i32.add\n    global.set $jn_stat_gc_count\n");
        wat.append("    i32.const 0\n    global.set $jn_gc_collecting\n  )\n");

        // free list + bump + alloc
        // Exact-size free-list first-fit; rewrites flags on reuse (ARRAY/REF_ELEMS).
        wat.append("  (func $jn_gc_try_free_list (param $n i32) (param $flags i32) (result i32)\n");
        wat.append("    (local $prev i32) (local $cur i32) (local $sz i32) (local $nx i32)\n");
        wat.append("    i32.const 0\n    local.set $prev\n");
        wat.append("    global.get $jn_free_head\n    local.set $cur\n");
        wat.append("    (block $done (result i32)\n");
        wat.append("      (loop $walk\n");
        wat.append("        local.get $cur\n        i32.eqz\n        if\n          i32.const 0\n          br $done\n        end\n");
        wat.append("        local.get $cur\n        i32.const 4\n        i32.add\n        i32.load\n        local.set $sz\n");
        wat.append("        local.get $sz\n        local.get $n\n        i32.eq\n");
        wat.append("        if\n");
        wat.append("          local.get $cur\n          i32.const 12\n          i32.add\n          i32.load\n          local.set $nx\n");
        wat.append("          local.get $prev\n          i32.eqz\n");
        wat.append("          if\n            local.get $nx\n            global.set $jn_free_head\n");
        wat.append("          else\n");
        wat.append("            local.get $prev\n            i32.const 12\n            i32.add\n            local.get $nx\n            i32.store\n");
        wat.append("          end\n");
        wat.append("          local.get $cur\n          i32.const 8\n          i32.add\n");
        wat.append("          local.get $flags\n          i32.store\n");
        wat.append("          local.get $cur\n          call $jn_gc_hdr_to_obj\n          br $done\n");
        wat.append("        end\n");
        wat.append("        local.get $cur\n        local.set $prev\n");
        wat.append("        local.get $cur\n        i32.const 12\n        i32.add\n        i32.load\n        local.set $cur\n");
        wat.append("        br $walk\n");
        wat.append("      )\n      i32.const 0\n    )\n  )\n");

        wat.append("  (func $jn_gc_bump (param $total i32) (result i32)\n");
        wat.append("    (local $p i32) (local $end i32) (local $cur i32) (local $pages i32)\n");
        wat.append("    global.get $heap\n    local.set $p\n");
        wat.append("    local.get $p\n    local.get $total\n    i32.const 7\n    i32.add\n");
        wat.append("    i32.const -8\n    i32.and\n    i32.add\n    local.set $end\n");
        wat.append("    local.get $end\n    local.get $p\n    i32.lt_u\n");
        wat.append("    if\n      i32.const 0\n      return\n    end\n");
        wat.append("    memory.size\n    i32.const 16\n    i32.shl\n    local.set $cur\n");
        wat.append("    local.get $end\n    local.get $cur\n    i32.gt_u\n");
        wat.append("    if\n");
        wat.append("      local.get $end\n      local.get $cur\n      i32.sub\n");
        wat.append("      i32.const 65535\n      i32.add\n      i32.const 16\n      i32.shr_u\n      local.set $pages\n");
        wat.append("      local.get $pages\n      memory.grow\n      i32.const -1\n      i32.eq\n");
        wat.append("      if\n        i32.const 0\n        return\n      end\n");
        wat.append("      call $js_memory_grew\n");
        wat.append("    end\n");
        wat.append("    local.get $end\n    global.set $heap\n");
        wat.append("    local.get $p\n  )\n");

        wat.append("  (func $jn_gc_init_block (param $h i32) (param $n i32) (param $flags i32) (result i32)\n");
        wat.append("    local.get $h\n    global.get $jn_heap_head\n    i32.store\n");
        wat.append("    local.get $h\n    global.set $jn_heap_head\n");
        wat.append("    local.get $h\n    i32.const 4\n    i32.add\n    local.get $n\n    i32.store\n");
        wat.append("    local.get $h\n    i32.const 8\n    i32.add\n    local.get $flags\n    i32.store\n");
        wat.append("    local.get $h\n    call $jn_gc_hdr_to_obj\n  )\n");

        wat.append("  (func $jn_gc_alloc (param $n i32) (param $flags i32) (result i32)\n");
        wat.append("    (local $p i32) (local $h i32) (local $total i32) (local $i i32)\n");
        wat.append("    local.get $n\n    i32.const 1\n    i32.lt_s\n    if\n      i32.const 1\n      local.set $n\n    end\n");
        wat.append("    global.get $jn_since_gc\n    global.get $jn_gc_threshold\n    i32.ge_u\n");
        wat.append("    if\n      call $jn_gc_collect\n    end\n");
        wat.append("    local.get $n\n    local.get $flags\n    call $jn_gc_try_free_list\n    local.set $p\n");
        wat.append("    local.get $p\n    i32.eqz\n");
        wat.append("    if\n");
        wat.append("      local.get $n\n      i32.const ").append(hdr).append("\n      i32.add\n      local.set $total\n");
        wat.append("      local.get $total\n      call $jn_gc_bump\n      local.set $h\n");
        wat.append("      local.get $h\n      i32.eqz\n");
        wat.append("      if\n");
        wat.append("        call $jn_gc_collect\n");
        wat.append("        local.get $n\n        local.get $flags\n        call $jn_gc_try_free_list\n        local.set $p\n");
        wat.append("        local.get $p\n        i32.eqz\n");
        wat.append("        if\n");
        wat.append("          local.get $total\n          call $jn_gc_bump\n          local.set $h\n");
        wat.append("          local.get $h\n          i32.eqz\n");
        wat.append("          if\n");
        wat.append("            global.get $jn_stat_alloc_fail\n            i32.const 1\n            i32.add\n");
        wat.append("            global.set $jn_stat_alloc_fail\n");
        wat.append("            i32.const 0\n            return\n");
        wat.append("          end\n");
        wat.append("          local.get $h\n          local.get $n\n          local.get $flags\n");
        wat.append("          call $jn_gc_init_block\n          local.set $p\n");
        wat.append("        end\n");
        wat.append("      else\n");
        wat.append("        local.get $h\n        local.get $n\n        local.get $flags\n");
        wat.append("        call $jn_gc_init_block\n        local.set $p\n");
        wat.append("      end\n");
        wat.append("    end\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $zd\n      (loop $z\n");
        wat.append("        local.get $i\n        local.get $n\n        i32.ge_u\n        br_if $zd\n");
        wat.append("        local.get $p\n        local.get $i\n        i32.add\n");
        wat.append("        i32.const 0\n        i32.store8\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $z\n      )\n    )\n");
        wat.append("    global.get $jn_stat_alloc_bytes\n    local.get $n\n    i32.add\n");
        wat.append("    global.set $jn_stat_alloc_bytes\n");
        wat.append("    global.get $jn_since_gc\n    local.get $n\n    i32.add\n");
        wat.append("    global.set $jn_since_gc\n");
        wat.append("    local.get $p\n  )\n");

        wat.append("  (func $jn_alloc (param $n i32) (result i32)\n");
        wat.append("    local.get $n\n    i32.const 0\n    call $jn_gc_alloc\n  )\n");
        wat.append("  (func $jn_alloc_zeroed (param $n i32) (result i32)\n");
        wat.append("    local.get $n\n    i32.const 0\n    call $jn_gc_alloc\n  )\n");
        wat.append("  (func $jn_alloc_opaque (param $n i32) (result i32)\n");
        wat.append("    local.get $n\n    i32.const ").append(WasmLayout.GC_FLAG_OPAQUE)
                .append("\n    call $jn_gc_alloc\n  )\n");
        wat.append("  (func $jn_gc_alloc_array (param $n i32) (param $refElems i32) (result i32)\n");
        wat.append("    (local $f i32)\n");
        wat.append("    i32.const 4\n    local.set $f\n");
        wat.append("    local.get $refElems\n    if\n");
        wat.append("      local.get $f\n      i32.const 8\n      i32.or\n      local.set $f\n");
        wat.append("    end\n");
        wat.append("    local.get $n\n    local.get $f\n    call $jn_gc_alloc\n  )\n");
        wat.append("  (func $jn_gc_safepoint\n");
        wat.append("    global.get $jn_since_gc\n    global.get $jn_gc_threshold\n    i32.ge_u\n");
        wat.append("    if\n      call $jn_gc_collect\n    end\n  )\n");

        wat.append("  (func $jn_gc_stats_alloc_bytes (result i32) global.get $jn_stat_alloc_bytes)\n");
        wat.append("  (func $jn_gc_stats_live_bytes (result i32) global.get $jn_stat_live_bytes)\n");
        wat.append("  (func $jn_gc_stats_free_bytes (result i32) global.get $jn_stat_free_bytes)\n");
        wat.append("  (func $jn_gc_stats_heap_cap (result i32) memory.size i32.const 16 i32.shl)\n");
        wat.append("  (func $jn_gc_stats_gc_count (result i32) global.get $jn_stat_gc_count)\n");
        wat.append("  (func $jn_gc_stats_host_roots (result i32) global.get $jn_stat_host_roots)\n");
        wat.append("  (func $jn_gc_stats_alloc_fail (result i32) global.get $jn_stat_alloc_fail)\n");
    }
}
