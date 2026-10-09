package io.jnative.backend;

import java.util.EnumSet;
import java.util.Set;

/** Compile-time capability matrix for native vs browser targets.
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
        return EnumSet.of(NATIVE_FFI, FILESYSTEM, RAW_SOCKET, PROCESS, THREADS, DATABASE, DESKTOP_UI,
                TLS_NATIVE, HTTP2_NATIVE);
    }

    public static Set<TargetCapability> forWasmBrowser() {
        return EnumSet.of(BROWSER_DOM, JS_INTEROP, BROWSER_FETCH);
    }
}
