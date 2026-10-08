/* Windows filesystem ABI for JNative Explorer — UTF-16 Win32 APIs, UTF-8 at boundary. */
#include "jnative_fs.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#pragma comment(lib, "Shell32.lib")
#pragma comment(lib, "Ole32.lib")

static wchar_t *utf8_to_wide(const char *utf8) {
    if (utf8 == NULL) {
        return NULL;
    }
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0);
    if (n <= 0) {
        return NULL;
    }
    wchar_t *w = (wchar_t *)malloc((size_t)n * sizeof(wchar_t));
    if (w == NULL) {
        return NULL;
    }
    if (MultiByteToWideChar(CP_UTF8, 0, utf8, -1, w, n) == 0) {
        free(w);
        return NULL;
    }
    return w;
}

static char *wide_to_utf8(const wchar_t *w) {
    if (w == NULL) {
        return NULL;
    }
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    if (n <= 0) {
        return NULL;
    }
    char *u = (char *)malloc((size_t)n);
    if (u == NULL) {
        return NULL;
    }
    if (WideCharToMultiByte(CP_UTF8, 0, w, -1, u, n, NULL, NULL) == 0) {
        free(u);
        return NULL;
    }
    return u;
}

static int64_t filetime_to_ms(const FILETIME *ft) {
    ULARGE_INTEGER uli;
    uli.LowPart = ft->dwLowDateTime;
    uli.HighPart = ft->dwHighDateTime;
    /* FILETIME is 100-ns since 1601; Unix epoch offset */
    const uint64_t EPOCH_DIFF = 116444736000000000ULL;
    if (uli.QuadPart < EPOCH_DIFF) {
        return 0;
    }
    return (int64_t)((uli.QuadPart - EPOCH_DIFF) / 10000ULL);
}

int32_t jnative_fs_exists(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attr = GetFileAttributesW(w);
    free(w);
    return attr != INVALID_FILE_ATTRIBUTES ? 1 : 0;
}

int32_t jnative_fs_is_directory(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attr = GetFileAttributesW(w);
    free(w);
    if (attr == INVALID_FILE_ATTRIBUTES) {
        return 0;
    }
    return (attr & FILE_ATTRIBUTE_DIRECTORY) ? 1 : 0;
}

int32_t jnative_fs_is_file(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attr = GetFileAttributesW(w);
    free(w);
    if (attr == INVALID_FILE_ATTRIBUTES) {
        return 0;
    }
    return (attr & FILE_ATTRIBUTE_DIRECTORY) ? 0 : 1;
}

int32_t jnative_fs_is_hidden(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attr = GetFileAttributesW(w);
    free(w);
    if (attr == INVALID_FILE_ATTRIBUTES) {
        return 0;
    }
    return (attr & FILE_ATTRIBUTE_HIDDEN) ? 1 : 0;
}

static int mkdir_p_w(const wchar_t *path) {
    if (path == NULL || path[0] == 0) {
        return -1;
    }
    size_t len = wcslen(path);
    wchar_t *tmp = (wchar_t *)malloc((len + 1) * sizeof(wchar_t));
    if (tmp == NULL) {
        return -1;
    }
    memcpy(tmp, path, (len + 1) * sizeof(wchar_t));
    for (wchar_t *p = tmp + 1; *p; p++) {
        if (*p == L'\\' || *p == L'/') {
            wchar_t save = *p;
            *p = 0;
            if (wcslen(tmp) > 0 && !(tmp[1] == L':' && tmp[2] == 0)) {
                CreateDirectoryW(tmp, NULL);
            }
            *p = save;
        }
    }
    CreateDirectoryW(tmp, NULL);
    free(tmp);
    return 0;
}

int32_t jnative_fs_create_directories(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    int rc = mkdir_p_w(w) == 0 ? 1 : 0;
    free(w);
    return rc;
}

int32_t jnative_fs_delete(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attr = GetFileAttributesW(w);
    int ok = 0;
    if (attr != INVALID_FILE_ATTRIBUTES) {
        if (attr & FILE_ATTRIBUTE_DIRECTORY) {
            ok = RemoveDirectoryW(w) ? 1 : 0;
        } else {
            ok = DeleteFileW(w) ? 1 : 0;
        }
    }
    free(w);
    return ok;
}

int32_t jnative_fs_rename(const char *from, const char *to) {
    wchar_t *a = utf8_to_wide(from);
    wchar_t *b = utf8_to_wide(to);
    if (a == NULL || b == NULL) {
        free(a);
        free(b);
        return 0;
    }
    int ok = MoveFileExW(a, b, MOVEFILE_REPLACE_EXISTING | MOVEFILE_COPY_ALLOWED) ? 1 : 0;
    free(a);
    free(b);
    return ok;
}

int32_t jnative_fs_copy_file(const char *from, const char *to) {
    wchar_t *a = utf8_to_wide(from);
    wchar_t *b = utf8_to_wide(to);
    if (a == NULL || b == NULL) {
        free(a);
        free(b);
        return 0;
    }
    int ok = CopyFileW(a, b, FALSE) ? 1 : 0;
    free(a);
    free(b);
    return ok;
}

int64_t jnative_fs_file_size(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return -1;
    }
    WIN32_FILE_ATTRIBUTE_DATA data;
    int ok = GetFileAttributesExW(w, GetFileExInfoStandard, &data);
    free(w);
    if (!ok) {
        return -1;
    }
    ULARGE_INTEGER uli;
    uli.LowPart = data.nFileSizeLow;
    uli.HighPart = data.nFileSizeHigh;
    return (int64_t)uli.QuadPart;
}

int64_t jnative_fs_mtime_ms(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    WIN32_FILE_ATTRIBUTE_DATA data;
    int ok = GetFileAttributesExW(w, GetFileExInfoStandard, &data);
    free(w);
    if (!ok) {
        return 0;
    }
    return filetime_to_ms(&data.ftLastWriteTime);
}

char *jnative_fs_list(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return NULL;
    }
    wchar_t pattern[MAX_PATH + 8];
    _snwprintf(pattern, MAX_PATH + 8, L"%s\\*", w);
    free(w);
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    size_t cap = 256;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    if (buf == NULL) {
        FindClose(h);
        return NULL;
    }
    buf[0] = 0;
    do {
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) {
            continue;
        }
        char *name = wide_to_utf8(fd.cFileName);
        if (name == NULL) {
            continue;
        }
        size_t nlen = strlen(name);
        if (len + nlen + 2 > cap) {
            cap = (len + nlen + 2) * 2;
            char *nb = (char *)realloc(buf, cap);
            if (nb == NULL) {
                free(name);
                free(buf);
                FindClose(h);
                return NULL;
            }
            buf = nb;
        }
        memcpy(buf + len, name, nlen);
        len += nlen;
        buf[len++] = '\n';
        buf[len] = 0;
        free(name);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return buf;
}

char *jnative_fs_list_info(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return NULL;
    }
    wchar_t pattern[MAX_PATH + 8];
    _snwprintf(pattern, MAX_PATH + 8, L"%s\\*", w);
    free(w);
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    size_t cap = 512;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    if (buf == NULL) {
        FindClose(h);
        return NULL;
    }
    buf[0] = 0;
    do {
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) {
            continue;
        }
        char *name = wide_to_utf8(fd.cFileName);
        if (name == NULL) {
            continue;
        }
        int is_dir = (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ? 1 : 0;
        int hidden = (fd.dwFileAttributes & FILE_ATTRIBUTE_HIDDEN) ? 1 : 0;
        ULARGE_INTEGER sz;
        sz.LowPart = fd.nFileSizeLow;
        sz.HighPart = fd.nFileSizeHigh;
        int64_t mtime = filetime_to_ms(&fd.ftLastWriteTime);
        char line[1024];
        int n = _snprintf(line, sizeof(line), "%s\t%d\t%lld\t%lld\t%d\n",
                name, is_dir, (long long)sz.QuadPart, (long long)mtime, hidden);
        free(name);
        if (n < 0) {
            continue;
        }
        if (len + (size_t)n + 1 > cap) {
            cap = (len + (size_t)n + 1) * 2;
            char *nb = (char *)realloc(buf, cap);
            if (nb == NULL) {
                free(buf);
                FindClose(h);
                return NULL;
            }
            buf = nb;
        }
        memcpy(buf + len, line, (size_t)n);
        len += (size_t)n;
        buf[len] = 0;
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return buf;
}

char *jnative_fs_roots(void) {
    DWORD mask = GetLogicalDrives();
    char tmp[128];
    size_t len = 0;
    tmp[0] = 0;
    for (int i = 0; i < 26; i++) {
        if (mask & (1u << i)) {
            char letter = (char)('A' + i);
            int n = _snprintf(tmp + len, sizeof(tmp) - len, "%c:\\\n", letter);
            if (n > 0) {
                len += (size_t)n;
            }
        }
    }
    return strdup(tmp);
}

char *jnative_fs_known_folder(const char *id) {
    KNOWNFOLDERID kfid = FOLDERID_Profile;
    if (id != NULL) {
        if (strcmp(id, "desktop") == 0) {
            kfid = FOLDERID_Desktop;
        } else if (strcmp(id, "documents") == 0) {
            kfid = FOLDERID_Documents;
        } else if (strcmp(id, "downloads") == 0) {
            kfid = FOLDERID_Downloads;
        } else if (strcmp(id, "pictures") == 0) {
            kfid = FOLDERID_Pictures;
        } else {
            kfid = FOLDERID_Profile;
        }
    }
    PWSTR wpath = NULL;
    HRESULT hr = SHGetKnownFolderPath(&kfid, 0, NULL, &wpath);
    if (FAILED(hr) || wpath == NULL) {
        char *home = getenv("USERPROFILE");
        return strdup(home != NULL ? home : "C:\\");
    }
    char *u = wide_to_utf8(wpath);
    CoTaskMemFree(wpath);
    return u;
}

int32_t jnative_fs_open(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    HINSTANCE hi = ShellExecuteW(NULL, L"open", w, NULL, NULL, SW_SHOWNORMAL);
    free(w);
    return ((INT_PTR)hi > 32) ? 1 : 0;
}

int32_t jnative_fs_trash(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    size_t len = wcslen(w);
    wchar_t *doubleNull = (wchar_t *)malloc((len + 2) * sizeof(wchar_t));
    if (doubleNull == NULL) {
        free(w);
        return 0;
    }
    memcpy(doubleNull, w, (len + 1) * sizeof(wchar_t));
    doubleNull[len + 1] = 0;
    free(w);
    SHFILEOPSTRUCTW op;
    memset(&op, 0, sizeof(op));
    op.wFunc = FO_DELETE;
    op.pFrom = doubleNull;
    op.fFlags = FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT | FOF_NOERRORUI;
    int rc = SHFileOperationW(&op);
    free(doubleNull);
    return rc == 0 ? 1 : 0;
}

void jnative_fs_free(char *p) {
    free(p);
}

char *jnative_fs_config_dir(const char *appName) {
    const char *name = (appName != NULL && appName[0] != '\0') ? appName : "JNativeStudio";
    char *base = jnative_fs_known_folder("home");
    if (base == NULL) {
        return strdup(".");
    }
    char path[MAX_PATH];
    _snprintf(path, sizeof(path), "%s\\AppData\\Roaming\\%s", base, name);
    free(base);
    jnative_fs_create_directories(path);
    return strdup(path);
}

/* ReadDirectoryChangesW watch handles */
typedef struct {
    HANDLE dir;
    OVERLAPPED ov;
    char buffer[8192];
    int armed;
    int pending;
} JnWinWatch;

#define JN_WIN_WATCH_MAX 64
static JnWinWatch g_ww[JN_WIN_WATCH_MAX];

int64_t jnative_fs_watch_open(const char *path) {
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    int slot = -1;
    for (int i = 0; i < JN_WIN_WATCH_MAX; i++) {
        if (!g_ww[i].armed) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        free(w);
        return 0;
    }
    HANDLE h = CreateFileW(w, FILE_LIST_DIRECTORY,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            NULL, OPEN_EXISTING,
            FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED, NULL);
    free(w);
    if (h == INVALID_HANDLE_VALUE) {
        return 0;
    }
    JnWinWatch *ww = &g_ww[slot];
    memset(ww, 0, sizeof(*ww));
    ww->dir = h;
    ww->armed = 1;
    ww->ov.hEvent = CreateEventW(NULL, TRUE, FALSE, NULL);
    DWORD br = 0;
    ReadDirectoryChangesW(ww->dir, ww->buffer, sizeof(ww->buffer), FALSE,
            FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME
                    | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE,
            &br, &ww->ov, NULL);
    ww->pending = 1;
    return (int64_t)(slot + 1);
}

int32_t jnative_fs_watch_poll(int64_t handle) {
    if (handle <= 0 || handle > JN_WIN_WATCH_MAX) {
        return 0;
    }
    JnWinWatch *ww = &g_ww[(int)handle - 1];
    if (!ww->armed) {
        return 0;
    }
    DWORD br = 0;
    if (GetOverlappedResult(ww->dir, &ww->ov, &br, FALSE)) {
        ResetEvent(ww->ov.hEvent);
        ReadDirectoryChangesW(ww->dir, ww->buffer, sizeof(ww->buffer), FALSE,
                FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME
                        | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE,
                &br, &ww->ov, NULL);
        return 1;
    }
    return 0;
}

void jnative_fs_watch_close(int64_t handle) {
    if (handle <= 0 || handle > JN_WIN_WATCH_MAX) {
        return;
    }
    JnWinWatch *ww = &g_ww[(int)handle - 1];
    if (!ww->armed) {
        return;
    }
    if (ww->dir != NULL && ww->dir != INVALID_HANDLE_VALUE) {
        CancelIo(ww->dir);
        CloseHandle(ww->dir);
    }
    if (ww->ov.hEvent != NULL) {
        CloseHandle(ww->ov.hEvent);
    }
    memset(ww, 0, sizeof(*ww));
}

int32_t jnative_fs_watch_poll_ex(int64_t handle) {
    return jnative_fs_watch_poll(handle) != 0 ? 1 : 0;
}

int64_t jnative_fs_stream_open(const char *path, int32_t mode) {
    if (path == NULL) {
        return -1;
    }
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return -1;
    }
    DWORD access = 0;
    DWORD creation = 0;
    if (mode == 0) {
        access = GENERIC_READ;
        creation = OPEN_EXISTING;
    } else if (mode == 1) {
        access = GENERIC_WRITE;
        creation = CREATE_ALWAYS;
    } else if (mode == 2) {
        access = GENERIC_WRITE;
        creation = OPEN_ALWAYS;
    } else {
        free(w);
        return -1;
    }
    HANDLE h = CreateFileW(w, access, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, creation,
            FILE_ATTRIBUTE_NORMAL, NULL);
    free(w);
    if (h == INVALID_HANDLE_VALUE) {
        return -1;
    }
    if (mode == 2) {
        SetFilePointer(h, 0, NULL, FILE_END);
    }
    return (int64_t)(intptr_t)h;
}

int32_t jnative_fs_stream_read(int64_t fd, void *buf, int32_t len) {
    if (fd < 0 || buf == NULL || len < 0) {
        return -1;
    }
    if (len == 0) {
        return 0;
    }
    DWORD n = 0;
    if (!ReadFile((HANDLE)(intptr_t)fd, buf, (DWORD)len, &n, NULL)) {
        return -1;
    }
    return (int32_t)n;
}

int32_t jnative_fs_stream_write(int64_t fd, const void *buf, int32_t len) {
    if (fd < 0 || (buf == NULL && len > 0) || len < 0) {
        return -1;
    }
    if (len == 0) {
        return 0;
    }
    DWORD n = 0;
    if (!WriteFile((HANDLE)(intptr_t)fd, buf, (DWORD)len, &n, NULL)) {
        return -1;
    }
    return (int32_t)n;
}

int32_t jnative_fs_stream_close(int64_t fd) {
    if (fd < 0) {
        return 0;
    }
    return CloseHandle((HANDLE)(intptr_t)fd) ? 0 : -1;
}

int32_t jnative_fs_stream_fsync(int64_t fd) {
    if (fd < 0) {
        return -1;
    }
    return FlushFileBuffers((HANDLE)(intptr_t)fd) ? 0 : -1;
}

int32_t jnative_fs_stream_seek(int64_t fd, int64_t offset) {
    if (fd < 0 || offset < 0) {
        return -1;
    }
    LARGE_INTEGER li;
    li.QuadPart = (LONGLONG)offset;
    if (!SetFilePointerEx((HANDLE)(intptr_t)fd, li, NULL, FILE_BEGIN)) {
        return -1;
    }
    return 0;
}

int32_t jnative_fs_is_symlink(const char *path) {
    if (path == NULL) {
        return 0;
    }
    wchar_t *w = utf8_to_wide(path);
    if (w == NULL) {
        return 0;
    }
    DWORD attrs = GetFileAttributesW(w);
    free(w);
    if (attrs == INVALID_FILE_ATTRIBUTES) {
        return 0;
    }
    return (attrs & FILE_ATTRIBUTE_REPARSE_POINT) ? 1 : 0;
}

char *jnative_fs_readlink(const char *path) {
    (void)path;
    return NULL;
}
