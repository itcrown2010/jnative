/* Author: 张晓达 */
/*
 * JNative 0.23 — macOS kqueue poller with EVFILT_USER cross-thread wakeup.
 */

#include "jnative_poll.h"

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/event.h>
#include <sys/types.h>
#include <unistd.h>

#define JN_USER_IDENT 0x4a4e0001u

struct JNPoll {
    int kq;
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
    JNPoll *p = (JNPoll *)calloc(1, sizeof(JNPoll));
    if (p == NULL) {
        return NULL;
    }
    p->kq = kqueue();
    if (p->kq < 0) {
        free(p);
        return NULL;
    }
    struct kevent ev;
    EV_SET(&ev, JN_USER_IDENT, EVFILT_USER, EV_ADD | EV_CLEAR, 0, 0, NULL);
    if (kevent(p->kq, &ev, 1, NULL, 0, NULL) < 0) {
        close(p->kq);
        free(p);
        return NULL;
    }
    return p;
}

void jnative_poll_destroy(JNPoll *p) {
    if (p == NULL) {
        return;
    }
    if (p->kq >= 0) {
        close(p->kq);
    }
    free(p);
}

static int kevent_ctl(JNPoll *p, int fd, int16_t filter, int16_t flags) {
    struct kevent ev;
    EV_SET(&ev, (uintptr_t)fd, filter, flags, 0, 0, NULL);
    if (kevent(p->kq, &ev, 1, NULL, 0, NULL) < 0) {
        return JN_POLL_ERR;
    }
    return JN_POLL_OK;
}

int jnative_poll_add(JNPoll *p, int fd, int events) {
    if (p == NULL || fd < 0) {
        return JN_POLL_ERR;
    }
    int rc = JN_POLL_OK;
    if (events & JN_POLL_EVT_READ) {
        rc = kevent_ctl(p, fd, EVFILT_READ, EV_ADD);
    }
    if (rc == JN_POLL_OK && (events & JN_POLL_EVT_WRITE)) {
        rc = kevent_ctl(p, fd, EVFILT_WRITE, EV_ADD);
    }
    if (rc == JN_POLL_OK) {
        p->registered = p->registered + 1;
    }
    return rc;
}

int jnative_poll_mod(JNPoll *p, int fd, int events) {
    if (p == NULL || fd < 0) {
        return JN_POLL_ERR;
    }
    (void)kevent_ctl(p, fd, EVFILT_READ, EV_DELETE);
    (void)kevent_ctl(p, fd, EVFILT_WRITE, EV_DELETE);
    return jnative_poll_add(p, fd, events);
}

int jnative_poll_del(JNPoll *p, int fd) {
    if (p == NULL || fd < 0) {
        return JN_POLL_ERR;
    }
    (void)kevent_ctl(p, fd, EVFILT_READ, EV_DELETE);
    (void)kevent_ctl(p, fd, EVFILT_WRITE, EV_DELETE);
    if (p->registered > 0) {
        p->registered = p->registered - 1;
    }
    return JN_POLL_OK;
}

int jnative_poll_wait(JNPoll *p, int timeout_ms) {
    if (p == NULL) {
        return JN_POLL_ERR;
    }
    p->wakeup_count = 0;
    struct timespec ts;
    struct timespec *tsp = NULL;
    if (timeout_ms >= 0) {
        ts.tv_sec = timeout_ms / 1000;
        ts.tv_nsec = (long)(timeout_ms % 1000) * 1000000L;
        tsp = &ts;
    }
    struct kevent out[64];
    int n = kevent(p->kq, NULL, 0, out, 64, tsp);
    if (n < 0) {
        if (errno == EINTR) {
            return 0;
        }
        return JN_POLL_ERR;
    }
    int fd_events = 0;
    int i = 0;
    while (i < n) {
        if (out[i].filter == EVFILT_USER) {
            p->wakeup_count = p->wakeup_count + 1;
        } else {
            fd_events = fd_events + 1;
        }
        i = i + 1;
    }
    return fd_events;
}

void jnative_poll_wakeup(JNPoll *p) {
    if (p == NULL) {
        return;
    }
    struct kevent ev;
    EV_SET(&ev, JN_USER_IDENT, EVFILT_USER, 0, NOTE_TRIGGER, 0, NULL);
    (void)kevent(p->kq, &ev, 1, NULL, 0, NULL);
}

int jnative_poll_registered_count(JNPoll *p) {
    if (p == NULL) {
        return 0;
    }
    return p->registered;
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
