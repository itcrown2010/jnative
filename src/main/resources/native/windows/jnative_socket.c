/*
 * Windows Winsock foundation stub (0.8.2).
 * Architecture reserved: WSAStartup / WSACleanup / SOCKET / closesocket.
 * Full Winsock backend is future work; CI currently exercises POSIX.
 */

#include "jnative_socket.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int jnative_plat_tcp_connect(
        const char *host,
        int port,
        int timeout_ms,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    (void)host;
    (void)port;
    (void)timeout_ms;
    if (out_handle) {
        *out_handle = JN_NET_INVALID_HANDLE;
    }
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_listen(
        const char *host,
        int port,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    (void)host;
    (void)port;
    if (out_handle) {
        *out_handle = JN_NET_INVALID_HANDLE;
    }
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_accept(
        int64_t listen_handle,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    (void)listen_handle;
    if (out_handle) {
        *out_handle = JN_NET_INVALID_HANDLE;
    }
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_read(
        int64_t handle,
        void *buf,
        int max,
        int *out_n,
        char *errbuf,
        size_t errbuf_len
) {
    (void)handle;
    (void)buf;
    (void)max;
    if (out_n) {
        *out_n = 0;
    }
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_read_all(
        int64_t handle,
        void **out_buf,
        int *out_n,
        char *errbuf,
        size_t errbuf_len
) {
    (void)handle;
    if (out_buf) {
        *out_buf = NULL;
    }
    if (out_n) {
        *out_n = 0;
    }
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_write_all(
        int64_t handle,
        const void *buf,
        int n,
        char *errbuf,
        size_t errbuf_len
) {
    (void)handle;
    (void)buf;
    (void)n;
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_close(int64_t handle) {
    (void)handle;
    return JN_NET_OK;
}

int jnative_plat_tcp_set_timeouts(
        int64_t handle,
        int read_ms,
        int write_ms,
        char *errbuf,
        size_t errbuf_len
) {
    (void)handle;
    (void)read_ms;
    (void)write_ms;
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return JN_NET_ERR_SOCKET;
}

char *jnative_plat_dns_resolve(const char *host, char *errbuf, size_t errbuf_len) {
    (void)host;
    if (errbuf && errbuf_len) {
        snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    }
    return NULL;
}

int64_t jnative_plat_udp_bind(
        const char *host,
        int port,
        char *errbuf,
        size_t errbuf_len
) {
    (void)host; (void)port;
    if (errbuf && errbuf_len) snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    return JN_NET_INVALID_HANDLE;
}

int jnative_plat_udp_sendto(
        int64_t handle, const void *buf, int n, const char *host, int port,
        char *errbuf, size_t errbuf_len
) {
    (void)handle; (void)buf; (void)n; (void)host; (void)port;
    if (errbuf && errbuf_len) snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_udp_recvfrom(
        int64_t handle, void *buf, int max, int *out_n,
        char *host_out, size_t host_out_len, int *out_port,
        char *errbuf, size_t errbuf_len
) {
    (void)handle; (void)buf; (void)max; (void)out_n;
    (void)host_out; (void)host_out_len; (void)out_port;
    if (errbuf && errbuf_len) snprintf(errbuf, errbuf_len, "Windows Winsock backend not implemented");
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_udp_close(int64_t handle) {
    (void)handle;
    return JN_NET_OK;
}
void jnative_plat_net_free(void *p) { free(p); }
