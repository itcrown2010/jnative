/* Author: 张晓达
 * POSIX stand-in for jn_fm (see native/jn_fm.h) used ONLY by the headless tests on macOS / Linux.
 * Windows-style paths ("C:\Users\me\x.txt") are mapped onto the directory $JNFM_ROOT, so the Java
 * code under test runs unchanged. Shell UI (menus, properties, ShellExecute) is recorded, not shown.
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

static char *dupz(const char *s) {
    size_t n = strlen(s);
    char *r = (char *)malloc(n + 1);
    memcpy(r, s, n + 1);
    return r;
}

static const char *root(void) {
    const char *r = getenv("JNFM_ROOT");
    return r != NULL ? r : "/tmp/jnfm";
}

/* "C:\a\b" -> "<root>/a/b" */
static char *map(const char *p) {
    size_t n = strlen(p) + strlen(root()) + 4;
    char *r = (char *)calloc(n, 1);
    const char *q = p;
    size_t o;
    if (q[0] != 0 && q[1] == ':') {
        q += 2;
    }
    strcpy(r, root());
    o = strlen(r);
    for (; *q; q++) {
        if (*q == '\\') {
            if (o > 0 && r[o - 1] != '/') {
                r[o++] = '/';
            }
        } else {
            r[o++] = *q;
        }
    }
    r[o] = 0;
    while (o > 1 && r[o - 1] == '/') {
        r[--o] = 0;
    }
    return r;
}

static void sq(char *out, size_t cap, const char *s) {
    size_t o = 0;
    out[o++] = '\'';
    for (; *s && o + 5 < cap; s++) {
        if (*s == '\'') {
            out[o++] = '\'';
            out[o++] = '\\';
            out[o++] = '\'';
            out[o++] = '\'';
        } else {
            out[o++] = *s;
        }
    }
    out[o++] = '\'';
    out[o] = 0;
}

void jn_fm_free(void *p) {
    free(p);
}

static void fmt_time(time_t t, char *out, size_t cap) {
    struct tm lt;
    localtime_r(&t, &lt);
    snprintf(out, cap, "%d/%d/%d %02d:%02d", lt.tm_year + 1900, lt.tm_mon + 1, lt.tm_mday, lt.tm_hour, lt.tm_min);
}

static void rec(char **buf, size_t *n, size_t *cap, const char *name, const struct stat *st, const char *extra) {
    char line[4096];
    char tm[64];
    int isdir = S_ISDIR(st->st_mode);
    int attrs = isdir ? 16 : 0;
    size_t l;
    if (name[0] == '.') {
        attrs |= 2;
    }
    fmt_time(st->st_mtime, tm, sizeof(tm));
    l = (size_t)snprintf(line, sizeof(line), "%c\t%s\t%d\t%lld\t%lld\t%lld\t%s%s%s\n", isdir ? 'D' : 'F', name, attrs,
            isdir ? -1LL : (long long)st->st_size, (long long)st->st_mtime * 1000LL, (long long)st->st_ctime * 1000LL,
            tm, extra != NULL ? "\t" : "", extra != NULL ? extra : "");
    if (*n + l + 1 > *cap) {
        *cap = (*cap + l) * 2;
        *buf = (char *)realloc(*buf, *cap);
    }
    memcpy(*buf + *n, line, l);
    *n += l;
    (*buf)[*n] = 0;
}

char *jn_fm_list(const char *path) {
    char *m = map(path);
    DIR *d = opendir(m);
    char *buf = (char *)malloc(4096);
    size_t n = 0, cap = 4096;
    struct dirent *de;
    buf[0] = 0;
    if (d == NULL) {
        snprintf(buf, 64, "!\t%d\n", errno == ENOENT ? 3 : 5);
        free(m);
        return buf;
    }
    while ((de = readdir(d)) != NULL) {
        char full[4096];
        struct stat st;
        if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) {
            continue;
        }
        snprintf(full, sizeof(full), "%s/%s", m, de->d_name);
        if (stat(full, &st) != 0 && lstat(full, &st) != 0) {
            continue;
        }
        rec(&buf, &n, &cap, de->d_name, &st, NULL);
    }
    closedir(d);
    free(m);
    return buf;
}

char *jn_fm_drives(void) {
    return dupz("C:\\\t本地磁盘 (C:)\t3\t500000000000\t120000000000\nD:\\\tData (D:)\t2\t64000000000\t1000\n");
}

char *jn_fm_known_folder(int32_t id) {
    switch (id) {
    case 0: return dupz("C:\\Users\\me");
    case 1: return dupz("C:\\Users\\me\\Desktop");
    case 2: return dupz("C:\\Users\\me\\Downloads");
    case 3: return dupz("C:\\Users\\me\\Documents");
    case 4: return dupz("C:\\Users\\me\\Pictures");
    case 5: return dupz("C:\\Users\\me\\Music");
    case 6: return dupz("C:\\Users\\me\\Videos");
    default: return dupz("");
    }
}

char *jn_fm_type_name(const char *name, int32_t isdir) {
    char t[128];
    const char *dot = strrchr(name, '.');
    if (isdir) {
        return dupz("文件夹");
    }
    if (dot == NULL) {
        return dupz("文件");
    }
    snprintf(t, sizeof(t), "%s 文件", dot + 1);
    {
        char *p;
        for (p = t; *p && *p != ' '; p++) {
            *p = (char)toupper((unsigned char)*p);
        }
    }
    return dupz(t);
}

char *jn_fm_display_name(const char *path) {
    /* test shim: no localization, the last path component is the display name */
    const char *b = path;
    const char *s;
    for (s = path; *s != '\0'; s++) {
        if ((*s == '/' || *s == '\\\\') && s[1] != '\0') {
            b = s + 1;
        }
    }
    return dupz(b);
}

int32_t jn_fm_exists(const char *path) {
    char *m = map(path);
    struct stat st;
    int r = 0;
    if (stat(m, &st) == 0) {
        r = S_ISDIR(st.st_mode) ? 2 : 1;
    }
    free(m);
    return r;
}

char *jn_fm_full_path(const char *path) {
    return dupz(path);
}

static int run(const char *cmd) {
    return system(cmd);
}

int32_t jn_fm_file_op(int32_t op, const char *srcs, const char *dst, int32_t flags) {
    char *copy = dupz(srcs);
    char *save = NULL;
    char *line;
    int rc = 0;
    static int trashSeq = 0;
    for (line = strtok_r(copy, "\n", &save); line != NULL; line = strtok_r(NULL, "\n", &save)) {
        char *ms = map(line);
        char a[4200], b[4200], cmd[9000];
        const char *base = strrchr(line, '\\');
        base = base != NULL ? base + 1 : line;
        sq(a, sizeof(a), ms);
        if (op == 1 || op == 2) {
            char *md = map(dst);
            char target[4096];
            struct stat st;
            snprintf(target, sizeof(target), "%s/%s", md, base);
            if (stat(target, &st) == 0 && (flags & 1) && op == 1) {
                snprintf(target, sizeof(target), "%s/%s - Copy", md, base);
            } else if (stat(target, &st) == 0) {
                free(md);
                free(ms);
                rc = 183;
                continue;
            }
            sq(b, sizeof(b), target);
            snprintf(cmd, sizeof(cmd), "%s %s %s", op == 1 ? "cp -R" : "mv", a, b);
            if (run(cmd) != 0) {
                rc = 5;
            }
            free(md);
        } else if (op == 3) {
            snprintf(cmd, sizeof(cmd), "rm -rf %s", a);
            if (run(cmd) != 0) {
                rc = 5;
            }
        } else {
            char trash[4096];
            snprintf(trash, sizeof(trash), "%s_RECYCLE", root());
            snprintf(cmd, sizeof(cmd), "mkdir -p '%s'", trash);
            run(cmd);
            snprintf(trash, sizeof(trash), "%s_RECYCLE/%d_%s", root(), ++trashSeq, base);
            sq(b, sizeof(b), trash);
            snprintf(cmd, sizeof(cmd), "mv %s %s", a, b);
            if (run(cmd) != 0) {
                rc = 5;
            }
        }
        free(ms);
    }
    free(copy);
    return rc;
}

static char *g_clip = NULL;
static int g_clip_cut = 0;
static char *g_clip_text = NULL;

int32_t jn_fm_clip_set(const char *paths, int32_t cut) {
    free(g_clip);
    g_clip = dupz(paths);
    g_clip_cut = cut;
    return 0;
}

char *jn_fm_clip_get(void) {
    char *r;
    if (g_clip == NULL) {
        return dupz("");
    }
    r = (char *)malloc(strlen(g_clip) + 8);
    sprintf(r, "%s\n%s\n", g_clip_cut ? "X" : "C", g_clip);
    return r;
}

int32_t jn_fm_clip_state(void) {
    if (g_clip == NULL) {
        return 0;
    }
    return g_clip_cut ? 2 : 1;
}

void jn_fm_clip_clear(void) {
    free(g_clip);
    g_clip = NULL;
}

int32_t jn_fm_clip_text(const char *text) {
    free(g_clip_text);
    g_clip_text = dupz(text);
    return 0;
}

int32_t jn_fm_rename(const char *path, const char *new_name) {
    char *m = map(path);
    char *slash = strrchr(m, '/');
    char dst[4096];
    struct stat st;
    int r = 0;
    if (slash == NULL) {
        free(m);
        return 87;
    }
    snprintf(dst, sizeof(dst), "%.*s/%s", (int)(slash - m), m, new_name);
    if (stat(dst, &st) == 0 && strcasecmp(dst, m) != 0) {
        r = 183;
    } else if (rename(m, dst) != 0) {
        r = errno == ENOENT ? 2 : 5;
    }
    free(m);
    return r;
}

int32_t jn_fm_create(const char *path, int32_t isdir) {
    char *m = map(path);
    int r = 0;
    struct stat st;
    if (stat(m, &st) == 0) {
        r = 183;
    } else if (isdir) {
        if (mkdir(m, 0755) != 0) {
            r = 5;
        }
    } else {
        FILE *f = fopen(m, "wb");
        if (f == NULL) {
            r = 5;
        } else {
            fclose(f);
        }
    }
    free(m);
    return r;
}

static char g_last_exec[4096];
static char g_last_verb[256];

int32_t jn_fm_shell_exec(const char *path, const char *verb, const char *params, const char *cwd) {
    snprintf(g_last_exec, sizeof(g_last_exec), "%s", path);
    snprintf(g_last_verb, sizeof(g_last_verb), "%s", verb != NULL ? verb : "");
    return 0;
}

int32_t jn_fm_shell_menu(const char *paths, int32_t flags) {
    snprintf(g_last_exec, sizeof(g_last_exec), "menu:%s", paths);
    return 0;
}

int32_t jn_fm_shell_verb(const char *paths, const char *verb) {
    snprintf(g_last_exec, sizeof(g_last_exec), "%s:%s", verb, paths);
    return 0;
}

/* ---- search: everything is produced by the first next() call */
typedef struct {
    int used;
    int done;
    char *root;
    char *query;
} PS;
static PS g_ps[4];

int32_t jn_fm_search_begin(const char *r, const char *q) {
    int i;
    for (i = 0; i < 4; i++) {
        if (!g_ps[i].used) {
            char *p;
            g_ps[i].used = 1;
            g_ps[i].done = 0;
            g_ps[i].root = dupz(r);
            g_ps[i].query = dupz(q);
            for (p = g_ps[i].query; *p; p++) {
                *p = (char)tolower((unsigned char)*p);
            }
            return i + 1;
        }
    }
    return 0;
}

static void walk(const char *winDir, const char *q, char **buf, size_t *n, size_t *cap) {
    char *m = map(winDir);
    DIR *d = opendir(m);
    struct dirent *de;
    if (d == NULL) {
        free(m);
        return;
    }
    while ((de = readdir(d)) != NULL) {
        char full[4096];
        char low[1024];
        char sub[4096];
        struct stat st;
        char *p;
        if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) {
            continue;
        }
        snprintf(full, sizeof(full), "%s/%s", m, de->d_name);
        if (stat(full, &st) != 0) {
            continue;
        }
        snprintf(low, sizeof(low), "%s", de->d_name);
        for (p = low; *p; p++) {
            *p = (char)tolower((unsigned char)*p);
        }
        if (strstr(low, q) != NULL) {
            rec(buf, n, cap, de->d_name, &st, winDir);
        }
        if (S_ISDIR(st.st_mode)) {
            snprintf(sub, sizeof(sub), "%s%s%s", winDir, winDir[strlen(winDir) - 1] == '\\' ? "" : "\\", de->d_name);
            walk(sub, q, buf, n, cap);
        }
    }
    closedir(d);
    free(m);
}

char *jn_fm_search_next(int32_t h, int32_t budget) {
    char *buf = (char *)malloc(4096);
    size_t n = 0, cap = 4096;
    buf[0] = 0;
    if (h < 1 || h > 4 || !g_ps[h - 1].used) {
        strcpy(buf, "#done\n");
        return buf;
    }
    if (!g_ps[h - 1].done) {
        walk(g_ps[h - 1].root, g_ps[h - 1].query, &buf, &n, &cap);
        g_ps[h - 1].done = 1;
        if (n + 8 > cap) {
            buf = (char *)realloc(buf, n + 8);
        }
        strcpy(buf + n, "#done\n");
    }
    return buf;
}

void jn_fm_search_end(int32_t h) {
    if (h >= 1 && h <= 4 && g_ps[h - 1].used) {
        free(g_ps[h - 1].root);
        free(g_ps[h - 1].query);
        g_ps[h - 1].used = 0;
    }
}

int32_t jn_fm_watch_set(const char *path) {
    return 0;
}

int32_t jn_fm_watch_poll(void) {
    return 0;
}

int32_t jn_fm_spawn_self(const char *path) {
    snprintf(g_last_exec, sizeof(g_last_exec), "spawn:%s", path);
    return 0;
}
