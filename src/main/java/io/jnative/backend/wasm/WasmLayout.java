/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

/**
 * Unified wasm32-browser object / array / callable / GC layout.
 * Instance field offsets follow {@link io.jnative.layout.LayoutConfig#configureWasm32()}.
 * Array payload offset stays 16 for compatibility with IR element addressing.
 *
 * <p>Managed heap objects are {@code [GcHeader 16 | payload...]}. Java/WASM refs point at
 * <em>payload</em> (typeId at payload+0), never at the GcHeader.
 *
 * <p>Regions (low → high):
 * <ul>
 *   <li>0..255 — reserved / unused</li>
 *   <li>256..767 — DOM callback id→callable table (not GC)</li>
 *   <li>1024+ — string constant data (not GC)</li>
 *   <li>shadow stack bump — LIFO frames (not GC)</li>
 *   <li>GC heap bump — managed objects + free list</li>
 *   <li>host-root handle table — fixed slots in low memory</li>
 * </ul>
 * @author 张晓达
 */
public final class WasmLayout {
    private WasmLayout() {
    }

    /** GcHeader size preceding every managed payload. */
    public static final int GC_HEADER_SIZE = 16;

    /** GcHeader.next (i32) — intrusive heap list. */
    public static final int GC_HDR_NEXT = 0;
    /** GcHeader.size (i32) — payload bytes. */
    public static final int GC_HDR_SIZE = 4;
    /** GcHeader.flags (i32). */
    public static final int GC_HDR_FLAGS = 8;
    /** GcHeader.free_next (i32) — free-list link when FREE. */
    public static final int GC_HDR_FREE_NEXT = 12;

    public static final int GC_FLAG_MARK = 1;
    public static final int GC_FLAG_FREE = 2;
    public static final int GC_FLAG_ARRAY = 4;
    public static final int GC_FLAG_REF_ELEMS = 8;
    /** Opaque blob (e.g. length-prefixed UTF-8): payload is not typed; do not scan fields. */
    public static final int GC_FLAG_OPAQUE = 16;

    /** Object / array payload: i32 typeId. */
    public static final int TYPE_ID_OFFSET = 0;

    /** Array length (i32). */
    public static final int ARRAY_LENGTH_OFFSET = 8;

    /** Array element payload start. */
    public static final int ARRAY_DATA_OFFSET = 16;

    /** SAM callable: {@code long code} field (funcref table index as i64). */
    public static final int CALLABLE_CODE_OFFSET = 8;

    /** SAM callable: env / capture object reference. */
    public static final int CALLABLE_ENV_OFFSET = 16;

    /** DOM callback table base (id,ptr)×64. */
    public static final int DOM_CB_BASE = 256;

    /** Host managed-root handle table: [gen i32][ptr i32] × 128. */
    public static final int HOST_ROOT_BASE = 768;
    public static final int HOST_ROOT_SLOTS = 128;
    public static final int HOST_ROOT_STRIDE = 8;

    /** UTF-8 string constant data (emitter starts here; must stay below SHADOW_BASE). */
    public static final int DATA_MIN = 2048;
    public static final int DATA_MAX = 8192;

    /** Shadow-stack region (LIFO frames, not GC-scanned as objects). */
    public static final int SHADOW_BASE = 8192;
    public static final int SHADOW_CAPACITY = 64 * 1024;

    /** Mark-stack region (object ptrs), bounded. */
    public static final int MARK_STACK_BASE = SHADOW_BASE + SHADOW_CAPACITY;
    public static final int MARK_STACK_CAP = 4096; // entries → 16KiB

    /** GC heap starts after mark stack. */
    public static final int HEAP_BASE = MARK_STACK_BASE + MARK_STACK_CAP * 4;

    public static final int MEMORY_MAX_PAGES = 1024;
    public static final int MEMORY_INITIAL_PAGES = 256;

    /** Collect when allocated-since-last-GC exceeds this (bytes). */
    public static final int GC_THRESHOLD_DEFAULT = 256 * 1024;
}
