/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

import java.util.Locale;
import java.util.Set;

/** Named compile targets.
 * @author 张晓达
 */
public enum CompileTarget {
    NATIVE_HOST("native", TargetCapability.forNative()),
    WASM32_BROWSER("wasm32-browser", TargetCapability.forWasmBrowser());

    private final String triple;
    private final Set<TargetCapability> capabilities;

    CompileTarget(String triple, Set<TargetCapability> capabilities) {
        this.triple = triple;
        this.capabilities = capabilities;
    }

    public String triple() {
        return triple;
    }

    public Set<TargetCapability> capabilities() {
        return capabilities;
    }

    public boolean isBrowser() {
        return this == WASM32_BROWSER;
    }

    public static CompileTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NATIVE_HOST;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "native", "host", "native-host" -> NATIVE_HOST;
            case "wasm32-browser", "wasm32", "browser", "wasm" -> WASM32_BROWSER;
            default -> throw new IllegalArgumentException("unknown target: " + raw
                    + " (expected native or wasm32-browser)");
        };
    }
}
