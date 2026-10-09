/* Author: 张晓达 */
#pragma once

/*
 * Platform socket layer (POSIX). Opaque NativeSocketHandle is int64_t here;
 * language / IR must not assume that encoding.
 */

#include <stddef.h>
#include <stdint.h>

#define JN_NET_OK 0
#define JN_NET_ERR_CONNECT 1
#define JN_NET_ERR_HOST 2
#define JN_NET_ERR_TIMEOUT 3
#define JN_NET_ERR_SOCKET 4
#define JN_NET_ERR_ARG 5
#define JN_NET_ERR_CLOSED 6

#define JN_NET_INVALID_HANDLE ((int64_t)-1)

int jnative_plat_tcp_connect(
        const char *host,
        int port,
        int timeout_ms,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_tcp_listen(
        const char *host,
        int port,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_tcp_accept(
        int64_t listen_handle,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_tcp_read(
        int64_t handle,
        void *buf,
        int max,
        int *out_n,
        char *errbuf,
        size_t errbuf_len);

/* Allocates *out_buf with malloc; caller frees. */
int jnative_plat_tcp_read_all(
        int64_t handle,
        void **out_buf,
        int *out_n,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_tcp_write_all(
        int64_t handle,
        const void *buf,
        int n,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_tcp_close(int64_t handle);

/* DNS: newline-separated IP addresses (IPv4/IPv6). Caller frees with free(). */
void jnative_plat_net_free(void *p);

char *jnative_plat_dns_resolve(const char *host, char *errbuf, size_t errbuf_len);

/** Returns socket handle >=0, or JN_NET_INVALID_HANDLE on error. */
int64_t jnative_plat_udp_bind(
        const char *host,
        int port,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_udp_sendto(
        int64_t handle,
        const void *buf,
        int n,
        const char *host,
        int port,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_udp_recvfrom(
        int64_t handle,
        void *buf,
        int max,
        int *out_n,
        char *host_out,
        size_t host_out_len,
        int *out_port,
        char *errbuf,
        size_t errbuf_len);

int jnative_plat_udp_close(int64_t handle);

int jnative_plat_tcp_set_timeouts(
        int64_t handle,
        int read_ms,
        int write_ms,
        char *errbuf,
        size_t errbuf_len);
