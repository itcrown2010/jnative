/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import java.util.List;

/**
 * Single source of truth for {@code (import "jnative" ...)} names emitted by
 * {@link BrowserWatEmitter}. Host glue (JsGlueGenerator) and Node test harnesses
 * must implement at least these imports — do not hand-maintain divergent tables.
 *
 * @author 张晓达
 */
public final class WasmHostImportContract {

    /**
     * Import names in emission order from {@link BrowserWatEmitter#emitImports}.
     * When adding a WAT import, update this list in the same change.
     */
    public static final List<String> JNATIVE_IMPORTS = List.of(
            "console_log",
            "document_get_element_by_id",
            "element_set_text",
            "document_create_element",
            "element_append_child",
            "create_text",
            "query_selector",
            "apply_dom_patches",
            "schedule_microtask",
            "delegate_event",
            "init_delegation",
            "register_callback",
            "unregister_callback",
            "set_count_label",
            "document_body",
            "input_value",
            "input_set_value",
            "memory_grew",
            "report_uncaught",
            "f64_to_chars"
    );

    /**
     * Host implementation of Java's {@code Double.toString} / {@code Float.toString} layout
     * (shortest round-trip digits, {@code 1.0E10} style outside [1e-3, 1e7)). Shared by
     * {@link JsGlueGenerator} and the Node test harness.
     */
    public static final String JAVA_FP_TO_STRING_JS = """
            function javaFpToString(v, isFloat) {
              if (Number.isNaN(v)) return 'NaN';
              if (v === Infinity) return 'Infinity';
              if (v === -Infinity) return '-Infinity';
              if (v === 0) return Object.is(v, -0) ? '-0.0' : '0.0';
              const neg = v < 0;
              const a = Math.abs(v);
              let s;
              if (isFloat) {
                for (let p = 1; p <= 9; p++) {
                  s = a.toExponential(p - 1);
                  if (Math.fround(parseFloat(s)) === a) break;
                }
              } else {
                s = a.toExponential();
              }
              const ei = s.indexOf('e');
              const mant = s.slice(0, ei).replace('.', '');
              const exp = parseInt(s.slice(ei + 1), 10);
              let digits = mant.replace(/0+$/, '');
              if (digits === '') digits = '0';
              let out;
              if (a >= 1e-3 && a < 1e7) {
                if (exp >= 0) {
                  if (digits.length > exp + 1) {
                    out = digits.slice(0, exp + 1) + '.' + digits.slice(exp + 1);
                  } else {
                    out = digits.padEnd(exp + 1, '0') + '.0';
                  }
                } else {
                  out = '0.' + '0'.repeat(-exp - 1) + digits;
                }
              } else {
                out = digits[0] + '.' + (digits.length > 1 ? digits.slice(1) : '0') + 'E' + exp;
              }
              return (neg ? '-' : '') + out;
            }
            """;

    /** JS body of the {@code f64_to_chars(v, isFloat, ptr)} import; {@code memory} must be in scope. */
    public static final String F64_TO_CHARS_JS_BODY = """
            const text = javaFpToString(v, isFloat !== 0);
            const view = new Uint8Array(memory.buffer, (ptr >>> 0) + 4, text.length);
            for (let i = 0; i < text.length; i++) view[i] = text.charCodeAt(i) & 0x7f;
            return text.length;
            """;

    private WasmHostImportContract() {
    }

    /**
     * Minimal Node ESM stub object body for {@code imports.jnative}, suitable for
     * {@code WebAssembly.instantiate}. Callables return 0 / no-op; {@code register_callback}
     * echoes the id. {@code f64_to_chars} is functional but needs the script to set
     * {@code globalThis.__jnMemory = instance.exports.memory} after instantiation.
     */
    public static String nodeJnativeImportsObjectLiteral() {
        StringBuilder sb = new StringBuilder();
        sb.append("(() => {\n");
        sb.append(JAVA_FP_TO_STRING_JS);
        sb.append("return {\n");
        for (String name : JNATIVE_IMPORTS) {
            sb.append("  ").append(name).append(": ");
            switch (name) {
                case "register_callback" -> sb.append("(id) => id");
                case "f64_to_chars" -> sb.append("(v, isFloat, ptr) => { ")
                        .append(F64_TO_CHARS_JS_BODY.replace("memory.buffer", "globalThis.__jnMemory.buffer"))
                        .append(" }");
                case "console_log", "console_warn", "console_error", "report_uncaught" ->
                        sb.append("() => {}");
                default -> {
                    // DOM / misc: return 0 for result-bearing stubs, no-op otherwise.
                    if (name.startsWith("document_") || name.equals("query_selector")
                            || name.equals("create_text") || name.equals("input_value")) {
                        sb.append("() => 0");
                    } else {
                        sb.append("() => {}");
                    }
                }
            }
            sb.append(",\n");
        }
        sb.append("};\n})()");
        return sb.toString();
    }
}
