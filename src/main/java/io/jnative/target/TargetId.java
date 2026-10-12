/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import java.util.Locale;

/**
 * Logical compile / deploy targets. Mobile containers reuse the wasm32-browser
 * compile triple; they must not be labeled as a full native mobile runtime.
 *
 * @author 张晓达
 */
public enum TargetId {
    /** Host OS native binary (clang/LLVM). Platforms: darwin/linux/windows host triples. */
    NATIVE_HOST("native"),
    /** Browser WebAssembly + JS glue. */
    WASM32_BROWSER("wasm32-browser"),
    /**
     * Mobile shell (iOS/Android WebView) that embeds a wasm32-browser artifact.
     * Compile target is {@link #WASM32_BROWSER}; packaging is platform-specific.
     */
    MOBILE_CONTAINER("mobile-container");

    private final String triple;

    TargetId(String triple) {
        this.triple = triple;
    }

    /** Canonical triple used by the compiler / package resolver. */
    public String triple() {
        return triple;
    }

    /** Triple actually passed to the compiler frontend/backend. */
    public String compileTriple() {
        return this == MOBILE_CONTAINER ? WASM32_BROWSER.triple : triple;
    }

    public boolean isBrowserCompile() {
        return this == WASM32_BROWSER || this == MOBILE_CONTAINER;
    }

    public static TargetId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NATIVE_HOST;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "native", "host", "native-host", "desktop-native" -> NATIVE_HOST;
            case "wasm32-browser", "wasm32", "browser", "wasm" -> WASM32_BROWSER;
            case "mobile-container", "mobile", "ios-container", "android-container" -> MOBILE_CONTAINER;
            default -> throw new IllegalArgumentException(
                    "unknown target: " + raw + " (expected native, wasm32-browser, or mobile-container)");
        };
    }

    /** Parse a compile triple; mobile packaging names map to {@link #MOBILE_CONTAINER}. */
    public static TargetId fromCompileTriple(String triple) {
        if (triple == null || triple.isBlank()) {
            return NATIVE_HOST;
        }
        String t = triple.trim().toLowerCase(Locale.ROOT);
        if (t.contains("mobile") || "ios".equals(t) || "android".equals(t)) {
            return MOBILE_CONTAINER;
        }
        if (t.contains("wasm") || t.contains("browser")) {
            return WASM32_BROWSER;
        }
        return NATIVE_HOST;
    }
}
