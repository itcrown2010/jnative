#include "jnative_fs.h"

#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#if defined(__APPLE__)
#include <limits.h>
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
