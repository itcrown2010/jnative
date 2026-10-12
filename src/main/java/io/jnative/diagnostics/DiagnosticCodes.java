/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.diagnostics;

/**
 * Stable diagnostic codes shared by CLI text, JSON reports, and VS Code Problems.
 *
 * @author 张晓达
 */
public final class DiagnosticCodes {
    /** Syntax / parse error in user source. */
    public static final String SOURCE_SYNTAX = "JN1001";
    /** Semantic / type error in user source. */
    public static final String SOURCE_SEMANTIC = "JN1002";
    /** Dependency / package resolution failure (no forged user location). */
    public static final String DEPENDENCY = "JN2001";
    /** Package requires a capability the target does not provide. */
    public static final String PACKAGE_CAPABILITY = "JN2002";
    /** Source uses an API unavailable on the selected target. */
    public static final String TARGET_API = "JN2101";
    /** Required tool (clang, wasm toolchain, etc.) missing. */
    public static final String TOOL_MISSING = "JN3001";
    /** Native library / linker input missing. */
    public static final String NATIVE_LIB_MISSING = "JN3002";
    /** Backend explicitly does not support a requested feature. */
    public static final String BACKEND_UNSUPPORTED = "JN3101";
    /** Link / object emission failure. */
    public static final String LINK = "JN4001";
    /** Build cancelled or superseded; do not run artifacts. */
    public static final String BUILD_CANCELLED = "JN5001";
    /** Stale artifact guard — previous success must not be treated as this build. */
    public static final String STALE_ARTIFACT = "JN5002";

    private DiagnosticCodes() {
    }
}
