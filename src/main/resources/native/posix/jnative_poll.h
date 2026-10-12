/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#pragma once

/*
 * JNative 0.23 — cross-platform I/O poller ABI.
 * macOS: kqueue (jnative_poll_kqueue.c)
 * Linux: epoll (jnative_poll_epoll.c) — PARTIAL
 * Windows: IOCP (jnative_poll_iocp.c) — PARTIAL
 */

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JN_POLL_OK 0
#define JN_POLL_ERR -1

#define JN_POLL_EVT_READ 1
#define JN_POLL_EVT_WRITE 2

typedef struct JNPoll JNPoll;

/** Create poller (includes cross-thread wakeup channel). */
JNPoll *jnative_poll_create(void);
void jnative_poll_destroy(JNPoll *p);

/** Register or update interest for fd. */
int jnative_poll_add(JNPoll *p, int fd, int events);
int jnative_poll_mod(JNPoll *p, int fd, int events);
int jnative_poll_del(JNPoll *p, int fd);

/**
 * Wait for I/O or wakeup.
 * @param timeout_ms -1 block forever, 0 poll, >0 milliseconds
 * @return fd event count (excluding wakeup), or JN_POLL_ERR
 */
int jnative_poll_wait(JNPoll *p, int timeout_ms);

/** 1 if the previous wait was woken by jnative_poll_wakeup. */
int jnative_poll_last_wakeup(JNPoll *p);

/** Wake a thread blocked in jnative_poll_wait (safe from other threads). */
void jnative_poll_wakeup(JNPoll *p);

/** Registered fd count (metrics). */
int jnative_poll_registered_count(JNPoll *p);

/** Non-blocking socket/file flag (shared with jnative_socket.c). */
int jnative_plat_set_nonblocking(int fd, int nb);

#ifdef __cplusplus
}
#endif
