/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

import io.jnative.target.TargetId;
import io.jnative.target.TargetProfiles;

import java.util.EnumSet;
import java.util.Set;

/**
 * Compile-time package/backend capability flags for native vs browser targets.
 * Canonical provided sets live in {@link TargetProfiles}.
 *
 * @author 张晓达
 */
public enum TargetCapability {
    NATIVE_FFI,
    FILESYSTEM,
    RAW_SOCKET,
    PROCESS,
    THREADS,
    BROWSER_DOM,
    JS_INTEROP,
    DATABASE,
    DESKTOP_UI,
    TLS_NATIVE,
    HTTP2_NATIVE,
    BROWSER_FETCH;

    public static Set<TargetCapability> forNative() {
        return EnumSet.copyOf(TargetProfiles.packageFlags(TargetId.NATIVE_HOST));
    }

    public static Set<TargetCapability> forWasmBrowser() {
        return EnumSet.copyOf(TargetProfiles.packageFlags(TargetId.WASM32_BROWSER));
    }

    public static Set<TargetCapability> forTriple(String triple) {
        return EnumSet.copyOf(TargetProfiles.packageFlagsForTriple(triple));
    }
}
