package io.jnative.layout;

/**
 * Mutable pointer-width for target-specific layouts.
 * Native host defaults to 8; wasm32-browser uses 4.
 * @author 张晓达
 */
public final class LayoutConfig {
    private static volatile int pointerSize = 8;
    private static volatile int pointerAlign = 8;
    private static volatile long headerSize = 8;

    private LayoutConfig() {
    }

    public static void configureNative64() {
        pointerSize = 8;
        pointerAlign = 8;
        headerSize = 8;
    }

    public static void configureWasm32() {
        pointerSize = 4;
        pointerAlign = 4;
        headerSize = 4;
    }

    public static int pointerSize() {
        return pointerSize;
    }

    public static int pointerAlign() {
        return pointerAlign;
    }

    public static long headerSize() {
        return headerSize;
    }
}
