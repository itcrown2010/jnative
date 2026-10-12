/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

/**
 * Explicit exception + abrupt-completion runtime for wasm32-browser.
 * Mirrors the native IR model ({@code jnative_exception_*} / {@code jnative_completion_*}):
 * no WASM exception-handling opcodes — pending state + control-flow only.
 *
 * <p>Throwable payload (wasm32, headerSize=4, ptr=4):
 * typeId@0, message@4, cause@8, suppressed@12.
 * @author 张晓达
 */
public final class WasmExceptionEmitter {

    /** Byte offset of suppressed Throwable[] field in payload. */
    public static final int THROWABLE_SUPPRESSED_OFFSET = 12;
    /** Minimum Throwable payload bytes. */
    public static final int THROWABLE_PAYLOAD_SIZE = 16;

    private final WasmMetaRegistry meta;

    public WasmExceptionEmitter(WasmMetaRegistry meta) {
        this.meta = meta;
    }

    public void emitGlobals(StringBuilder wat) {
        // jn_pending_exc already emitted by WasmGcEmitter
        wat.append("  (global $jn_comp_kind (mut i32) (i32.const 0))\n"); // 0 none, 1 ret, 2 br, 3 cont
        wat.append("  (global $jn_comp_target (mut i32) (i32.const 0))\n");
        wat.append("  (global $jn_comp_ret_i64 (mut i64) (i64.const 0))\n");
        wat.append("  (global $jn_comp_ret_ptr (mut i32) (i32.const 0))\n");
    }

    public void emitRuntime(StringBuilder wat) {
        int npeId = typeIdOrZero("meta.NullPointerException");
        int iaeId = typeIdOrZero("meta.IllegalArgumentException");
        int thrId = typeIdOrZero("meta.Throwable");
        int arrMeta = typeIdOrZero("meta.array.CThrowable");

        wat.append("  ;; ---- wasm32 explicit exception / completion (no WASM EH) ----\n");

        // Allocate a simple exception: [typeId][message][cause=0][suppressed=0]
        wat.append("  (func $jn_ex_alloc (param $tid i32) (param $msg i32) (result i32)\n");
        wat.append("    (local $p i32)\n");
        wat.append("    i32.const ").append(THROWABLE_PAYLOAD_SIZE).append("\n");
        wat.append("    call $jn_alloc_zeroed\n");
        wat.append("    local.set $p\n");
        wat.append("    local.get $p\n    i32.eqz\n    if (result i32)\n      i32.const 0\n    else\n");
        wat.append("      local.get $p\n      local.get $tid\n      i32.store\n");
        wat.append("      local.get $p\n      i32.const 4\n      i32.add\n");
        wat.append("      local.get $msg\n      i32.store\n");
        wat.append("      local.get $p\n    end\n  )\n");

        wat.append("  (func $jnative_exception_throw (param $ex i32)\n");
        wat.append("    local.get $ex\n    i32.eqz\n");
        wat.append("    if\n");
        wat.append("      call $jnative_throw_npe\n");
        wat.append("      return\n");
        wat.append("    end\n");
        wat.append("    local.get $ex\n    global.set $jn_pending_exc\n");
        wat.append("    i32.const 0\n    global.set $jn_comp_kind\n  )\n");

        wat.append("  (func $jnative_exception_pending (result i32)\n");
        wat.append("    global.get $jn_pending_exc\n    i32.const 0\n    i32.ne\n  )\n");

        wat.append("  (func $jnative_exception_peek (result i32)\n");
        wat.append("    global.get $jn_pending_exc\n  )\n");

        wat.append("  (func $jnative_exception_take (result i32)\n");
        wat.append("    (local $ex i32)\n");
        wat.append("    global.get $jn_pending_exc\n    local.set $ex\n");
        wat.append("    i32.const 0\n    global.set $jn_pending_exc\n");
        wat.append("    local.get $ex\n  )\n");

        wat.append("  (func $jnative_exception_clear\n");
        wat.append("    i32.const 0\n    global.set $jn_pending_exc\n  )\n");

        wat.append("  (func $jnative_throw_npe\n");
        if (npeId > 0) {
            wat.append("    i32.const ").append(npeId).append("\n");
            wat.append("    i32.const 0\n");
            wat.append("    call $jn_ex_alloc\n");
            wat.append("    global.set $jn_pending_exc\n");
        } else if (thrId > 0) {
            wat.append("    i32.const ").append(thrId).append("\n");
            wat.append("    i32.const 0\n");
            wat.append("    call $jn_ex_alloc\n");
            wat.append("    global.set $jn_pending_exc\n");
        } else {
            // Last resort: non-null sentinel so pending is observable (no typed object).
            wat.append("    i32.const 1\n    global.set $jn_pending_exc\n");
        }
        wat.append("  )\n");

        wat.append("  (func $jnative_throw_illegal_argument\n");
        if (iaeId > 0) {
            wat.append("    i32.const ").append(iaeId).append("\n");
            wat.append("    i32.const 0\n");
            wat.append("    call $jn_ex_alloc\n");
            wat.append("    global.set $jn_pending_exc\n");
        } else {
            wat.append("    call $jnative_throw_npe\n");
        }
        wat.append("  )\n");

        // Soft stubs for other simple throws used by stdlib
        for (String name : new String[]{
                "jnative_throw_aioobe", "jnative_throw_negative_array_size",
                "jnative_throw_class_cast", "jnative_throw_arithmetic",
                "jnative_throw_illegal_monitor", "jnative_throw_illegal_state"
        }) {
            wat.append("  (func $").append(name).append("\n");
            wat.append("    call $jnative_throw_npe\n  )\n");
        }

        emitSuppressed(wat, arrMeta);
        emitGetSuppressed(wat, arrMeta);
        emitCompletion(wat);
        emitUncaught(wat);
        emitHostRaise(wat);
    }

    private void emitHostRaise(StringBuilder wat) {
        int rtId = typeIdOrZero("meta.RuntimeException");
        if (rtId == 0) {
            rtId = typeIdOrZero("meta.Exception");
        }
        if (rtId == 0) {
            rtId = typeIdOrZero("meta.Throwable");
        }
        wat.append("  (func $jn_raise_host_error (param $msg i32)\n");
        if (rtId > 0) {
            wat.append("    i32.const ").append(rtId).append("\n");
            wat.append("    local.get $msg\n");
            wat.append("    call $jn_ex_alloc\n");
            wat.append("    call $jnative_exception_throw\n");
        } else {
            wat.append("    call $jnative_throw_npe\n");
        }
        wat.append("  )\n");
        wat.append("  (func (export \"jn_raise_host_error\") (param $msg i32)\n");
        wat.append("    local.get $msg\n");
        wat.append("    call $jn_raise_host_error\n  )\n");
        wat.append("  (func (export \"jn_exception_pending\") (result i32)\n");
        wat.append("    call $jnative_exception_pending\n  )\n");
        wat.append("  (func (export \"jn_exception_peek\") (result i32)\n");
        wat.append("    call $jnative_exception_peek\n  )\n");
        wat.append("  (func (export \"jn_exception_take\") (result i32)\n");
        wat.append("    call $jnative_exception_take\n  )\n");
        wat.append("  (func (export \"jn_exception_clear\")\n");
        wat.append("    call $jnative_exception_clear\n  )\n");
        wat.append("  (func (export \"jn_exception_uncaught\")\n");
        wat.append("    call $jnative_exception_uncaught\n  )\n");
    }

    private void emitGetSuppressed(StringBuilder wat, int arrTypeId) {
        int off = THROWABLE_SUPPRESSED_OFFSET;
        wat.append("  (func $jnative_throwable_get_suppressed (param $ex i32) (result i32)\n");
        wat.append("    (local $old i32) (local $len i32) (local $out i32) (local $i i32)\n");
        wat.append("    local.get $ex\n    i32.eqz\n");
        wat.append("    if (result i32)\n");
        wat.append("      call $jnative_throw_npe\n");
        wat.append("      i32.const 0\n");
        wat.append("    else\n");
        wat.append("      local.get $ex\n      i32.const ").append(off).append("\n      i32.add\n");
        wat.append("      i32.load\n      local.set $old\n");
        wat.append("      local.get $old\n      i32.eqz\n");
        wat.append("      if (result i32)\n");
        wat.append("        i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET).append("\n");
        wat.append("        i32.const 1\n");
        wat.append("        call $jn_gc_alloc_array\n");
        wat.append("        local.set $out\n");
        wat.append("        local.get $out\n        i32.eqz\n");
        wat.append("        if (result i32)\n          i32.const 0\n        else\n");
        if (arrTypeId > 0) {
            wat.append("          local.get $out\n          i32.const ").append(arrTypeId)
                    .append("\n          i32.store\n");
        }
        wat.append("          local.get $out\n          i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n          i32.add\n          i32.const 0\n          i32.store\n");
        wat.append("          local.get $out\n        end\n");
        wat.append("      else\n");
        wat.append("        local.get $old\n        i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n        i32.add\n        i32.load\n        local.set $len\n");
        wat.append("        local.get $len\n        i32.const 2\n        i32.shl\n");
        wat.append("        i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET).append("\n        i32.add\n");
        wat.append("        i32.const 1\n");
        wat.append("        call $jn_gc_alloc_array\n");
        wat.append("        local.set $out\n");
        wat.append("        local.get $out\n        i32.eqz\n");
        wat.append("        if (result i32)\n          i32.const 0\n        else\n");
        if (arrTypeId > 0) {
            wat.append("          local.get $out\n          i32.const ").append(arrTypeId)
                    .append("\n          i32.store\n");
        }
        wat.append("          local.get $out\n          i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n          i32.add\n          local.get $len\n          i32.store\n");
        wat.append("          i32.const 0\n          local.set $i\n");
        wat.append("          (block $gd\n            (loop $gl\n");
        wat.append("              local.get $i\n              local.get $len\n              i32.ge_s\n");
        wat.append("              br_if $gd\n");
        wat.append("              local.get $out\n              i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n              i32.add\n");
        wat.append("              local.get $i\n              i32.const 2\n              i32.shl\n              i32.add\n");
        wat.append("              local.get $old\n              i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n              i32.add\n");
        wat.append("              local.get $i\n              i32.const 2\n              i32.shl\n              i32.add\n");
        wat.append("              i32.load\n              i32.store\n");
        wat.append("              local.get $i\n              i32.const 1\n              i32.add\n");
        wat.append("              local.set $i\n              br $gl\n            )\n          )\n");
        wat.append("          local.get $out\n        end\n");
        wat.append("      end\n");
        wat.append("    end\n  )\n");
    }

    private void emitSuppressed(StringBuilder wat, int arrTypeId) {
        int off = THROWABLE_SUPPRESSED_OFFSET;
        wat.append("  (func $jnative_throwable_add_suppressed (param $primary i32) (param $sup i32)\n");
        wat.append("    (local $old i32) (local $len i32) (local $narr i32) (local $i i32)\n");
        wat.append("    local.get $primary\n    i32.eqz\n    if\n      return\n    end\n");
        wat.append("    local.get $sup\n    i32.eqz\n    if\n      call $jnative_throw_npe\n      return\n    end\n");
        wat.append("    local.get $primary\n    local.get $sup\n    i32.eq\n");
        wat.append("    if\n      call $jnative_throw_illegal_argument\n      return\n    end\n");
        wat.append("    local.get $primary\n    i32.const ").append(off).append("\n    i32.add\n");
        wat.append("    i32.load\n    local.set $old\n");
        wat.append("    local.get $old\n    i32.eqz\n");
        wat.append("    if\n");
        // first: alloc length-1 ref array
        wat.append("      i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET + 4).append("\n");
        wat.append("      i32.const 1\n");
        wat.append("      call $jn_gc_alloc_array\n");
        wat.append("      local.set $narr\n");
        wat.append("      local.get $narr\n      i32.eqz\n      if\n        return\n      end\n");
        if (arrTypeId > 0) {
            wat.append("      local.get $narr\n      i32.const ").append(arrTypeId).append("\n      i32.store\n");
        }
        wat.append("      local.get $narr\n      i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n      i32.add\n      i32.const 1\n      i32.store\n");
        wat.append("      local.get $narr\n      i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n      i32.add\n      local.get $sup\n      i32.store\n");
        wat.append("      local.get $primary\n      i32.const ").append(off).append("\n      i32.add\n");
        wat.append("      local.get $narr\n      i32.store\n");
        wat.append("      return\n");
        wat.append("    end\n");
        // grow
        wat.append("    local.get $old\n    i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n    i32.add\n    i32.load\n    local.set $len\n");
        wat.append("    local.get $len\n    i32.const 1\n    i32.add\n");
        wat.append("    i32.const 2\n    i32.shl\n"); // *4
        wat.append("    i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET).append("\n    i32.add\n");
        wat.append("    i32.const 1\n");
        wat.append("    call $jn_gc_alloc_array\n");
        wat.append("    local.set $narr\n");
        wat.append("    local.get $narr\n    i32.eqz\n    if\n      return\n    end\n");
        if (arrTypeId > 0) {
            wat.append("    local.get $narr\n    i32.const ").append(arrTypeId).append("\n    i32.store\n");
        }
        wat.append("    local.get $narr\n    i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET)
                .append("\n    i32.add\n");
        wat.append("    local.get $len\n    i32.const 1\n    i32.add\n    i32.store\n");
        wat.append("    i32.const 0\n    local.set $i\n");
        wat.append("    (block $cdone\n      (loop $c\n");
        wat.append("        local.get $i\n        local.get $len\n        i32.ge_s\n        br_if $cdone\n");
        wat.append("        local.get $narr\n        i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n        i32.add\n");
        wat.append("        local.get $i\n        i32.const 2\n        i32.shl\n        i32.add\n");
        wat.append("        local.get $old\n        i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n        i32.add\n");
        wat.append("        local.get $i\n        i32.const 2\n        i32.shl\n        i32.add\n");
        wat.append("        i32.load\n        i32.store\n");
        wat.append("        local.get $i\n        i32.const 1\n        i32.add\n        local.set $i\n");
        wat.append("        br $c\n      )\n    )\n");
        wat.append("    local.get $narr\n    i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET)
                .append("\n    i32.add\n");
        wat.append("    local.get $len\n    i32.const 2\n    i32.shl\n    i32.add\n");
        wat.append("    local.get $sup\n    i32.store\n");
        wat.append("    local.get $primary\n    i32.const ").append(off).append("\n    i32.add\n");
        wat.append("    local.get $narr\n    i32.store\n  )\n");
    }

    private void emitCompletion(StringBuilder wat) {
        wat.append("  (func $jnative_completion_clear\n");
        wat.append("    i32.const 0\n    global.set $jn_comp_kind\n");
        wat.append("    i32.const 0\n    global.set $jn_comp_target\n");
        wat.append("    i64.const 0\n    global.set $jn_comp_ret_i64\n");
        wat.append("    i32.const 0\n    global.set $jn_comp_ret_ptr\n  )\n");

        wat.append("  (func $jnative_completion_set_return_i64 (param $v i64)\n");
        wat.append("    i32.const 1\n    global.set $jn_comp_kind\n");
        wat.append("    local.get $v\n    global.set $jn_comp_ret_i64\n");
        wat.append("    i32.const 0\n    global.set $jn_comp_ret_ptr\n  )\n");

        wat.append("  (func $jnative_completion_set_return_ptr (param $v i32)\n");
        wat.append("    i32.const 1\n    global.set $jn_comp_kind\n");
        wat.append("    local.get $v\n    global.set $jn_comp_ret_ptr\n");
        wat.append("    i64.const 0\n    global.set $jn_comp_ret_i64\n  )\n");

        wat.append("  (func $jnative_completion_set_break (param $t i32)\n");
        wat.append("    i32.const 2\n    global.set $jn_comp_kind\n");
        wat.append("    local.get $t\n    global.set $jn_comp_target\n  )\n");

        wat.append("  (func $jnative_completion_set_continue (param $t i32)\n");
        wat.append("    i32.const 3\n    global.set $jn_comp_kind\n");
        wat.append("    local.get $t\n    global.set $jn_comp_target\n  )\n");

        wat.append("  (func $jnative_completion_has_return (result i32)\n");
        wat.append("    global.get $jn_comp_kind\n    i32.const 1\n    i32.eq\n  )\n");
        wat.append("  (func $jnative_completion_has_break (result i32)\n");
        wat.append("    global.get $jn_comp_kind\n    i32.const 2\n    i32.eq\n  )\n");
        wat.append("  (func $jnative_completion_has_continue (result i32)\n");
        wat.append("    global.get $jn_comp_kind\n    i32.const 3\n    i32.eq\n  )\n");

        wat.append("  (func $jnative_completion_take_return_i64 (result i64)\n");
        wat.append("    (local $v i64)\n");
        wat.append("    global.get $jn_comp_ret_i64\n    local.set $v\n");
        wat.append("    call $jnative_completion_clear\n");
        wat.append("    local.get $v\n  )\n");

        wat.append("  (func $jnative_completion_take_return_ptr (result i32)\n");
        wat.append("    (local $v i32)\n");
        wat.append("    global.get $jn_comp_ret_ptr\n    local.set $v\n");
        wat.append("    call $jnative_completion_clear\n");
        wat.append("    local.get $v\n  )\n");

        wat.append("  (func $jnative_completion_take_target (result i32)\n");
        wat.append("    (local $v i32)\n");
        wat.append("    global.get $jn_comp_target\n    local.set $v\n");
        wat.append("    call $jnative_completion_clear\n");
        wat.append("    local.get $v\n  )\n");
    }

    private void emitUncaught(StringBuilder wat) {
        wat.append("  (func $jnative_exception_uncaught\n");
        wat.append("    (local $ex i32) (local $msg i32) (local $ptr i32) (local $len i32)\n");
        wat.append("    global.get $jn_pending_exc\n    local.set $ex\n");
        wat.append("    local.get $ex\n    i32.eqz\n    if\n      return\n    end\n");
        // Clear before host throw — report_uncaught rethrows into JS and aborts this frame.
        wat.append("    i32.const 0\n    global.set $jn_pending_exc\n");
        wat.append("    local.get $ex\n    i32.const 4\n    i32.add\n    i32.load\n    local.set $msg\n");
        wat.append("    local.get $msg\n    i32.eqz\n");
        wat.append("    if\n");
        wat.append("      i32.const 0\n      i32.const 0\n");
        wat.append("      call $js_report_uncaught\n");
        wat.append("    else\n");
        wat.append("      local.get $msg\n      call $jn_str_data\n      local.set $ptr\n");
        wat.append("      local.get $msg\n      call $jn_str_len\n      local.set $len\n");
        wat.append("      local.get $ptr\n      local.get $len\n");
        wat.append("      call $js_report_uncaught\n");
        wat.append("    end\n  )\n");
    }

    private int typeIdOrZero(String metaGlobal) {
        Integer id = meta.typeIdOrNull(metaGlobal);
        return id == null ? 0 : id;
    }
}
