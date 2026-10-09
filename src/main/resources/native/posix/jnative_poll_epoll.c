/* Author: 张晓达 */
/*
 * JNative 0.23 — Linux epoll poller.
 * PARTIAL: create/destroy/wakeup stub only; fd registration and wait return 0.
 * Do not use in production on Linux until completed.
 */

#include "jnative_poll.h"

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>

#ifdef __linux__
#include <sys/epoll.h>
#endif

struct JNPoll {
    int epfd;
    int registered;
    int wakeup_count;
};

int jnative_plat_set_nonblocking(int fd, int nb) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return JN_POLL_ERR;
    }
    if (nb) {
        flags = flags | O_NONBLOCK;
    } else {
        flags = flags & ~O_NONBLOCK;
    }
    if (fcntl(fd, F_SETFL, flags) < 0) {
        return JN_POLL_ERR;
    }
    return JN_POLL_OK;
}

JNPoll *jnative_poll_create(void) {
#ifdef __linux__
    JNPoll *p = (JNPoll *)calloc(1, sizeof(JNPoll));
    if (p == NULL) {
        return NULL;
    }
    p->epfd = epoll_create1(0);
    if (p->epfd < 0) {
        free(p);
        return NULL;
    }
    return p;
#else
    return NULL;
#endif
}

void jnative_poll_destroy(JNPoll *p) {
    if (p == NULL) {
        return;
    }
    if (p->epfd >= 0) {
        close(p->epfd);
    }
    free(p);
}

int jnative_poll_add(JNPoll *p, int fd, int events) {
    (void)p;
    (void)fd;
    (void)events;
    /* PARTIAL */
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
    /* PARTIAL — no fd or timer integration yet */
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
