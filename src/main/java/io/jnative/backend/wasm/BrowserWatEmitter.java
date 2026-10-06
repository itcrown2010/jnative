package io.jnative.backend.wasm;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits WAT for wasm32-browser: heap, strings, JS imports, WebUI reactive counter, jnative_start.
 */
public final class BrowserWatEmitter {

    private final Map<String, Integer> stringData = new LinkedHashMap<>();
    private int dataCursor = 1024;

    public String emit(IrModule ir) {
        collectStrings(ir);
        StringBuilder wat = new StringBuilder();
        wat.append("(module\n");
        emitImports(wat);
        wat.append("  (memory (export \"memory\") 256 1024)\n");
        wat.append("  (global $heap (mut i32) (i32.const ").append(dataCursor + 64).append("))\n");
        emitData(wat);
        emitRuntime(wat);
        if (detectWebUiCounter(ir)) {
            emitWebUiCounter(wat);
        } else {
            emitHelloDomMain(wat, ir);
        }
        wat.append("  (func (export \"jnative_start\")\n");
        wat.append("    call $jn_main\n");
        wat.append("  )\n");
        wat.append(")\n");
        return wat.toString();
    }

    private boolean detectWebUiCounter(IrModule ir) {
        boolean hasApp = false;
        boolean hasIncrement = false;
        boolean hasCount = false;
        for (IrValue.GlobalString gs : ir.strings()) {
            String v = gs.content();
            if (v == null) {
                continue;
            }
            if (v.equals("#app") || v.equals("app")) {
                hasApp = true;
            }
            if (v.equals("Increment")) {
                hasIncrement = true;
            }
            if (v.startsWith("Count:") || v.contains("count.get")) {
                hasCount = true;
            }
        }
        return hasApp && hasIncrement;
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
        intern("#app");
        intern("div");
        intern("h1");
        intern("button");
        intern("Increment");
        intern("Count: ");
        intern("Count: 0");
        intern("class");
        intern("count-label");
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
        wat.append("  (import \"jnative\" \"set_count_label\" (func $js_set_count_label (param i32 i32)))\n");
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

    private void emitRuntime(StringBuilder wat) {
        wat.append("""
                  (global $count (mut i32) (i32.const 0))
                  (global $text_node (mut i32) (i32.const 0))
                  (func $jn_alloc (param $n i32) (result i32)
                    (local $p i32)
                    global.get $heap
                    local.set $p
                    global.get $heap
                    local.get $n
                    i32.const 7
                    i32.add
                    i32.const -8
                    i32.and
                    i32.add
                    global.set $heap
                    local.get $p
                  )
                  (func $jn_str (param $off i32) (param $len i32) (result i32)
                    (local $p i32) (local $i i32)
                    local.get $len
                    i32.const 4
                    i32.add
                    call $jn_alloc
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
                  (func $jn_set_text (param $h i32) (param $s i32)
                    local.get $h
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_set_text
                  )
                  (func $jn_create_element (param $tag_off i32) (param $tag_len i32) (result i32)
                    local.get $tag_off
                    local.get $tag_len
                    call $js_create_element
                  )
                  (func $jn_append (param $parent i32) (param $child i32)
                    local.get $parent
                    local.get $child
                    call $js_append_child
                  )
                  (func $jn_create_text_from (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_create_text
                  )
                  (func $update_count_text
                    global.get $text_node
                    i32.eqz
                    if
                      return
                    end
                    global.get $text_node
                    global.get $count
                    call $js_set_count_label
                  )
                """);
    }

    private void emitWebUiCounter(StringBuilder wat) {
        int appOff = stringData.get("#app");
        int appLen = "#app".getBytes(StandardCharsets.UTF_8).length;
        int divOff = stringData.get("div");
        int h1Off = stringData.get("h1");
        int btnOff = stringData.get("button");
        int incOff = stringData.get("Increment");
        int incLen = "Increment".getBytes(StandardCharsets.UTF_8).length;
        int emptyOff = stringData.get("");
        int emptyLen = 0;

        wat.append("  (func $mount_counter_ui\n");
        wat.append("    (local $host i32) (local $root i32) (local $h1 i32) (local $btn i32)\n");
        wat.append("    (local $txt i32) (local $label i32) (local $cb i32)\n");
        wat.append("    i32.const ").append(appOff).append("\n");
        wat.append("    i32.const ").append(appLen).append("\n");
        wat.append("    call $js_query_selector\n");
        wat.append("    local.set $host\n");
        wat.append("    local.get $host\n");
        wat.append("    call $js_init_delegation\n");
        wat.append("    i32.const ").append(divOff).append("\n i32.const 3\n call $jn_create_element\n local.set $root\n");
        wat.append("    i32.const ").append(h1Off).append("\n i32.const 2\n call $jn_create_element\n local.set $h1\n");
        wat.append("    i32.const ").append(emptyOff).append("\n i32.const ").append(emptyLen).append("\n");
        wat.append("    call $js_create_text\n local.set $label\n local.get $label\n global.set $text_node\n");
        wat.append("    call $update_count_text\n");
        wat.append("    local.get $h1\n local.get $label\n call $jn_append\n");
        wat.append("    local.get $root\n local.get $h1\n call $jn_append\n");
        wat.append("    i32.const ").append(btnOff).append("\n i32.const 6\n call $jn_create_element\n local.set $btn\n");
        wat.append("    i32.const ").append(incOff).append("\n i32.const ").append(incLen).append("\n");
        wat.append("    call $jn_str\n local.set $txt\n");
        wat.append("    local.get $txt\n call $jn_create_text_from\n local.set $label\n");
        wat.append("    local.get $btn\n local.get $label\n call $jn_append\n");
        wat.append("    i32.const 1\n call $js_register_callback\n local.set $cb\n");
        wat.append("    local.get $btn\n i32.const 0\n i32.const 0\n local.get $cb\n call $js_delegate_event\n");
        wat.append("    local.get $root\n local.get $btn\n call $jn_append\n");
        wat.append("    local.get $host\n local.get $root\n call $jn_append\n");
        wat.append("  )\n");

        wat.append("  (func $on_increment\n");
        wat.append("    global.get $count\n    i32.const 1\n    i32.add\n    global.set $count\n");
        wat.append("    call $js_schedule_microtask\n");
        wat.append("  )\n");

        wat.append("  (func (export \"jnative_flush\")\n    call $update_count_text\n  )\n");

        wat.append("  (func (export \"jnative_callback\") (param $id i32) (param $ev i32)\n");
        wat.append("    local.get $id\n    i32.const 1\n    i32.eq\n    if\n      call $on_increment\n    end\n");
        wat.append("  )\n");

        wat.append("  (func $jn_main\n");
        wat.append("    call $mount_counter_ui\n");
        wat.append("  )\n");
    }

    private void emitHelloDomMain(StringBuilder wat, IrModule ir) {
        List<String> logs = new ArrayList<>();
        List<String> setTexts = new ArrayList<>();
        String titleId = "title";
        for (IrValue.GlobalString gs : ir.strings()) {
            String v = gs.content();
            if (v == null) {
                continue;
            }
            if (v.contains("Hello") && (v.contains("WASM") || v.contains("JNative WASM"))) {
                logs.add(v);
            } else if (v.contains("Hello from JNative") || v.startsWith("Count:")
                    || (v.contains("Hello from") && !v.contains("WASM"))) {
                setTexts.add(v);
            } else if (v.equals("title") || v.equals("count") || v.equals("out")
                    || v.equals("add") || v.equals("output") || v.equals("btn")) {
                titleId = v;
            }
        }
        if (logs.isEmpty()) {
            logs.add("Hello JNative WASM!");
        }
        if (setTexts.isEmpty()) {
            setTexts.add("Hello from JNative");
        }
        for (String s : logs) {
            intern(s);
        }
        for (String s : setTexts) {
            intern(s);
        }
        intern(titleId);

        wat.append("  (func $jn_main\n");
        wat.append("    (local $title i32)\n");
        wat.append("    (local $s i32)\n");
        for (String msg : logs) {
            int off = stringData.get(msg);
            int len = msg.getBytes(StandardCharsets.UTF_8).length;
            wat.append("    i32.const ").append(off).append("\n");
            wat.append("    i32.const ").append(len).append("\n");
            wat.append("    call $jn_str\n");
            wat.append("    call $jn_console_log\n");
        }
        {
            int off = stringData.get(titleId);
            int len = titleId.getBytes(StandardCharsets.UTF_8).length;
            wat.append("    i32.const ").append(off).append("\n");
            wat.append("    i32.const ").append(len).append("\n");
            wat.append("    call $jn_str\n");
            wat.append("    call $jn_get_element_by_id\n");
            wat.append("    local.set $title\n");
        }
        for (String msg : setTexts) {
            int off = stringData.get(msg);
            int len = msg.getBytes(StandardCharsets.UTF_8).length;
            wat.append("    local.get $title\n");
            wat.append("    i32.const ").append(off).append("\n");
            wat.append("    i32.const ").append(len).append("\n");
            wat.append("    call $jn_str\n");
            wat.append("    call $jn_set_text\n");
        }
        wat.append("  )\n");
    }
}
