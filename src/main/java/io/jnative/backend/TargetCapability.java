package io.jnative.backend;

import java.util.EnumSet;
import java.util.Set;

/** Compile-time capability matrix for native vs browser targets. */
public enum TargetCapability {
    NATIVE_FFI,
    FILESYSTEM,
    RAW_SOCKET,
    PROCESS,
    THREADS,
    BROWSER_DOM,
    JS_INTEROP,
    DATABASE,
    DESKTOP_UI;

    public static Set<TargetCapability> forNative() {
        return EnumSet.of(NATIVE_FFI, FILESYSTEM, RAW_SOCKET, PROCESS, THREADS, DATABASE, DESKTOP_UI);
    }

    public static Set<TargetCapability> forWasmBrowser() {
        return EnumSet.of(BROWSER_DOM, JS_INTEROP);
    }
}
