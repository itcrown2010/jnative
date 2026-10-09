package io.jnative.backend.wasm;

/**
 * Unified wasm32-browser object / array / callable layout.
 * Must stay consistent with {@link io.jnative.layout.LayoutConfig#configureWasm32()}
 * for instance fields; array header keeps native-compatible data offset 16.
 *
 * <p>Memory: bump allocator over linear memory; {@code memory.grow} on demand.
 * No GC in this round — heap only grows until memory.max; alloc failure traps.
 */
public final class WasmLayout {
    private WasmLayout() {
    }

    /** Object / array header: i32 typeId (not a host pointer). */
    public static final int TYPE_ID_OFFSET = 0;

    /** Array length (i32), same absolute offset as native for payload compatibility. */
    public static final int ARRAY_LENGTH_OFFSET = 8;

    /** Array element payload start. */
    public static final int ARRAY_DATA_OFFSET = 16;

    /** SAM callable: {@code long code} field (funcref table index as i64). */
    public static final int CALLABLE_CODE_OFFSET = 8;

    /** SAM callable: env / capture object reference. */
    public static final int CALLABLE_ENV_OFFSET = 16;

    /** Max linear-memory pages advertised in module (64KiB each). */
    public static final int MEMORY_MAX_PAGES = 1024;

    /** Initial pages. */
    public static final int MEMORY_INITIAL_PAGES = 256;
}
