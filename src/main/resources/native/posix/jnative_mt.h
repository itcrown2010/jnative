/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Execution states */
#define JN_EXEC_MANAGED 0
#define JN_EXEC_NATIVE 1
#define JN_EXEC_SAFEPOINT 2
#define JN_EXEC_TERMINATED 3

typedef struct JNThreadState JNThreadState;

void jn_mt_init(void);
void jn_thread_attach_main(void);
/** Returns 1 if newly attached, 0 if already attached. */
int jn_thread_attach_foreign(void);
/** Detaches only if this thread was foreign-attached. */
void jn_thread_detach_foreign(void);

JNThreadState *jn_thread_current(void);
int64_t jn_thread_id(void);
void *jn_thread_current_obj(void);
void jn_thread_set_current_obj(void *obj);

void *jn_tls_frame(void);
void jn_tls_set_frame(void *frame);

void *jn_tls_ex_current(void);
void jn_tls_set_ex_current(void *ex);
int jn_tls_ex_pending(void);
void jn_tls_set_ex_pending(int v);
int jn_tls_ex_return_pending(void);
void jn_tls_set_ex_return_pending(int v);
int64_t jn_tls_ex_return_value(void);
void jn_tls_set_ex_return_value(int64_t v);

void jn_safepoint_poll(void);
void jn_enter_native(void);
void jn_leave_native(void);

/** @NoGC region depth — managed allocation aborts when > 0. */
void jn_nogc_enter(void);
void jn_nogc_leave(void);
int jn_nogc_depth(void);

void jn_heap_lock(void);
void jn_heap_unlock(void);

/* STW: request GC, stop mutators, then caller runs mark/sweep, then end. */
void jn_gc_stw_begin(void);
void jn_gc_stw_end(void);

/* Serialize collectors; parks caller as SAFEPOINT while waiting. */
void jn_gc_collect_lock(void);
void jn_gc_collect_unlock(void);

/* Iterate all thread GC root slots (pointers to managed-ref slots). */
typedef void (*jn_root_slot_fn)(void **slot, void *ctx);
void jn_foreach_root_slot(jn_root_slot_fn fn, void *ctx);

/* Also visit exception current pointers as roots (pass as fake slot). */
typedef void (*jn_root_obj_fn)(void *obj, void *ctx);
void jn_foreach_ex_root(jn_root_obj_fn fn, void *ctx);
void jn_foreach_thread_obj_root(jn_root_obj_fn fn, void *ctx);

/* Moving-GC: updateable slots for exception / Thread object roots. */
void jn_foreach_ex_root_slot(jn_root_slot_fn fn, void *ctx);
void jn_foreach_thread_obj_root_slot(jn_root_slot_fn fn, void *ctx);

/* TLAB (0.13 GC2) — bump-pointer region owned by a managed thread. */
typedef struct {
    uint8_t *top;
    uint8_t *end;
    uint8_t *start;
} JNTlab;

JNTlab jn_tlab_get(void);
void jn_tlab_set(uint8_t *top, uint8_t *end, uint8_t *start);
void jn_tlab_clear(void);
/** Retire every registered thread's TLAB (call under STW). */
void jn_tlab_retire_all(void);
/** Highest TLAB top among all threads (for eden high-water). */
uint8_t *jn_tlab_max_top(void);
typedef void (*jn_tlab_visit_fn)(uint8_t *top, uint8_t *end, void *ctx);
void jn_foreach_tlab(jn_tlab_visit_fn fn, void *ctx);

/*
 * Start a managed thread. entry is called with thread_obj after TLS attach.
 * Returns runtime thread id, or -1 on failure.
 */
int64_t jn_thread_start(void *thread_obj, void (*entry)(void *));
void jn_thread_join_id(int64_t id);
void jn_thread_sleep_ms(int64_t ms);
void jn_thread_mark_terminated(void);

void *jn_mutex_create(void);
void jn_mutex_destroy(void *m);
void jn_mutex_lock(void *m);
void jn_mutex_unlock(void *m);
int jn_mutex_owner_check(void *m); /* 1 if current thread owns */

void *jn_cond_create(void);
void jn_cond_destroy(void *c);
void jn_cond_wait(void *c, void *m);
void jn_cond_signal(void *c);
void jn_cond_broadcast(void *c);

int64_t jn_os_thread_count(void); /* registered managed threads */

/** Online CPU / logical processor count (sysconf / equivalent). */
int32_t jnative_cpu_count(void);
int64_t jnative_web_mono_millis(void);
/** Wall-clock epoch milliseconds (UTC). Not monotonic. */
int64_t jnative_wall_millis(void);
/** Monotonic nanoseconds (CLOCK_MONOTONIC). */
int64_t jnative_mono_nanos(void);

/** IANA zone offset minutes east of UTC at epoch_ms; INT32_MIN if unknown. */
int32_t jnative_tz_offset_minutes_at(const char *zone_id, int64_t epoch_ms);
/** 1 if zone_id is recognized by platform zoneinfo. */
int32_t jnative_tz_exists(const char *zone_id);

/* Join all self-started worker threads still registered (non-daemon). */
void jn_thread_join_remaining(void);

/** Capture backtrace into TLS for the current throw (best-effort). */
void jn_ex_capture_stack(void);
/** Print captured frames as "    at <symbol>" (demangles _JN_Class_method). */
void jn_ex_print_stack(void);

#ifdef __cplusplus
}
#endif
