#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * JNative 0.13 GC2 — generational heap with TLAB + young semi-space copying
 * and malloc-backed old/large mark-sweep.
 *
 * When JNATIVE_GC=marksweep (or unset legacy), LLVM IR mark-sweep remains.
 * When JNATIVE_GC=generational (default in 0.13), these entry points are used.
 */

#define JN_GC_HEADER_SIZE 32

void jn_gc2_init(void);
int jn_gc2_enabled(void);

/** Zeroed managed allocation (payload size, not including header). */
void *jn_gc2_alloc(uint64_t payload);

/** System.gc() / full collection. */
void jn_gc2_collect(void);

/** Minor collection only (may promote). */
void jn_gc2_minor(void);

/**
 * Write barrier after storing a managed reference into an object field/element.
 * owner = object containing the field; value = stored reference (may be null).
 */
void jn_gc2_write_barrier(void *owner, void *value);

/** Address classification for tests/verifier. */
typedef enum {
    JN_ADDR_INVALID = 0,
    JN_ADDR_YOUNG = 1,
    JN_ADDR_OLD = 2,
    JN_ADDR_LARGE = 3,
    JN_ADDR_STACK_OR_OTHER = 4
} JNAddrClass;

JNAddrClass jn_gc2_classify(void *obj);

/** Optional: LLVM module provides static root slot visitation. */
typedef void (*jn_static_root_visitor)(void (*fn)(void **slot, void *ctx), void *ctx);
void jn_gc2_set_static_root_visitor(jn_static_root_visitor v);

/** Debug: raw payload address (test-only). */
int64_t jn_gc2_debug_address(void *obj);

/** Stable identity hash (survives moving GC). Lazy-assigned. */
int32_t jn_gc2_identity_hash(void *obj);

#ifdef __cplusplus
}
#endif
