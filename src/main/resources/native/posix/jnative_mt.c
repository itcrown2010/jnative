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

struct JNThreadState {
    int64_t id;
    void *gc_frame;
    void *ex_current;
    int ex_pending;
    int ex_return_pending;
    int64_t ex_return_value;
    int exec_state;
    int safepoint_parked; /* 1 if waiting inside safepoint poll */
    void *thread_obj;
    pthread_t pthread;
    int registered;
    int joined;
    int self_started;
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

void jn_thread_attach_main(void) {
    jn_mt_init();
    if (jn_thread_current() != NULL) {
        return;
    }
    JNThreadState *st = alloc_state();
    pthread_setspecific(tls_key, st);
    st->pthread = pthread_self();
    registry_add(st);
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
    JNThreadState *st = jn_thread_current();
    if (st) {
        st->ex_return_pending = v ? 1 : 0;
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
        st->exec_state = JN_EXEC_MANAGED;
        pthread_mutex_unlock(&gc_mu);
        jn_safepoint_poll();
        return;
    }
    pthread_mutex_unlock(&gc_mu);
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
        pthread_cond_broadcast(&gc_cv);
    }
    pthread_mutex_unlock(&gc_mu);
    pthread_mutex_lock(&collect_mu);
}

void jn_gc_collect_unlock(void) {
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
    pthread_setspecific(tls_key, st);
    st->pthread = pthread_self();

    pthread_mutex_lock(&sa->ready_mu);
    sa->ready = 1;
    pthread_cond_signal(&sa->ready_cv);
    pthread_mutex_unlock(&sa->ready_mu);

    st->thread_obj = sa->thread_obj;
    st->exec_state = JN_EXEC_MANAGED;

    sa->entry(sa->thread_obj);

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
