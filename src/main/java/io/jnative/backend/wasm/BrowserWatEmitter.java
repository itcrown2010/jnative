/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;

import io.jnative.ir.IrType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits WAT for wasm32-browser from real JNative IR (via {@link WebUiIrToWat}).
 * No fixed-template programs keyed on class names or string literals.
 * @author 张晓达
 */
public final class BrowserWatEmitter {

    private final Map<String, Integer> stringData = new LinkedHashMap<>();
    private int dataCursor = WasmLayout.DATA_MIN;

    public String emit(IrModule ir) {
        collectStrings(ir);
        if (dataCursor >= WasmLayout.DATA_MAX) {
            throw new IllegalStateException(
                    "wasm32-browser: string data overflow into shadow/GC region (cursor="
                            + dataCursor + " max=" + WasmLayout.DATA_MAX + ")");
        }
        WebUiIrToWat lowering = new WebUiIrToWat(ir, stringData);
        lowering.collect();
        WasmMetaRegistry meta = lowering.meta();
        WasmGcEmitter gc = new WasmGcEmitter(meta);
        WasmExceptionEmitter exc = new WasmExceptionEmitter(meta);

        StringBuilder wat = new StringBuilder();
        wat.append("(module\n");
        for (Map.Entry<String, String> e : lowering.indirectTypes().entrySet()) {
            wat.append("  (type $").append(e.getKey()).append(" ").append(e.getValue()).append(")\n");
        }
        emitImports(wat);
        wat.append("  (memory (export \"memory\") ")
                .append(WasmLayout.MEMORY_INITIAL_PAGES).append(' ')
                .append(WasmLayout.MEMORY_MAX_PAGES).append(")\n");
        int tableSize = Math.max(1, meta.funcrefSymbols().size());
        wat.append("  (table $jn_funcref ").append(tableSize).append(" funcref)\n");
        gc.emitGlobals(wat);
        exc.emitGlobals(wat);
        List<String> staticRefGlobals = new ArrayList<>();
        for (String g : lowering.staticGlobals()) {
            String gt = lowering.globalWatType(g);
            String zero = switch (gt) {
                case "i64" -> "i64.const 0";
                case "f32" -> "f32.const 0x0p0";
                case "f64" -> "f64.const 0x0p0";
                default -> "i32.const 0";
            };
            String watName = WebUiIrToWat.sanitizeGlobal(g);
            wat.append("  (global $").append(watName)
                    .append(" (mut ").append(gt).append(") (").append(zero).append("))\n");
            if ("i32".equals(gt) && isReferenceStatic(ir, g)) {
                staticRefGlobals.add(watName);
            }
        }
        emitData(wat);
        // Type/static tracers before mark helpers that call them
        gc.emitTypeAndStaticTracers(wat, staticRefGlobals);
        gc.emitRuntime(wat);
        emitStringAndDomRuntime(wat);
        exc.emitRuntime(wat);
        lowering.emitAll(wat);
        emitFuncrefElem(wat, meta, lowering);
        emitCallbackExport(wat, meta, lowering);
        wat.append("  (func (export \"jnative_start\")\n");
        if (lowering.emittedFunctions().contains("jnative_clinit")) {
            wat.append("    call $jnative_clinit\n");
            wat.append("    call $jnative_exception_pending\n");
            wat.append("    if\n");
            wat.append("      call $jnative_exception_uncaught\n");
            wat.append("      return\n");
            wat.append("    end\n");
        }
        if (lowering.emittedFunctions().contains("jn_main")) {
            wat.append("    i32.const 0\n");
            wat.append("    call $jn_main\n");
            wat.append("    call $jnative_exception_pending\n");
            wat.append("    if\n");
            wat.append("      call $jnative_exception_uncaught\n");
            wat.append("    end\n");
        }
        wat.append("  )\n");
        wat.append("  (func (export \"jn_bump\") (param $n i32) (result i32)\n");
        wat.append("    local.get $n\n");
        wat.append("    call $jn_alloc_opaque\n");
        wat.append("  )\n");
        wat.append("  (func (export \"jn_release_callback\") (param $id i32)\n");
        wat.append("    local.get $id\n");
        wat.append("    call $jn_unregister_dom_callback\n");
        wat.append("  )\n");
        wat.append("  (export \"jn_host_root_retain\" (func $jn_host_root_retain))\n");
        wat.append("  (export \"jn_host_root_release\" (func $jn_host_root_release))\n");
        wat.append("  (export \"jn_host_root_get\" (func $jn_host_root_get))\n");
        wat.append("  (export \"jn_gc_collect\" (func $jn_gc_collect))\n");
        wat.append("  (func (export \"jn_gc_stats\") (result i32 i32 i32 i32 i32 i32 i32)\n");
        wat.append("    call $jn_gc_stats_alloc_bytes\n");
        wat.append("    call $jn_gc_stats_live_bytes\n");
        wat.append("    call $jn_gc_stats_free_bytes\n");
        wat.append("    call $jn_gc_stats_heap_cap\n");
        wat.append("    call $jn_gc_stats_gc_count\n");
        wat.append("    call $jn_gc_stats_host_roots\n");
        wat.append("    call $jn_gc_stats_alloc_fail\n");
        wat.append("  )\n");
        wat.append(")\n");
        return wat.toString();
    }

    private static boolean isReferenceStatic(IrModule ir, String globalName) {
        for (IrModule.StaticField sf : ir.staticFields()) {
            if (sf.globalName().equals(globalName)) {
                return sf.isReference() || sf.type() == IrType.PTR;
            }
        }
        return false;
    }

    private void emitFuncrefElem(StringBuilder wat, WasmMetaRegistry meta, WebUiIrToWat lowering) {
        List<String> syms = meta.funcrefSymbols();
        if (syms.isEmpty()) {
            return;
        }
        wat.append("  (elem (table $jn_funcref) (i32.const 0)");
        for (String sym : syms) {
            if (!lowering.emittedFunctions().contains(sym)) {
                throw new IllegalStateException(
                        "wasm32-browser: MakeCallable code not emitted: " + sym);
            }
            wat.append(" $").append(WebUiIrToWat.sanitize(sym));
        }
        wat.append(")\n");
    }

    private void emitCallbackExport(StringBuilder wat, WasmMetaRegistry meta, WebUiIrToWat lowering) {
        int jsHandleTid = meta.typeIdOrNull("meta.JsHandle") == null ? 0 : meta.typeId("meta.JsHandle");
        int eventTid = meta.typeIdOrNull("meta.BrowserEvent") == null ? 0 : meta.typeId("meta.BrowserEvent");
        // jnative_callback(cbId, eventHandle) — invoke BrowserEventHandler.handle(event)
        wat.append("  (func (export \"jnative_callback\") (param $id i32) (param $ev i32)\n");
        wat.append("    (local $callable i32) (local $env i32) (local $idx i32)\n");
        wat.append("    (local $hobj i32) (local $eobj i32)\n");
        wat.append("    local.get $id\n");
        wat.append("    call $jn_lookup_dom_callback\n");
        wat.append("    local.set $callable\n");
        wat.append("    local.get $callable\n");
        wat.append("    i32.eqz\n");
        wat.append("    if\n");
        wat.append("      return\n");
        wat.append("    end\n");
        wat.append("    local.get $callable\n");
        wat.append("    i32.const ").append(WasmLayout.CALLABLE_ENV_OFFSET).append("\n");
        wat.append("    i32.add\n");
        wat.append("    i32.load\n");
        wat.append("    local.set $env\n");
        wat.append("    local.get $callable\n");
        wat.append("    i32.const ").append(WasmLayout.CALLABLE_CODE_OFFSET).append("\n");
        wat.append("    i32.add\n");
        wat.append("    i64.load\n");
        wat.append("    i32.wrap_i64\n");
        wat.append("    local.set $idx\n");
        // JsHandle { typeId, id }
        wat.append("    i32.const 8\n");
        wat.append("    call $jn_alloc_zeroed\n");
        wat.append("    local.set $hobj\n");
        wat.append("    local.get $hobj\n");
        wat.append("    i32.const ").append(jsHandleTid).append("\n");
        wat.append("    i32.store\n");
        wat.append("    local.get $hobj\n");
        wat.append("    i32.const 4\n");
        wat.append("    i32.add\n");
        wat.append("    local.get $ev\n");
        wat.append("    i32.store\n");
        // BrowserEvent { typeId, handle* }
        wat.append("    i32.const 8\n");
        wat.append("    call $jn_alloc_zeroed\n");
        wat.append("    local.set $eobj\n");
        wat.append("    local.get $eobj\n");
        wat.append("    i32.const ").append(eventTid).append("\n");
        wat.append("    i32.store\n");
        wat.append("    local.get $eobj\n");
        wat.append("    i32.const 4\n");
        wat.append("    i32.add\n");
        wat.append("    local.get $hobj\n");
        wat.append("    i32.store\n");
        wat.append("    local.get $env\n");
        wat.append("    local.get $eobj\n");
        wat.append("    local.get $idx\n");
        String domType = "jn_sig_0";
        for (Map.Entry<String, String> e : lowering.indirectTypes().entrySet()) {
            if ("(func (param i32) (param i32))".equals(e.getValue())) {
                domType = e.getKey();
                break;
            }
        }
        wat.append("    call_indirect $jn_funcref (type $").append(domType).append(")\n");
        // Uncaught language exceptions surface to JS; traps remain traps.
        wat.append("    call $jnative_exception_pending\n");
        wat.append("    if\n");
        wat.append("      call $jnative_exception_uncaught\n");
        wat.append("    end\n");
        wat.append("  )\n");
    }

    private void collectStrings(IrModule ir) {
        for (IrValue.GlobalString gs : ir.strings()) {
            if (gs != null && gs.content() != null) {
                intern(gs.content());
            }
        }
        for (IrFunction fn : ir.functions()) {
            for (IrBasicBlock block : fn.blocks()) {
                for (IrInstruction insn : block.instructions()) {
                    if (insn instanceof IrInstruction.StringLiteral sl && sl.cString() != null) {
                        intern(sl.cString().content());
                    }
                }
            }
        }
        intern("");
    }

    private int intern(String s) {
        if (s == null) {
            s = "";
        }
        Integer existing = stringData.get(s);
        if (existing != null) {
            return existing;
        }
        int off = dataCursor;
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        stringData.put(s, off);
        dataCursor += Math.max(1, utf8.length);
        return off;
    }

    private void emitImports(StringBuilder wat) {
        wat.append("  (import \"jnative\" \"console_log\" (func $js_console_log (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"document_get_element_by_id\" (func $js_get_by_id (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"element_set_text\" (func $js_set_text (param i32 i32 i32)))\n");
        wat.append("  (import \"jnative\" \"document_create_element\" (func $js_create_element (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"element_append_child\" (func $js_append_child (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"create_text\" (func $js_create_text (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"query_selector\" (func $js_query_selector (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"apply_dom_patches\" (func $js_apply_dom_patches (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"schedule_microtask\" (func $js_schedule_microtask))\n");
        wat.append("  (import \"jnative\" \"delegate_event\" (func $js_delegate_event (param i32 i32 i32 i32)))\n");
        wat.append("  (import \"jnative\" \"init_delegation\" (func $js_init_delegation (param i32)))\n");
        wat.append("  (import \"jnative\" \"register_callback\" (func $js_register_callback (param i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"unregister_callback\" (func $js_unregister_callback (param i32)))\n");
        wat.append("  (import \"jnative\" \"set_count_label\" (func $js_set_count_label (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"document_body\" (func $js_document_body (result i32)))\n");
        wat.append("  (import \"jnative\" \"input_value\" (func $js_input_value (param i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"input_set_value\" (func $js_input_set_value (param i32 i32 i32)))\n");
        wat.append("  (import \"jnative\" \"memory_grew\" (func $js_memory_grew))\n");
        wat.append("  (import \"jnative\" \"report_uncaught\" (func $js_report_uncaught (param i32 i32)))\n");
    }

    private void emitData(StringBuilder wat) {
        for (Map.Entry<String, Integer> e : stringData.entrySet()) {
            byte[] utf8 = e.getKey().getBytes(StandardCharsets.UTF_8);
            StringBuilder esc = new StringBuilder();
            for (byte b : utf8) {
                esc.append(String.format("\\%02x", b & 0xff));
            }
            wat.append("  (data (i32.const ").append(e.getValue()).append(") \"").append(esc).append("\")\n");
        }
    }

    /** String helpers + DOM glue (allocation via GC {@code $jn_alloc}). */
    private void emitStringAndDomRuntime(StringBuilder wat) {
        wat.append("""
                  (func $jn_str (param $off i32) (param $len i32) (result i32)
                    (local $p i32) (local $i i32)
                    local.get $len
                    i32.const 4
                    i32.add
                    call $jn_alloc_opaque
                    local.set $p
                    local.get $p
                    local.get $len
                    i32.store
                    i32.const 0
                    local.set $i
                    (block $done
                      (loop $copy
                        local.get $i
                        local.get $len
                        i32.ge_u
                        br_if $done
                        local.get $p
                        i32.const 4
                        i32.add
                        local.get $i
                        i32.add
                        local.get $off
                        local.get $i
                        i32.add
                        i32.load8_u
                        i32.store8
                        local.get $i
                        i32.const 1
                        i32.add
                        local.set $i
                        br $copy
                      )
                    )
                    local.get $p
                  )
                  (func $jn_str_len (param $s i32) (result i32)
                    local.get $s
                    i32.eqz
                    if (result i32)
                      i32.const 0
                    else
                      local.get $s
                      i32.load
                    end
                  )
                  (func $jn_str_data (param $s i32) (result i32)
                    local.get $s
                    i32.const 4
                    i32.add
                  )
                  (func $jn_string_length (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_len
                  )
                  (func $jn_string_char_at (param $s i32) (param $index i32) (result i32)
                    (local $len i32)
                    local.get $s
                    i32.eqz
                    if
                      i32.const 0
                      return
                    end
                    local.get $s
                    i32.load
                    local.set $len
                    local.get $index
                    i32.const 0
                    i32.lt_s
                    if
                      i32.const 0
                      return
                    end
                    local.get $index
                    local.get $len
                    i32.ge_s
                    if
                      i32.const 0
                      return
                    end
                    local.get $s
                    i32.const 4
                    i32.add
                    local.get $index
                    i32.add
                    i32.load8_u
                    return
                  )
                  (func $jn_console_log (param $s i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_console_log
                  )
                  (func $jn_get_element_by_id (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_get_by_id
                  )
                  (func $jn_create_element (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_create_element
                  )
                  (func $jn_document_body (result i32)
                    call $js_document_body
                  )
                  (func $jn_set_text (param $h i32) (param $s i32)
                    local.get $h
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_set_text
                  )
                  (func $jn_append_child (param $p i32) (param $c i32)
                    local.get $p
                    local.get $c
                    call $js_append_child
                  )
                  (func $jn_delegate_event (param $node i32) (param $typeStr i32) (param $cbId i32)
                    local.get $node
                    local.get $typeStr
                    call $jn_str_data
                    local.get $typeStr
                    call $jn_str_len
                    local.get $cbId
                    call $js_delegate_event
                  )
                  ;; callback table in reserved memory [256..768): 64 slots × 8 bytes (id,ptr) — simple map
                  (func $jn_register_dom_callback (param $callable i32) (result i32)
                    (local $id i32) (local $slot i32)
                    global.get $jn_cb_next
                    local.set $id
                    global.get $jn_cb_next
                    i32.const 1
                    i32.add
                    global.set $jn_cb_next
                    local.get $id
                    i32.const 63
                    i32.and
                    i32.const 8
                    i32.mul
                    i32.const 256
                    i32.add
                    local.set $slot
                    local.get $slot
                    local.get $id
                    i32.store
                    local.get $slot
                    i32.const 4
                    i32.add
                    local.get $callable
                    i32.store
                    local.get $id
                    call $js_register_callback
                    drop
                    local.get $id
                  )
                  (func $jn_lookup_dom_callback (param $id i32) (result i32)
                    (local $slot i32)
                    local.get $id
                    i32.const 63
                    i32.and
                    i32.const 8
                    i32.mul
                    i32.const 256
                    i32.add
                    local.set $slot
                    local.get $slot
                    i32.load
                    local.get $id
                    i32.ne
                    if (result i32)
                      i32.const 0
                    else
                      local.get $slot
                      i32.const 4
                      i32.add
                      i32.load
                    end
                  )
                  (func $jn_unregister_dom_callback (param $id i32)
                    (local $slot i32)
                    local.get $id
                    i32.const 63
                    i32.and
                    i32.const 8
                    i32.mul
                    i32.const 256
                    i32.add
                    local.set $slot
                    local.get $slot
                    i32.load
                    local.get $id
                    i32.eq
                    if
                      local.get $slot
                      i32.const 0
                      i32.store
                      local.get $slot
                      i32.const 4
                      i32.add
                      i32.const 0
                      i32.store
                    end
                    local.get $id
                    call $js_unregister_callback
                  )
                  (func $jn_input_value (param $h i32) (result i32)
                    local.get $h
                    call $js_input_value
                  )
                  (func $jn_input_set_value (param $h i32) (param $s i32)
                    local.get $h
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_input_set_value
                  )
                  (func $jn_string_concat (param $a i32) (param $b i32) (result i32)
                    (local $la i32) (local $lb i32) (local $p i32) (local $i i32)
                    local.get $a
                    call $jn_str_len
                    local.set $la
                    local.get $b
                    call $jn_str_len
                    local.set $lb
                    local.get $la
                    local.get $lb
                    i32.add
                    i32.const 4
                    i32.add
                    call $jn_alloc_opaque
                    local.set $p
                    local.get $p
                    local.get $la
                    local.get $lb
                    i32.add
                    i32.store
                    i32.const 0
                    local.set $i
                    (block $da
                      (loop $ca
                        local.get $i
                        local.get $la
                        i32.ge_u
                        br_if $da
                        local.get $p
                        i32.const 4
                        i32.add
                        local.get $i
                        i32.add
                        local.get $a
                        call $jn_str_data
                        local.get $i
                        i32.add
                        i32.load8_u
                        i32.store8
                        local.get $i
                        i32.const 1
                        i32.add
                        local.set $i
                        br $ca
                      )
                    )
                    i32.const 0
                    local.set $i
                    (block $db
                      (loop $cb
                        local.get $i
                        local.get $lb
                        i32.ge_u
                        br_if $db
                        local.get $p
                        i32.const 4
                        i32.add
                        local.get $la
                        i32.add
                        local.get $i
                        i32.add
                        local.get $b
                        call $jn_str_data
                        local.get $i
                        i32.add
                        i32.load8_u
                        i32.store8
                        local.get $i
                        i32.const 1
                        i32.add
                        local.set $i
                        br $cb
                      )
                    )
                    local.get $p
                  )
                  ;; decimal int → string (for money display helpers)
                  (func $jn_string_value_of_i32 (param $v i32) (result i32)
                    (local $neg i32) (local $n i32) (local $tmp i32) (local $len i32) (local $p i32) (local $i i32)
                    local.get $v
                    i32.const 0
                    i32.lt_s
                    local.set $neg
                    local.get $v
                    local.get $v
                    i32.const 31
                    i32.shr_s
                    i32.xor
                    local.get $v
                    i32.const 31
                    i32.shr_s
                    i32.sub
                    local.set $n
                    local.get $n
                    i32.eqz
                    if (result i32)
                      i32.const 1
                      local.set $len
                      i32.const 0
                    else
                      i32.const 0
                      local.set $len
                      local.get $n
                      local.set $tmp
                      (loop $cnt
                        local.get $len
                        i32.const 1
                        i32.add
                        local.set $len
                        local.get $tmp
                        i32.const 10
                        i32.div_u
                        local.set $tmp
                        local.get $tmp
                        br_if $cnt
                      )
                      i32.const 0
                    end
                    drop
                    local.get $len
                    local.get $neg
                    i32.add
                    i32.const 4
                    i32.add
                    call $jn_alloc_opaque
                    local.set $p
                    local.get $p
                    local.get $len
                    local.get $neg
                    i32.add
                    i32.store
                    local.get $neg
                    if
                      local.get $p
                      i32.const 4
                      i32.add
                      i32.const 45
                      i32.store8
                    end
                    local.get $n
                    i32.eqz
                    if
                      local.get $p
                      i32.const 4
                      i32.add
                      local.get $neg
                      i32.add
                      i32.const 48
                      i32.store8
                      local.get $p
                      return
                    end
                    local.get $len
                    local.set $i
                    (loop $dig
                      local.get $i
                      i32.const 1
                      i32.sub
                      local.set $i
                      local.get $p
                      i32.const 4
                      i32.add
                      local.get $neg
                      i32.add
                      local.get $i
                      i32.add
                      local.get $n
                      i32.const 10
                      i32.rem_u
                      i32.const 48
                      i32.add
                      i32.store8
                      local.get $n
                      i32.const 10
                      i32.div_u
                      local.set $n
                      local.get $i
                      br_if $dig
                    )
                    local.get $p
                  )
                """);
    }
}
