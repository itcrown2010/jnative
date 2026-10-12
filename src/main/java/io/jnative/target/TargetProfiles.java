/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import io.jnative.backend.CompileTarget;
import io.jnative.backend.TargetCapability;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for target capability matrices.
 * Package {@link io.jnative.pkg.TargetCapability} and backend
 * {@link TargetCapability} must not diverge from {@link #packageFlags(TargetId)}.
 *
 * @author 张晓达
 */
public final class TargetProfiles {
    private static final Map<TargetId, TargetProfile> PROFILES = build();

    private TargetProfiles() {
    }

    public static TargetProfile profile(TargetId id) {
        TargetProfile p = PROFILES.get(id == null ? TargetId.NATIVE_HOST : id);
        if (p == null) {
            throw new IllegalStateException("missing profile for " + id);
        }
        return p;
    }

    public static TargetProfile profileOfTriple(String triple) {
        return profile(TargetId.fromCompileTriple(triple));
    }

    public static Set<TargetCapability> packageFlags(TargetId id) {
        return profile(id).packageFlags();
    }

    public static Set<TargetCapability> packageFlagsForTriple(String triple) {
        return packageFlags(TargetId.fromCompileTriple(triple));
    }

    /** Aligns with {@link CompileTarget#capabilities()} for compile triples. */
    public static Set<TargetCapability> compileTargetFlags(CompileTarget target) {
        if (target == null) {
            return packageFlags(TargetId.NATIVE_HOST);
        }
        return packageFlags(target.isBrowser() ? TargetId.WASM32_BROWSER : TargetId.NATIVE_HOST);
    }

    private static Map<TargetId, TargetProfile> build() {
        EnumMap<TargetId, TargetProfile> m = new EnumMap<>(TargetId.class);
        m.put(TargetId.NATIVE_HOST, nativeHost());
        m.put(TargetId.WASM32_BROWSER, wasmBrowser());
        m.put(TargetId.MOBILE_CONTAINER, mobileContainer());
        return Map.copyOf(m);
    }

    private static TargetProfile nativeHost() {
        EnumMap<Capability, CapabilityEvidence> e = baseLanguage(CapabilityEvidence.IMPLEMENTED);
        // Runtime / concurrency
        e.put(Capability.GC, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.THREADS, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.ASYNC_AWAIT, CapabilityEvidence.IMPLEMENTED);
        // I/O
        e.put(Capability.FILESYSTEM, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.NETWORK, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.DATABASE, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.PROCESS, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.TLS_NATIVE, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.HTTP2_NATIVE, CapabilityEvidence.IMPLEMENTED);
        // UI / FFI — present in stdlib; platform verification varies
        e.put(Capability.UI_DESKTOP, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.UI_BROWSER, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.FFI_NATIVE, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.JS_INTEROP, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.BROWSER_FETCH, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.JAVA_BRIDGE, CapabilityEvidence.UNVERIFIED);
        // Host-specific verification is tracked in CAPABILITY_MATRIX (macOS arm64 VERIFIED for smoke).
        markVerifiedSmoke(e);
        Set<TargetCapability> flags = EnumSet.of(
                TargetCapability.NATIVE_FFI,
                TargetCapability.FILESYSTEM,
                TargetCapability.RAW_SOCKET,
                TargetCapability.PROCESS,
                TargetCapability.THREADS,
                TargetCapability.DATABASE,
                TargetCapability.DESKTOP_UI,
                TargetCapability.TLS_NATIVE,
                TargetCapability.HTTP2_NATIVE);
        return new TargetProfile(
                TargetId.NATIVE_HOST,
                "Host native binary via clang/LLVM. Platforms: current host OS/arch "
                        + "(darwin/linux/windows). Not a mobile runtime.",
                e,
                flags);
    }

    private static TargetProfile wasmBrowser() {
        EnumMap<Capability, CapabilityEvidence> e = baseLanguage(CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.GC, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.THREADS, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.ASYNC_AWAIT, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.FILESYSTEM, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.NETWORK, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.DATABASE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.PROCESS, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.TLS_NATIVE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.HTTP2_NATIVE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.UI_DESKTOP, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.UI_BROWSER, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.FFI_NATIVE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.JS_INTEROP, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.BROWSER_FETCH, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.JAVA_BRIDGE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.NUMERICS, CapabilityEvidence.VERIFIED);
        e.put(Capability.OBJECTS, CapabilityEvidence.VERIFIED);
        e.put(Capability.UI_BROWSER, CapabilityEvidence.VERIFIED);
        e.put(Capability.JS_INTEROP, CapabilityEvidence.VERIFIED);
        Set<TargetCapability> flags = EnumSet.of(
                TargetCapability.BROWSER_DOM,
                TargetCapability.JS_INTEROP,
                TargetCapability.BROWSER_FETCH);
        return new TargetProfile(
                TargetId.WASM32_BROWSER,
                "Browser WebAssembly (wasm32) + JS glue. Single-threaded; no host filesystem, "
                        + "raw sockets, native FFI, or desktop UI.",
                e,
                flags);
    }

    private static TargetProfile mobileContainer() {
        // Same compile surface as wasm32-browser; packaging into WebView shells only.
        EnumMap<Capability, CapabilityEvidence> e = new EnumMap<>(wasmBrowser().evidence());
        e.put(Capability.UI_BROWSER, CapabilityEvidence.IMPLEMENTED);
        e.put(Capability.FFI_NATIVE, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.UI_DESKTOP, CapabilityEvidence.UNSUPPORTED);
        e.put(Capability.THREADS, CapabilityEvidence.UNSUPPORTED);
        // Device interaction / install paths remain unverified in matrix docs.
        e.put(Capability.UI_BROWSER, CapabilityEvidence.UNVERIFIED);
        return new TargetProfile(
                TargetId.MOBILE_CONTAINER,
                "Mobile WebView container that embeds a wasm32-browser artifact. "
                        + "Compile target is wasm32-browser — not a full native iOS/Android runtime "
                        + "(no native widgets, no host THREADS/FFI as mobile-native).",
                e,
                EnumSet.copyOf(wasmBrowser().packageFlags()));
    }

    private static EnumMap<Capability, CapabilityEvidence> baseLanguage(CapabilityEvidence status) {
        EnumMap<Capability, CapabilityEvidence> e = new EnumMap<>(Capability.class);
        e.put(Capability.NUMERICS, status);
        e.put(Capability.OBJECTS, status);
        e.put(Capability.ARRAYS, status);
        e.put(Capability.DYNAMIC_DISPATCH, status);
        e.put(Capability.CLOSURES, status);
        e.put(Capability.EXCEPTIONS, status);
        e.put(Capability.RESOURCE_MANAGEMENT, status);
        return e;
    }

    private static void markVerifiedSmoke(EnumMap<Capability, CapabilityEvidence> e) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean macArm = os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"));
        if (macArm) {
            e.put(Capability.NUMERICS, CapabilityEvidence.VERIFIED);
            e.put(Capability.OBJECTS, CapabilityEvidence.VERIFIED);
            e.put(Capability.ARRAYS, CapabilityEvidence.VERIFIED);
            e.put(Capability.FILESYSTEM, CapabilityEvidence.VERIFIED);
        }
    }
}
