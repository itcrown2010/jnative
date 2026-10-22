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
            "report_uncaught"
    );

    private WasmHostImportContract() {
    }

    /**
     * Minimal Node ESM stub object body for {@code imports.jnative}, suitable for
     * {@code WebAssembly.instantiate}. Callables return 0 / no-op; {@code register_callback}
     * echoes the id.
     */
    public static String nodeJnativeImportsObjectLiteral() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (String name : JNATIVE_IMPORTS) {
            sb.append("  ").append(name).append(": ");
            switch (name) {
                case "register_callback" -> sb.append("(id) => id");
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
        sb.append("}");
        return sb.toString();
    }
}
