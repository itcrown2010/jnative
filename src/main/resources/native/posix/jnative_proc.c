/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#include "jnative_proc.h"

#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

static char **split_argv_lines(const char *joined, int32_t argc, char **out_blob) {
    if (joined == NULL || argc <= 0 || out_blob == NULL) {
        return NULL;
    }
    char *copy = strdup(joined);
    if (copy == NULL) {
        return NULL;
    }
    char **argv = (char **)calloc((size_t)argc + 1, sizeof(char *));
    if (argv == NULL) {
        free(copy);
        return NULL;
    }
    int32_t i = 0;
    char *p = copy;
    while (i < argc && p != NULL) {
        argv[i] = p;
        char *nl = strchr(p, '\n');
        if (nl != NULL) {
            *nl = '\0';
            p = nl + 1;
        } else {
            p = NULL;
        }
        i++;
    }
    argv[argc] = NULL;
    *out_blob = copy;
    return argv;
}

static char *spawn_impl(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd,
        int with_stdin
) {
    if (file == NULL || argv_joined == NULL || argc <= 0) {
        return NULL;
    }
    int outp[2];
    int errp[2];
    int inp[2] = {-1, -1};
    if (pipe(outp) != 0 || pipe(errp) != 0) {
        return NULL;
    }
    if (with_stdin && pipe(inp) != 0) {
        close(outp[0]);
        close(outp[1]);
        close(errp[0]);
        close(errp[1]);
        return NULL;
    }
    char *blob = NULL;
    char **argv = split_argv_lines(argv_joined, argc, &blob);
    pid_t pid = -1;
    if (argv != NULL) {
        pid = fork();
    }
    if (argv == NULL || pid < 0) {
        free(blob);
        free(argv);
        close(outp[0]);
        close(outp[1]);
        close(errp[0]);
        close(errp[1]);
        if (with_stdin) {
            close(inp[0]);
            close(inp[1]);
        }
        return NULL;
    }
    if (pid == 0) {
        if (cwd != NULL && cwd[0] != '\0') {
            if (chdir(cwd) != 0) {
                _exit(127);
            }
        }
        dup2(outp[1], STDOUT_FILENO);
        dup2(errp[1], STDERR_FILENO);
        if (with_stdin) {
            dup2(inp[0], STDIN_FILENO);
            close(inp[0]);
            close(inp[1]);
        }
        close(outp[0]);
        close(outp[1]);
        close(errp[0]);
        close(errp[1]);
        execv(file, argv);
        _exit(127);
    }
    free(blob);
    free(argv);
    close(outp[1]);
    close(errp[1]);
    if (with_stdin) {
        close(inp[0]);
        /* writing to a dead child must fail with EPIPE, not kill the IDE */
        signal(SIGPIPE, SIG_IGN);
    }
    char *desc = (char *)malloc(96);
    if (desc == NULL) {
        close(outp[0]);
        close(errp[0]);
        if (with_stdin) {
            close(inp[1]);
        }
        kill(pid, SIGTERM);
        waitpid(pid, NULL, 0);
        return NULL;
    }
    if (with_stdin) {
        snprintf(desc, 96, "%d\n%d\n%d\n%d", (int)pid, outp[0], errp[0], inp[1]);
    } else {
        snprintf(desc, 96, "%d\n%d\n%d", (int)pid, outp[0], errp[0]);
    }
    return desc;
}

char *jnative_proc_spawn_s(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd
) {
    return spawn_impl(file, argv_joined, argc, cwd, 0);
}

char *jnative_proc_spawn_pipe_s(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd
) {
    return spawn_impl(file, argv_joined, argc, cwd, 1);
}

/* Writes the whole NUL-terminated string to fd (retrying on EINTR / short writes).
 * Returns the number of bytes written, or -1 on error (e.g. the child closed its stdin). */
int32_t jnative_proc_write_s(int32_t fd, const char *data) {
    if (fd < 0 || data == NULL) {
        return -1;
    }
    size_t len = strlen(data);
    size_t done = 0;
    while (done < len) {
        ssize_t n = write(fd, data + done, len - done);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            if (errno == EAGAIN) {
                struct pollfd pf;
                pf.fd = fd;
                pf.events = POLLOUT;
                pf.revents = 0;
                poll(&pf, 1, 100);
                continue;
            }
            return -1;
        }
        done += (size_t)n;
    }
    return (int32_t)done;
}

int32_t jnative_proc_wait(int32_t pid) {
    if (pid <= 0) {
        return -1;
    }
    int status = 0;
    if (waitpid((pid_t)pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

int32_t jnative_proc_kill(int32_t pid) {
    if (pid <= 0) {
        return 0;
    }
    return kill((pid_t)pid, SIGTERM) == 0 ? 1 : 0;
}

char *jnative_proc_read_string(int32_t fd, int32_t max_bytes) {
    if (fd < 0 || max_bytes <= 0) {
        return NULL;
    }
    if (max_bytes > 8 * 1024 * 1024) {
        max_bytes = 8 * 1024 * 1024;
    }
    char *buf = (char *)malloc((size_t)max_bytes + 1);
    if (buf == NULL) {
        return NULL;
    }
    size_t total = 0;
    while ((int32_t)total < max_bytes) {
        ssize_t n = read(fd, buf + total, (size_t)max_bytes - total);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        if (n == 0) {
            break;
        }
        total += (size_t)n;
    }
    buf[total] = '\0';
    return buf;
}

/* Non-blocking style read: waits up to timeout_ms for data.
 * Returns "" when nothing arrived yet, NULL at EOF/error, otherwise the bytes read. */
char *jnative_proc_read_nb(int32_t fd, int32_t max_bytes, int32_t timeout_ms) {
    if (fd < 0 || max_bytes <= 0) {
        return NULL;
    }
    if (max_bytes > 1024 * 1024) {
        max_bytes = 1024 * 1024;
    }
    struct pollfd pf;
    pf.fd = fd;
    pf.events = POLLIN;
    pf.revents = 0;
    int r = poll(&pf, 1, timeout_ms < 0 ? 0 : timeout_ms);
    if (r == 0 || (r < 0 && errno == EINTR)) {
        char *e = (char *)malloc(1);
        if (e != NULL) {
            e[0] = '\0';
        }
        return e;
    }
    if (r < 0) {
        return NULL;
    }
    char *buf = (char *)malloc((size_t)max_bytes + 1);
    if (buf == NULL) {
        return NULL;
    }
    ssize_t n = read(fd, buf, (size_t)max_bytes);
    if (n <= 0) {
        free(buf);
        return NULL;
    }
    buf[n] = '\0';
    return buf;
}

/* Returns the exit status once the child has finished, or -1000 while it is still running. */
int32_t jnative_proc_try_wait(int32_t pid) {
    if (pid <= 0) {
        return -1;
    }
    int status = 0;
    pid_t r = waitpid((pid_t)pid, &status, WNOHANG);
    if (r == 0) {
        return -1000;
    }
    if (r < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

void jnative_proc_close_fd(int32_t fd) {
    if (fd >= 0) {
        close(fd);
    }
}

void jnative_proc_free(char *p) {
    free(p);
}
