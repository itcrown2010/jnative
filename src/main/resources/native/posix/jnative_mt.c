/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/*
 * JNative 0.9 — POSIX multithreading runtime
 * ThreadState TLS, registry, safepoints, STW coordination, Mutex/Condition.
 */

#include "jnative_mt.h"

#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#if defined(_SC_NPROCESSORS_ONLN)
#include <unistd.h>
#endif
#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif
#if defined(__APPLE__) || defined(__linux__)
#include <dlfcn.h>
#include <execinfo.h>
#endif

struct JNThreadState {
    int64_t id;
    void *gc_frame;
    void *ex_current;
    int ex_pending;
    int ex_return_pending;
    int64_t ex_return_value;
    int exec_state;
    int safepoint_parked; /* 1 if waiting inside safepoint poll */
    int gc_restore_managed; /* collector thread was MANAGED before jn_gc_collect_lock */
    int no_gc_depth;
    void *thread_obj;
    pthread_t pthread;
    int registered;
    int joined;
    int self_started;
    int foreign_attached;
    /* GC2 TLAB */
    uint8_t *tlab_top;
    uint8_t *tlab_end;
    uint8_t *tlab_start;
    struct JNThreadState *next;
};

static pthread_key_t tls_key;
static pthread_once_t tls_once = PTHREAD_ONCE_INIT;

static pthread_mutex_t registry_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t heap_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t gc_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t collect_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t gc_cv = PTHREAD_COND_INITIALIZER;

static JNThreadState *registry_head = NULL;
static int64_t next_tid = 1;
static int gc_requested = 0;
static int gc_world_stopped = 0;
static int stw_generation = 0;

static void tls_dtor(void *p) {
    (void)p;
}

static void tls_init(void) {
    pthread_key_create(&tls_key, tls_dtor);
}

void jn_mt_init(void) {
    pthread_once(&tls_once, tls_init);
}

static JNThreadState *alloc_state(void) {
    JNThreadState *st = (JNThreadState *)calloc(1, sizeof(JNThreadState));
    st->exec_state = JN_EXEC_MANAGED;
    return st;
}

static void registry_add(JNThreadState *st) {
    pthread_mutex_lock(&registry_mu);
    st->id = next_tid++;
    st->next = registry_head;
    registry_head = st;
    st->registered = 1;
    pthread_mutex_unlock(&registry_mu);
}

static void registry_remove(JNThreadState *st) {
    pthread_mutex_lock(&registry_mu);
    JNThreadState **pp = &registry_head;
    while (*pp) {
        if (*pp == st) {
            *pp = st->next;
            break;
        }
        pp = &(*pp)->next;
    }
    st->registered = 0;
    pthread_mutex_unlock(&registry_mu);
}

JNThreadState *jn_thread_current(void) {
    jn_mt_init();
    return (JNThreadState *)pthread_getspecific(tls_key);
}

/* Runtime Stability 0.8 — crash diagnostics (async-signal-safe handlers). */
#if !defined(_WIN32)
extern void jn_crash_install_handlers(void);
#endif

void jn_thread_attach_main(void) {
    jn_mt_init();
#if !defined(_WIN32)
    jn_crash_install_handlers();
#endif
    if (jn_thread_current() != NULL) {
        return;
    }
    JNThreadState *st = alloc_state();
    pthread_setspecific(tls_key, st);
    st->pthread = pthread_self();
    registry_add(st);
}

int jn_thread_attach_foreign(void) {
    jn_mt_init();
    if (jn_thread_current() != NULL) {
        return 0;
    }
    JNThreadState *st = alloc_state();
    pthread_setspecific(tls_key, st);
    st->pthread = pthread_self();
    st->foreign_attached = 1;
    registry_add(st);
    return 1;
}

void jn_thread_detach_foreign(void) {
    JNThreadState *st = jn_thread_current();
    if (st == NULL || !st->foreign_attached) {
        return;
    }
    st->tlab_top = NULL;
    st->tlab_end = NULL;
    st->tlab_start = NULL;
    registry_remove(st);
    pthread_setspecific(tls_key, NULL);
    free(st);
}

JNTlab jn_tlab_get(void) {
    JNTlab t = {0};
    JNThreadState *st = jn_thread_current();
    if (st) {
        t.top = st->tlab_top;
        t.end = st->tlab_end;
        t.start = st->tlab_start;
    }
    return t;
}

void jn_tlab_set(uint8_t *top, uint8_t *end, uint8_t *start) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->tlab_top = top;
        st->tlab_end = end;
        st->tlab_start = start;
    }
}

void jn_tlab_clear(void) {
    jn_tlab_set(NULL, NULL, NULL);
}

void jn_tlab_retire_all(void) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        t->tlab_top = NULL;
        t->tlab_end = NULL;
        t->tlab_start = NULL;
    }
    pthread_mutex_unlock(&registry_mu);
}

uint8_t *jn_tlab_max_top(void) {
    uint8_t *max = NULL;
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->tlab_top != NULL && (max == NULL || t->tlab_top > max)) {
            max = t->tlab_top;
        }
    }
    pthread_mutex_unlock(&registry_mu);
    return max;
}

void jn_foreach_tlab(jn_tlab_visit_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->tlab_top != NULL && t->tlab_end != NULL) {
            fn(t->tlab_top, t->tlab_end, ctx);
        }
    }
    pthread_mutex_unlock(&registry_mu);
}

int64_t jn_thread_id(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->id : 0;
}

void *jn_thread_current_obj(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->thread_obj : NULL;
}

void jn_thread_set_current_obj(void *obj) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->thread_obj = obj;
    }
}

void *jn_tls_frame(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->gc_frame : NULL;
}

void jn_tls_set_frame(void *frame) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->gc_frame = frame;
    }
}

void *jn_tls_ex_current(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->ex_current : NULL;
}

void jn_tls_set_ex_current(void *ex) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->ex_current = ex;
    }
}

int jn_tls_ex_pending(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->ex_pending : 0;
}

void jn_tls_set_ex_pending(int v) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->ex_pending = v ? 1 : 0;
    }
}

int jn_tls_ex_return_pending(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->ex_return_pending : 0;
}

void jn_tls_set_ex_return_pending(int v) {
    /* Store abrupt-completion kind: 0=none, 1=return, 2=break, 3=continue. */
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->ex_return_pending = v;
    }
}

int64_t jn_tls_ex_return_value(void) {
    JNThreadState *st = jn_thread_current();
    return st ? st->ex_return_value : 0;
}

void jn_tls_set_ex_return_value(int64_t v) {
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->ex_return_value = v;
    }
}

void jn_heap_lock(void) {
    pthread_mutex_lock(&heap_mu);
}

void jn_heap_unlock(void) {
    pthread_mutex_unlock(&heap_mu);
}

static void park_at_safepoint(JNThreadState *st) {
    pthread_mutex_lock(&gc_mu);
    if (st->exec_state == JN_EXEC_MANAGED) {
        st->exec_state = JN_EXEC_SAFEPOINT;
        pthread_cond_broadcast(&gc_cv);
    }
    int gen = stw_generation;
    while (gc_requested && stw_generation == gen) {
        pthread_cond_wait(&gc_cv, &gc_mu);
    }
    if (st->exec_state == JN_EXEC_SAFEPOINT) {
        st->exec_state = JN_EXEC_MANAGED;
    }
    pthread_mutex_unlock(&gc_mu);
}

void jn_safepoint_poll(void) {
    JNThreadState *st = jn_thread_current();
    if (st == NULL) {
        return;
    }
    if (st->exec_state != JN_EXEC_MANAGED) {
        return;
    }
    /* Check under gc_mu to close the race with jn_gc_stw_begin. */
    pthread_mutex_lock(&gc_mu);
    if (gc_requested && st->exec_state == JN_EXEC_MANAGED) {
        st->exec_state = JN_EXEC_SAFEPOINT;
        st->safepoint_parked = 1;
        pthread_cond_broadcast(&gc_cv);
        int gen = stw_generation;
        while (gc_requested && stw_generation == gen) {
            pthread_cond_wait(&gc_cv, &gc_mu);
        }
        st->safepoint_parked = 0;
        if (st->exec_state == JN_EXEC_SAFEPOINT) {
            st->exec_state = JN_EXEC_MANAGED;
        }
    }
    pthread_mutex_unlock(&gc_mu);
}

void jn_enter_native(void) {
    JNThreadState *st = jn_thread_current();
    if (st == NULL) {
        return;
    }
    pthread_mutex_lock(&gc_mu);
    if (st->exec_state == JN_EXEC_MANAGED) {
        st->exec_state = JN_EXEC_NATIVE;
        pthread_cond_broadcast(&gc_cv);
    }
    pthread_mutex_unlock(&gc_mu);
}

void jn_leave_native(void) {
    JNThreadState *st = jn_thread_current();
    if (st == NULL) {
        return;
    }
    pthread_mutex_lock(&gc_mu);
    if (st->exec_state == JN_EXEC_NATIVE) {
        /* Do not resume managed execution while the world is stopped. */
        while (gc_requested) {
            pthread_cond_wait(&gc_cv, &gc_mu);
        }
        st->exec_state = JN_EXEC_MANAGED;
        pthread_mutex_unlock(&gc_mu);
        jn_safepoint_poll();
        return;
    }
    pthread_mutex_unlock(&gc_mu);
}

void jn_nogc_enter(void) {
    JNThreadState *st = jn_thread_current();
    if (st != NULL) {
        st->no_gc_depth++;
    }
}

void jn_nogc_leave(void) {
    JNThreadState *st = jn_thread_current();
    if (st != NULL && st->no_gc_depth > 0) {
        st->no_gc_depth--;
    }
}

int jn_nogc_depth(void) {
    JNThreadState *st = jn_thread_current();
    return st != NULL ? st->no_gc_depth : 0;
}

static int count_managed_mutators(void) {
    int n = 0;
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->exec_state == JN_EXEC_MANAGED) {
            n++;
        }
    }
    return n;
}

void jn_gc_collect_lock(void) {
    JNThreadState *self = jn_thread_current();
    pthread_mutex_lock(&gc_mu);
    if (self && self->exec_state == JN_EXEC_MANAGED) {
        self->exec_state = JN_EXEC_SAFEPOINT;
        self->gc_restore_managed = 1;
        pthread_cond_broadcast(&gc_cv);
    }
    pthread_mutex_unlock(&gc_mu);
    pthread_mutex_lock(&collect_mu);
}

void jn_gc_collect_unlock(void) {
    /* The collector itself is never "parked", so jn_gc_stw_end does not resume it. Without this
       it would stay SAFEPOINT forever and later stop-the-world collections would treat a running
       mutator as stopped (heap corruption with more than one thread). Restore it while still
       holding collect_mu so no new STW can observe the half-state. */
    JNThreadState *self = jn_thread_current();
    pthread_mutex_lock(&gc_mu);
    if (self && self->gc_restore_managed) {
        self->gc_restore_managed = 0;
        if (self->exec_state == JN_EXEC_SAFEPOINT) {
            self->exec_state = JN_EXEC_MANAGED;
        }
    }
    pthread_mutex_unlock(&gc_mu);
    pthread_mutex_unlock(&collect_mu);
}

void jn_gc_stw_begin(void) {
    JNThreadState *self = jn_thread_current();
    pthread_mutex_lock(&gc_mu);
    if (self && self->exec_state == JN_EXEC_MANAGED) {
        self->exec_state = JN_EXEC_SAFEPOINT;
        pthread_cond_broadcast(&gc_cv);
    }
    gc_requested = 1;
    stw_generation++;
    for (;;) {
        int n = 0;
        for (JNThreadState *t = registry_head; t; t = t->next) {
            /* Only true mutators block STW; NATIVE/SAFEPOINT/TERMINATED are fine. */
            if (t->exec_state == JN_EXEC_MANAGED && t != self) {
                n++;
            }
        }
        if (n == 0) {
            break;
        }
        pthread_cond_wait(&gc_cv, &gc_mu);
    }
    gc_world_stopped = 1;
    pthread_mutex_unlock(&gc_mu);
}

void jn_gc_stw_end(void) {
    pthread_mutex_lock(&gc_mu);
    gc_requested = 0;
    gc_world_stopped = 0;
    stw_generation++;
    for (JNThreadState *t = registry_head; t; t = t->next) {
        /* Only resume threads parked at a safepoint poll — not collect-lock waiters. */
        if (t->safepoint_parked && t->exec_state == JN_EXEC_SAFEPOINT) {
            t->exec_state = JN_EXEC_MANAGED;
        }
    }
    pthread_cond_broadcast(&gc_cv);
    pthread_mutex_unlock(&gc_mu);
}

void jn_foreach_root_slot(jn_root_slot_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        void *frame = t->gc_frame;
        while (frame) {
            void *prev = *(void **)frame;
            int32_t n = *(int32_t *)((char *)frame + 8);
            void **roots = (void **)((char *)frame + 16);
            for (int32_t i = 0; i < n; i++) {
                void **slot = (void **)roots[i];
                if (slot != NULL) {
                    fn(slot, ctx);
                }
            }
            frame = prev;
        }
    }
    pthread_mutex_unlock(&registry_mu);
}

void jn_foreach_ex_root(jn_root_obj_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->ex_current) {
            fn(t->ex_current, ctx);
        }
    }
    pthread_mutex_unlock(&registry_mu);
}

void jn_foreach_thread_obj_root(jn_root_obj_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->thread_obj) {
            fn(t->thread_obj, ctx);
        }
    }
    pthread_mutex_unlock(&registry_mu);
}

void jn_foreach_ex_root_slot(jn_root_slot_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        fn((void **)&t->ex_current, ctx);
    }
    pthread_mutex_unlock(&registry_mu);
}

void jn_foreach_thread_obj_root_slot(jn_root_slot_fn fn, void *ctx) {
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        fn((void **)&t->thread_obj, ctx);
    }
    pthread_mutex_unlock(&registry_mu);
}

typedef struct {
    void *thread_obj;
    void (*entry)(void *);
    JNThreadState *st;
    pthread_mutex_t ready_mu;
    pthread_cond_t ready_cv;
    int ready;
} StartArgs;

static void *thread_trampoline(void *arg) {
    StartArgs *sa = (StartArgs *)arg;
    jn_mt_init();
    JNThreadState *st = sa->st;
    /* Parent frees sa after ready handshake. Copy fields, then pin them in
       registers — clang otherwise reloads sa->entry after unlock (UAF). */
    void *thread_obj = sa->thread_obj;
    void (*entry)(void *) = sa->entry;
    pthread_setspecific(tls_key, st);
    st->pthread = pthread_self();

    pthread_mutex_lock(&sa->ready_mu);
    sa->ready = 1;
    pthread_cond_signal(&sa->ready_cv);
    pthread_mutex_unlock(&sa->ready_mu);
#if defined(__GNUC__) || defined(__clang__)
    __asm__ __volatile__("" : "+r"(thread_obj), "+r"(entry) : : "memory");
#endif
    /* sa may be freed by jn_thread_start after the ready handshake. */

    st->thread_obj = thread_obj;
    /* A new mutator must not become MANAGED while a stop-the-world collection is walking the
       shadow stacks: wait for the collection to finish before entering managed code. */
    pthread_mutex_lock(&gc_mu);
    while (gc_requested) {
        pthread_cond_wait(&gc_cv, &gc_mu);
    }
    st->exec_state = JN_EXEC_MANAGED;
    pthread_mutex_unlock(&gc_mu);

    entry(thread_obj);

    jn_thread_mark_terminated();
    return NULL;
}

int64_t jn_thread_start(void *thread_obj, void (*entry)(void *)) {
    jn_mt_init();
    JNThreadState *st = alloc_state();
    st->self_started = 1;
    st->thread_obj = thread_obj;
    st->exec_state = JN_EXEC_NATIVE; /* until trampoline sets MANAGED */
    registry_add(st);

    StartArgs *sa = (StartArgs *)calloc(1, sizeof(StartArgs));
    sa->thread_obj = thread_obj;
    sa->entry = entry;
    sa->st = st;
    pthread_mutex_init(&sa->ready_mu, NULL);
    pthread_cond_init(&sa->ready_cv, NULL);

    pthread_mutex_lock(&gc_mu);
    /* do not start while world stopped */
    while (gc_requested) {
        pthread_cond_wait(&gc_cv, &gc_mu);
    }
    pthread_mutex_unlock(&gc_mu);

    if (pthread_create(&st->pthread, NULL, thread_trampoline, sa) != 0) {
        registry_remove(st);
        free(st);
        free(sa);
        return -1;
    }

    pthread_mutex_lock(&sa->ready_mu);
    while (!sa->ready) {
        pthread_cond_wait(&sa->ready_cv, &sa->ready_mu);
    }
    pthread_mutex_unlock(&sa->ready_mu);
    pthread_mutex_destroy(&sa->ready_mu);
    pthread_cond_destroy(&sa->ready_cv);
    free(sa);
    return st->id;
}

void jn_thread_join_id(int64_t id) {
    JNThreadState *st = NULL;
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        if (t->id == id) {
            st = t;
            break;
        }
    }
    pthread_mutex_unlock(&registry_mu);
    if (st == NULL || st->joined) {
        return;
    }
    jn_enter_native();
    pthread_join(st->pthread, NULL);
    jn_leave_native();
    st->joined = 1;
    registry_remove(st);
    free(st);
}

void jn_thread_sleep_ms(int64_t ms) {
    if (ms < 0) {
        ms = 0;
    }
    jn_enter_native();
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    while (nanosleep(&ts, &ts) && errno == EINTR) {
    }
    jn_leave_native();
}

void jn_thread_mark_terminated(void) {
    JNThreadState *st = jn_thread_current();
    if (st == NULL) {
        return;
    }
    pthread_mutex_lock(&gc_mu);
    st->exec_state = JN_EXEC_TERMINATED;
    st->thread_obj = NULL;
    pthread_cond_broadcast(&gc_cv);
    pthread_mutex_unlock(&gc_mu);
}

typedef struct {
    pthread_mutex_t mu;
    pthread_t owner;
    int locked;
} JNMutex;

void *jn_mutex_create(void) {
    JNMutex *m = (JNMutex *)calloc(1, sizeof(JNMutex));
    pthread_mutex_init(&m->mu, NULL);
    return m;
}

void jn_mutex_destroy(void *p) {
    JNMutex *m = (JNMutex *)p;
    if (!m) return;
    pthread_mutex_destroy(&m->mu);
    free(m);
}

void jn_mutex_lock(void *p) {
    JNMutex *m = (JNMutex *)p;
    for (;;) {
        jn_enter_native();
        int rc = pthread_mutex_trylock(&m->mu);
        if (rc == 0) {
            m->locked = 1;
            m->owner = pthread_self();
            jn_leave_native();
            return;
        }
        jn_leave_native();
        /* Yield to GC while waiting for a contended lock. */
        jn_safepoint_poll();
        struct timespec ts;
        ts.tv_sec = 0;
        ts.tv_nsec = 1000000L; /* 1ms */
        nanosleep(&ts, NULL);
    }
}

void jn_mutex_unlock(void *p) {
    JNMutex *m = (JNMutex *)p;
    m->locked = 0;
    pthread_mutex_unlock(&m->mu);
}

int jn_mutex_trylock(void *p) {
    JNMutex *m = (JNMutex *)p;
    jn_enter_native();
    int rc = pthread_mutex_trylock(&m->mu);
    if (rc == 0) {
        m->locked = 1;
        m->owner = pthread_self();
        jn_leave_native();
        return 1;
    }
    jn_leave_native();
    return 0;
}

int jn_mutex_owner_check(void *p) {
    JNMutex *m = (JNMutex *)p;
    return m && m->locked && pthread_equal(m->owner, pthread_self());
}

typedef struct {
    pthread_cond_t cv;
} JNCond;

void *jn_cond_create(void) {
    JNCond *c = (JNCond *)calloc(1, sizeof(JNCond));
    pthread_cond_init(&c->cv, NULL);
    return c;
}

void jn_cond_destroy(void *p) {
    JNCond *c = (JNCond *)p;
    if (!c) return;
    pthread_cond_destroy(&c->cv);
    free(c);
}

void jn_cond_wait(void *cp, void *mp) {
    JNCond *c = (JNCond *)cp;
    JNMutex *m = (JNMutex *)mp;
    jn_enter_native();
    m->locked = 0;
    pthread_cond_wait(&c->cv, &m->mu);
    m->locked = 1;
    m->owner = pthread_self();
    jn_leave_native();
}

void jn_cond_signal(void *p) {
    JNCond *c = (JNCond *)p;
    pthread_cond_signal(&c->cv);
}

void jn_cond_broadcast(void *p) {
    JNCond *c = (JNCond *)p;
    pthread_cond_broadcast(&c->cv);
}

int64_t jn_os_thread_count(void) {
    int64_t n = 0;
    pthread_mutex_lock(&registry_mu);
    for (JNThreadState *t = registry_head; t; t = t->next) {
        n++;
    }
    pthread_mutex_unlock(&registry_mu);
    return n;
}

void jn_thread_join_remaining(void) {
    for (;;) {
        int64_t id = -1;
        JNThreadState *self = jn_thread_current();
        pthread_mutex_lock(&registry_mu);
        for (JNThreadState *t = registry_head; t; t = t->next) {
            if (t->self_started && !t->joined && t != self) {
                id = t->id;
                break;
            }
        }
        pthread_mutex_unlock(&registry_mu);
        if (id < 0) {
            break;
        }
        jn_thread_join_id(id);
    }
}


int32_t jnative_cpu_count(void) {
#if defined(_WIN32)
    SYSTEM_INFO info;
    GetSystemInfo(&info);
    unsigned long n = info.dwNumberOfProcessors;
#else
    long n = sysconf(_SC_NPROCESSORS_ONLN);
#endif
    if (n < 1) {
        return 1;
    }
    if (n > 1024) {
        return 1024;
    }
    return (int32_t)n;
}

int64_t jnative_web_mono_millis(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return (int64_t)ts.tv_sec * 1000 + (int64_t)ts.tv_nsec / 1000000;
}

int64_t jnative_wall_millis(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_REALTIME, &ts) != 0) {
        return 0;
    }
    return (int64_t)ts.tv_sec * 1000 + (int64_t)ts.tv_nsec / 1000000;
}

int64_t jnative_mono_nanos(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

#define JN_EX_BT_MAX 64
static _Thread_local void *jn_ex_bt[JN_EX_BT_MAX];
static _Thread_local int jn_ex_bt_n = 0;

void jn_ex_capture_stack(void) {
#if defined(__APPLE__) || defined(__linux__)
    jn_ex_bt_n = backtrace(jn_ex_bt, JN_EX_BT_MAX);
#else
    jn_ex_bt_n = 0;
#endif
}

static int jn_ex_skip_frame(const char *name) {
    if (!name) {
        return 0;
    }
    return strstr(name, "jn_ex_") != NULL
            || strstr(name, "jnative_exception_") != NULL
            || strstr(name, "jnative_debugger_on_uncaught") != NULL
            || strstr(name, "jnative_print_uncaught") != NULL
            || strstr(name, "jnative_throw_") != NULL
            || strstr(name, "jnative_throw_simple") != NULL;
}

/** Best-effort: _JN_Class_method[_types...] → Class.method */
static void jn_ex_demangle_jn(const char *sym, char *out, size_t outsz) {
    if (!sym || !out || outsz == 0) {
        return;
    }
    const char *p = sym;
    if (p[0] == '_') {
        /* Darwin sometimes prefixes an extra underscore. */
        if (strncmp(p, "__JN_", 5) == 0) {
            p += 1;
        }
    }
    if (strncmp(p, "_JN_", 4) != 0) {
        snprintf(out, outsz, "%s", sym);
        return;
    }
    p += 4;
    const char *us = strchr(p, '_');
    if (!us || us == p) {
        snprintf(out, outsz, "%s", p);
        return;
    }
    const char *method = us + 1;
    const char *us2 = strchr(method, '_');
    size_t clen = (size_t)(us - p);
    size_t mlen = us2 ? (size_t)(us2 - method) : strlen(method);
    if (clen == 0 || mlen == 0) {
        snprintf(out, outsz, "%s", sym);
        return;
    }
    snprintf(out, outsz, "%.*s.%.*s", (int)clen, p, (int)mlen, method);
}

void jn_ex_print_stack(void) {
#if defined(__APPLE__) || defined(__linux__)
    for (int i = 0; i < jn_ex_bt_n; i++) {
        Dl_info info;
        memset(&info, 0, sizeof(info));
        const char *name = NULL;
        if (dladdr(jn_ex_bt[i], &info) && info.dli_sname) {
            name = info.dli_sname;
        }
        if (jn_ex_skip_frame(name)) {
            continue;
        }
        if (name) {
            char pretty[256];
            jn_ex_demangle_jn(name, pretty, sizeof(pretty));
            printf("    at %s\n", pretty);
            continue;
        }
        char **syms = backtrace_symbols(&jn_ex_bt[i], 1);
        if (syms && syms[0]) {
            printf("    at %s\n", syms[0]);
        } else {
            printf("    at %p\n", jn_ex_bt[i]);
        }
        free(syms);
    }
#else
    (void)jn_ex_bt;
    (void)jn_ex_bt_n;
#endif
}
