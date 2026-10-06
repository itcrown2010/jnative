#include "jnative_proc.h"

#include <errno.h>
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

char *jnative_proc_spawn_s(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd
) {
    if (file == NULL || argv_joined == NULL || argc <= 0) {
        return NULL;
    }
    int outp[2];
    int errp[2];
    if (pipe(outp) != 0 || pipe(errp) != 0) {
        return NULL;
    }
    char *blob = NULL;
    char **argv = split_argv_lines(argv_joined, argc, &blob);
    if (argv == NULL) {
        close(outp[0]);
        close(outp[1]);
        close(errp[0]);
        close(errp[1]);
        return NULL;
    }
    pid_t pid = fork();
    if (pid < 0) {
        free(blob);
        free(argv);
        close(outp[0]);
        close(outp[1]);
        close(errp[0]);
        close(errp[1]);
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
    char *desc = (char *)malloc(64);
    if (desc == NULL) {
        close(outp[0]);
        close(errp[0]);
        kill(pid, SIGTERM);
        waitpid(pid, NULL, 0);
        return NULL;
    }
    snprintf(desc, 64, "%d\n%d\n%d", (int)pid, outp[0], errp[0]);
    return desc;
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

void jnative_proc_close_fd(int32_t fd) {
    if (fd >= 0) {
        close(fd);
    }
}

void jnative_proc_free(char *p) {
    free(p);
}
