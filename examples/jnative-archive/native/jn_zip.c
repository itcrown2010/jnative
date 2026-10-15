/* Author: 张晓达
 * JNative Archive engine ABI — minizip-ng 4.0.8 (Phase B).
 */
#include "jn_zip.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#if defined(_WIN32)
#  include <windows.h>
#  include <io.h>
#  include <fcntl.h>
#  include <sys/stat.h>
#  include <direct.h>
#else
#  include <unistd.h>
#  include <fcntl.h>
#  include <sys/stat.h>
#  include <sys/time.h>
#  include <pthread.h>
#  if defined(__APPLE__)
#    include <sys/attr.h>
/* renamex_np + RENAME_EXCL for no-clobber publish */
int renamex_np(const char *from, const char *to, unsigned int flags);
#  endif
#endif

#include "mz.h"
#include "mz_crypt.h"
#include "mz_os.h"
#include "mz_strm.h"
#include "mz_zip.h"
#include "mz_zip_rw.h"

#define JN_ZIP_BUF 65536

#if defined(_WIN32)
typedef CRITICAL_SECTION jn_mutex_t;
static void jn_mutex_init(jn_mutex_t *m) { InitializeCriticalSection(m); }
static void jn_mutex_destroy(jn_mutex_t *m) { DeleteCriticalSection(m); }
static void jn_mutex_lock(jn_mutex_t *m) { EnterCriticalSection(m); }
static void jn_mutex_unlock(jn_mutex_t *m) { LeaveCriticalSection(m); }
#else
typedef pthread_mutex_t jn_mutex_t;
static void jn_mutex_init(jn_mutex_t *m) { pthread_mutex_init(m, NULL); }
static void jn_mutex_destroy(jn_mutex_t *m) { pthread_mutex_destroy(m); }
static void jn_mutex_lock(jn_mutex_t *m) { pthread_mutex_lock(m); }
static void jn_mutex_unlock(jn_mutex_t *m) { pthread_mutex_unlock(m); }
#endif

typedef struct jn_writer {
    void *handle;
    int open;
    int finished; /* central directory written; file is a complete ZIP */
    int entry_open;
    int aes_strength; /* MZ_AES_STRENGTH_* */
    char *password;
    char *entry_name;
    int32_t last_err;
    int32_t cancel_req;
    int32_t cancel_ack;
    int64_t bytes_in;
    int64_t bytes_out;
    int64_t entries_done;
    char current_name[512];
    jn_mutex_t lock;
    int lock_inited;
} jn_writer;

typedef struct jn_reader {
    void *handle;
    int open;
    int entry_open;
    char *password;
    int32_t last_err;
    int32_t cancel_req;
    int32_t cancel_ack;
    int64_t bytes_in;
    int64_t bytes_out;
    int64_t entries_done;
    int64_t expand_total;
    char current_name[512];
    jn_mutex_t lock;
    int lock_inited;
    char *owned_stage_dir; /* mkdtemp staging for current extract session; freed on destroy */
} jn_reader;

static int32_t g_last_err = JN_ZIP_OK;
static jn_mutex_t g_err_lock;
static int g_err_lock_inited = 0;

static void ensure_g_err_lock(void) {
    if (!g_err_lock_inited) {
        jn_mutex_init(&g_err_lock);
        g_err_lock_inited = 1;
    }
}

static int32_t map_err(int32_t mz) {
    if (mz == MZ_OK) {
        return JN_ZIP_OK;
    }
    if (mz == MZ_END_OF_LIST || mz == MZ_END_OF_STREAM) {
        return JN_ZIP_ERR_EOF;
    }
    if (mz == MZ_PARAM_ERROR) {
        return JN_ZIP_ERR_PARAM;
    }
    if (mz == MZ_FORMAT_ERROR || mz == MZ_DATA_ERROR) {
        return JN_ZIP_ERR_FORMAT;
    }
    if (mz == MZ_CRC_ERROR) {
        return JN_ZIP_ERR_CRC;
    }
    /* Engine often cannot distinguish wrong password vs truncated/corrupt ciphertext. */
    if (mz == MZ_PASSWORD_ERROR || mz == MZ_CRYPT_ERROR) {
        return JN_ZIP_ERR_PASSWORD;
    }
    if (mz == MZ_SUPPORT_ERROR) {
        return JN_ZIP_ERR_UNSUPPORTED;
    }
    if (mz == MZ_EXIST_ERROR) {
        return JN_ZIP_ERR_EXISTS;
    }
    if (mz == MZ_BUF_ERROR) {
        return JN_ZIP_ERR_FORMAT;
    }
    if (mz == MZ_OPEN_ERROR || mz == MZ_READ_ERROR || mz == MZ_WRITE_ERROR
            || mz == MZ_CLOSE_ERROR || mz == MZ_SEEK_ERROR) {
        return JN_ZIP_ERR_IO;
    }
    return JN_ZIP_ERR;
}

static void set_err(int32_t *slot, int32_t code) {
    ensure_g_err_lock();
    jn_mutex_lock(&g_err_lock);
    g_last_err = code;
    jn_mutex_unlock(&g_err_lock);
    if (slot) {
        *slot = code;
    }
}

void jn_zip_free(void *p) {
    free(p);
}

void jn_zip_entry_info_free(jn_zip_entry_info *info) {
    if (info == NULL) {
        return;
    }
    free(info->name);
    info->name = NULL;
}

const char *jn_zip_engine_version(void) {
    return "minizip-ng 4.0.8";
}

const char *jn_zip_crypto_info(void) {
    return "WinZip AES (AE-1/AE-2), default AES-256, password UTF-8, ZipCrypto=OFF; "
           "ZIP central directory names remain visible (no filename encryption in ZIP mode)";
}

int32_t jn_zip_last_error(void) {
    return g_last_err;
}

const char *jn_zip_strerror(int32_t code) {
    switch (code) {
    case JN_ZIP_OK: return "ok";
    case JN_ZIP_ERR_PARAM: return "invalid parameter";
    case JN_ZIP_ERR_IO: return "I/O error";
    case JN_ZIP_ERR_FORMAT: return "bad archive format";
    case JN_ZIP_ERR_CRC: return "CRC / integrity failure";
    case JN_ZIP_ERR_PASSWORD: return "password or integrity/auth verification failed";
    case JN_ZIP_ERR_UNSUPPORTED: return "unsupported method or feature";
    case JN_ZIP_ERR_PATH: return "unsafe or invalid path";
    case JN_ZIP_ERR_EOF: return "end of list/stream";
    case JN_ZIP_ERR_STATE: return "invalid state";
    case JN_ZIP_ERR_EXISTS: return "target exists";
    case JN_ZIP_ERR_NEED_PASSWORD: return "password required";
    case JN_ZIP_ERR_CANCELLED: return "cancelled";
    case JN_ZIP_ERR_LIMIT: return "size or entry limit exceeded";
    case JN_ZIP_ERR_LINK: return "symlink or reparse point rejected";
    case JN_ZIP_ERR_SPACE: return "insufficient disk space";
    case JN_ZIP_ERR_SOURCE: return "source read failed";
    case JN_ZIP_ERR_TARGET: return "target write failed";
    default: return "error";
    }
}

uint32_t jn_zip_crc32(uint32_t crc, const void *buf, int32_t len) {
    if (buf == NULL || len <= 0) {
        return crc;
    }
    return mz_crypt_crc32_update(crc, (const uint8_t *)buf, len);
}

int64_t jn_zip_now_ms(void) {
    struct timeval tv;
    if (gettimeofday(&tv, NULL) != 0) {
        return 0;
    }
    return ((int64_t)tv.tv_sec * 1000) + ((int64_t)tv.tv_usec / 1000);
}

static char *dup_str(const char *s) {
    size_t n;
    char *o;
    if (s == NULL) {
        return NULL;
    }
    n = strlen(s);
    o = (char *)malloc(n + 1);
    if (o == NULL) {
        return NULL;
    }
    memcpy(o, s, n + 1);
    return o;
}

static void wipe_free(char **pp) {
    if (pp == NULL || *pp == NULL) {
        return;
    }
    {
        size_t n = strlen(*pp);
        volatile char *p = *pp;
        size_t i;
        for (i = 0; i < n; i++) {
            p[i] = 0;
        }
    }
    free(*pp);
    *pp = NULL;
}

void jn_zip_password_wipe(char *buf, int32_t cap) {
    int32_t i;
    if (buf == NULL || cap <= 0) {
        return;
    }
    for (i = 0; i < cap; i++) {
        ((volatile char *)buf)[i] = 0;
    }
}

int32_t jn_zip_password_read_fd(int32_t fd, char *buf, int32_t cap) {
#if defined(_WIN32)
    int n;
    int32_t i;
    int total = 0;
    if (fd < 0 || buf == NULL || cap < 2) {
        return JN_ZIP_ERR_PARAM;
    }
    memset(buf, 0, (size_t)cap);
    for (;;) {
        n = _read(fd, buf + total, (unsigned)(cap - 1 - total));
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            jn_zip_password_wipe(buf, cap);
            return JN_ZIP_ERR_IO;
        }
        if (n == 0) {
            break;
        }
        total += n;
        if (total >= cap - 1) {
            /* filled buffer — treat as too long (do not silently truncate) */
            jn_zip_password_wipe(buf, cap);
            return JN_ZIP_ERR_PARAM;
        }
        /* stop at first newline for single-line secrets */
        if (memchr(buf, '\n', (size_t)total) != NULL || memchr(buf, '\r', (size_t)total) != NULL) {
            break;
        }
    }
    i = total;
    while (i > 0 && (buf[i - 1] == '\n' || buf[i - 1] == '\r')) {
        buf[i - 1] = 0;
        i--;
    }
    buf[i] = 0;
    if (i == 0) {
        jn_zip_password_wipe(buf, cap);
        return JN_ZIP_ERR_PARAM;
    }
    return JN_ZIP_OK;
#else
    ssize_t n;
    int32_t i;
    int32_t total = 0;
    if (fd < 0 || buf == NULL || cap < 2) {
        return JN_ZIP_ERR_PARAM;
    }
    memset(buf, 0, (size_t)cap);
    for (;;) {
        n = read(fd, buf + total, (size_t)(cap - 1 - total));
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            jn_zip_password_wipe(buf, cap);
            return JN_ZIP_ERR_IO;
        }
        if (n == 0) {
            break;
        }
        total += (int32_t)n;
        if (total >= cap - 1) {
            jn_zip_password_wipe(buf, cap);
            return JN_ZIP_ERR_PARAM;
        }
        if (memchr(buf, '\n', (size_t)total) != NULL || memchr(buf, '\r', (size_t)total) != NULL) {
            break;
        }
    }
    i = total;
    while (i > 0 && (buf[i - 1] == '\n' || buf[i - 1] == '\r')) {
        buf[i - 1] = 0;
        i--;
    }
    buf[i] = 0;
    if (i == 0) {
        jn_zip_password_wipe(buf, cap);
        return JN_ZIP_ERR_PARAM;
    }
    return JN_ZIP_OK;
#endif
}

char *jn_zip_make_unique_temp_path(const char *final_path) {
#if defined(_WIN32)
    char *tmpl;
    size_t n;
    char dir[MAX_PATH];
    char name[MAX_PATH];
    char *slash;
    if (final_path == NULL || final_path[0] == '\0') {
        return NULL;
    }
    n = strlen(final_path);
    tmpl = (char *)malloc(n + 32);
    if (tmpl == NULL) {
        return NULL;
    }
    memcpy(tmpl, final_path, n + 1);
    slash = strrchr(tmpl, '\\');
    if (slash == NULL) {
        slash = strrchr(tmpl, '/');
    }
    if (slash) {
        *slash = '\0';
        snprintf(dir, sizeof(dir), "%s", tmpl);
        *slash = (strchr(final_path + (slash - tmpl), '\\') != NULL) ? '\\' : '/';
    } else {
        snprintf(dir, sizeof(dir), ".");
    }
    {
        UINT u = GetTempFileNameA(dir, "jna", 0, name);
        free(tmpl);
        if (u == 0) {
            return NULL;
        }
        return dup_str(name);
    }
#else
    char *tmpl;
    size_t n;
    int fd;
    if (final_path == NULL || final_path[0] == '\0') {
        return NULL;
    }
    n = strlen(final_path);
    tmpl = (char *)malloc(n + 16);
    if (tmpl == NULL) {
        return NULL;
    }
    memcpy(tmpl, final_path, n);
    memcpy(tmpl + n, ".XXXXXX", 8);
    fd = mkstemp(tmpl);
    if (fd < 0) {
        free(tmpl);
        return NULL;
    }
    close(fd);
    return tmpl;
#endif
}

int32_t jn_zip_password_read_tty(const char *prompt, char *buf, int32_t cap) {
#if defined(__APPLE__) || defined(__linux__)
    char *got;
    size_t n;
    if (buf == NULL || cap < 2) {
        return JN_ZIP_ERR_PARAM;
    }
    got = getpass(prompt ? prompt : "Password: ");
    if (got == NULL) {
        return JN_ZIP_ERR_CANCELLED;
    }
    n = strlen(got);
    if (n == 0) {
        return JN_ZIP_ERR_PARAM;
    }
    if ((int32_t)n >= cap) {
        /* wipe getpass buffer best-effort */
        jn_zip_password_wipe(got, (int32_t)n);
        return JN_ZIP_ERR_PARAM;
    }
    memcpy(buf, got, n + 1);
    jn_zip_password_wipe(got, (int32_t)n);
    return JN_ZIP_OK;
#else
    (void)prompt;
    (void)buf;
    (void)cap;
    return JN_ZIP_ERR_UNSUPPORTED;
#endif
}

/* Reject absolute, drive, UNC, and .. path segments. */
static int path_is_safe_entry(const char *name) {
    const char *p;
    if (name == NULL || name[0] == '\0') {
        return 0;
    }
    if (name[0] == '/' || name[0] == '\\') {
        return 0;
    }
    if (name[0] == '~') {
        return 0;
    }
    if (((name[0] >= 'A' && name[0] <= 'Z') || (name[0] >= 'a' && name[0] <= 'z'))
            && name[1] == ':') {
        return 0;
    }
    if (name[0] == '\\' && name[1] == '\\') {
        return 0;
    }
    p = name;
    while (*p) {
        if (p[0] == '.' && p[1] == '.' && (p[2] == '/' || p[2] == '\\' || p[2] == '\0')) {
            if (p == name || p[-1] == '/' || p[-1] == '\\') {
                return 0;
            }
        }
        p++;
    }
    return 1;
}

static void set_current_name(char *dst, size_t cap, const char *name) {
    if (dst == NULL || cap == 0) {
        return;
    }
    if (name == NULL) {
        dst[0] = 0;
        return;
    }
    strncpy(dst, name, cap - 1);
    dst[cap - 1] = 0;
}

static int32_t check_cancel_w(jn_writer *w) {
    int32_t req;
    if (w == NULL) {
        return JN_ZIP_OK;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    req = w->cancel_req;
    if (req) {
        w->cancel_ack = 1;
    }
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    if (req) {
        set_err(&w->last_err, JN_ZIP_ERR_CANCELLED);
        return JN_ZIP_ERR_CANCELLED;
    }
    return JN_ZIP_OK;
}

static int32_t check_cancel_r(jn_reader *r) {
    int32_t req;
    if (r == NULL) {
        return JN_ZIP_OK;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    req = r->cancel_req;
    if (req) {
        r->cancel_ack = 1;
    }
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
    if (req) {
        set_err(&r->last_err, JN_ZIP_ERR_CANCELLED);
        return JN_ZIP_ERR_CANCELLED;
    }
    return JN_ZIP_OK;
}

static void fill_stats_w(jn_writer *w, jn_zip_stats *out) {
    if (out == NULL || w == NULL) {
        return;
    }
    memset(out, 0, sizeof(*out));
    out->bytes_in = w->bytes_in;
    out->bytes_out = w->bytes_out;
    out->entries_done = w->entries_done;
    out->entries_total = -1;
    out->bytes_total_in = -1;
    out->cancel_requested = w->cancel_req;
    out->cancel_acknowledged = w->cancel_ack;
    out->last_error = w->last_err;
    memcpy(out->current_name, w->current_name, sizeof(out->current_name));
}

static void fill_stats_r(jn_reader *r, jn_zip_stats *out) {
    if (out == NULL || r == NULL) {
        return;
    }
    memset(out, 0, sizeof(*out));
    out->bytes_in = r->bytes_in;
    out->bytes_out = r->bytes_out;
    out->entries_done = r->entries_done;
    out->entries_total = -1;
    out->bytes_total_in = -1;
    out->cancel_requested = r->cancel_req;
    out->cancel_acknowledged = r->cancel_ack;
    out->last_error = r->last_err;
    memcpy(out->current_name, r->current_name, sizeof(out->current_name));
}

/* ---- Writer ---- */

void *jn_zip_writer_create(void) {
    jn_writer *w = (jn_writer *)calloc(1, sizeof(jn_writer));
    if (w == NULL) {
        return NULL;
    }
    w->handle = mz_zip_writer_create();
    if (w->handle == NULL) {
        free(w);
        return NULL;
    }
    w->aes_strength = MZ_AES_STRENGTH_256;
    jn_mutex_init(&w->lock);
    w->lock_inited = 1;
    return w;
}

int32_t jn_zip_writer_open(void *wp, const char *path, int32_t append) {
    jn_writer *w = (jn_writer *)wp;
    int32_t err;
    if (w == NULL || path == NULL || path[0] == '\0') {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (w->open || w->finished) {
        set_err(&w->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    err = mz_zip_writer_open_file(w->handle, path, 0, append ? 1 : 0);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&w->last_err, c);
        return c;
    }
    w->open = 1;
    w->finished = 0;
    w->bytes_in = 0;
    w->bytes_out = 0;
    w->entries_done = 0;
    w->cancel_req = 0;
    w->cancel_ack = 0;
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_set_password(void *wp, const char *password) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (password != NULL && password[0] == '\0') {
        set_err(&w->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM; /* empty must not silently disable encryption */
    }
    wipe_free(&w->password);
    if (password == NULL) {
        mz_zip_writer_set_password(w->handle, NULL);
        mz_zip_writer_set_aes(w->handle, 0);
        set_err(&w->last_err, JN_ZIP_OK);
        return JN_ZIP_OK;
    }
    w->password = dup_str(password);
    if (w->password == NULL) {
        set_err(&w->last_err, JN_ZIP_ERR);
        return JN_ZIP_ERR;
    }
    mz_zip_writer_set_password(w->handle, w->password);
    mz_zip_writer_set_aes(w->handle, 1); /* never ZipCrypto */
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_set_aes_strength(void *wp, int32_t strength) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (strength != JN_ZIP_AES_128 && strength != JN_ZIP_AES_192 && strength != JN_ZIP_AES_256) {
        set_err(&w->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    w->aes_strength = strength;
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_set_aes(void *wp, int32_t enable) {
    /* Kept for ABI: enable must be 1 when password set; 0 only when clearing. */
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (enable == 0 && w->password != NULL) {
        set_err(&w->last_err, JN_ZIP_ERR_UNSUPPORTED);
        return JN_ZIP_ERR_UNSUPPORTED;
    }
    mz_zip_writer_set_aes(w->handle, enable ? 1 : 0);
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_put_next_entry(void *wp, const char *name, int32_t method, int32_t level, int32_t is_dir) {
    jn_writer *w = (jn_writer *)wp;
    mz_zip_file file_info;
    int32_t err;
    if (check_cancel_w(w) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    if (w == NULL || name == NULL || name[0] == '\0') {
        set_err(w ? &w->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (!w->open || w->finished) {
        set_err(&w->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (w->entry_open) {
        set_err(&w->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (method != JN_ZIP_METHOD_STORE && method != JN_ZIP_METHOD_DEFLATE) {
        set_err(&w->last_err, JN_ZIP_ERR_UNSUPPORTED);
        return JN_ZIP_ERR_UNSUPPORTED;
    }
    if (!path_is_safe_entry(name)) {
        set_err(&w->last_err, JN_ZIP_ERR_PATH);
        return JN_ZIP_ERR_PATH;
    }
    free(w->entry_name);
    w->entry_name = dup_str(name);
    if (w->entry_name == NULL) {
        set_err(&w->last_err, JN_ZIP_ERR);
        return JN_ZIP_ERR;
    }
    set_current_name(w->current_name, sizeof(w->current_name), name);
    memset(&file_info, 0, sizeof(file_info));
    file_info.filename = w->entry_name;
    file_info.compression_method = (uint16_t)method;
    file_info.flag = MZ_ZIP_FLAG_UTF8;
    file_info.modified_date = time(NULL);
    if (w->password != NULL) {
        file_info.flag |= MZ_ZIP_FLAG_ENCRYPTED;
        file_info.aes_version = MZ_AES_VERSION;
        file_info.aes_strength = (uint8_t)w->aes_strength;
    }
    if (is_dir) {
        size_t n = strlen(w->entry_name);
        if (n == 0 || w->entry_name[n - 1] != '/') {
            free(w->entry_name);
            w->entry_name = NULL;
            set_err(&w->last_err, JN_ZIP_ERR_PARAM);
            return JN_ZIP_ERR_PARAM;
        }
        file_info.compression_method = MZ_COMPRESS_METHOD_STORE;
    }
    if (level == JN_ZIP_LEVEL_DEFAULT) {
        mz_zip_writer_set_compress_level(w->handle, MZ_COMPRESS_LEVEL_DEFAULT);
    } else {
        mz_zip_writer_set_compress_level(w->handle, level);
    }
    mz_zip_writer_set_compress_method(w->handle, (uint16_t)(is_dir ? MZ_COMPRESS_METHOD_STORE : method));
    if (w->password != NULL) {
        mz_zip_writer_set_password(w->handle, w->password);
        mz_zip_writer_set_aes(w->handle, 1);
    }
    err = mz_zip_writer_entry_open(w->handle, &file_info);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        free(w->entry_name);
        w->entry_name = NULL;
        set_err(&w->last_err, c);
        return c;
    }
    w->entry_open = 1;
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_write(void *wp, const void *buf, int32_t len) {
    jn_writer *w = (jn_writer *)wp;
    int32_t written;
    if (check_cancel_w(w) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    if (w == NULL || !w->open || !w->entry_open) {
        set_err(w ? &w->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (len < 0 || (buf == NULL && len > 0)) {
        set_err(&w->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (len == 0) {
        return 0;
    }
    written = mz_zip_writer_entry_write(w->handle, buf, len);
    if (written < 0) {
        int32_t c = map_err(written);
        set_err(&w->last_err, c);
        return c;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    w->bytes_in += written;
    w->bytes_out += written; /* compressed size unknown until close; track input */
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    return written;
}

int32_t jn_zip_writer_close_entry(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    int32_t err;
    if (w == NULL || !w->open || !w->entry_open) {
        set_err(w ? &w->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    err = mz_zip_writer_entry_close(w->handle);
    w->entry_open = 0;
    free(w->entry_name);
    w->entry_name = NULL;
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&w->last_err, c);
        return c;
    }
    w->entries_done += 1;
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_add_file(void *wp, const char *src_path, const char *entry_name,
        int32_t method, int32_t level) {
    jn_writer *w = (jn_writer *)wp;
    FILE *fp = NULL;
    uint8_t buf[JN_ZIP_BUF];
    size_t n;
    int32_t err;
    if (w == NULL || !w->open || src_path == NULL || entry_name == NULL) {
        set_err(w ? &w->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (check_cancel_w(w) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    if (w->entry_open) {
        set_err(&w->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (method != JN_ZIP_METHOD_STORE && method != JN_ZIP_METHOD_DEFLATE) {
        set_err(&w->last_err, JN_ZIP_ERR_UNSUPPORTED);
        return JN_ZIP_ERR_UNSUPPORTED;
    }
    if (!path_is_safe_entry(entry_name)) {
        set_err(&w->last_err, JN_ZIP_ERR_PATH);
        return JN_ZIP_ERR_PATH;
    }
    /* Refuse to follow source symlink as a silent side channel (store link content path separately later). */
    if (mz_os_is_symlink(src_path) == MZ_OK) {
        set_err(&w->last_err, JN_ZIP_ERR_LINK);
        return JN_ZIP_ERR_LINK;
    }
    fp = fopen(src_path, "rb");
    if (fp == NULL) {
        set_err(&w->last_err, JN_ZIP_ERR_SOURCE);
        return JN_ZIP_ERR_SOURCE;
    }
    err = jn_zip_writer_put_next_entry(w, entry_name, method, level, 0);
    if (err != JN_ZIP_OK) {
        fclose(fp);
        return err;
    }
    while ((n = fread(buf, 1, sizeof(buf), fp)) > 0) {
        if (check_cancel_w(w) != JN_ZIP_OK) {
            jn_zip_writer_close_entry(w);
            fclose(fp);
            return JN_ZIP_ERR_CANCELLED;
        }
        err = jn_zip_writer_write(w, buf, (int32_t)n);
        if (err < 0) {
            jn_zip_writer_close_entry(w);
            fclose(fp);
            return err;
        }
    }
    if (ferror(fp)) {
        jn_zip_writer_close_entry(w);
        fclose(fp);
        set_err(&w->last_err, JN_ZIP_ERR_SOURCE);
        return JN_ZIP_ERR_SOURCE;
    }
    fclose(fp);
    return jn_zip_writer_close_entry(w);
}

int32_t jn_zip_writer_finish(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    int32_t err;
    if (w == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    /* Idempotent: after finish the ZIP on disk is complete (central directory written). */
    if (w->finished) {
        set_err(&w->last_err, JN_ZIP_OK);
        return JN_ZIP_OK;
    }
    if (!w->open) {
        set_err(&w->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (w->entry_open) {
        int32_t c = jn_zip_writer_close_entry(w);
        if (c != JN_ZIP_OK) {
            return c;
        }
    }
    err = mz_zip_writer_close(w->handle);
    w->open = 0;
    w->finished = 1;
    wipe_free(&w->password);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&w->last_err, c);
        return c;
    }
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_writer_close(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        return JN_ZIP_OK;
    }
    /* close = finish if needed; never writes central directory twice */
    if (!w->finished && w->open) {
        return jn_zip_writer_finish(w);
    }
    set_err(&w->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

void jn_zip_writer_destroy(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        return;
    }
    jn_zip_writer_close(w);
    if (w->handle) {
        mz_zip_writer_delete(&w->handle);
    }
    wipe_free(&w->password);
    free(w->entry_name);
    w->entry_name = NULL;
    if (w->lock_inited) {
        jn_mutex_destroy(&w->lock);
        w->lock_inited = 0;
    }
    free(w);
}

void jn_zip_writer_request_cancel(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL) {
        return;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    w->cancel_req = 1;
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
}

int32_t jn_zip_writer_get_stats(void *wp, jn_zip_stats *out) {
    jn_writer *w = (jn_writer *)wp;
    if (w == NULL || out == NULL) {
        return JN_ZIP_ERR_PARAM;
    }
    fill_stats_w(w, out);
    return JN_ZIP_OK;
}

int64_t jn_zip_writer_stat_bytes_in(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    int64_t v;
    if (w == NULL) {
        return 0;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    v = w->bytes_in;
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    return v;
}

int64_t jn_zip_writer_stat_entries_done(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    int64_t v;
    if (w == NULL) {
        return 0;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    v = w->entries_done;
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    return v;
}

int32_t jn_zip_writer_stat_cancel_ack(void *wp) {
    jn_writer *w = (jn_writer *)wp;
    int32_t v;
    if (w == NULL) {
        return 0;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    v = w->cancel_ack;
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    return v;
}

int32_t jn_zip_writer_stat_current_name(void *wp, char *buf, int32_t cap) {
    jn_writer *w = (jn_writer *)wp;
    size_t n;
    if (w == NULL || buf == NULL || cap < 2) {
        return JN_ZIP_ERR_PARAM;
    }
    if (w->lock_inited) {
        jn_mutex_lock(&w->lock);
    }
    n = strlen(w->current_name);
    if ((int32_t)n >= cap) {
        if (w->lock_inited) {
            jn_mutex_unlock(&w->lock);
        }
        return JN_ZIP_ERR_PARAM;
    }
    memcpy(buf, w->current_name, n + 1);
    if (w->lock_inited) {
        jn_mutex_unlock(&w->lock);
    }
    return (int32_t)n;
}

/* ---- Reader ---- */

void *jn_zip_reader_create(void) {
    jn_reader *r = (jn_reader *)calloc(1, sizeof(jn_reader));
    if (r == NULL) {
        return NULL;
    }
    r->handle = mz_zip_reader_create();
    if (r->handle == NULL) {
        free(r);
        return NULL;
    }
    jn_mutex_init(&r->lock);
    r->lock_inited = 1;
    return r;
}

int32_t jn_zip_reader_open(void *rp, const char *path) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL || path == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (r->open) {
        set_err(&r->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    err = mz_zip_reader_open_file(r->handle, path);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    r->open = 1;
    r->bytes_in = 0;
    r->bytes_out = 0;
    r->entries_done = 0;
    r->expand_total = 0;
    r->cancel_req = 0;
    r->cancel_ack = 0;
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_set_password(void *rp, const char *password) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL) {
        set_err(NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (password != NULL && password[0] == '\0') {
        set_err(&r->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    wipe_free(&r->password);
    if (password == NULL) {
        mz_zip_reader_set_password(r->handle, NULL);
        set_err(&r->last_err, JN_ZIP_OK);
        return JN_ZIP_OK;
    }
    r->password = dup_str(password);
    if (r->password == NULL) {
        set_err(&r->last_err, JN_ZIP_ERR);
        return JN_ZIP_ERR;
    }
    mz_zip_reader_set_password(r->handle, r->password);
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_goto_first(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL || !r->open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (r->entry_open) {
        err = jn_zip_reader_close_entry(r);
        if (err != JN_ZIP_OK) {
            return err; /* propagate CRC/auth failure; do not swallow */
        }
    }
    err = mz_zip_reader_goto_first_entry(r->handle);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_goto_next(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL || !r->open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (r->entry_open) {
        err = jn_zip_reader_close_entry(r);
        if (err != JN_ZIP_OK) {
            return err;
        }
    }
    err = mz_zip_reader_goto_next_entry(r->handle);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_locate(void *rp, const char *name, int32_t ignore_case) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL || !r->open || name == NULL) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (r->entry_open) {
        err = jn_zip_reader_close_entry(r);
        if (err != JN_ZIP_OK) {
            return err;
        }
    }
    err = mz_zip_reader_locate_entry(r->handle, name, ignore_case ? 1 : 0);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_entry_info(void *rp, jn_zip_entry_info *out) {
    jn_reader *r = (jn_reader *)rp;
    mz_zip_file *fi = NULL;
    int32_t err;
    if (r == NULL || !r->open || out == NULL) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    memset(out, 0, sizeof(*out));
    err = mz_zip_reader_entry_get_info(r->handle, &fi);
    if (err != MZ_OK || fi == NULL) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    out->name = dup_str(fi->filename ? fi->filename : "");
    out->compressed_size = fi->compressed_size;
    out->uncompressed_size = fi->uncompressed_size;
    out->crc32 = fi->crc;
    out->method = (int32_t)fi->compression_method;
    out->is_dir = mz_zip_reader_entry_is_dir(r->handle) == MZ_OK ? 1 : 0;
    out->encrypted = (fi->flag & MZ_ZIP_FLAG_ENCRYPTED) ? 1 : 0;
    out->aes_strength = fi->aes_version ? (int32_t)fi->aes_strength : 0;
    out->is_symlink = mz_zip_attrib_is_symlink(fi->external_fa, fi->version_madeby) == MZ_OK ? 1 : 0;
    out->modified_unix = (int64_t)fi->modified_date;
    set_current_name(r->current_name, sizeof(r->current_name), out->name);
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_entry_name(void *rp, char *buf, int32_t cap) {
    jn_zip_entry_info info;
    int32_t err;
    size_t n;
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL || buf == NULL || cap < 2) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    memset(&info, 0, sizeof(info));
    err = jn_zip_reader_entry_info(r, &info);
    if (err != JN_ZIP_OK) {
        return err;
    }
    n = strlen(info.name ? info.name : "");
    if ((int32_t)n >= cap) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    memcpy(buf, info.name, n + 1);
    jn_zip_entry_info_free(&info);
    return (int32_t)n;
}

int64_t jn_zip_reader_entry_uncompressed_size(void *rp) {
    jn_zip_entry_info info;
    int64_t v = -1;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.uncompressed_size;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int64_t jn_zip_reader_entry_compressed_size(void *rp) {
    jn_zip_entry_info info;
    int64_t v = -1;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.compressed_size;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

uint32_t jn_zip_reader_entry_crc32(void *rp) {
    jn_zip_entry_info info;
    uint32_t v = 0;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.crc32;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int32_t jn_zip_reader_entry_method(void *rp) {
    jn_zip_entry_info info;
    int32_t v = -1;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.method;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int32_t jn_zip_reader_entry_is_dir(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL || !r->open) {
        return 0;
    }
    return mz_zip_reader_entry_is_dir(r->handle) == MZ_OK ? 1 : 0;
}

int32_t jn_zip_reader_entry_encrypted(void *rp) {
    jn_zip_entry_info info;
    int32_t v = 0;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.encrypted;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int32_t jn_zip_reader_entry_aes_strength(void *rp) {
    jn_zip_entry_info info;
    int32_t v = 0;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.aes_strength;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int32_t jn_zip_reader_entry_is_symlink(void *rp) {
    jn_zip_entry_info info;
    int32_t v = 0;
    if (jn_zip_reader_entry_info(rp, &info) == JN_ZIP_OK) {
        v = info.is_symlink;
        jn_zip_entry_info_free(&info);
    }
    return v;
}

int32_t jn_zip_reader_entry_needs_password(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL) {
        return 0;
    }
    if (jn_zip_reader_entry_encrypted(r) == 0) {
        return 0;
    }
    return r->password == NULL ? 1 : 0;
}

int32_t jn_zip_reader_open_entry(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (check_cancel_r(r) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    if (r == NULL || !r->open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (r->entry_open) {
        set_err(&r->last_err, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (jn_zip_reader_entry_needs_password(r)) {
        set_err(&r->last_err, JN_ZIP_ERR_NEED_PASSWORD);
        return JN_ZIP_ERR_NEED_PASSWORD;
    }
    if (jn_zip_reader_entry_is_symlink(r)) {
        set_err(&r->last_err, JN_ZIP_ERR_LINK);
        return JN_ZIP_ERR_LINK;
    }
    err = mz_zip_reader_entry_open(r->handle);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    r->entry_open = 1;
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_read(void *rp, void *buf, int32_t len) {
    jn_reader *r = (jn_reader *)rp;
    int32_t n;
    if (check_cancel_r(r) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    if (r == NULL || !r->open || !r->entry_open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    if (len < 0 || (buf == NULL && len > 0)) {
        set_err(&r->last_err, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (len == 0) {
        return 0;
    }
    n = mz_zip_reader_entry_read(r->handle, buf, len);
    if (n < 0) {
        int32_t c = map_err(n);
        set_err(&r->last_err, c);
        return c;
    }
    r->bytes_out += n;
    return n;
}

int32_t jn_zip_reader_close_entry(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL || !r->open || !r->entry_open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    err = mz_zip_reader_entry_close(r->handle);
    r->entry_open = 0;
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    r->entries_done += 1;
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

/** Ensure no symlink exists on any prefix of path (absolute path string). */
static int32_t path_has_symlink_component(const char *path) {
    char tmp[4096];
    size_t n;
    size_t i;
    if (path == NULL) {
        return JN_ZIP_ERR_PARAM;
    }
    n = strlen(path);
    if (n >= sizeof(tmp)) {
        return JN_ZIP_ERR_PATH;
    }
    memcpy(tmp, path, n + 1);
    for (i = 1; i <= n; i++) {
        if (tmp[i] == '/' || tmp[i] == '\0') {
            char saved = tmp[i];
            tmp[i] = '\0';
            if (tmp[0] != '\0' && mz_os_is_symlink(tmp) == MZ_OK) {
                return JN_ZIP_ERR_LINK;
            }
            tmp[i] = saved;
        }
    }
    return JN_ZIP_OK;
}

static int32_t ensure_parent_dirs(char *path) {
    char *slash = strrchr(path, '/');
    int32_t err;
    if (slash == NULL) {
        return JN_ZIP_OK;
    }
    *slash = '\0';
    err = mz_dir_make(path);
    *slash = '/';
    if (err != MZ_OK && err != MZ_EXIST_ERROR) {
        return map_err(err);
    }
    return JN_ZIP_OK;
}

/** lstat helper — never follow final component symlink for existence checks. */
static int path_lstat(const char *path, struct stat *st) {
#if defined(_WIN32)
    return _stat(path, (struct _stat *)st);
#else
    return lstat(path, st);
#endif
}

/**
 * Publish tmp → final without deleting the old file first.
 * FAIL: exclusive rename (RENAME_EXCL / link) — race-safe no-clobber.
 * REPLACE: atomic rename over existing regular file (keeps old until rename succeeds).
 * SKIP: leave existing, unlink tmp.
 * RENAME: pick unused sibling name with exclusive create.
 */
static int32_t publish_rename(const char *tmp, const char *final_path, int32_t overwrite, char *alt, size_t alt_cap) {
    struct stat st;
    const char *dest = final_path;
    int exists;

    if (tmp == NULL || final_path == NULL) {
        return JN_ZIP_ERR_PARAM;
    }
    /* Refuse if final path component is a symlink (would follow on replace). */
    if (path_lstat(final_path, &st) == 0) {
        exists = 1;
#if !defined(_WIN32)
        if (S_ISLNK(st.st_mode)) {
            return JN_ZIP_ERR_LINK;
        }
#endif
    } else {
        exists = 0;
    }

    if (exists) {
        if (overwrite == JN_ZIP_OVERWRITE_FAIL) {
            return JN_ZIP_ERR_EXISTS;
        }
        if (overwrite == JN_ZIP_OVERWRITE_SKIP) {
            unlink(tmp);
            return JN_ZIP_OK;
        }
        if (overwrite == JN_ZIP_OVERWRITE_RENAME) {
            int n = 1;
            for (;;) {
                int fd;
                snprintf(alt, alt_cap, "%s.%d", final_path, n);
                if (path_lstat(alt, &st) != 0) {
#if defined(_WIN32)
                    dest = alt;
                    break;
#else
                    fd = open(alt, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
                    if (fd >= 0) {
                        close(fd);
                        unlink(alt); /* reserve name then rename onto it */
                        dest = alt;
                        break;
                    }
                    if (errno != EEXIST) {
                        return JN_ZIP_ERR_TARGET;
                    }
#endif
                }
                n++;
                if (n > 10000) {
                    return JN_ZIP_ERR_EXISTS;
                }
            }
            /* fall through to exclusive/atomic rename onto dest */
            overwrite = JN_ZIP_OVERWRITE_FAIL; /* treat as no-clobber onto new name */
            exists = 0;
        } else if (overwrite != JN_ZIP_OVERWRITE_REPLACE) {
            return JN_ZIP_ERR_PARAM;
        }
    }

    if (overwrite == JN_ZIP_OVERWRITE_REPLACE && exists) {
        /* Atomic replace — do NOT unlink old file first. */
        if (rename(tmp, dest) != 0) {
            if (errno == ENOSPC) {
                return JN_ZIP_ERR_SPACE;
            }
            return JN_ZIP_ERR_TARGET;
        }
        return JN_ZIP_OK;
    }

    /* No-clobber publish */
#if defined(__APPLE__)
    if (renamex_np(tmp, dest, RENAME_EXCL) != 0) {
        if (errno == EEXIST) {
            return JN_ZIP_ERR_EXISTS;
        }
        if (errno == ENOSPC) {
            return JN_ZIP_ERR_SPACE;
        }
        return JN_ZIP_ERR_TARGET;
    }
    return JN_ZIP_OK;
#elif defined(_WIN32)
    if (!MoveFileExA(tmp, dest, 0)) { /* fail if exists */
        if (GetLastError() == ERROR_ALREADY_EXISTS) {
            return JN_ZIP_ERR_EXISTS;
        }
        return JN_ZIP_ERR_TARGET;
    }
    return JN_ZIP_OK;
#else
    /* link+unlink is no-clobber when dest absent; if race creates dest, link fails */
    if (link(tmp, dest) != 0) {
        if (errno == EEXIST) {
            return JN_ZIP_ERR_EXISTS;
        }
        if (errno == ENOSPC) {
            return JN_ZIP_ERR_SPACE;
        }
        /* Some FS lack link — fall back to rename if dest still absent */
        if (path_lstat(dest, &st) != 0) {
            if (rename(tmp, dest) != 0) {
                return JN_ZIP_ERR_TARGET;
            }
            return JN_ZIP_OK;
        }
        return JN_ZIP_ERR_EXISTS;
    }
    unlink(tmp);
    return JN_ZIP_OK;
#endif
}

int32_t jn_zip_reader_extract_entry_ex(void *rp, const char *dest_dir, int32_t overwrite,
        int64_t max_entry_uncompressed, int64_t max_total_uncompressed, int64_t max_entries) {
    jn_reader *r = (jn_reader *)rp;
    jn_zip_entry_info info;
    char final_path[4096];
    char tmp_path[4224];
    char alt_path[4224];
    char staging[4096];
    uint8_t buf[JN_ZIP_BUF];
    FILE *fp = NULL;
    int32_t err;
    int64_t written = 0;
    size_t dl;

    if (r == NULL || !r->open || dest_dir == NULL) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_PARAM);
        return JN_ZIP_ERR_PARAM;
    }
    if (check_cancel_r(r) != JN_ZIP_OK) {
        return JN_ZIP_ERR_CANCELLED;
    }
    memset(&info, 0, sizeof(info));
    err = jn_zip_reader_entry_info(r, &info);
    if (err != JN_ZIP_OK) {
        return err;
    }
    if (!path_is_safe_entry(info.name)) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_PATH);
        return JN_ZIP_ERR_PATH;
    }
    if (info.is_symlink) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_LINK);
        return JN_ZIP_ERR_LINK;
    }
    if (max_entries > 0 && r->entries_done >= max_entries) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_LIMIT);
        return JN_ZIP_ERR_LIMIT;
    }
    if (max_entry_uncompressed > 0 && info.uncompressed_size > 0
            && info.uncompressed_size > max_entry_uncompressed) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_LIMIT);
        return JN_ZIP_ERR_LIMIT;
    }
    if (max_total_uncompressed > 0 && info.uncompressed_size > 0
            && r->expand_total + info.uncompressed_size > max_total_uncompressed) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_LIMIT);
        return JN_ZIP_ERR_LIMIT;
    }

    dl = strlen(dest_dir);
    if (dl + 1 + strlen(info.name) + 1 >= sizeof(final_path)) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_PATH);
        return JN_ZIP_ERR_PATH;
    }
    snprintf(final_path, sizeof(final_path), "%s/%s", dest_dir, info.name);

    if (info.is_dir) {
        err = path_has_symlink_component(final_path);
        if (err != JN_ZIP_OK) {
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, err);
            return err;
        }
        err = mz_dir_make(final_path);
        jn_zip_entry_info_free(&info);
        if (err != MZ_OK && err != MZ_EXIST_ERROR) {
            int32_t c = map_err(err);
            set_err(&r->last_err, c);
            return c;
        }
        r->entries_done += 1;
        set_err(&r->last_err, JN_ZIP_OK);
        return JN_ZIP_OK;
    }

    err = path_has_symlink_component(final_path);
    if (err != JN_ZIP_OK) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, err);
        return err;
    }
    err = path_has_symlink_component(dest_dir);
    if (err != JN_ZIP_OK) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, err);
        return err;
    }
    err = ensure_parent_dirs(final_path);
    if (err != JN_ZIP_OK) {
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, err);
        return err;
    }

    /*
     * Unique staging directory via mkdtemp — never a fixed predictable path.
     * Reject if parent of staging is a symlink component (checked above on dest_dir).
     */
#if defined(_WIN32)
    {
        char *uniq = jn_zip_make_unique_temp_path(dest_dir);
        char *slash;
        if (uniq == NULL) {
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_TARGET);
            return JN_ZIP_ERR_TARGET;
        }
        /* GetTempFileName created a file; convert to directory staging */
        DeleteFileA(uniq);
        if (!CreateDirectoryA(uniq, NULL)) {
            free(uniq);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_TARGET);
            return JN_ZIP_ERR_TARGET;
        }
        snprintf(staging, sizeof(staging), "%s", uniq);
        free(uniq);
        snprintf(tmp_path, sizeof(tmp_path), "%s\\data.XXXXXX", staging);
        /* Windows: exclusive create via GetTempFileName inside staging */
        {
            char tname[MAX_PATH];
            if (GetTempFileNameA(staging, "ex", 0, tname) == 0) {
                RemoveDirectoryA(staging);
                jn_zip_entry_info_free(&info);
                set_err(&r->last_err, JN_ZIP_ERR_TARGET);
                return JN_ZIP_ERR_TARGET;
            }
            snprintf(tmp_path, sizeof(tmp_path), "%s", tname);
        }
        (void)slash;
    }
#else
    {
        int tfd;
        struct stat stg;
        snprintf(staging, sizeof(staging), "%s/.jn-ex-XXXXXX", dest_dir);
        if (mkdtemp(staging) == NULL) {
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_TARGET);
            return JN_ZIP_ERR_TARGET;
        }
        if (lstat(staging, &stg) != 0 || !S_ISDIR(stg.st_mode) || S_ISLNK(stg.st_mode)) {
            rmdir(staging);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_LINK);
            return JN_ZIP_ERR_LINK;
        }
        snprintf(tmp_path, sizeof(tmp_path), "%s/data.XXXXXX", staging);
        tfd = mkstemp(tmp_path);
        if (tfd < 0) {
            rmdir(staging);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_TARGET);
            return JN_ZIP_ERR_TARGET;
        }
        /* Re-open with O_NOFOLLOW to refuse symlink substitution after mkstemp */
        close(tfd);
        tfd = open(tmp_path, O_WRONLY | O_NOFOLLOW | O_CLOEXEC);
        if (tfd < 0) {
            unlink(tmp_path);
            rmdir(staging);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_LINK);
            return JN_ZIP_ERR_LINK;
        }
        fp = fdopen(tfd, "wb");
        if (fp == NULL) {
            close(tfd);
            unlink(tmp_path);
            rmdir(staging);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_TARGET);
            return JN_ZIP_ERR_TARGET;
        }
    }
#endif

    err = jn_zip_reader_open_entry(r);
    if (err != JN_ZIP_OK) {
#if defined(_WIN32)
        DeleteFileA(tmp_path);
        RemoveDirectoryA(staging);
#else
        if (fp) {
            fclose(fp);
            fp = NULL;
        }
        unlink(tmp_path);
        rmdir(staging);
#endif
        jn_zip_entry_info_free(&info);
        return err;
    }

#if defined(_WIN32)
    fp = fopen(tmp_path, "wb");
    if (fp == NULL) {
        jn_zip_reader_close_entry(r);
        DeleteFileA(tmp_path);
        RemoveDirectoryA(staging);
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_TARGET);
        return JN_ZIP_ERR_TARGET;
    }
#endif
    /* POSIX: fp already opened via fdopen above */

#define JN_EXTRACT_CLEANUP() do { \
        if (fp) { fclose(fp); fp = NULL; } \
        unlink(tmp_path); \
        rmdir(staging); \
    } while (0)

    for (;;) {
        int32_t n;
        if (check_cancel_r(r) != JN_ZIP_OK) {
            JN_EXTRACT_CLEANUP();
            jn_zip_reader_close_entry(r);
            jn_zip_entry_info_free(&info);
            return JN_ZIP_ERR_CANCELLED;
        }
        n = jn_zip_reader_read(r, buf, (int32_t)sizeof(buf));
        if (n == 0) {
            break;
        }
        if (n < 0) {
            JN_EXTRACT_CLEANUP();
            jn_zip_reader_close_entry(r);
            jn_zip_entry_info_free(&info);
            return n;
        }
        written += n;
        /* Update mid-entry progress under lock */
        if (r->lock_inited) {
            jn_mutex_lock(&r->lock);
        }
        r->bytes_out = r->expand_total + written;
        if (r->lock_inited) {
            jn_mutex_unlock(&r->lock);
        }
        if (max_entry_uncompressed > 0 && written > max_entry_uncompressed) {
            JN_EXTRACT_CLEANUP();
            jn_zip_reader_close_entry(r);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_LIMIT);
            return JN_ZIP_ERR_LIMIT;
        }
        if (max_total_uncompressed > 0
                && r->expand_total + written > max_total_uncompressed) {
            JN_EXTRACT_CLEANUP();
            jn_zip_reader_close_entry(r);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, JN_ZIP_ERR_LIMIT);
            return JN_ZIP_ERR_LIMIT;
        }
        if (fwrite(buf, 1, (size_t)n, fp) != (size_t)n) {
            int32_t c = (errno == ENOSPC) ? JN_ZIP_ERR_SPACE : JN_ZIP_ERR_TARGET;
            JN_EXTRACT_CLEANUP();
            jn_zip_reader_close_entry(r);
            jn_zip_entry_info_free(&info);
            set_err(&r->last_err, c);
            return c;
        }
    }
    if (fflush(fp) != 0) {
        JN_EXTRACT_CLEANUP();
        jn_zip_reader_close_entry(r);
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, JN_ZIP_ERR_TARGET);
        return JN_ZIP_ERR_TARGET;
    }
    fclose(fp);
    fp = NULL;

    /* Auth/CRC finalized here — only then publish */
    err = jn_zip_reader_close_entry(r);
    if (err != JN_ZIP_OK) {
        unlink(tmp_path);
        rmdir(staging);
        jn_zip_entry_info_free(&info);
        return err;
    }

    err = publish_rename(tmp_path, final_path, overwrite, alt_path, sizeof(alt_path));
    if (err != JN_ZIP_OK) {
        unlink(tmp_path);
        rmdir(staging);
        jn_zip_entry_info_free(&info);
        set_err(&r->last_err, err);
        return err;
    }
    rmdir(staging); /* temp file already renamed away */
    r->expand_total += written;
    jn_zip_entry_info_free(&info);
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
#undef JN_EXTRACT_CLEANUP
}

int32_t jn_zip_reader_extract_entry(void *rp, const char *dest_dir) {
    return jn_zip_reader_extract_entry_ex(rp, dest_dir, JN_ZIP_OVERWRITE_FAIL, 0, 0, 0);
}

int32_t jn_zip_reader_test_all(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    uint8_t buf[JN_ZIP_BUF];
    if (r == NULL || !r->open) {
        set_err(r ? &r->last_err : NULL, JN_ZIP_ERR_STATE);
        return JN_ZIP_ERR_STATE;
    }
    err = jn_zip_reader_goto_first(r);
    if (err == JN_ZIP_ERR_EOF) {
        return JN_ZIP_OK;
    }
    if (err != JN_ZIP_OK) {
        return err;
    }
    for (;;) {
        jn_zip_entry_info info;
        if (check_cancel_r(r) != JN_ZIP_OK) {
            return JN_ZIP_ERR_CANCELLED;
        }
        memset(&info, 0, sizeof(info));
        err = jn_zip_reader_entry_info(r, &info);
        if (err != JN_ZIP_OK) {
            return err;
        }
        set_current_name(r->current_name, sizeof(r->current_name), info.name);
        if (!info.is_dir) {
            if (info.is_symlink) {
                jn_zip_entry_info_free(&info);
                set_err(&r->last_err, JN_ZIP_ERR_LINK);
                return JN_ZIP_ERR_LINK;
            }
            if (jn_zip_reader_entry_needs_password(r)) {
                jn_zip_entry_info_free(&info);
                set_err(&r->last_err, JN_ZIP_ERR_NEED_PASSWORD);
                return JN_ZIP_ERR_NEED_PASSWORD;
            }
            err = jn_zip_reader_open_entry(r);
            if (err != JN_ZIP_OK) {
                jn_zip_entry_info_free(&info);
                return err;
            }
            for (;;) {
                int32_t n = jn_zip_reader_read(r, buf, (int32_t)sizeof(buf));
                if (n == 0) {
                    break;
                }
                if (n < 0) {
                    jn_zip_reader_close_entry(r);
                    jn_zip_entry_info_free(&info);
                    return n;
                }
            }
            err = jn_zip_reader_close_entry(r);
            if (err != JN_ZIP_OK) {
                jn_zip_entry_info_free(&info);
                return err;
            }
        } else {
            r->entries_done += 1;
        }
        jn_zip_entry_info_free(&info);
        err = jn_zip_reader_goto_next(r);
        if (err == JN_ZIP_ERR_EOF) {
            break;
        }
        if (err != JN_ZIP_OK) {
            return err;
        }
    }
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

int32_t jn_zip_reader_close(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t err;
    if (r == NULL) {
        return JN_ZIP_OK;
    }
    if (!r->open) {
        return JN_ZIP_OK;
    }
    if (r->entry_open) {
        jn_zip_reader_close_entry(r);
    }
    err = mz_zip_reader_close(r->handle);
    r->open = 0;
    wipe_free(&r->password);
    if (err != MZ_OK) {
        int32_t c = map_err(err);
        set_err(&r->last_err, c);
        return c;
    }
    set_err(&r->last_err, JN_ZIP_OK);
    return JN_ZIP_OK;
}

void jn_zip_reader_destroy(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL) {
        return;
    }
    jn_zip_reader_close(r);
    if (r->handle) {
        mz_zip_reader_delete(&r->handle);
    }
    wipe_free(&r->password);
    free(r->owned_stage_dir);
    r->owned_stage_dir = NULL;
    if (r->lock_inited) {
        jn_mutex_destroy(&r->lock);
        r->lock_inited = 0;
    }
    free(r);
}

void jn_zip_reader_request_cancel(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL) {
        return;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    r->cancel_req = 1;
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
}

int32_t jn_zip_reader_get_stats(void *rp, jn_zip_stats *out) {
    jn_reader *r = (jn_reader *)rp;
    if (r == NULL || out == NULL) {
        return JN_ZIP_ERR_PARAM;
    }
    fill_stats_r(r, out);
    return JN_ZIP_OK;
}

int64_t jn_zip_reader_stat_bytes_out(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int64_t v;
    if (r == NULL) {
        return 0;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    v = r->bytes_out;
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
    return v;
}

int64_t jn_zip_reader_stat_entries_done(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int64_t v;
    if (r == NULL) {
        return 0;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    v = r->entries_done;
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
    return v;
}

int32_t jn_zip_reader_stat_cancel_ack(void *rp) {
    jn_reader *r = (jn_reader *)rp;
    int32_t v;
    if (r == NULL) {
        return 0;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    v = r->cancel_ack;
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
    return v;
}

int32_t jn_zip_reader_stat_current_name(void *rp, char *buf, int32_t cap) {
    jn_reader *r = (jn_reader *)rp;
    size_t n;
    if (r == NULL || buf == NULL || cap < 2) {
        return JN_ZIP_ERR_PARAM;
    }
    if (r->lock_inited) {
        jn_mutex_lock(&r->lock);
    }
    n = strlen(r->current_name);
    if ((int32_t)n >= cap) {
        if (r->lock_inited) {
            jn_mutex_unlock(&r->lock);
        }
        return JN_ZIP_ERR_PARAM;
    }
    memcpy(buf, r->current_name, n + 1);
    if (r->lock_inited) {
        jn_mutex_unlock(&r->lock);
    }
    return (int32_t)n;
}
