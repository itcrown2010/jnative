/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#include "jnative_fs.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <spawn.h>

extern char **environ;

#if defined(__APPLE__)
#include <copyfile.h>
#include <sys/event.h>
#include <sys/time.h>
#elif defined(__linux__)
#include <sys/inotify.h>
#include <sys/select.h>
#endif

int32_t jnative_fs_exists(const char *path) {
    if (path == NULL) {
        return 0;
    }
    struct stat st;
    return stat(path, &st) == 0 ? 1 : 0;
}

int32_t jnative_fs_is_directory(const char *path) {
    if (path == NULL) {
        return 0;
    }
    struct stat st;
    if (stat(path, &st) != 0) {
        return 0;
    }
    return S_ISDIR(st.st_mode) ? 1 : 0;
}

int32_t jnative_fs_is_file(const char *path) {
    if (path == NULL) {
        return 0;
    }
    struct stat st;
    if (stat(path, &st) != 0) {
        return 0;
    }
    return S_ISREG(st.st_mode) ? 1 : 0;
}

int32_t jnative_fs_is_hidden(const char *path) {
    if (path == NULL) {
        return 0;
    }
    const char *base = strrchr(path, '/');
    base = (base == NULL) ? path : (base + 1);
    return (base[0] == '.') ? 1 : 0;
}

static int mkdir_p(const char *path) {
    if (path == NULL || path[0] == '\0') {
        return -1;
    }
    char *tmp = strdup(path);
    if (tmp == NULL) {
        return -1;
    }
    size_t len = strlen(tmp);
    if (len == 0) {
        free(tmp);
        return -1;
    }
    if (tmp[len - 1] == '/') {
        tmp[len - 1] = '\0';
    }
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
                free(tmp);
                return -1;
            }
            *p = '/';
        }
    }
    if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
        free(tmp);
        return -1;
    }
    free(tmp);
    return 0;
}

int32_t jnative_fs_create_directories(const char *path) {
    return mkdir_p(path) == 0 ? 1 : 0;
}

int32_t jnative_fs_delete(const char *path) {
    if (path == NULL) {
        return 0;
    }
    return unlink(path) == 0 || rmdir(path) == 0 ? 1 : 0;
}

int32_t jnative_fs_rename(const char *from, const char *to) {
    if (from == NULL || to == NULL) {
        return 0;
    }
    return rename(from, to) == 0 ? 1 : 0;
}

int32_t jnative_fs_copy_file(const char *from, const char *to) {
    if (from == NULL || to == NULL) {
        return 0;
    }
#if defined(__APPLE__)
    return copyfile(from, to, NULL, COPYFILE_ALL) == 0 ? 1 : 0;
#else
    int in = open(from, O_RDONLY);
    if (in < 0) {
        return 0;
    }
    int out = open(to, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (out < 0) {
        close(in);
        return 0;
    }
    char buf[8192];
    ssize_t n;
    int ok = 1;
    while ((n = read(in, buf, sizeof(buf))) > 0) {
        ssize_t off = 0;
        while (off < n) {
            ssize_t w = write(out, buf + off, (size_t)(n - off));
            if (w < 0) {
                ok = 0;
                break;
            }
            off += w;
        }
        if (!ok) {
            break;
        }
    }
    if (n < 0) {
        ok = 0;
    }
    close(in);
    close(out);
    return ok ? 1 : 0;
#endif
}

int64_t jnative_fs_file_size(const char *path) {
    if (path == NULL) {
        return -1;
    }
    struct stat st;
    if (stat(path, &st) != 0) {
        return -1;
    }
    return (int64_t)st.st_size;
}

int64_t jnative_fs_mtime_ms(const char *path) {
    if (path == NULL) {
        return 0;
    }
    struct stat st;
    if (stat(path, &st) != 0) {
        return 0;
    }
#if defined(__APPLE__)
    return (int64_t)st.st_mtimespec.tv_sec * 1000
            + (int64_t)st.st_mtimespec.tv_nsec / 1000000;
#else
    return (int64_t)st.st_mtim.tv_sec * 1000
            + (int64_t)st.st_mtim.tv_nsec / 1000000;
#endif
}

char *jnative_fs_list(const char *path) {
    if (path == NULL) {
        return NULL;
    }
    DIR *d = opendir(path);
    if (d == NULL) {
        return NULL;
    }
    size_t cap = 256;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    if (buf == NULL) {
        closedir(d);
        return NULL;
    }
    buf[0] = '\0';
    struct dirent *ent;
    while ((ent = readdir(d)) != NULL) {
        const char *name = ent->d_name;
        if (strcmp(name, ".") == 0 || strcmp(name, "..") == 0) {
            continue;
        }
        size_t nlen = strlen(name);
        if (len + nlen + 2 > cap) {
            cap = (len + nlen + 2) * 2;
            char *nb = (char *)realloc(buf, cap);
            if (nb == NULL) {
                free(buf);
                closedir(d);
                return NULL;
            }
            buf = nb;
        }
        memcpy(buf + len, name, nlen);
        len += nlen;
        buf[len++] = '\n';
        buf[len] = '\0';
    }
    closedir(d);
    return buf;
}

char *jnative_fs_list_info(const char *path) {
    if (path == NULL) {
        return NULL;
    }
    DIR *d = opendir(path);
    if (d == NULL) {
        return NULL;
    }
    size_t cap = 512;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    if (buf == NULL) {
        closedir(d);
        return NULL;
    }
    buf[0] = '\0';
    struct dirent *ent;
    while ((ent = readdir(d)) != NULL) {
        const char *name = ent->d_name;
        if (strcmp(name, ".") == 0 || strcmp(name, "..") == 0) {
            continue;
        }
        char full[PATH_MAX];
        snprintf(full, sizeof(full), "%s/%s", path, name);
        struct stat st;
        int is_dir = 0;
        int64_t size = 0;
        int64_t mtime = 0;
        if (stat(full, &st) == 0) {
            is_dir = S_ISDIR(st.st_mode) ? 1 : 0;
            size = (int64_t)st.st_size;
#if defined(__APPLE__)
            mtime = (int64_t)st.st_mtimespec.tv_sec * 1000
                    + (int64_t)st.st_mtimespec.tv_nsec / 1000000;
#else
            mtime = (int64_t)st.st_mtim.tv_sec * 1000
                    + (int64_t)st.st_mtim.tv_nsec / 1000000;
#endif
        }
        int hidden = (name[0] == '.') ? 1 : 0;
        char line[PATH_MAX + 128];
        int n = snprintf(line, sizeof(line), "%s\t%d\t%lld\t%lld\t%d\n",
                name, is_dir, (long long)size, (long long)mtime, hidden);
        if (n < 0) {
            continue;
        }
        if (len + (size_t)n + 1 > cap) {
            cap = (len + (size_t)n + 1) * 2;
            char *nb = (char *)realloc(buf, cap);
            if (nb == NULL) {
                free(buf);
                closedir(d);
                return NULL;
            }
            buf = nb;
        }
        memcpy(buf + len, line, (size_t)n);
        len += (size_t)n;
        buf[len] = '\0';
    }
    closedir(d);
    return buf;
}

char *jnative_fs_roots(void) {
    return strdup("/\n");
}

char *jnative_fs_known_folder(const char *id) {
    const char *home = getenv("HOME");
    if (home == NULL) {
        home = ".";
    }
    char path[PATH_MAX];
    if (id == NULL || strcmp(id, "home") == 0) {
        snprintf(path, sizeof(path), "%s", home);
    } else if (strcmp(id, "desktop") == 0) {
        snprintf(path, sizeof(path), "%s/Desktop", home);
    } else if (strcmp(id, "documents") == 0) {
        snprintf(path, sizeof(path), "%s/Documents", home);
    } else if (strcmp(id, "downloads") == 0) {
        snprintf(path, sizeof(path), "%s/Downloads", home);
    } else if (strcmp(id, "pictures") == 0) {
        snprintf(path, sizeof(path), "%s/Pictures", home);
    } else {
        snprintf(path, sizeof(path), "%s", home);
    }
    return strdup(path);
}

int32_t jnative_fs_open(const char *path) {
    if (path == NULL) {
        return 0;
    }
    /* posix_spawn — avoid fork() in multithreaded Cocoa GUI processes. */
#if defined(__APPLE__)
    pid_t pid = 0;
    char *argv[] = { "/usr/bin/open", (char *)path, NULL };
    int rc = posix_spawn(&pid, "/usr/bin/open", NULL, NULL, argv, environ);
    return rc == 0 ? 1 : 0;
#else
    pid_t pid = 0;
    char *argv[] = { "xdg-open", (char *)path, NULL };
    int rc = posix_spawnp(&pid, "xdg-open", NULL, NULL, argv, environ);
    return rc == 0 ? 1 : 0;
#endif
}

int32_t jnative_fs_trash(const char *path) {
    if (path == NULL) {
        return 0;
    }
#if defined(__APPLE__)
    /* Prefer Finder trash via AppleScript for Recycle Bin semantics. */
    char cmd[PATH_MAX * 2 + 128];
    snprintf(cmd, sizeof(cmd),
            "osascript -e 'tell application \"Finder\" to delete POSIX file \"%s\"' >/dev/null 2>&1",
            path);
    int rc = system(cmd);
    if (rc == 0) {
        return 1;
    }
    /* Fallback: move into ~/.Trash */
    const char *home = getenv("HOME");
    if (home == NULL) {
        return 0;
    }
    char dest[PATH_MAX];
    const char *base = strrchr(path, '/');
    base = (base == NULL) ? path : (base + 1);
    snprintf(dest, sizeof(dest), "%s/.Trash/%s", home, base);
    return rename(path, dest) == 0 ? 1 : 0;
#else
    const char *home = getenv("HOME");
    if (home == NULL) {
        return 0;
    }
    char trash[PATH_MAX];
    snprintf(trash, sizeof(trash), "%s/.local/share/Trash/files", home);
    mkdir_p(trash);
    const char *base = strrchr(path, '/');
    base = (base == NULL) ? path : (base + 1);
    char dest[PATH_MAX];
    snprintf(dest, sizeof(dest), "%s/%s", trash, base);
    return rename(path, dest) == 0 ? 1 : 0;
#endif
}

void jnative_fs_free(char *p) {
    free(p);
}

char *jnative_fs_config_dir(const char *appName) {
    const char *name = (appName != NULL && appName[0] != '\0') ? appName : "JNativeStudio";
#if defined(__APPLE__)
    const char *home = getenv("HOME");
    if (home == NULL) {
        home = ".";
    }
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/Library/Application Support/%s", home, name);
    mkdir_p(path);
    return strdup(path);
#else
    const char *xdg = getenv("XDG_CONFIG_HOME");
    char path[4096];
    if (xdg != NULL && xdg[0] != '\0') {
        snprintf(path, sizeof(path), "%s/%s", xdg, name);
    } else {
        const char *home = getenv("HOME");
        if (home == NULL) {
            home = ".";
        }
        snprintf(path, sizeof(path), "%s/.config/%s", home, name);
    }
    mkdir_p(path);
    return strdup(path);
#endif
}

/* ---- WatchService backend (kqueue / inotify / poll fingerprint) ---- */

typedef struct {
    int fd;      /* kqueue / inotify fd */
    int dirfd;   /* vnode fd kept alive for EVFILT_VNODE */
    char path[PATH_MAX];
    int64_t last_mtime;
    int armed;
} JnWatch;

#define JN_WATCH_MAX 64
static JnWatch g_watches[JN_WATCH_MAX];
static pthread_mutex_t g_watch_lock = PTHREAD_MUTEX_INITIALIZER;

static int64_t dir_mtime(const char *path) {
    struct stat st;
    if (stat(path, &st) != 0) {
        return 0;
    }
#if defined(__APPLE__)
    return (int64_t)st.st_mtimespec.tv_sec * 1000
            + (int64_t)st.st_mtimespec.tv_nsec / 1000000;
#else
    return (int64_t)st.st_mtim.tv_sec * 1000
            + (int64_t)st.st_mtim.tv_nsec / 1000000;
#endif
}

static void watch_release_locked(JnWatch *w) {
    if (w == NULL) {
        return;
    }
#if defined(__APPLE__)
    if (w->fd >= 0) {
        close(w->fd);
    }
    if (w->dirfd >= 0) {
        close(w->dirfd);
    }
#else
    if (w->fd >= 0) {
        close(w->fd);
    }
#endif
    memset(w, 0, sizeof(*w));
    w->fd = -1;
    w->dirfd = -1;
}

int64_t jnative_fs_watch_open(const char *path) {
    if (path == NULL) {
        return 0;
    }
    pthread_mutex_lock(&g_watch_lock);
    int slot = -1;
    for (int i = 0; i < JN_WATCH_MAX; i++) {
        if (!g_watches[i].armed) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        pthread_mutex_unlock(&g_watch_lock);
        return 0;
    }
    JnWatch *w = &g_watches[slot];
    memset(w, 0, sizeof(*w));
    w->fd = -1;
    w->dirfd = -1;
    strncpy(w->path, path, sizeof(w->path) - 1);
    w->last_mtime = dir_mtime(path);
#if defined(__APPLE__)
    int kq = kqueue();
    if (kq >= 0) {
        int dirfd = open(path, O_RDONLY);
        if (dirfd >= 0) {
            struct kevent kev;
            EV_SET(&kev, (uintptr_t)dirfd, EVFILT_VNODE,
                    EV_ADD | EV_CLEAR,
                    NOTE_WRITE | NOTE_EXTEND | NOTE_DELETE | NOTE_RENAME | NOTE_ATTRIB,
                    0, NULL);
            if (kevent(kq, &kev, 1, NULL, 0, NULL) == 0) {
                w->fd = kq;
                w->dirfd = dirfd;
            } else {
                close(dirfd);
                close(kq);
            }
        } else {
            close(kq);
        }
    }
#elif defined(__linux__)
    int fd = inotify_init1(IN_NONBLOCK);
    if (fd >= 0) {
        int wd = inotify_add_watch(fd, path, IN_CREATE | IN_DELETE | IN_MOVED_FROM | IN_MOVED_TO | IN_ATTRIB);
        if (wd >= 0) {
            w->fd = fd;
        } else {
            close(fd);
            w->fd = -1;
        }
    }
#endif
    w->armed = 1;
    int64_t handle = (int64_t)(slot + 1);
    pthread_mutex_unlock(&g_watch_lock);
    return handle;
}

int32_t jnative_fs_watch_poll(int64_t handle) {
    if (handle <= 0 || handle > JN_WATCH_MAX) {
        return 0;
    }
    pthread_mutex_lock(&g_watch_lock);
    JnWatch *w = &g_watches[(int)handle - 1];
    if (!w->armed) {
        pthread_mutex_unlock(&g_watch_lock);
        return 0;
    }
    int32_t changed = 0;
#if defined(__APPLE__)
    if (w->fd >= 0) {
        struct kevent kev;
        struct timespec ts = {0, 0};
        int n = kevent(w->fd, NULL, 0, &kev, 1, &ts);
        if (n > 0) {
            w->last_mtime = dir_mtime(w->path);
            changed = 1;
        }
    }
#elif defined(__linux__)
    if (w->fd >= 0) {
        char buf[4096];
        ssize_t n = read(w->fd, buf, sizeof(buf));
        if (n > 0) {
            w->last_mtime = dir_mtime(w->path);
            changed = 1;
        }
    }
#endif
    if (changed == 0) {
        int64_t m = dir_mtime(w->path);
        if (m != w->last_mtime) {
            w->last_mtime = m;
            changed = 1;
        }
    }
    pthread_mutex_unlock(&g_watch_lock);
    return changed;
}

void jnative_fs_watch_close(int64_t handle) {
    if (handle <= 0 || handle > JN_WATCH_MAX) {
        return;
    }
    pthread_mutex_lock(&g_watch_lock);
    JnWatch *w = &g_watches[(int)handle - 1];
    if (w->armed) {
        watch_release_locked(w);
    }
    pthread_mutex_unlock(&g_watch_lock);
}

/* ---- Round 4: streaming FD + watch overflow + symlink ---- */

int32_t jnative_fs_watch_poll_ex(int64_t handle) {
#if defined(__linux__)
    if (handle <= 0 || handle > JN_WATCH_MAX) {
        return 0;
    }
    pthread_mutex_lock(&g_watch_lock);
    JnWatch *w = &g_watches[(int)handle - 1];
    if (!w->armed) {
        pthread_mutex_unlock(&g_watch_lock);
        return 0;
    }
    int32_t result = 0;
    if (w->fd >= 0) {
        char buf[4096];
        for (;;) {
            ssize_t n = read(w->fd, buf, sizeof(buf));
            if (n < 0) {
                if (errno == EINTR) {
                    continue;
                }
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    break;
                }
                break;
            }
            if (n == 0) {
                break;
            }
            result = 1;
            char *p = buf;
            char *end = buf + n;
            while (p < end) {
                struct inotify_event *ev = (struct inotify_event *)p;
                if (ev->mask & IN_Q_OVERFLOW) {
                    result = 2;
                }
                p += sizeof(struct inotify_event) + ev->len;
            }
            if (n < (ssize_t)sizeof(buf)) {
                break;
            }
        }
    }
    if (result == 0) {
        int64_t m = dir_mtime(w->path);
        if (m != w->last_mtime) {
            w->last_mtime = m;
            result = 1;
        }
    } else if (result == 1) {
        w->last_mtime = dir_mtime(w->path);
    }
    pthread_mutex_unlock(&g_watch_lock);
    return result;
#else
    int32_t c = jnative_fs_watch_poll(handle);
    return c != 0 ? 1 : 0;
#endif
}

int64_t jnative_fs_stream_open(const char *path, int32_t mode) {
    if (path == NULL) {
        return -1;
    }
    int flags = 0;
    if (mode == 0) {
        flags = O_RDONLY;
    } else if (mode == 1) {
        flags = O_WRONLY | O_CREAT | O_TRUNC;
    } else if (mode == 2) {
        flags = O_WRONLY | O_CREAT | O_APPEND;
    } else {
        return -1;
    }
    int fd;
    do {
        fd = open(path, flags, 0644);
    } while (fd < 0 && errno == EINTR);
    return fd < 0 ? -1 : (int64_t)fd;
}

int32_t jnative_fs_stream_read(int64_t fd, void *buf, int32_t len) {
    if (fd < 0 || buf == NULL || len < 0) {
        return -1;
    }
    if (len == 0) {
        return 0;
    }
    for (;;) {
        ssize_t n = read((int)fd, buf, (size_t)len);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        return (int32_t)n;
    }
}

int32_t jnative_fs_stream_write(int64_t fd, const void *buf, int32_t len) {
    if (fd < 0 || (buf == NULL && len > 0) || len < 0) {
        return -1;
    }
    if (len == 0) {
        return 0;
    }
    for (;;) {
        ssize_t n = write((int)fd, buf, (size_t)len);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        return (int32_t)n;
    }
}

int32_t jnative_fs_stream_close(int64_t fd) {
    if (fd < 0) {
        return 0;
    }
    for (;;) {
        if (close((int)fd) == 0) {
            return 0;
        }
        if (errno == EINTR) {
            continue;
        }
        return -1;
    }
}

int32_t jnative_fs_stream_fsync(int64_t fd) {
    if (fd < 0) {
        return -1;
    }
    for (;;) {
        if (fsync((int)fd) == 0) {
            return 0;
        }
        if (errno == EINTR) {
            continue;
        }
        return -1;
    }
}

int32_t jnative_fs_stream_seek(int64_t fd, int64_t offset) {
    if (fd < 0 || offset < 0) {
        return -1;
    }
    off_t r = lseek((int)fd, (off_t)offset, SEEK_SET);
    if (r < 0) {
        return -1;
    }
    return 0;
}

int32_t jnative_fs_is_symlink(const char *path) {
    if (path == NULL) {
        return 0;
    }
    struct stat st;
    if (lstat(path, &st) != 0) {
        return 0;
    }
    return S_ISLNK(st.st_mode) ? 1 : 0;
}

char *jnative_fs_readlink(const char *path) {
    if (path == NULL) {
        return NULL;
    }
    char buf[PATH_MAX];
    ssize_t n = readlink(path, buf, sizeof(buf) - 1);
    if (n < 0) {
        return NULL;
    }
    buf[n] = '\0';
    return strdup(buf);
}
