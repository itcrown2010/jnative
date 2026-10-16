/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/*
 * JNative 0.13 GC2 — TLAB + young semi-space (Cheney) + old mark-sweep.
 */

#include "jnative_gc2.h"
#include "jnative_mt.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* ---- Header layout (32 bytes, matches LLVM %jn.GcHeader) ---- */
typedef struct {
    void *link;      /* old list next OR forwarding ptr when FORWARDED */
    uint64_t size;   /* total bytes including header */
    uint32_t mark;
    uint32_t meta;   /* age(8) | gen(8) | flags(16) */
    uint32_t identity_hash; /* stable across moves; 0 = unset */
    uint32_t pad;
} GcHeader;

#define GEN_YOUNG 0
#define GEN_OLD 1
#define GEN_LARGE 2

#define FLAG_FORWARDED (1u << 16)
#define FLAG_DIRTY (1u << 17)
#define FLAG_PINNED (1u << 18)
#define FLAG_FILLER (1u << 19)

#define META_AGE(m) ((m) & 0xFFu)
#define META_GEN(m) (((m) >> 8) & 0xFFu)
#define META_SET_AGE(m, a) (((m) & ~0xFFu) | ((uint32_t)(a) & 0xFFu))
#define META_SET_GEN(m, g) (((m) & ~0xFF00u) | (((uint32_t)(g) & 0xFFu) << 8))

/* ClassMetadata layout matches LLVM %jn.ClassMeta */
typedef struct {
    void *vtable;
    void *super_meta;
    int64_t size;
    int32_t flags;
    void *name;
    int32_t ref_count;
    int64_t *ref_offsets;
} ClassMeta;

#define META_FLAG_ARRAY 1
#define META_FLAG_REF_ELEMS 2

static int g_enabled = 0;
static int g_inited = 0;
static int g_verify = 0;
static int g_log = 0;
static int g_stats = 0;
static int g_stress = 0; /* 0=off, 1=minor, 2=major, 3=all */

static size_t g_young_size = 4 * 1024 * 1024; /* per semi-space */
static size_t g_tlab_size = 32 * 1024;
static size_t g_large_threshold = 32 * 1024;
static uint32_t g_promotion_age = 2;

static uint8_t *g_space_a = NULL;
static uint8_t *g_space_b = NULL;
static uint8_t *g_from = NULL;
static uint8_t *g_to = NULL;
static uint8_t *g_chunk_free = NULL; /* next free byte for TLAB chunks */
static uint8_t *g_eden_high = NULL;  /* exclusive end of initialized objects */

static uint8_t *g_to_alloc = NULL; /* Cheney alloc in to-space */
static uint8_t *g_to_scan = NULL;

static GcHeader *g_old_head = NULL;
static GcHeader *g_large_head = NULL;

#define REMSET_CAP 65536
static void **g_remset = NULL;
static size_t g_remset_len = 0;

static jn_static_root_visitor g_static_visitor = NULL;

/* stats */
static uint64_t st_young_allocs = 0;
static uint64_t st_tlab_allocs = 0;
static uint64_t st_tlab_refills = 0;
static uint64_t st_tlab_waste = 0;
static uint64_t st_minor = 0;
static uint64_t st_major = 0;
static uint64_t st_bytes_copied = 0;
static uint64_t st_bytes_promoted = 0;
static uint64_t st_young_reclaimed = 0;
static uint64_t st_old_freed = 0;
static uint64_t st_minor_ns = 0;
static uint64_t st_major_ns = 0;
static uint64_t st_minor_max_ns = 0;
static uint64_t st_allocated_objects = 0;
static uint64_t st_freed_objects = 0;

static uint64_t now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static GcHeader *hdr_of(void *obj) {
    return (GcHeader *)((uint8_t *)obj - JN_GC_HEADER_SIZE);
}

static void *obj_of(GcHeader *h) {
    return (uint8_t *)h + JN_GC_HEADER_SIZE;
}

static int in_range(void *p, uint8_t *base, size_t sz) {
    uint8_t *u = (uint8_t *)p;
    return u >= base && u < base + sz;
}

static int is_young_obj(void *obj) {
    if (obj == NULL || g_from == NULL) {
        return 0;
    }
    GcHeader *h = hdr_of(obj);
    return in_range(h, g_from, g_young_size) || in_range(h, g_to, g_young_size);
}

static int is_in_from(void *obj) {
    if (obj == NULL || g_from == NULL) {
        return 0;
    }
    return in_range(hdr_of(obj), g_from, g_young_size);
}

static size_t align8(size_t n) {
    return (n + 7u) & ~(size_t)7u;
}

static size_t env_size(const char *name, size_t def) {
    const char *v = getenv(name);
    if (!v || !*v) {
        return def;
    }
    char *end = NULL;
    unsigned long long n = strtoull(v, &end, 10);
    if (end == v || n == 0) {
        return def;
    }
    return (size_t)n;
}

static void parse_stress(void) {
    const char *v = getenv("JNATIVE_GC_STRESS");
    if (!v || !*v) {
        g_stress = 0;
        return;
    }
    if (strcmp(v, "minor") == 0) {
        g_stress = 1; /* every allocation — use only on small tests */
    } else if (strcmp(v, "major") == 0) {
        g_stress = 2;
    } else if (strcmp(v, "all") == 0) {
        g_stress = 3;
    } else if (strcmp(v, "1") == 0) {
        /* Legacy flag: soft pressure (frequent minors), not per-alloc. */
        g_stress = 5;
        if (g_young_size > 256u * 1024u) {
            g_young_size = 256u * 1024u;
        }
    } else {
        g_stress = 5;
    }
}

void jn_gc2_set_static_root_visitor(jn_static_root_visitor v) {
    g_static_visitor = v;
}

int jn_gc2_enabled(void) {
    return g_enabled;
}

JNAddrClass jn_gc2_classify(void *obj) {
    if (obj == NULL) {
        return JN_ADDR_INVALID;
    }
    GcHeader *h = hdr_of(obj);
    if (g_from && (in_range(h, g_from, g_young_size) || in_range(h, g_to, g_young_size))) {
        return JN_ADDR_YOUNG;
    }
    uint32_t gen = META_GEN(h->meta);
    if (gen == GEN_LARGE) {
        return JN_ADDR_LARGE;
    }
    if (gen == GEN_OLD) {
        return JN_ADDR_OLD;
    }
    return JN_ADDR_STACK_OR_OTHER;
}

int64_t jn_gc2_debug_address(void *obj) {
    return (int64_t)(intptr_t)obj;
}

static void remset_add(void *owner) {
    if (owner == NULL) {
        return;
    }
    GcHeader *h = hdr_of(owner);
    if (h->meta & FLAG_DIRTY) {
        return;
    }
    if (g_remset_len >= REMSET_CAP) {
        return; /* conservative: major GC will catch */
    }
    h->meta |= FLAG_DIRTY;
    g_remset[g_remset_len++] = owner;
}

void jn_gc2_write_barrier(void *owner, void *value) {
    jn_gc2_init();
    if (!g_enabled || owner == NULL || value == NULL) {
        return;
    }
    if (!is_young_obj(value)) {
        return;
    }
    JNAddrClass c = jn_gc2_classify(owner);
    if (c == JN_ADDR_OLD || c == JN_ADDR_LARGE) {
        remset_add(owner);
    }
}

static void dump_stats(void) {
    if (!g_stats) {
        return;
    }
    /* Keep "JNative GC:" prefix for existing test parsers. */
    fprintf(stderr, "JNative GC:\n");
    fprintf(stderr, "collections: %" PRIu64 "\n", st_minor + st_major);
    fprintf(stderr, "allocated: %" PRIu64 "\n", st_allocated_objects);
    fprintf(stderr, "freed: %" PRIu64 "\n", st_freed_objects);
    fprintf(stderr, "live: %" PRIu64 "\n",
            st_allocated_objects > st_freed_objects ? st_allocated_objects - st_freed_objects : 0);
    fprintf(stderr, "peak: %" PRIu64 "\n", (uint64_t)g_young_size * 2);
    fprintf(stderr, "minor: %" PRIu64 "\n", st_minor);
    fprintf(stderr, "major: %" PRIu64 "\n", st_major);
    fprintf(stderr, "tlab_allocs: %" PRIu64 "\n", st_tlab_allocs);
    fprintf(stderr, "tlab_refills: %" PRIu64 "\n", st_tlab_refills);
    fprintf(stderr, "bytes_copied: %" PRIu64 "\n", st_bytes_copied);
    fprintf(stderr, "bytes_promoted: %" PRIu64 "\n", st_bytes_promoted);
}

void jn_gc2_init(void) {
    if (g_inited) {
        return;
    }
    g_inited = 1;

    const char *mode = getenv("JNATIVE_GC");
    if (mode == NULL || *mode == '\0' || strcmp(mode, "generational") == 0) {
        g_enabled = 1;
    } else if (strcmp(mode, "marksweep") == 0) {
        g_enabled = 0;
        return;
    } else {
        g_enabled = 1; /* default GC2 */
    }

    g_young_size = env_size("JNATIVE_YOUNG_SIZE", 4u * 1024u * 1024u);
    if (g_young_size < 64 * 1024) {
        g_young_size = 64 * 1024;
    }
    g_tlab_size = env_size("JNATIVE_TLAB_SIZE", 32u * 1024u);
    g_large_threshold = env_size("JNATIVE_LARGE_THRESHOLD", 32u * 1024u);
    g_promotion_age = (uint32_t)env_size("JNATIVE_PROMOTION_AGE", 2);
    g_verify = getenv("JNATIVE_GC_VERIFY") != NULL;
    g_log = getenv("JNATIVE_GC_LOG") != NULL;
    g_stats = getenv("JNATIVE_GC_STATS") != NULL;
    parse_stress();

    g_space_a = (uint8_t *)malloc(g_young_size);
    g_space_b = (uint8_t *)malloc(g_young_size);
    if (!g_space_a || !g_space_b) {
        fprintf(stderr, "JNative GC2: failed to allocate nursery\n");
        abort();
    }
    memset(g_space_a, 0, g_young_size);
    memset(g_space_b, 0, g_young_size);
    g_from = g_space_a;
    g_to = g_space_b;
    g_chunk_free = g_from;
    g_eden_high = g_from;

    g_remset = (void **)calloc(REMSET_CAP, sizeof(void *));
    if (g_stats) {
        atexit(dump_stats);
    }
}

static void init_header(GcHeader *h, uint64_t total, uint32_t gen) {
    h->link = NULL;
    h->size = total;
    h->mark = 0;
    h->meta = META_SET_GEN(0, gen);
    h->identity_hash = 0;
    h->pad = 0;
}

static uint32_t g_next_identity = 1;

int32_t jn_gc2_identity_hash(void *obj) {
    if (obj == NULL) {
        return 0;
    }
    GcHeader *h = hdr_of(obj);
    if (h->identity_hash != 0) {
        return (int32_t)h->identity_hash;
    }
    uint32_t v = g_next_identity++;
    if (v == 0) {
        v = g_next_identity++;
    }
    h->identity_hash = v;
    return (int32_t)v;
}

/** Fill unused TLAB tail so eden remains a contiguous object walk. */
static void fill_tlab_waste_range(uint8_t *top, uint8_t *end) {
    if (!top || !end || top >= end) {
        return;
    }
    size_t waste = (size_t)(end - top);
    st_tlab_waste += waste;
    if (waste < JN_GC_HEADER_SIZE) {
        /*
         * Cannot install a filler header. Leave the bytes beyond the last
         * real object unused — do NOT advance g_eden_high into uninitialized
         * memory (that caused false "corrupt young header" under verify).
         */
        return;
    }
    GcHeader *fill = (GcHeader *)top;
    memset(fill, 0, waste);
    fill->size = (uint64_t)waste;
    fill->meta = META_SET_GEN(FLAG_FILLER, GEN_YOUNG);
    if (end > g_eden_high) {
        g_eden_high = end;
    }
}

static void fill_tlab_waste_cb(uint8_t *top, uint8_t *end, void *ctx) {
    (void)ctx;
    fill_tlab_waste_range(top, end);
}

static void fill_tlab_waste(JNTlab cur) {
    fill_tlab_waste_range(cur.top, cur.end);
}

static int refill_tlab(size_t need) {
    size_t chunk = g_tlab_size;
    if (chunk < need + JN_GC_HEADER_SIZE + 64) {
        chunk = align8(need + JN_GC_HEADER_SIZE + 64);
    }
    JNTlab cur = jn_tlab_get();
    fill_tlab_waste(cur);

    jn_heap_lock();
    size_t used = (size_t)(g_chunk_free - g_from);
    if (used + chunk > g_young_size) {
        jn_heap_unlock();
        return 0;
    }
    uint8_t *start = g_chunk_free;
    g_chunk_free += chunk;
    jn_heap_unlock();

    jn_tlab_set(start, start + chunk, start);
    st_tlab_refills++;
    return 1;
}

static void *alloc_old_or_large(uint64_t payload, uint32_t gen) {
    uint64_t total = (uint64_t)JN_GC_HEADER_SIZE + payload;
    total = align8((size_t)total);
    GcHeader *h = (GcHeader *)malloc((size_t)total);
    if (!h) {
        return NULL;
    }
    memset(h, 0, (size_t)total);
    init_header(h, total, gen);
    jn_heap_lock();
    if (gen == GEN_LARGE) {
        h->link = g_large_head;
        g_large_head = h;
    } else {
        h->link = g_old_head;
        g_old_head = h;
    }
    jn_heap_unlock();
    st_allocated_objects++;
    return obj_of(h);
}

static void scan_object_refs(void *obj);

static void *forward_ref(void *obj) {
    if (obj == NULL) {
        return NULL;
    }
    if (!is_in_from(obj)) {
        /* old/large/to-space/stack — unchanged */
        return obj;
    }
    GcHeader *h = hdr_of(obj);
    if (h->meta & FLAG_FILLER) {
        return NULL;
    }
    if (h->meta & FLAG_FORWARDED) {
        return h->link;
    }

    uint64_t total = h->size;
    uint32_t age = META_AGE(h->meta) + 1;
    uint32_t promote = age >= g_promotion_age;

    void *neu;
    int need_scan_promote = 0;
    if (promote) {
        neu = alloc_old_or_large(total - JN_GC_HEADER_SIZE, GEN_OLD);
        if (!neu) {
            /* promotion failure — keep in to-space */
            promote = 0;
        } else {
            uint32_t idhash = h->identity_hash;
            memcpy(neu, obj, (size_t)(total - JN_GC_HEADER_SIZE));
            GcHeader *nh = hdr_of(neu);
            nh->meta = META_SET_AGE(META_SET_GEN(0, GEN_OLD), age);
            nh->identity_hash = idhash;
            nh->pad = 0;
            st_bytes_promoted += total;
            need_scan_promote = 1;
            remset_add(neu);
        }
    }
    if (!promote) {
        size_t need = (size_t)total;
        if (g_to_alloc + need > g_to + g_young_size) {
            fprintf(stderr, "JNative GC2: to-space overflow\n");
            abort();
        }
        GcHeader *nh = (GcHeader *)g_to_alloc;
        g_to_alloc += need;
        memcpy(nh, h, (size_t)total);
        nh->meta = META_SET_AGE(META_SET_GEN(nh->meta & ~(FLAG_FORWARDED | FLAG_DIRTY), GEN_YOUNG), age);
        nh->link = NULL;
        nh->mark = 0;
        neu = obj_of(nh);
        st_bytes_copied += total;
    }

    /* Install forwarding BEFORE scanning fields so object cycles cannot recurse forever. */
    h->meta |= FLAG_FORWARDED;
    h->link = neu;

    if (need_scan_promote) {
        /* Fields may still point into from-space — forward them now (not next GC). */
        scan_object_refs(neu);
    }
    return neu;
}

static void update_slot(void **slot, void *ctx) {
    (void)ctx;
    if (slot == NULL) {
        return;
    }
    *slot = forward_ref(*slot);
}

static void scan_object_refs(void *obj) {
    if (obj == NULL) {
        return;
    }
    GcHeader *oh = hdr_of(obj);
    if (oh->meta & FLAG_FILLER) {
        return;
    }
    ClassMeta *meta = *(ClassMeta **)obj;
    if (meta == NULL) {
        return;
    }
    if (meta->flags & META_FLAG_ARRAY) {
        if (!(meta->flags & META_FLAG_REF_ELEMS)) {
            return;
        }
        int32_t len = *(int32_t *)((uint8_t *)obj + 8);
        void **elems = (void **)((uint8_t *)obj + 16);
        for (int32_t i = 0; i < len; i++) {
            elems[i] = forward_ref(elems[i]);
        }
        return;
    }
    if (meta->ref_count <= 0 || meta->ref_offsets == NULL) {
        return;
    }
    for (int32_t i = 0; i < meta->ref_count; i++) {
        int64_t off = meta->ref_offsets[i];
        void **slot = (void **)((uint8_t *)obj + off);
        *slot = forward_ref(*slot);
    }
}

static void visit_all_root_slots(void) {
    jn_foreach_root_slot(update_slot, NULL);
    jn_foreach_ex_root_slot(update_slot, NULL);
    jn_foreach_thread_obj_root_slot(update_slot, NULL);
    if (g_static_visitor) {
        g_static_visitor(update_slot, NULL);
    }
}

static void scan_remset(void) {
    size_t n = g_remset_len;
    for (size_t i = 0; i < n; i++) {
        void *owner = g_remset[i];
        if (owner == NULL) {
            continue;
        }
        GcHeader *h = hdr_of(owner);
        h->meta &= ~FLAG_DIRTY;
        scan_object_refs(owner);
    }
    g_remset_len = 0;
}

static uint8_t *g_abandoned = NULL;

static void verify_no_abandoned_pointers(void **slot, void *ctx) {
    (void)ctx;
    if (!slot || !*slot || g_abandoned == NULL) {
        return;
    }
    GcHeader *h = hdr_of(*slot);
    if (in_range(h, g_abandoned, g_young_size)) {
        fprintf(stderr, "JNative GC VERIFY: stale abandoned-space pointer %p\n", *slot);
        abort();
    }
}

static void minor_gc_locked(void) {
    uint64_t t0 = now_ns();

    /* Fill every thread's TLAB waste so eden stays contiguous, then retire. */
    jn_foreach_tlab(fill_tlab_waste_cb, NULL);
    {
        uint8_t *mtop = jn_tlab_max_top();
        if (mtop != NULL && mtop > g_eden_high) {
            g_eden_high = mtop;
        }
    }

    size_t used_before = (size_t)(g_eden_high - g_from);
    uint64_t objs_before = 0;
    for (uint8_t *q = g_from; q < g_eden_high; ) {
        GcHeader *hh = (GcHeader *)q;
        if (hh->size < JN_GC_HEADER_SIZE) {
            break;
        }
        if (!(hh->meta & FLAG_FILLER)) {
            objs_before++;
        }
        q += (size_t)hh->size;
    }

    jn_tlab_retire_all();

    g_to_alloc = g_to;
    g_to_scan = g_to;

    visit_all_root_slots();
    scan_remset();

    /* Cheney scan to-space */
    while (g_to_scan < g_to_alloc) {
        GcHeader *h = (GcHeader *)g_to_scan;
        void *obj = obj_of(h);
        scan_object_refs(obj);
        g_to_scan += (size_t)h->size;
    }

    /*
     * Conservative old→young rescan: any old/large object may point into to-space
     * after promotion or a missed barrier. Cheap enough for 0.13 correctness.
     */
    for (GcHeader *oh = g_old_head; oh != NULL; oh = (GcHeader *)oh->link) {
        scan_object_refs(obj_of(oh));
    }
    for (GcHeader *oh = g_large_head; oh != NULL; oh = (GcHeader *)oh->link) {
        scan_object_refs(obj_of(oh));
    }
    /* Rescan to-space for objects newly copied via old→young edges. */
    while (g_to_scan < g_to_alloc) {
        GcHeader *h = (GcHeader *)g_to_scan;
        scan_object_refs(obj_of(h));
        g_to_scan += (size_t)h->size;
    }

    size_t survived = (size_t)(g_to_alloc - g_to);
    uint64_t objs_after = 0;
    for (uint8_t *q = g_to; q < g_to_alloc; ) {
        GcHeader *hh = (GcHeader *)q;
        objs_after++;
        q += (size_t)hh->size;
    }
    st_young_reclaimed += (used_before > survived) ? (used_before - survived) : 0;
    if (objs_before > objs_after) {
        st_freed_objects += objs_before - objs_after;
    }

    /* swap — abandoned space is the old from-space */
    g_abandoned = g_from;
    uint8_t *tmp = g_from;
    g_from = g_to;
    g_to = tmp;
    g_eden_high = g_from + survived;
    g_chunk_free = g_eden_high;

    if (g_verify) {
        jn_foreach_root_slot(verify_no_abandoned_pointers, NULL);
        jn_foreach_ex_root_slot(verify_no_abandoned_pointers, NULL);
        jn_foreach_thread_obj_root_slot(verify_no_abandoned_pointers, NULL);
        if (g_static_visitor) {
            g_static_visitor(verify_no_abandoned_pointers, NULL);
        }
    }

    memset(g_to, 0, g_young_size);
    g_abandoned = NULL;

    st_minor++;
    uint64_t dt = now_ns() - t0;
    st_minor_ns += dt;
    if (dt > st_minor_max_ns) {
        st_minor_max_ns = dt;
    }

    if (g_log) {
        fprintf(stderr, "[GC Minor] young used %zu -> %zu promoted=%" PRIu64 " pause=%.3fms\n",
                used_before, survived, st_bytes_promoted, dt / 1e6);
    }
}

/* Mark-sweep for old/large */
static void mark_push_old(void *obj, void ***stack, size_t *sz, size_t *cap);

static void mark_scan_old(void *obj, void ***stack, size_t *sz, size_t *cap) {
    if (obj == NULL) {
        return;
    }
    ClassMeta *meta = *(ClassMeta **)obj;
    if (!meta) {
        return;
    }
    if (meta->flags & META_FLAG_ARRAY) {
        if (meta->flags & META_FLAG_REF_ELEMS) {
            int32_t len = *(int32_t *)((uint8_t *)obj + 8);
            void **elems = (void **)((uint8_t *)obj + 16);
            for (int32_t i = 0; i < len; i++) {
                mark_push_old(elems[i], stack, sz, cap);
            }
        }
        return;
    }
    for (int32_t i = 0; i < meta->ref_count; i++) {
        void **slot = (void **)((uint8_t *)obj + meta->ref_offsets[i]);
        mark_push_old(*slot, stack, sz, cap);
    }
}

static void mark_push_old(void *obj, void ***stack, size_t *sz, size_t *cap) {
    if (obj == NULL) {
        return;
    }
    /* Young should have been evacuated; if still young, skip mark list */
    if (is_young_obj(obj) && is_in_from(obj)) {
        return;
    }
    GcHeader *h = hdr_of(obj);
    uint32_t gen = META_GEN(h->meta);
    if (gen != GEN_OLD && gen != GEN_LARGE) {
        return;
    }
    if (h->mark) {
        return;
    }
    h->mark = 1;
    if (*sz >= *cap) {
        *cap = *cap ? *cap * 2 : 64;
        *stack = (void **)realloc(*stack, *cap * sizeof(void *));
    }
    (*stack)[(*sz)++] = obj;
}

typedef struct {
    void **stack;
    size_t sz;
    size_t cap;
} MarkState;

static void mark_slot_cb(void **slot, void *ctx) {
    MarkState *ms = (MarkState *)ctx;
    if (slot && *slot) {
        mark_push_old(*slot, &ms->stack, &ms->sz, &ms->cap);
    }
}

static void sweep_list(GcHeader **head) {
    GcHeader *cur = *head;
    GcHeader *prev = NULL;
    while (cur) {
        GcHeader *next = (GcHeader *)cur->link;
        if (!cur->mark) {
            if (prev) {
                prev->link = next;
            } else {
                *head = next;
            }
            st_old_freed++;
            st_freed_objects++;
            free(cur);
        } else {
            cur->mark = 0;
            prev = cur;
        }
        cur = next;
    }
}

static void major_gc_body(void) {
    uint64_t t0 = now_ns();
    /* Evacuate young first */
    minor_gc_locked();

    MarkState ms = {0};
    jn_foreach_root_slot(mark_slot_cb, &ms);
    jn_foreach_ex_root_slot(mark_slot_cb, &ms);
    jn_foreach_thread_obj_root_slot(mark_slot_cb, &ms);
    if (g_static_visitor) {
        g_static_visitor(mark_slot_cb, &ms);
    }
    /* also mark remset owners and young survivors pointing to old — scan all young */
    uint8_t *p = g_from;
    while (p < g_eden_high) {
        GcHeader *h = (GcHeader *)p;
        if (h->size < JN_GC_HEADER_SIZE) {
            break;
        }
        if (!(h->meta & FLAG_FILLER)) {
            mark_scan_old(obj_of(h), &ms.stack, &ms.sz, &ms.cap);
        }
        p += (size_t)h->size;
    }

    while (ms.sz > 0) {
        void *obj = ms.stack[--ms.sz];
        mark_scan_old(obj, &ms.stack, &ms.sz, &ms.cap);
    }
    free(ms.stack);

    jn_heap_lock();
    sweep_list(&g_old_head);
    sweep_list(&g_large_head);
    jn_heap_unlock();

    st_major++;
    uint64_t dt = now_ns() - t0;
    st_major_ns += dt;
    if (g_log) {
        fprintf(stderr, "[GC Major] pause=%.3fms old_freed=%" PRIu64 "\n", dt / 1e6, st_old_freed);
    }
}

void jn_gc2_minor(void) {
    jn_gc2_init();
    if (!g_enabled) {
        return;
    }
    jn_gc_collect_lock();
    jn_gc_stw_begin();
    minor_gc_locked();
    jn_gc_stw_end();
    jn_gc_collect_unlock();
}

void jn_gc2_collect(void) {
    jn_gc2_init();
    if (!g_enabled) {
        return;
    }
    jn_gc_collect_lock();
    jn_gc_stw_begin();
    major_gc_body();
    jn_gc_stw_end();
    jn_gc_collect_unlock();
}

/* >0 while a C native allocates a result while still holding raw pointers to managed
 * inputs: a moving collection there would leave those pointers stale, so allocation must
 * not collect (the heap grows / spills to the old generation; the next managed allocation
 * outside the native collects). */
static __thread int t_native_alloc_depth = 0;

void jn_gc2_native_enter(void) {
    t_native_alloc_depth++;
}

void jn_gc2_native_leave(void) {
    if (t_native_alloc_depth > 0) {
        t_native_alloc_depth--;
    }
}

static void *alloc_young(uint64_t payload) {
    size_t total = align8((size_t)JN_GC_HEADER_SIZE + (size_t)payload);
    for (int attempt = 0; attempt < 3; attempt++) {
        JNTlab t = jn_tlab_get();
        if (t.top && t.end && t.top + total <= t.end) {
            GcHeader *h = (GcHeader *)t.top;
            t.top += total;
            jn_tlab_set(t.top, t.end, t.start);
            if (t.top > g_eden_high) {
                g_eden_high = t.top;
            }
            memset(h, 0, total);
            init_header(h, total, GEN_YOUNG);
            st_tlab_allocs++;
            st_young_allocs++;
            st_allocated_objects++;
            return obj_of(h);
        }
        if (!refill_tlab(payload)) {
            if (t_native_alloc_depth > 0) {
                break; /* no GC inside natives: fall through to the old generation */
            }
            jn_gc2_minor();
            continue;
        }
    }
    /* fallback: promote path as large/old if nursery exhausted after GC */
    return alloc_old_or_large(payload, GEN_OLD);
}

static int verify_object_meta(void *obj) {
    if (obj == NULL) {
        return 1;
    }
    ClassMeta *meta = *(ClassMeta **)obj;
    if (meta == NULL) {
        fprintf(stderr, "JNative GC VERIFY: null ClassMeta at object %p\n", obj);
        return 0;
    }
    return 1;
}

int jn_gc2_verify_heap(void) {
    jn_gc2_init();
    if (!g_enabled) {
        return 1;
    }
    /*
     * TLAB bump regions leave uninitialized gaps until waste is filled.
     * Verify must fill waste first (same as minor GC) or it reports false
     * "corrupt young header" (Runtime Stability 0.8 regression).
     */
    jn_foreach_tlab(fill_tlab_waste_cb, NULL);
    {
        uint8_t *mtop = jn_tlab_max_top();
        if (mtop != NULL && mtop > g_eden_high) {
            g_eden_high = mtop;
        }
    }
    /* Young eden */
    for (uint8_t *q = g_from; q != NULL && q < g_eden_high; ) {
        GcHeader *hh = (GcHeader *)q;
        if (hh->size < JN_GC_HEADER_SIZE) {
            fprintf(stderr, "JNative GC VERIFY: corrupt young header size at %p\n", (void *)q);
            return 0;
        }
        if (!(hh->meta & FLAG_FILLER)) {
            if (!verify_object_meta(obj_of(hh))) {
                return 0;
            }
        }
        q += (size_t)hh->size;
    }
    for (GcHeader *oh = g_old_head; oh != NULL; oh = (GcHeader *)oh->link) {
        if (!verify_object_meta(obj_of(oh))) {
            return 0;
        }
    }
    for (GcHeader *oh = g_large_head; oh != NULL; oh = (GcHeader *)oh->link) {
        if (!verify_object_meta(obj_of(oh))) {
            return 0;
        }
    }
    return 1;
}

int jn_gc_user_verify_heap(void) {
    return jn_gc2_verify_heap();
}

void *jn_gc2_alloc(uint64_t payload) {
    jn_gc2_init();
    if (!g_enabled) {
        return NULL; /* caller uses legacy */
    }
    if (jn_nogc_depth() > 0) {
        fprintf(stderr, "JNative runtime violation: managed allocation inside @NoGC region\n");
        abort();
    }
    if (t_native_alloc_depth > 0) {
        /* Native holding raw managed pointers: never collect here. */
        if (payload >= g_large_threshold) {
            return alloc_old_or_large(payload, GEN_LARGE);
        }
        return alloc_young(payload);
    }
    jn_safepoint_poll();

    if (g_stress == 1 || g_stress == 3) {
        jn_gc2_minor();
    } else if (g_stress == 2) {
        jn_gc2_collect();
    } else if (g_stress == 5) {
        size_t used = (size_t)(g_chunk_free - g_from);
        if (used * 2 > g_young_size) {
            jn_gc2_minor();
        }
    }

    if (payload >= g_large_threshold) {
        void *p = alloc_old_or_large(payload, GEN_LARGE);
        if (!p) {
            jn_gc2_collect();
            p = alloc_old_or_large(payload, GEN_LARGE);
        }
        return p;
    }
    return alloc_young(payload);
}
