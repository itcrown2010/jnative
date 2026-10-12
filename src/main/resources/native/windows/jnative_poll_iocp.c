/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/*
 * JNative 0.23 — Windows IOCP poller.
 * PARTIAL: stub only — does NOT fake IOCP with select().
 * Async I/O on Windows requires completing this implementation.
 */

#include "jnative_poll.h"

#include <stdlib.h>

#ifdef _WIN32
#include <windows.h>
#endif

struct JNPoll {
    void *iocp;
    int registered;
    int wakeup_count;
};

int jnative_plat_set_nonblocking(int fd, int nb) {
    (void)fd;
    (void)nb;
    return JN_POLL_ERR;
}

JNPoll *jnative_poll_create(void) {
    JNPoll *p = (JNPoll *)calloc(1, sizeof(JNPoll));
    if (p == NULL) {
        return NULL;
    }
    /* PARTIAL — IOCP handle not created */
    p->iocp = NULL;
    return p;
}

void jnative_poll_destroy(JNPoll *p) {
    if (p == NULL) {
        return;
    }
    free(p);
}

int jnative_poll_add(JNPoll *p, int fd, int events) {
    (void)p;
    (void)fd;
    (void)events;
    return JN_POLL_ERR;
}

int jnative_poll_mod(JNPoll *p, int fd, int events) {
    (void)p;
    (void)fd;
    (void)events;
    return JN_POLL_ERR;
}

int jnative_poll_del(JNPoll *p, int fd) {
    (void)p;
    (void)fd;
    return JN_POLL_ERR;
}

int jnative_poll_wait(JNPoll *p, int timeout_ms) {
    (void)p;
    (void)timeout_ms;
    return 0;
}

int jnative_poll_last_wakeup(JNPoll *p) {
    if (p == NULL) {
        return 0;
    }
    if (p->wakeup_count > 0) {
        return 1;
    }
    return 0;
}

void jnative_poll_wakeup(JNPoll *p) {
    if (p == NULL) {
        return;
    }
    p->wakeup_count = p->wakeup_count + 1;
}

int jnative_poll_registered_count(JNPoll *p) {
    if (p == NULL) {
        return 0;
    }
    return p->registered;
}
