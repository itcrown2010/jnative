/* Author: 张晓达
 * JNative Windows Explorer - native shell layer (see jn_fm.h for the ABI).
 * Builds with clang / MinGW-w64 for x86_64 and aarch64 Windows (no inline asm).
 */
#define INITGUID
#define COBJMACROS
#define WIN32_LEAN_AND_MEAN
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <objbase.h>
#include <shellapi.h>
#include <shlobj.h>
#include <knownfolders.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include "jn_fm.h"

/* ----------------------------------------------------------------- text helpers */
static wchar_t *u2w(const char *s) {
    int n;
    wchar_t *w;
    if (s == NULL) {
        s = "";
    }
    n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    w = (wchar_t *)calloc((size_t)(n > 0 ? n : 1) + 1, sizeof(wchar_t));
    if (w == NULL) {
        return NULL;
    }
    if (n > 0) {
        MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n);
    }
    return w;
}

static char *w2u(const wchar_t *w) {
    int n;
    char *s;
    if (w == NULL) {
        w = L"";
    }
    n = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    s = (char *)calloc((size_t)(n > 0 ? n : 1) + 1, 1);
    if (s == NULL) {
        return NULL;
    }
    if (n > 0) {
        WideCharToMultiByte(CP_UTF8, 0, w, -1, s, n, NULL, NULL);
    }
    return s;
}

static char *dup_str(const char *s) {
    size_t n = strlen(s);
    char *r = (char *)malloc(n + 1);
    if (r != NULL) {
        memcpy(r, s, n + 1);
    }
    return r;
}

void jn_fm_free(void *p) {
    free(p);
}

typedef struct {
    char *p;
    size_t n;
    size_t cap;
} Buf;

static void buf_init(Buf *b) {
    b->cap = 4096;
    b->n = 0;
    b->p = (char *)malloc(b->cap);
    if (b->p != NULL) {
        b->p[0] = 0;
    }
}

static void buf_put(Buf *b, const char *s, size_t len) {
    if (b->p == NULL) {
        return;
    }
    if (b->n + len + 1 > b->cap) {
        size_t nc = b->cap * 2;
        char *np;
        while (nc < b->n + len + 1) {
            nc *= 2;
        }
        np = (char *)realloc(b->p, nc);
        if (np == NULL) {
            return;
        }
        b->p = np;
        b->cap = nc;
    }
    memcpy(b->p + b->n, s, len);
    b->n += len;
    b->p[b->n] = 0;
}

static void buf_str(Buf *b, const char *s) {
    buf_put(b, s, strlen(s));
}

/* UTF-8 text field: tab / CR / LF become a space. */
static void buf_field(Buf *b, const wchar_t *w) {
    char *u = w2u(w);
    char *q;
    if (u == NULL) {
        return;
    }
    for (q = u; *q; q++) {
        if (*q == '\t' || *q == '\r' || *q == '\n') {
            *q = ' ';
        }
    }
    buf_str(b, u);
    free(u);
}

static void buf_num(Buf *b, long long v) {
    char t[40];
    _snprintf(t, sizeof(t), "%lld", v);
    buf_str(b, t);
}

static char *buf_take(Buf *b) {
    if (b->p == NULL) {
        return dup_str("");
    }
    return b->p;
}

/* ----------------------------------------------------------------- file times */
static long long ft_to_unix_ms(const FILETIME *ft) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    if (u.QuadPart < 116444736000000000ULL) {
        return 0;
    }
    return (long long)((u.QuadPart - 116444736000000000ULL) / 10000ULL);
}

static void ft_to_text(const FILETIME *ft, wchar_t *out, int cap) {
    FILETIME lf;
    SYSTEMTIME st;
    wchar_t d[64];
    wchar_t t[32];
    out[0] = 0;
    if (ft->dwHighDateTime == 0 && ft->dwLowDateTime == 0) {
        return;
    }
    if (!FileTimeToLocalFileTime(ft, &lf) || !FileTimeToSystemTime(&lf, &st)) {
        return;
    }
    d[0] = 0;
    t[0] = 0;
    GetDateFormatEx(LOCALE_NAME_USER_DEFAULT, DATE_SHORTDATE, &st, NULL, d, 64, NULL);
    GetTimeFormatEx(LOCALE_NAME_USER_DEFAULT, TIME_NOSECONDS, &st, NULL, t, 32);
    _snwprintf(out, (size_t)cap, L"%ls %ls", d, t);
    out[cap - 1] = 0;
}

static void put_find_record(Buf *b, const WIN32_FIND_DATAW *fd) {
    wchar_t ds[96];
    int isdir = (fd->dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
    unsigned long long size = ((unsigned long long)fd->nFileSizeHigh << 32) | fd->nFileSizeLow;
    buf_str(b, isdir ? "D\t" : "F\t");
    buf_field(b, fd->cFileName);
    buf_str(b, "\t");
    buf_num(b, (long long)fd->dwFileAttributes);
    buf_str(b, "\t");
    buf_num(b, isdir ? -1 : (long long)size);
    buf_str(b, "\t");
    buf_num(b, ft_to_unix_ms(&fd->ftLastWriteTime));
    buf_str(b, "\t");
    buf_num(b, ft_to_unix_ms(&fd->ftCreationTime));
    buf_str(b, "\t");
    ft_to_text(&fd->ftLastWriteTime, ds, 96);
    buf_field(b, ds);
}

/* Path -> wide pattern base. Adds the \\?\ prefix for very long absolute paths. */
static wchar_t *long_path(const wchar_t *p) {
    size_t n = wcslen(p);
    wchar_t *r;
    if (n < 240 || wcsncmp(p, L"\\\\?\\", 4) == 0) {
        r = (wchar_t *)calloc(n + 8, sizeof(wchar_t));
        if (r != NULL) {
            wcscpy(r, p);
        }
        return r;
    }
    r = (wchar_t *)calloc(n + 16, sizeof(wchar_t));
    if (r == NULL) {
        return NULL;
    }
    if (p[0] == L'\\' && p[1] == L'\\') {
        wcscpy(r, L"\\\\?\\UNC\\");
        wcscat(r, p + 2);
    } else {
        wcscpy(r, L"\\\\?\\");
        wcscat(r, p);
    }
    return r;
}

/* ------------------------------------------------------------------- listing */
char *jn_fm_list(const char *path) {
    Buf b;
    wchar_t *wp = u2w(path);
    wchar_t *lp;
    wchar_t *pat;
    size_t n;
    HANDLE h;
    WIN32_FIND_DATAW fd;
    buf_init(&b);
    if (wp == NULL) {
        return buf_take(&b);
    }
    lp = long_path(wp);
    free(wp);
    if (lp == NULL) {
        return buf_take(&b);
    }
    n = wcslen(lp);
    pat = (wchar_t *)calloc(n + 4, sizeof(wchar_t));
    if (pat == NULL) {
        free(lp);
        return buf_take(&b);
    }
    wcscpy(pat, lp);
    if (n > 0 && pat[n - 1] != L'\\' && pat[n - 1] != L'/') {
        wcscat(pat, L"\\");
    }
    wcscat(pat, L"*");
    free(lp);
    h = FindFirstFileExW(pat, FindExInfoBasic, &fd, FindExSearchNameMatch, NULL, FIND_FIRST_EX_LARGE_FETCH);
    free(pat);
    if (h == INVALID_HANDLE_VALUE) {
        DWORD e = GetLastError();
        if (e == ERROR_FILE_NOT_FOUND) {
            return buf_take(&b);
        }
        buf_str(&b, "!\t");
        buf_num(&b, (long long)e);
        buf_str(&b, "\n");
        return buf_take(&b);
    }
    do {
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) {
            continue;
        }
        put_find_record(&b, &fd);
        buf_str(&b, "\n");
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return buf_take(&b);
}

char *jn_fm_drives(void) {
    Buf b;
    DWORD mask = GetLogicalDrives();
    int i;
    UINT oldMode = SetErrorMode(SEM_FAILCRITICALERRORS);
    buf_init(&b);
    for (i = 0; i < 26; i++) {
        wchar_t root[4];
        UINT type;
        SHFILEINFOW sfi;
        ULARGE_INTEGER freeB, totalB, totalFree;
        if ((mask & (1u << i)) == 0) {
            continue;
        }
        root[0] = (wchar_t)(L'A' + i);
        root[1] = L':';
        root[2] = L'\\';
        root[3] = 0;
        type = GetDriveTypeW(root);
        if (type == DRIVE_NO_ROOT_DIR || type == DRIVE_UNKNOWN) {
            continue;
        }
        memset(&sfi, 0, sizeof(sfi));
        SHGetFileInfoW(root, 0, &sfi, sizeof(sfi), SHGFI_DISPLAYNAME);
        freeB.QuadPart = 0;
        totalB.QuadPart = 0;
        totalFree.QuadPart = 0;
        if (!GetDiskFreeSpaceExW(root, &freeB, &totalB, &totalFree)) {
            totalB.QuadPart = 0;
            freeB.QuadPart = 0;
        }
        buf_field(&b, root);
        buf_str(&b, "\t");
        buf_field(&b, sfi.szDisplayName);
        buf_str(&b, "\t");
        buf_num(&b, (long long)type);
        buf_str(&b, "\t");
        buf_num(&b, (type == DRIVE_CDROM && totalB.QuadPart == 0) ? -1 : (long long)totalB.QuadPart);
        buf_str(&b, "\t");
        buf_num(&b, (type == DRIVE_CDROM && totalB.QuadPart == 0) ? -1 : (long long)freeB.QuadPart);
        buf_str(&b, "\n");
    }
    SetErrorMode(oldMode);
    return buf_take(&b);
}

char *jn_fm_known_folder(int32_t id) {
    const KNOWNFOLDERID *fid = NULL;
    PWSTR p = NULL;
    char *r;
    if (id == 8) {
        wchar_t tmp[MAX_PATH + 2];
        DWORD n = GetTempPathW(MAX_PATH, tmp);
        if (n > 3 && tmp[n - 1] == L'\\') {
            tmp[n - 1] = 0;
        }
        return w2u(tmp);
    }
    if (id == 9) {
        wchar_t od[MAX_PATH + 2];
        DWORD n = GetEnvironmentVariableW(L"OneDrive", od, MAX_PATH);
        if (n == 0 || n >= MAX_PATH || GetFileAttributesW(od) == INVALID_FILE_ATTRIBUTES) {
            return dup_str("");
        }
        return w2u(od);
    }
    switch (id) {
    case 0: fid = &FOLDERID_Profile; break;
    case 1: fid = &FOLDERID_Desktop; break;
    case 2: fid = &FOLDERID_Downloads; break;
    case 3: fid = &FOLDERID_Documents; break;
    case 4: fid = &FOLDERID_Pictures; break;
    case 5: fid = &FOLDERID_Music; break;
    case 6: fid = &FOLDERID_Videos; break;
    case 7: fid = &FOLDERID_RoamingAppData; break;
    default: break;
    }
    if (fid == NULL || FAILED(SHGetKnownFolderPath(fid, 0, NULL, &p)) || p == NULL) {
        return dup_str("");
    }
    r = w2u(p);
    CoTaskMemFree(p);
    return r;
}

char *jn_fm_type_name(const char *name, int32_t isdir) {
    wchar_t *w = u2w(name);
    SHFILEINFOW sfi;
    char *r;
    memset(&sfi, 0, sizeof(sfi));
    SHGetFileInfoW(w != NULL ? w : L"", isdir ? FILE_ATTRIBUTE_DIRECTORY : FILE_ATTRIBUTE_NORMAL, &sfi,
            sizeof(sfi), SHGFI_TYPENAME | SHGFI_USEFILEATTRIBUTES);
    free(w);
    {
        Buf b;
        buf_init(&b);
        buf_field(&b, sfi.szTypeName);
        r = buf_take(&b);
    }
    return r;
}

char *jn_fm_display_name(const char *path) {
    wchar_t *w = u2w(path);
    SHFILEINFOW sfi;
    char *r;
    memset(&sfi, 0, sizeof(sfi));
    if (w != NULL) {
        SHGetFileInfoW(w, 0, &sfi, sizeof(sfi), SHGFI_DISPLAYNAME);
    }
    free(w);
    {
        Buf b;
        buf_init(&b);
        buf_field(&b, sfi.szDisplayName);
        r = buf_take(&b);
    }
    return r;
}

int32_t jn_fm_exists(const char *path) {
    wchar_t *w = u2w(path);
    wchar_t *lp;
    DWORD a;
    if (w == NULL) {
        return 0;
    }
    lp = long_path(w);
    free(w);
    if (lp == NULL) {
        return 0;
    }
    a = GetFileAttributesW(lp);
    free(lp);
    if (a == INVALID_FILE_ATTRIBUTES) {
        return 0;
    }
    return (a & FILE_ATTRIBUTE_DIRECTORY) ? 2 : 1;
}

char *jn_fm_full_path(const char *path) {
    wchar_t *w = u2w(path);
    wchar_t ex[4096];
    wchar_t full[4096];
    DWORD n;
    char *r;
    if (w == NULL) {
        return dup_str("");
    }
    n = ExpandEnvironmentStringsW(w, ex, 4096);
    if (n == 0 || n > 4096) {
        wcsncpy(ex, w, 4095);
        ex[4095] = 0;
    }
    free(w);
    n = GetFullPathNameW(ex, 4096, full, NULL);
    if (n == 0 || n >= 4096) {
        return w2u(ex);
    }
    r = w2u(full);
    return r;
}

/* ------------------------------------------------------------- file operations */
/* '\n' separated UTF-8 list -> double-NUL terminated wide list. */
static wchar_t *to_multi(const char *list, int *count) {
    wchar_t *w = u2w(list);
    wchar_t *out;
    size_t n, i, o = 0;
    int c = 0;
    if (w == NULL) {
        return NULL;
    }
    n = wcslen(w);
    out = (wchar_t *)calloc(n + 4, sizeof(wchar_t));
    if (out == NULL) {
        free(w);
        return NULL;
    }
    for (i = 0; i < n; i++) {
        if (w[i] == L'\n' || w[i] == L'\r') {
            if (o > 0 && out[o - 1] != 0) {
                out[o++] = 0;
                c++;
            }
        } else {
            out[o++] = w[i];
        }
    }
    if (o > 0 && out[o - 1] != 0) {
        out[o++] = 0;
        c++;
    }
    out[o] = 0;
    out[o + 1] = 0;
    free(w);
    if (count != NULL) {
        *count = c;
    }
    return out;
}

int32_t jn_fm_file_op(int32_t op, const char *srcs, const char *dst, int32_t flags) {
    SHFILEOPSTRUCTW fo;
    wchar_t *from;
    wchar_t *to = NULL;
    int cnt = 0;
    int r;
    from = to_multi(srcs, &cnt);
    if (from == NULL || cnt == 0) {
        free(from);
        return 87;
    }
    memset(&fo, 0, sizeof(fo));
    fo.hwnd = GetActiveWindow();
    fo.pFrom = from;
    if (op == 1 || op == 2) {
        if (dst == NULL || dst[0] == 0) {
            free(from);
            return 87;
        }
        to = to_multi(dst, NULL);
        fo.pTo = to;
        fo.wFunc = (op == 1) ? FO_COPY : FO_MOVE;
        fo.fFlags = FOF_NOCONFIRMMKDIR;
        if (flags & 1) {
            fo.fFlags |= FOF_RENAMEONCOLLISION;
        }
    } else {
        fo.wFunc = FO_DELETE;
        fo.fFlags = (op == 4) ? FOF_ALLOWUNDO : 0;
    }
    r = SHFileOperationW(&fo);
    free(from);
    free(to);
    if (r == 0 && fo.fAnyOperationsAborted) {
        return 1223;
    }
    if (r == 0x75 || r == 0x10000) {
        return 1223;
    }
    return r;
}

/* ---------------------------------------------------------------- helper window */
static HWND g_helper = NULL;
static IContextMenu2 *g_cm2 = NULL;
static IContextMenu3 *g_cm3 = NULL;

static LRESULT CALLBACK helper_proc(HWND h, UINT m, WPARAM w, LPARAM l) {
    if (m == WM_INITMENUPOPUP || m == WM_DRAWITEM || m == WM_MEASUREITEM || m == WM_MENUCHAR) {
        if (g_cm3 != NULL) {
            LRESULT lr = 0;
            if (SUCCEEDED(IContextMenu3_HandleMenuMsg2(g_cm3, m, w, l, &lr))) {
                return lr;
            }
        } else if (g_cm2 != NULL) {
            if (SUCCEEDED(IContextMenu2_HandleMenuMsg(g_cm2, m, w, l))) {
                return 0;
            }
        }
    }
    return DefWindowProcW(h, m, w, l);
}

static HWND helper_window(void) {
    if (g_helper == NULL) {
        WNDCLASSW wc;
        memset(&wc, 0, sizeof(wc));
        wc.lpfnWndProc = helper_proc;
        wc.hInstance = GetModuleHandleW(NULL);
        wc.lpszClassName = L"JnFmHelperWindow";
        RegisterClassW(&wc);
        g_helper = CreateWindowExW(WS_EX_TOOLWINDOW, L"JnFmHelperWindow", L"", WS_POPUP, 0, 0, 0, 0, NULL, NULL,
                wc.hInstance, NULL);
    }
    return g_helper;
}

/* ---------------------------------------------------------------- clipboard */
static UINT drop_effect_format(void) {
    static UINT f = 0;
    if (f == 0) {
        f = RegisterClipboardFormatW(L"Preferred DropEffect");
    }
    return f;
}

static int clip_open(void) {
    int i;
    HWND w = helper_window();
    for (i = 0; i < 10; i++) {
        if (OpenClipboard(w)) {
            return 1;
        }
        Sleep(15);
    }
    return 0;
}

int32_t jn_fm_clip_set(const char *paths, int32_t cut) {
    int cnt = 0;
    wchar_t *list = to_multi(paths, &cnt);
    size_t chars = 0;
    HGLOBAL hg;
    HGLOBAL he;
    DROPFILES *df;
    DWORD *eff;
    if (list == NULL || cnt == 0) {
        free(list);
        return 87;
    }
    while (list[chars] != 0 || list[chars + 1] != 0) {
        chars++;
    }
    chars += 2;
    hg = GlobalAlloc(GMEM_MOVEABLE | GMEM_ZEROINIT, sizeof(DROPFILES) + chars * sizeof(wchar_t) + 4);
    he = GlobalAlloc(GMEM_MOVEABLE | GMEM_ZEROINIT, sizeof(DWORD));
    if (hg == NULL || he == NULL) {
        free(list);
        if (hg != NULL) {
            GlobalFree(hg);
        }
        if (he != NULL) {
            GlobalFree(he);
        }
        return 8;
    }
    df = (DROPFILES *)GlobalLock(hg);
    df->pFiles = sizeof(DROPFILES);
    df->fWide = TRUE;
    memcpy((char *)df + sizeof(DROPFILES), list, chars * sizeof(wchar_t));
    GlobalUnlock(hg);
    eff = (DWORD *)GlobalLock(he);
    *eff = cut ? DROPEFFECT_MOVE : DROPEFFECT_COPY;
    GlobalUnlock(he);
    free(list);
    if (!clip_open()) {
        GlobalFree(hg);
        GlobalFree(he);
        return 5;
    }
    EmptyClipboard();
    if (SetClipboardData(CF_HDROP, hg) == NULL) {
        GlobalFree(hg);
    }
    if (SetClipboardData(drop_effect_format(), he) == NULL) {
        GlobalFree(he);
    }
    CloseClipboard();
    return 0;
}

/* 0 none, 1 copy, 2 cut. Cached per clipboard sequence number: the app polls this on a timer. */
static DWORD clip_seq_cached = 0;
static int32_t clip_state_cached = 0;

int32_t jn_fm_clip_state(void) {
    int32_t r = 0;
    DWORD seq = GetClipboardSequenceNumber();
    if (seq != 0 && seq == clip_seq_cached) {
        return clip_state_cached;
    }
    if (IsClipboardFormatAvailable(CF_HDROP)) {
        r = 1;
        if (clip_open()) {
            HANDLE h = GetClipboardData(drop_effect_format());
            if (h != NULL) {
                DWORD *e = (DWORD *)GlobalLock(h);
                if (e != NULL) {
                    if ((*e & DROPEFFECT_MOVE) != 0 && (*e & DROPEFFECT_COPY) == 0) {
                        r = 2;
                    }
                    GlobalUnlock(h);
                }
            }
            CloseClipboard();
        } else {
            return r; /* busy: do not cache */
        }
    }
    clip_seq_cached = seq;
    clip_state_cached = r;
    return r;
}

char *jn_fm_clip_get(void) {
    Buf b;
    int cut = 0;
    buf_init(&b);
    if (!IsClipboardFormatAvailable(CF_HDROP)) {
        return buf_take(&b);
    }
    if (!clip_open()) {
        return buf_take(&b);
    }
    {
        HANDLE he = GetClipboardData(drop_effect_format());
        if (he != NULL) {
            DWORD *e = (DWORD *)GlobalLock(he);
            if (e != NULL) {
                if ((*e & DROPEFFECT_MOVE) != 0 && (*e & DROPEFFECT_COPY) == 0) {
                    cut = 1;
                }
                GlobalUnlock(he);
            }
        }
    }
    {
        HDROP hd = (HDROP)GetClipboardData(CF_HDROP);
        if (hd != NULL) {
            UINT n = DragQueryFileW(hd, 0xFFFFFFFFu, NULL, 0);
            UINT i;
            buf_str(&b, cut ? "X\n" : "C\n");
            for (i = 0; i < n; i++) {
                UINT len = DragQueryFileW(hd, i, NULL, 0);
                wchar_t *p = (wchar_t *)calloc((size_t)len + 2, sizeof(wchar_t));
                if (p == NULL) {
                    continue;
                }
                DragQueryFileW(hd, i, p, len + 1);
                buf_field(&b, p);
                buf_str(&b, "\n");
                free(p);
            }
        }
    }
    CloseClipboard();
    return buf_take(&b);
}

void jn_fm_clip_clear(void) {
    if (clip_open()) {
        EmptyClipboard();
        CloseClipboard();
    }
}

int32_t jn_fm_clip_text(const char *text) {
    wchar_t *w = u2w(text);
    HGLOBAL hg;
    size_t bytes;
    void *dst;
    if (w == NULL) {
        return 8;
    }
    bytes = (wcslen(w) + 1) * sizeof(wchar_t);
    hg = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (hg == NULL) {
        free(w);
        return 8;
    }
    dst = GlobalLock(hg);
    memcpy(dst, w, bytes);
    GlobalUnlock(hg);
    free(w);
    if (!clip_open()) {
        GlobalFree(hg);
        return 5;
    }
    EmptyClipboard();
    if (SetClipboardData(CF_UNICODETEXT, hg) == NULL) {
        GlobalFree(hg);
    }
    CloseClipboard();
    return 0;
}

/* ------------------------------------------------------------ rename / create */
int32_t jn_fm_rename(const char *path, const char *new_name) {
    wchar_t *src = u2w(path);
    wchar_t *nn = u2w(new_name);
    wchar_t *dst;
    wchar_t *s2;
    wchar_t *d2;
    wchar_t *slash;
    int32_t r = 0;
    size_t n;
    if (src == NULL || nn == NULL || nn[0] == 0) {
        free(src);
        free(nn);
        return 87;
    }
    n = wcslen(src) + wcslen(nn) + 4;
    dst = (wchar_t *)calloc(n, sizeof(wchar_t));
    if (dst == NULL) {
        free(src);
        free(nn);
        return 8;
    }
    wcscpy(dst, src);
    slash = wcsrchr(dst, L'\\');
    if (slash == NULL) {
        slash = wcsrchr(dst, L'/');
    }
    if (slash == NULL) {
        wcscpy(dst, nn);
    } else {
        wcscpy(slash + 1, nn);
    }
    s2 = long_path(src);
    d2 = long_path(dst);
    if (s2 == NULL || d2 == NULL || !MoveFileExW(s2, d2, 0)) {
        r = (int32_t)GetLastError();
        if (r == 0) {
            r = 8;
        }
    }
    free(s2);
    free(d2);
    free(src);
    free(nn);
    free(dst);
    return r;
}

int32_t jn_fm_create(const char *path, int32_t isdir) {
    wchar_t *w = u2w(path);
    wchar_t *lp;
    int32_t r = 0;
    if (w == NULL) {
        return 87;
    }
    lp = long_path(w);
    free(w);
    if (lp == NULL) {
        return 8;
    }
    if (isdir) {
        if (!CreateDirectoryW(lp, NULL)) {
            r = (int32_t)GetLastError();
        }
    } else {
        HANDLE h = CreateFileW(lp, GENERIC_WRITE, 0, NULL, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
        if (h == INVALID_HANDLE_VALUE) {
            r = (int32_t)GetLastError();
        } else {
            CloseHandle(h);
        }
    }
    free(lp);
    return r;
}

/* --------------------------------------------------------------- shell execute */
int32_t jn_fm_shell_exec(const char *path, const char *verb, const char *params, const char *cwd) {
    SHELLEXECUTEINFOW sei;
    wchar_t *wp = u2w(path);
    wchar_t *wv = (verb != NULL && verb[0] != 0) ? u2w(verb) : NULL;
    wchar_t *wa = (params != NULL && params[0] != 0) ? u2w(params) : NULL;
    wchar_t *wd = (cwd != NULL && cwd[0] != 0) ? u2w(cwd) : NULL;
    int32_t r = 0;
    memset(&sei, 0, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_FLAG_NO_UI | SEE_MASK_NOASYNC;
    if (wv != NULL && (_wcsicmp(wv, L"properties") == 0)) {
        sei.fMask |= SEE_MASK_INVOKEIDLIST;
    }
    sei.hwnd = GetActiveWindow();
    sei.lpVerb = wv;
    sei.lpFile = wp;
    sei.lpParameters = wa;
    sei.lpDirectory = wd;
    sei.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&sei)) {
        r = (int32_t)GetLastError();
        if (r == 0) {
            r = 31;
        }
    }
    free(wp);
    free(wv);
    free(wa);
    free(wd);
    return r;
}

/* --------------------------------------------------------------- shell menu */
typedef struct {
    ITEMIDLIST **full;
    LPCITEMIDLIST *child;
    int n;
    IShellFolder *folder;
} CmSet;

static void cm_free(CmSet *s) {
    int i;
    if (s->folder != NULL) {
        IShellFolder_Release(s->folder);
    }
    for (i = 0; i < s->n; i++) {
        if (s->full[i] != NULL) {
            CoTaskMemFree(s->full[i]);
        }
    }
    free(s->full);
    free(s->child);
    memset(s, 0, sizeof(*s));
}

static IContextMenu *cm_create(const char *paths, CmSet *s) {
    int cnt = 0;
    wchar_t *list = to_multi(paths, &cnt);
    wchar_t *p;
    int i = 0;
    IContextMenu *cm = NULL;
    memset(s, 0, sizeof(*s));
    if (list == NULL || cnt == 0) {
        free(list);
        return NULL;
    }
    s->full = (ITEMIDLIST **)calloc((size_t)cnt, sizeof(ITEMIDLIST *));
    s->child = (LPCITEMIDLIST *)calloc((size_t)cnt, sizeof(LPCITEMIDLIST));
    if (s->full == NULL || s->child == NULL) {
        free(list);
        cm_free(s);
        return NULL;
    }
    s->n = cnt;
    for (p = list; *p != 0 && i < cnt; p += wcslen(p) + 1, i++) {
        IShellFolder *f = NULL;
        LPCITEMIDLIST child = NULL;
        if (FAILED(SHParseDisplayName(p, NULL, &s->full[i], 0, NULL)) || s->full[i] == NULL) {
            s->full[i] = NULL;
            free(list);
            cm_free(s);
            return NULL;
        }
        if (FAILED(SHBindToParent(s->full[i], &IID_IShellFolder, (void **)&f, &child))) {
            free(list);
            cm_free(s);
            return NULL;
        }
        s->child[i] = child;
        if (i == 0) {
            s->folder = f;
        } else {
            IShellFolder_Release(f);
        }
    }
    free(list);
    if (s->folder == NULL
            || FAILED(IShellFolder_GetUIObjectOf(s->folder, GetActiveWindow(), (UINT)s->n, s->child,
                    &IID_IContextMenu, NULL, (void **)&cm))) {
        cm_free(s);
        return NULL;
    }
    return cm;
}

#define CM_FIRST 1
#define CM_LAST 0x7FFF

int32_t jn_fm_shell_menu(const char *paths, int32_t flags) {
    CmSet set;
    IContextMenu *cm;
    HMENU menu;
    UINT qf = CMF_NORMAL | CMF_CANRENAME;
    UINT cmd;
    POINT pt;
    HWND owner;
    int32_t ret = 0;
    HRESULT hr;
    OleInitialize(NULL);
    owner = helper_window();
    cm = cm_create(paths, &set);
    if (cm == NULL) {
        return -1;
    }
    if (flags & 1) {
        qf |= CMF_EXTENDEDVERBS;
    }
    menu = CreatePopupMenu();
    hr = IContextMenu_QueryContextMenu(cm, menu, 0, CM_FIRST, CM_LAST, qf);
    if (FAILED(hr)) {
        DestroyMenu(menu);
        IContextMenu_Release(cm);
        cm_free(&set);
        return -1;
    }
    g_cm2 = NULL;
    g_cm3 = NULL;
    IContextMenu_QueryInterface(cm, &IID_IContextMenu3, (void **)&g_cm3);
    if (g_cm3 == NULL) {
        IContextMenu_QueryInterface(cm, &IID_IContextMenu2, (void **)&g_cm2);
    }
    GetCursorPos(&pt);
    SetForegroundWindow(owner);
    cmd = (UINT)TrackPopupMenuEx(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON, pt.x, pt.y, owner, NULL);
    PostMessageW(owner, WM_NULL, 0, 0);
    if (cmd >= CM_FIRST) {
        char verb[80];
        verb[0] = 0;
        IContextMenu_GetCommandString(cm, cmd - CM_FIRST, GCS_VERBA, NULL, verb, sizeof(verb));
        if (_stricmp(verb, "rename") == 0) {
            ret = 1;
        } else if ((flags & 2) && _stricmp(verb, "open") == 0) {
            ret = 2;
        } else {
            CMINVOKECOMMANDINFOEX ci;
            memset(&ci, 0, sizeof(ci));
            ci.cbSize = sizeof(ci);
            ci.fMask = CMIC_MASK_UNICODE | CMIC_MASK_PTINVOKE;
            ci.hwnd = GetActiveWindow();
            ci.lpVerb = MAKEINTRESOURCEA(cmd - CM_FIRST);
            ci.lpVerbW = MAKEINTRESOURCEW(cmd - CM_FIRST);
            ci.nShow = SW_SHOWNORMAL;
            ci.ptInvoke = pt;
            IContextMenu_InvokeCommand(cm, (CMINVOKECOMMANDINFO *)&ci);
        }
    }
    if (g_cm3 != NULL) {
        IContextMenu3_Release(g_cm3);
        g_cm3 = NULL;
    }
    if (g_cm2 != NULL) {
        IContextMenu2_Release(g_cm2);
        g_cm2 = NULL;
    }
    DestroyMenu(menu);
    IContextMenu_Release(cm);
    cm_free(&set);
    return ret;
}

int32_t jn_fm_shell_verb(const char *paths, const char *verb) {
    CmSet set;
    IContextMenu *cm;
    HMENU menu;
    CMINVOKECOMMANDINFOEX ci;
    HRESULT hr;
    OleInitialize(NULL);
    cm = cm_create(paths, &set);
    if (cm == NULL) {
        return -1;
    }
    menu = CreatePopupMenu();
    IContextMenu_QueryContextMenu(cm, menu, 0, CM_FIRST, CM_LAST, CMF_NORMAL | CMF_CANRENAME);
    memset(&ci, 0, sizeof(ci));
    ci.cbSize = sizeof(ci);
    ci.fMask = CMIC_MASK_UNICODE;
    ci.hwnd = GetActiveWindow();
    ci.lpVerb = verb;
    {
        wchar_t *wv = u2w(verb);
        ci.lpVerbW = wv;
        ci.nShow = SW_SHOWNORMAL;
        hr = IContextMenu_InvokeCommand(cm, (CMINVOKECOMMANDINFO *)&ci);
        free(wv);
    }
    DestroyMenu(menu);
    IContextMenu_Release(cm);
    cm_free(&set);
    return FAILED(hr) ? (int32_t)(hr & 0xFFFF) : 0;
}

/* ------------------------------------------------------------------- search */
#define SEARCH_MAX 4

typedef struct {
    int used;
    wchar_t **stack;
    int n;
    int cap;
    HANDLE h;
    wchar_t *cur;
    wchar_t *query;
    long long found;
} Search;

static Search g_search[SEARCH_MAX];

static void search_push(Search *s, const wchar_t *dir) {
    if (s->n >= s->cap) {
        int nc = s->cap == 0 ? 64 : s->cap * 2;
        wchar_t **ns = (wchar_t **)realloc(s->stack, (size_t)nc * sizeof(wchar_t *));
        if (ns == NULL) {
            return;
        }
        s->stack = ns;
        s->cap = nc;
    }
    s->stack[s->n] = _wcsdup(dir);
    if (s->stack[s->n] != NULL) {
        s->n++;
    }
}

static int contains_ci(const wchar_t *hay, const wchar_t *needleLower) {
    size_t hn = wcslen(hay);
    size_t nn = wcslen(needleLower);
    size_t i, j;
    if (nn == 0) {
        return 1;
    }
    if (nn > hn) {
        return 0;
    }
    for (i = 0; i + nn <= hn; i++) {
        for (j = 0; j < nn; j++) {
            wchar_t a = hay[i + j];
            if (a >= L'A' && a <= L'Z') {
                a = (wchar_t)(a + 32);
            } else if (a > 127) {
                a = (wchar_t)towlower(a);
            }
            if (a != needleLower[j]) {
                break;
            }
        }
        if (j == nn) {
            return 1;
        }
    }
    return 0;
}

int32_t jn_fm_search_begin(const char *root, const char *query) {
    int i;
    wchar_t *wr = u2w(root);
    wchar_t *wq = u2w(query);
    wchar_t *q;
    if (wr == NULL || wq == NULL) {
        free(wr);
        free(wq);
        return 0;
    }
    for (q = wq; *q; q++) {
        if (*q >= L'A' && *q <= L'Z') {
            *q = (wchar_t)(*q + 32);
        } else if (*q > 127) {
            *q = (wchar_t)towlower(*q);
        }
    }
    for (i = 0; i < SEARCH_MAX; i++) {
        if (!g_search[i].used) {
            Search *s = &g_search[i];
            memset(s, 0, sizeof(*s));
            s->used = 1;
            s->h = INVALID_HANDLE_VALUE;
            s->query = wq;
            search_push(s, wr);
            free(wr);
            return i + 1;
        }
    }
    free(wr);
    free(wq);
    return 0;
}

void jn_fm_search_end(int32_t handle) {
    Search *s;
    int i;
    if (handle < 1 || handle > SEARCH_MAX) {
        return;
    }
    s = &g_search[handle - 1];
    if (!s->used) {
        return;
    }
    if (s->h != INVALID_HANDLE_VALUE) {
        FindClose(s->h);
    }
    for (i = 0; i < s->n; i++) {
        free(s->stack[i]);
    }
    free(s->stack);
    free(s->cur);
    free(s->query);
    memset(s, 0, sizeof(*s));
}

char *jn_fm_search_next(int32_t handle, int32_t budget_ms) {
    Buf b;
    Search *s;
    ULONGLONG t0 = GetTickCount64();
    int iter = 0;
    buf_init(&b);
    if (handle < 1 || handle > SEARCH_MAX || !g_search[handle - 1].used) {
        buf_str(&b, "#done\n");
        return buf_take(&b);
    }
    s = &g_search[handle - 1];
    for (;;) {
        WIN32_FIND_DATAW fd;
        if ((++iter & 63) == 0 && (GetTickCount64() - t0) >= (ULONGLONG)budget_ms) {
            break;
        }
        if (s->h == INVALID_HANDLE_VALUE) {
            wchar_t *dir;
            wchar_t *lp;
            wchar_t *pat;
            size_t n;
            if (s->n == 0) {
                buf_str(&b, "#done\n");
                break;
            }
            dir = s->stack[--s->n];
            free(s->cur);
            s->cur = dir;
            lp = long_path(dir);
            if (lp == NULL) {
                continue;
            }
            n = wcslen(lp);
            pat = (wchar_t *)calloc(n + 4, sizeof(wchar_t));
            if (pat == NULL) {
                free(lp);
                continue;
            }
            wcscpy(pat, lp);
            if (n > 0 && pat[n - 1] != L'\\') {
                wcscat(pat, L"\\");
            }
            wcscat(pat, L"*");
            free(lp);
            s->h = FindFirstFileExW(pat, FindExInfoBasic, &fd, FindExSearchNameMatch, NULL,
                    FIND_FIRST_EX_LARGE_FETCH);
            free(pat);
            if (s->h == INVALID_HANDLE_VALUE) {
                continue;
            }
        } else if (!FindNextFileW(s->h, &fd)) {
            FindClose(s->h);
            s->h = INVALID_HANDLE_VALUE;
            continue;
        }
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) {
            continue;
        }
        if ((fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) && !(fd.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT)) {
            size_t cl = wcslen(s->cur);
            size_t nl = wcslen(fd.cFileName);
            wchar_t *sub = (wchar_t *)calloc(cl + nl + 4, sizeof(wchar_t));
            if (sub != NULL) {
                wcscpy(sub, s->cur);
                if (cl > 0 && sub[cl - 1] != L'\\') {
                    wcscat(sub, L"\\");
                }
                wcscat(sub, fd.cFileName);
                search_push(s, sub);
                free(sub);
            }
        }
        if (contains_ci(fd.cFileName, s->query)) {
            put_find_record(&b, &fd);
            buf_str(&b, "\t");
            buf_field(&b, s->cur);
            buf_str(&b, "\n");
            s->found++;
            if (s->found >= 20000) {
                s->n = 0;
                if (s->h != INVALID_HANDLE_VALUE) {
                    FindClose(s->h);
                    s->h = INVALID_HANDLE_VALUE;
                }
            }
        }
    }
    return buf_take(&b);
}

/* -------------------------------------------------------------------- watch */
static HANDLE g_watch = INVALID_HANDLE_VALUE;

int32_t jn_fm_watch_set(const char *path) {
    wchar_t *w;
    wchar_t *lp;
    if (g_watch != INVALID_HANDLE_VALUE) {
        FindCloseChangeNotification(g_watch);
        g_watch = INVALID_HANDLE_VALUE;
    }
    if (path == NULL || path[0] == 0) {
        return 0;
    }
    w = u2w(path);
    if (w == NULL) {
        return 8;
    }
    lp = long_path(w);
    free(w);
    if (lp == NULL) {
        return 8;
    }
    g_watch = FindFirstChangeNotificationW(lp, FALSE,
            FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES
                    | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE);
    free(lp);
    if (g_watch == INVALID_HANDLE_VALUE) {
        return (int32_t)GetLastError();
    }
    return 0;
}

int32_t jn_fm_watch_poll(void) {
    if (g_watch == INVALID_HANDLE_VALUE) {
        return 0;
    }
    if (WaitForSingleObject(g_watch, 0) == WAIT_OBJECT_0) {
        FindNextChangeNotification(g_watch);
        return 1;
    }
    return 0;
}

/* --------------------------------------------------------------------- spawn */
int32_t jn_fm_spawn_self(const char *path) {
    wchar_t exe[MAX_PATH * 2];
    wchar_t *wp = u2w(path);
    wchar_t *cmd;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    size_t n;
    int32_t r = 0;
    DWORD len = GetModuleFileNameW(NULL, exe, MAX_PATH * 2);
    if (len == 0 || len >= MAX_PATH * 2 || wp == NULL) {
        free(wp);
        return 2;
    }
    n = wcslen(exe) + wcslen(wp) + 16;
    cmd = (wchar_t *)calloc(n, sizeof(wchar_t));
    if (cmd == NULL) {
        free(wp);
        return 8;
    }
    _snwprintf(cmd, n - 1, L"\"%ls\" \"%ls\"", exe, wp);
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    memset(&pi, 0, sizeof(pi));
    if (!CreateProcessW(exe, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        r = (int32_t)GetLastError();
    } else {
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
    free(cmd);
    free(wp);
    return r;
}
