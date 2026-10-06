/*
 * POSIX PlatformSocket backend for JNative 0.8.2.
 * Handles: DNS (getaddrinfo), TCP connect/listen/accept, partial I/O,
 * EINTR retry, SIGPIPE suppression, SO_REUSEADDR, timeouts.
 */

#include "jnative_socket.h"

#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <poll.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#ifdef __APPLE__
#ifndef SO_NOSIGPIPE
#define SO_NOSIGPIPE 0x1022
#endif
#endif

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

static void set_err(char *errbuf, size_t errbuf_len, const char *msg) {
    if (errbuf == NULL || errbuf_len == 0) {
        return;
    }
    if (msg == NULL) {
        msg = "network error";
    }
    snprintf(errbuf, errbuf_len, "%s", msg);
}

static void set_err_fmt(char *errbuf, size_t errbuf_len, const char *fmt, ...) {
    if (errbuf == NULL || errbuf_len == 0) {
        return;
    }
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(errbuf, errbuf_len, fmt, ap);
    va_end(ap);
}

static void suppress_sigpipe(int fd) {
#ifdef __APPLE__
    int one = 1;
    (void)setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, sizeof(one));
#else
    (void)fd;
#endif
}

static int set_nonblocking(int fd, int nb) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return -1;
    }
    if (nb) {
        flags |= O_NONBLOCK;
    } else {
        flags &= ~O_NONBLOCK;
    }
    return fcntl(fd, F_SETFL, flags);
}

static int wait_connect(int fd, int timeout_ms) {
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLOUT;
    for (;;) {
        int pr = poll(&pfd, 1, timeout_ms < 0 ? -1 : timeout_ms);
        if (pr < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        if (pr == 0) {
            errno = ETIMEDOUT;
            return -1;
        }
        int soerr = 0;
        socklen_t len = sizeof(soerr);
        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &soerr, &len) < 0) {
            return -1;
        }
        if (soerr != 0) {
            errno = soerr;
            return -1;
        }
        return 0;
    }
}

static int apply_timeouts(int fd, int read_ms, int write_ms) {
    struct timeval tv;
    if (read_ms >= 0) {
        tv.tv_sec = read_ms / 1000;
        tv.tv_usec = (read_ms % 1000) * 1000;
        if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
            return -1;
        }
    }
    if (write_ms >= 0) {
        tv.tv_sec = write_ms / 1000;
        tv.tv_usec = (write_ms % 1000) * 1000;
        if (setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) {
            return -1;
        }
    }
    return 0;
}

static int map_errno(int e) {
    if (e == ETIMEDOUT || e == EAGAIN || e == EWOULDBLOCK) {
        return JN_NET_ERR_TIMEOUT;
    }
    if (e == ECONNREFUSED || e == EHOSTUNREACH || e == ENETUNREACH
            || e == ECONNRESET || e == EPIPE || e == ENOTCONN) {
        return JN_NET_ERR_CONNECT;
    }
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_connect(
        const char *host,
        int port,
        int timeout_ms,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    if (out_handle == NULL || host == NULL) {
        set_err(errbuf, errbuf_len, "invalid connect arguments");
        return JN_NET_ERR_ARG;
    }
    *out_handle = JN_NET_INVALID_HANDLE;
    if (port < 0 || port > 65535) {
        set_err_fmt(errbuf, errbuf_len, "invalid port: %d", port);
        return JN_NET_ERR_ARG;
    }

    char portstr[16];
    snprintf(portstr, sizeof(portstr), "%d", port);

    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;

    struct addrinfo *res = NULL;
    int gai = getaddrinfo(host, portstr, &hints, &res);
    if (gai != 0) {
        set_err_fmt(errbuf, errbuf_len, "unknown host: %s (%s)", host, gai_strerror(gai));
        return JN_NET_ERR_HOST;
    }

    int last_errno = 0;
    int timed_out = 0;
    for (struct addrinfo *ai = res; ai != NULL; ai = ai->ai_next) {
        int fd = -1;
        do {
            fd = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
        } while (fd < 0 && errno == EINTR);
        if (fd < 0) {
            last_errno = errno;
            continue;
        }
        suppress_sigpipe(fd);

        if (timeout_ms >= 0) {
            if (set_nonblocking(fd, 1) < 0) {
                last_errno = errno;
                close(fd);
                continue;
            }
            int cr;
            do {
                cr = connect(fd, ai->ai_addr, ai->ai_addrlen);
            } while (cr < 0 && errno == EINTR);
            if (cr < 0 && errno != EINPROGRESS) {
                last_errno = errno;
                close(fd);
                continue;
            }
            if (cr < 0) {
                if (wait_connect(fd, timeout_ms) < 0) {
                    last_errno = errno;
                    if (errno == ETIMEDOUT) {
                        timed_out = 1;
                    }
                    close(fd);
                    continue;
                }
            }
            if (set_nonblocking(fd, 0) < 0) {
                last_errno = errno;
                close(fd);
                continue;
            }
        } else {
            int cr;
            do {
                cr = connect(fd, ai->ai_addr, ai->ai_addrlen);
            } while (cr < 0 && errno == EINTR);
            if (cr < 0) {
                last_errno = errno;
                close(fd);
                continue;
            }
        }

        freeaddrinfo(res);
        *out_handle = (int64_t)fd;
        return JN_NET_OK;
    }

    freeaddrinfo(res);
    if (timed_out) {
        set_err_fmt(errbuf, errbuf_len, "connection timed out: %s:%d", host, port);
        return JN_NET_ERR_TIMEOUT;
    }
    set_err_fmt(errbuf, errbuf_len, "connection refused: %s:%d", host, port);
    (void)last_errno;
    return JN_NET_ERR_CONNECT;
}

int jnative_plat_tcp_listen(
        const char *host,
        int port,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    if (out_handle == NULL || host == NULL) {
        set_err(errbuf, errbuf_len, "invalid listen arguments");
        return JN_NET_ERR_ARG;
    }
    *out_handle = JN_NET_INVALID_HANDLE;
    if (port < 0 || port > 65535) {
        set_err_fmt(errbuf, errbuf_len, "invalid port: %d", port);
        return JN_NET_ERR_ARG;
    }

    char portstr[16];
    snprintf(portstr, sizeof(portstr), "%d", port);

    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;
    hints.ai_flags = AI_PASSIVE;

    struct addrinfo *res = NULL;
    int gai = getaddrinfo(host, portstr, &hints, &res);
    if (gai != 0) {
        set_err_fmt(errbuf, errbuf_len, "unknown host: %s (%s)", host, gai_strerror(gai));
        return JN_NET_ERR_HOST;
    }

    int last_errno = 0;
    for (struct addrinfo *ai = res; ai != NULL; ai = ai->ai_next) {
        int fd = -1;
        do {
            fd = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
        } while (fd < 0 && errno == EINTR);
        if (fd < 0) {
            last_errno = errno;
            continue;
        }
        int one = 1;
        (void)setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
        suppress_sigpipe(fd);

        if (bind(fd, ai->ai_addr, ai->ai_addrlen) < 0) {
            last_errno = errno;
            close(fd);
            continue;
        }
        if (listen(fd, 128) < 0) {
            last_errno = errno;
            close(fd);
            continue;
        }

        freeaddrinfo(res);
        *out_handle = (int64_t)fd;
        return JN_NET_OK;
    }

    freeaddrinfo(res);
    set_err_fmt(errbuf, errbuf_len, "failed to listen: %s:%d (%s)",
            host, port, strerror(last_errno ? last_errno : errno));
    return JN_NET_ERR_SOCKET;
}

int jnative_plat_tcp_accept(
        int64_t listen_handle,
        int64_t *out_handle,
        char *errbuf,
        size_t errbuf_len
) {
    if (out_handle == NULL) {
        set_err(errbuf, errbuf_len, "invalid accept arguments");
        return JN_NET_ERR_ARG;
    }
    *out_handle = JN_NET_INVALID_HANDLE;
    if (listen_handle < 0) {
        set_err(errbuf, errbuf_len, "socket closed");
        return JN_NET_ERR_CLOSED;
    }
    int fd = (int)listen_handle;
    int client = -1;
    do {
        client = accept(fd, NULL, NULL);
    } while (client < 0 && errno == EINTR);
    if (client < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == ETIMEDOUT) {
            set_err(errbuf, errbuf_len, "accept timed out");
            return JN_NET_ERR_TIMEOUT;
        }
        set_err_fmt(errbuf, errbuf_len, "accept failed: %s", strerror(errno));
        return JN_NET_ERR_SOCKET;
    }
    suppress_sigpipe(client);
    *out_handle = (int64_t)client;
    return JN_NET_OK;
}

int jnative_plat_tcp_read(
        int64_t handle,
        void *buf,
        int max,
        int *out_n,
        char *errbuf,
        size_t errbuf_len
) {
    if (out_n == NULL || buf == NULL || max < 0) {
        set_err(errbuf, errbuf_len, "invalid read arguments");
        return JN_NET_ERR_ARG;
    }
    *out_n = 0;
    if (handle < 0) {
        set_err(errbuf, errbuf_len, "socket closed");
        return JN_NET_ERR_CLOSED;
    }
    if (max == 0) {
        return JN_NET_OK;
    }
    int fd = (int)handle;
    for (;;) {
        ssize_t n = recv(fd, buf, (size_t)max, 0);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            int code = map_errno(errno);
            if (code == JN_NET_ERR_TIMEOUT) {
                set_err(errbuf, errbuf_len, "read timed out");
            } else {
                set_err_fmt(errbuf, errbuf_len, "read failed: %s", strerror(errno));
            }
            return code;
        }
        *out_n = (int)n;
        return JN_NET_OK;
    }
}

int jnative_plat_tcp_read_all(
        int64_t handle,
        void **out_buf,
        int *out_n,
        char *errbuf,
        size_t errbuf_len
) {
    if (out_buf == NULL || out_n == NULL) {
        set_err(errbuf, errbuf_len, "invalid readAll arguments");
        return JN_NET_ERR_ARG;
    }
    *out_buf = NULL;
    *out_n = 0;
    if (handle < 0) {
        set_err(errbuf, errbuf_len, "socket closed");
        return JN_NET_ERR_CLOSED;
    }

    size_t cap = 4096;
    size_t len = 0;
    unsigned char *buf = (unsigned char *)malloc(cap);
    if (buf == NULL) {
        set_err(errbuf, errbuf_len, "out of memory");
        return JN_NET_ERR_SOCKET;
    }

    unsigned char chunk[8192];
    for (;;) {
        int n = 0;
        int rc = jnative_plat_tcp_read(handle, chunk, (int)sizeof(chunk), &n, errbuf, errbuf_len);
        if (rc != JN_NET_OK) {
            free(buf);
            *out_buf = NULL;
            *out_n = 0;
            return rc;
        }
        if (n == 0) {
            break;
        }
        if (len + (size_t)n > cap) {
            size_t ncap = cap;
            while (len + (size_t)n > ncap) {
                ncap *= 2;
            }
            unsigned char *nb = (unsigned char *)realloc(buf, ncap);
            if (nb == NULL) {
                free(buf);
                set_err(errbuf, errbuf_len, "out of memory");
                return JN_NET_ERR_SOCKET;
            }
            buf = nb;
            cap = ncap;
        }
        memcpy(buf + len, chunk, (size_t)n);
        len += (size_t)n;
        if (len > (size_t)0x7fffffff) {
            free(buf);
            set_err(errbuf, errbuf_len, "response too large");
            return JN_NET_ERR_SOCKET;
        }
    }
    *out_buf = buf;
    *out_n = (int)len;
    return JN_NET_OK;
}

int jnative_plat_tcp_write_all(
        int64_t handle,
        const void *buf,
        int n,
        char *errbuf,
        size_t errbuf_len
) {
    if (buf == NULL && n > 0) {
        set_err(errbuf, errbuf_len, "invalid write arguments");
        return JN_NET_ERR_ARG;
    }
    if (handle < 0) {
        set_err(errbuf, errbuf_len, "socket closed");
        return JN_NET_ERR_CLOSED;
    }
    if (n <= 0) {
        return JN_NET_OK;
    }
    int fd = (int)handle;
    const unsigned char *p = (const unsigned char *)buf;
    int remaining = n;
    while (remaining > 0) {
        int flags = 0;
#if defined(__linux__) || defined(__ANDROID__)
        flags = MSG_NOSIGNAL;
#endif
        ssize_t w = send(fd, p, (size_t)remaining, flags);
        if (w < 0) {
            if (errno == EINTR) {
                continue;
            }
            int code = map_errno(errno);
            if (code == JN_NET_ERR_TIMEOUT) {
                set_err(errbuf, errbuf_len, "write timed out");
            } else if (errno == EPIPE) {
                set_err(errbuf, errbuf_len, "write failed: broken pipe");
                return JN_NET_ERR_SOCKET;
            } else {
                set_err_fmt(errbuf, errbuf_len, "write failed: %s", strerror(errno));
            }
            return code;
        }
        if (w == 0) {
            set_err(errbuf, errbuf_len, "write failed: short write");
            return JN_NET_ERR_SOCKET;
        }
        p += w;
        remaining -= (int)w;
    }
    return JN_NET_OK;
}

int jnative_plat_tcp_close(int64_t handle) {
    if (handle < 0) {
        return JN_NET_OK;
    }
    int fd = (int)handle;
    (void)shutdown(fd, SHUT_RDWR);
    for (;;) {
        if (close(fd) == 0) {
            return JN_NET_OK;
        }
        if (errno == EINTR) {
            continue;
        }
        return JN_NET_OK; /* idempotent / best-effort */
    }
}

int jnative_plat_tcp_set_timeouts(
        int64_t handle,
        int read_ms,
        int write_ms,
        char *errbuf,
        size_t errbuf_len
) {
    if (handle < 0) {
        set_err(errbuf, errbuf_len, "socket closed");
        return JN_NET_ERR_CLOSED;
    }
    if (apply_timeouts((int)handle, read_ms, write_ms) < 0) {
        set_err_fmt(errbuf, errbuf_len, "set timeout failed: %s", strerror(errno));
        return JN_NET_ERR_SOCKET;
    }
    return JN_NET_OK;
}
