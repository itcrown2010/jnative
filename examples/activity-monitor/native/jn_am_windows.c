/* Author: 张晓达
 * Windows process / host sampling for JNative Task Monitor.
 * Toolhelp32 + GetProcessTimes + GetProcessMemoryInfo + GetSystemTimes +
 * EnumWindows grouping hints + EcoQoS + version publisher.
 * No mock metrics; unavailable fields stay -1 / empty / "—".
 */
#define WIN32_LEAN_AND_MEAN
#include "jn_am.h"

#include <windows.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <sddl.h>
#include <shellapi.h>

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "version.lib")
#pragma comment(lib, "shell32.lib")

#ifndef PROCESS_POWER_THROTTLING_CURRENT_VERSION
#define PROCESS_POWER_THROTTLING_CURRENT_VERSION 1
#endif
#ifndef PROCESS_POWER_THROTTLING_EXECUTION_SPEED
#define PROCESS_POWER_THROTTLING_EXECUTION_SPEED 0x1
#endif
/* ProcessPowerThrottling = 4 — use int to avoid MinGW header variance. */
#define JN_ProcessPowerThrottling 4

typedef struct JN_PROCESS_POWER_THROTTLING_STATE {
    ULONG Version;
    ULONG ControlMask;
    ULONG StateMask;
} JN_PROCESS_POWER_THROTTLING_STATE;

typedef BOOL (WINAPI *GetProcessInformation_fn)(HANDLE, int, LPVOID, DWORD);
typedef BOOL (WINAPI *SetProcessInformation_fn)(HANDLE, int, LPVOID, DWORD);

static int32_t g_last_errno = 0;

void jn_am_free(char *p) {
    free(p);
}

int32_t jn_am_last_errno(void) {
    return g_last_errno;
}

int32_t jn_am_self_pid(void) {
    return (int32_t)GetCurrentProcessId();
}

int64_t jn_am_mono_ns(void) {
    static LARGE_INTEGER freq = {0};
    LARGE_INTEGER now;
    if (freq.QuadPart == 0) {
        if (!QueryPerformanceFrequency(&freq) || freq.QuadPart == 0) {
            return 0;
        }
    }
    if (!QueryPerformanceCounter(&now)) {
        return 0;
    }
    return (int64_t)((now.QuadPart * 1000000000ULL) / (unsigned long long)freq.QuadPart);
}

static void sanitize_field(char *s) {
    if (s == NULL) {
        return;
    }
    for (char *p = s; *p; p++) {
        if (*p == '\t' || *p == '\n' || *p == '\r') {
            *p = ' ';
        }
    }
}

static int append_fmt(char **buf, size_t *len, size_t *cap, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    char tmp[2048];
    int n = vsnprintf(tmp, sizeof(tmp), fmt, ap);
    va_end(ap);
    if (n < 0) {
        return -1;
    }
    if ((size_t)n >= sizeof(tmp)) {
        n = (int)sizeof(tmp) - 1;
    }
    if (*len + (size_t)n + 1 > *cap) {
        size_t nc = (*cap == 0) ? 65536 : (*cap * 2);
        while (nc < *len + (size_t)n + 1) {
            nc *= 2;
        }
        char *nb = (char *)realloc(*buf, nc);
        if (nb == NULL) {
            return -1;
        }
        *buf = nb;
        *cap = nc;
    }
    memcpy(*buf + *len, tmp, (size_t)n);
    *len += (size_t)n;
    (*buf)[*len] = '\0';
    return 0;
}

static int wide_to_utf8(const wchar_t *w, char *out, size_t out_cap) {
    if (w == NULL || out == NULL || out_cap == 0) {
        return -1;
    }
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, out, (int)out_cap, NULL, NULL);
    if (n <= 0) {
        out[0] = '\0';
        return -1;
    }
    return 0;
}

static void filetime_to_unix(const FILETIME *ft, int64_t *sec_out, int32_t *usec_out) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    const unsigned long long EPOCH_DIFF = 116444736000000000ULL;
    if (u.QuadPart < EPOCH_DIFF) {
        *sec_out = -1;
        *usec_out = -1;
        return;
    }
    unsigned long long ticks = u.QuadPart - EPOCH_DIFF;
    *sec_out = (int64_t)(ticks / 10000000ULL);
    *usec_out = (int32_t)((ticks % 10000000ULL) / 10ULL);
}

static int64_t filetime_to_ns(const FILETIME *ft) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    if (u.QuadPart > (unsigned long long)INT64_MAX / 100ULL) {
        return INT64_MAX;
    }
    return (int64_t)(u.QuadPart * 100ULL);
}

static int read_start_time(DWORD pid, int64_t *sec_out, int32_t *usec_out) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (h == NULL) {
        h = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, pid);
    }
    if (h == NULL) {
        return -1;
    }
    FILETIME create, exit_t, kernel, user;
    BOOL ok = GetProcessTimes(h, &create, &exit_t, &kernel, &user);
    CloseHandle(h);
    if (!ok) {
        return -1;
    }
    filetime_to_unix(&create, sec_out, usec_out);
    return (*sec_out >= 0) ? 0 : -1;
}

int32_t jn_am_check_identity(int32_t pid, int64_t start_sec, int32_t start_usec) {
    g_last_errno = 0;
    if (pid <= 0) {
        return 0;
    }
    int64_t sec = -1;
    int32_t usec = -1;
    if (read_start_time((DWORD)pid, &sec, &usec) != 0) {
        g_last_errno = (int32_t)GetLastError();
        return 0;
    }
    if (sec == start_sec && usec == start_usec) {
        return 1;
    }
    return 0;
}

int32_t jn_am_signal(int32_t pid, int32_t sig) {
    g_last_errno = 0;
    (void)sig;
    if (pid <= 0 || pid == (int32_t)GetCurrentProcessId()) {
        g_last_errno = ERROR_INVALID_PARAMETER;
        return ERROR_INVALID_PARAMETER;
    }
    HANDLE h = OpenProcess(PROCESS_TERMINATE | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        DWORD err = GetLastError();
        g_last_errno = (int32_t)err;
        return (int32_t)err;
    }
    DWORD code = 0;
    if (GetExitCodeProcess(h, &code) && code != STILL_ACTIVE) {
        CloseHandle(h);
        g_last_errno = ERROR_NOT_FOUND;
        return ERROR_NOT_FOUND;
    }
    if (!TerminateProcess(h, 1)) {
        DWORD err = GetLastError();
        CloseHandle(h);
        g_last_errno = (int32_t)err;
        return (int32_t)err;
    }
    CloseHandle(h);
    return 0;
}

int32_t jn_am_alive(int32_t pid) {
    g_last_errno = 0;
    if (pid <= 0) {
        return 0;
    }
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        DWORD err = GetLastError();
        g_last_errno = (int32_t)err;
        if (err == ERROR_INVALID_PARAMETER || err == ERROR_NOT_FOUND) {
            return 0;
        }
        if (err == ERROR_ACCESS_DENIED) {
            return 1;
        }
        return -1;
    }
    DWORD code = 0;
    BOOL ok = GetExitCodeProcess(h, &code);
    CloseHandle(h);
    if (!ok) {
        g_last_errno = (int32_t)GetLastError();
        return -1;
    }
    return code == STILL_ACTIVE ? 1 : 0;
}

static GetProcessInformation_fn load_get_proc_info(void) {
    static GetProcessInformation_fn fn;
    static int tried;
    if (!tried) {
        tried = 1;
        HMODULE k = GetModuleHandleW(L"kernel32.dll");
        if (k != NULL) {
            fn = (GetProcessInformation_fn)GetProcAddress(k, "GetProcessInformation");
        }
    }
    return fn;
}

static SetProcessInformation_fn load_set_proc_info(void) {
    static SetProcessInformation_fn fn;
    static int tried;
    if (!tried) {
        tried = 1;
        HMODULE k = GetModuleHandleW(L"kernel32.dll");
        if (k != NULL) {
            fn = (SetProcessInformation_fn)GetProcAddress(k, "SetProcessInformation");
        }
    }
    return fn;
}

static int read_eco_handle(HANDLE h) {
    GetProcessInformation_fn getpi = load_get_proc_info();
    if (getpi == NULL) {
        return -1;
    }
    JN_PROCESS_POWER_THROTTLING_STATE st;
    memset(&st, 0, sizeof(st));
    st.Version = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
    if (!getpi(h, JN_ProcessPowerThrottling, &st, sizeof(st))) {
        return -1;
    }
    if ((st.StateMask & PROCESS_POWER_THROTTLING_EXECUTION_SPEED) != 0) {
        return 1;
    }
    return 0;
}

int32_t jn_am_set_efficiency(int32_t pid, int32_t enable) {
    g_last_errno = 0;
    if (pid <= 0) {
        g_last_errno = ERROR_INVALID_PARAMETER;
        return ERROR_INVALID_PARAMETER;
    }
    SetProcessInformation_fn setpi = load_set_proc_info();
    if (setpi == NULL) {
        g_last_errno = ERROR_NOT_SUPPORTED;
        return -1;
    }
    HANDLE h = OpenProcess(PROCESS_SET_INFORMATION | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        DWORD err = GetLastError();
        g_last_errno = (int32_t)err;
        return (int32_t)err;
    }
    JN_PROCESS_POWER_THROTTLING_STATE st;
    memset(&st, 0, sizeof(st));
    st.Version = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
    st.ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED;
    st.StateMask = enable ? PROCESS_POWER_THROTTLING_EXECUTION_SPEED : 0;
    if (!setpi(h, JN_ProcessPowerThrottling, &st, sizeof(st))) {
        DWORD err = GetLastError();
        CloseHandle(h);
        g_last_errno = (int32_t)err;
        return (int32_t)err;
    }
    CloseHandle(h);
    return 0;
}

static int query_image_path(DWORD pid, wchar_t *wpath, DWORD cap) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (h == NULL) {
        h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    }
    if (h == NULL) {
        return -1;
    }
    DWORD n = GetModuleFileNameExW(h, NULL, wpath, cap);
    if (n == 0) {
        DWORD c = cap;
        if (QueryFullProcessImageNameW(h, 0, wpath, &c)) {
            n = c;
        }
    }
    CloseHandle(h);
    return n > 0 ? 0 : -1;
}

int32_t jn_am_open_file_location(int32_t pid) {
    g_last_errno = 0;
    if (pid <= 0) {
        return ERROR_INVALID_PARAMETER;
    }
    wchar_t wpath[MAX_PATH * 4];
    if (query_image_path((DWORD)pid, wpath, (DWORD)(sizeof(wpath) / sizeof(wpath[0]))) != 0) {
        g_last_errno = (int32_t)GetLastError();
        return g_last_errno != 0 ? g_last_errno : ERROR_NOT_FOUND;
    }
    wchar_t params[MAX_PATH * 4 + 32];
    _snwprintf(params, sizeof(params) / sizeof(params[0]), L"/select,\"%s\"", wpath);
    params[(sizeof(params) / sizeof(params[0])) - 1] = L'\0';
    HINSTANCE r = ShellExecuteW(NULL, L"open", L"explorer.exe", params, NULL, SW_SHOWNORMAL);
    if ((INT_PTR)r <= 32) {
        g_last_errno = (int32_t)(INT_PTR)r;
        return g_last_errno;
    }
    return 0;
}

int32_t jn_am_run_task(const char *cmdline) {
    g_last_errno = 0;
    if (cmdline == NULL || cmdline[0] == '\0') {
        g_last_errno = ERROR_INVALID_PARAMETER;
        return ERROR_INVALID_PARAMETER;
    }
    int wlen = MultiByteToWideChar(CP_UTF8, 0, cmdline, -1, NULL, 0);
    if (wlen <= 0) {
        g_last_errno = ERROR_INVALID_PARAMETER;
        return ERROR_INVALID_PARAMETER;
    }
    wchar_t *wcmd = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)wlen);
    if (wcmd == NULL) {
        return ERROR_OUTOFMEMORY;
    }
    MultiByteToWideChar(CP_UTF8, 0, cmdline, -1, wcmd, wlen);
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    memset(&pi, 0, sizeof(pi));
    /* CreateProcess may modify the buffer. */
    BOOL ok = CreateProcessW(NULL, wcmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi);
    free(wcmd);
    if (!ok) {
        DWORD err = GetLastError();
        g_last_errno = (int32_t)err;
        return (int32_t)err;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}

static void lookup_user(HANDLE proc, char *user, size_t user_cap, int *flags) {
    user[0] = '\0';
    HANDLE token = NULL;
    if (!OpenProcessToken(proc, TOKEN_QUERY, &token)) {
        return;
    }
    DWORD need = 0;
    GetTokenInformation(token, TokenUser, NULL, 0, &need);
    if (need == 0) {
        CloseHandle(token);
        return;
    }
    TOKEN_USER *tu = (TOKEN_USER *)malloc(need);
    if (tu == NULL) {
        CloseHandle(token);
        return;
    }
    if (!GetTokenInformation(token, TokenUser, tu, need, &need)) {
        free(tu);
        CloseHandle(token);
        return;
    }
    wchar_t name[128];
    wchar_t domain[128];
    DWORD name_len = 128;
    DWORD domain_len = 128;
    SID_NAME_USE use;
    if (LookupAccountSidW(NULL, tu->User.Sid, name, &name_len, domain, &domain_len, &use)) {
        char utf8[256];
        if (wide_to_utf8(name, utf8, sizeof(utf8)) == 0 && utf8[0] != '\0') {
            strncpy(user, utf8, user_cap - 1);
            user[user_cap - 1] = '\0';
            *flags |= 2;
        }
    }
    free(tu);
    CloseHandle(token);
}

static void read_publisher(const char *utf8_path, char *out, size_t out_cap) {
    out[0] = '\0';
    if (utf8_path == NULL || utf8_path[0] == '\0' || out_cap == 0) {
        return;
    }
    int wlen = MultiByteToWideChar(CP_UTF8, 0, utf8_path, -1, NULL, 0);
    if (wlen <= 0) {
        return;
    }
    wchar_t *wpath = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)wlen);
    if (wpath == NULL) {
        return;
    }
    MultiByteToWideChar(CP_UTF8, 0, utf8_path, -1, wpath, wlen);
    DWORD h = 0;
    DWORD sz = GetFileVersionInfoSizeW(wpath, &h);
    if (sz == 0) {
        free(wpath);
        return;
    }
    void *block = malloc(sz);
    if (block == NULL) {
        free(wpath);
        return;
    }
    if (!GetFileVersionInfoW(wpath, 0, sz, block)) {
        free(block);
        free(wpath);
        return;
    }
    free(wpath);
    struct LANGANDCODEPAGE {
        WORD wLanguage;
        WORD wCodePage;
    } *tr = NULL;
    UINT tr_len = 0;
    if (!VerQueryValueW(block, L"\\VarFileInfo\\Translation", (LPVOID *)&tr, &tr_len)
            || tr == NULL || tr_len < sizeof(*tr)) {
        free(block);
        return;
    }
    wchar_t sub[64];
    _snwprintf(sub, 64, L"\\StringFileInfo\\%04x%04x\\CompanyName", tr[0].wLanguage, tr[0].wCodePage);
    sub[63] = L'\0';
    wchar_t *company = NULL;
    UINT clen = 0;
    if (VerQueryValueW(block, sub, (LPVOID *)&company, &clen) && company != NULL && clen > 0) {
        wide_to_utf8(company, out, out_cap);
        sanitize_field(out);
    }
    free(block);
}

static void query_process_extras(DWORD pid, int64_t *cpu_ns, int64_t *rss, int64_t *footprint,
        int64_t *start_sec, int32_t *start_usec, char *user, size_t user_cap,
        char *path, size_t path_cap, int *flags, int *eco_out) {
    *cpu_ns = -1;
    *rss = -1;
    *footprint = -1;
    *eco_out = -1;
    path[0] = '\0';

    DWORD access = PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ;
    HANDLE h = OpenProcess(access, FALSE, pid);
    if (h == NULL) {
        h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    }
    if (h == NULL) {
        return;
    }

    FILETIME create, exit_t, kernel, user_t;
    if (GetProcessTimes(h, &create, &exit_t, &kernel, &user_t)) {
        filetime_to_unix(&create, start_sec, start_usec);
        int64_t kn = filetime_to_ns(&kernel);
        int64_t un = filetime_to_ns(&user_t);
        if (kn >= 0 && un >= 0 && kn <= INT64_MAX - un) {
            *cpu_ns = kn + un;
            *flags |= 4;
        }
    }

    PROCESS_MEMORY_COUNTERS_EX pmc;
    memset(&pmc, 0, sizeof(pmc));
    pmc.cb = sizeof(pmc);
    if (GetProcessMemoryInfo(h, (PROCESS_MEMORY_COUNTERS *)&pmc, sizeof(pmc))) {
        *rss = (int64_t)pmc.WorkingSetSize;
        *flags |= 8;
        if (pmc.PrivateUsage > 0) {
            *footprint = (int64_t)pmc.PrivateUsage;
            *flags |= 16;
        } else if (pmc.WorkingSetSize > 0) {
            *footprint = (int64_t)pmc.WorkingSetSize;
            *flags |= 16;
        }
    }

    wchar_t wpath[MAX_PATH * 4];
    DWORD n = GetModuleFileNameExW(h, NULL, wpath, (DWORD)(sizeof(wpath) / sizeof(wpath[0])));
    if (n == 0) {
        DWORD cap = (DWORD)(sizeof(wpath) / sizeof(wpath[0]));
        if (QueryFullProcessImageNameW(h, 0, wpath, &cap)) {
            n = cap;
        }
    }
    if (n > 0) {
        if (wide_to_utf8(wpath, path, path_cap) == 0 && path[0] != '\0') {
            *flags |= 128;
        }
    }

    lookup_user(h, user, user_cap, flags);
    *eco_out = read_eco_handle(h);
    CloseHandle(h);
}

/* ---- window map for grouping / hung ---- */
#define JN_WIN_MAP_MAX 8192
static DWORD g_win_pids[JN_WIN_MAP_MAX];
static int g_win_counts[JN_WIN_MAP_MAX];
static int g_win_hung[JN_WIN_MAP_MAX];
static int g_win_n;

static void win_map_clear(void) {
    g_win_n = 0;
}

static void win_map_add(DWORD pid, int hung) {
    int i;
    for (i = 0; i < g_win_n; i++) {
        if (g_win_pids[i] == pid) {
            g_win_counts[i]++;
            if (hung) {
                g_win_hung[i] = 1;
            }
            return;
        }
    }
    if (g_win_n >= JN_WIN_MAP_MAX) {
        return;
    }
    g_win_pids[g_win_n] = pid;
    g_win_counts[g_win_n] = 1;
    g_win_hung[g_win_n] = hung ? 1 : 0;
    g_win_n++;
}

static int win_map_count(DWORD pid) {
    int i;
    for (i = 0; i < g_win_n; i++) {
        if (g_win_pids[i] == pid) {
            return g_win_counts[i];
        }
    }
    return 0;
}

static int win_map_hung(DWORD pid) {
    int i;
    for (i = 0; i < g_win_n; i++) {
        if (g_win_pids[i] == pid) {
            return g_win_hung[i];
        }
    }
    return 0;
}

static BOOL CALLBACK enum_top_windows(HWND hwnd, LPARAM lp) {
    (void)lp;
    if (!IsWindowVisible(hwnd)) {
        return TRUE;
    }
    if (GetWindow(hwnd, GW_OWNER) != NULL) {
        return TRUE;
    }
    LONG_PTR ex = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
    if ((ex & WS_EX_TOOLWINDOW) != 0) {
        return TRUE;
    }
    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);
    if (pid == 0) {
        return TRUE;
    }
    int hung = 0;
    if (IsHungAppWindow(hwnd)) {
        hung = 1;
    }
    win_map_add(pid, hung);
    return TRUE;
}

static int path_is_under_windows(const char *path) {
    if (path == NULL || path[0] == '\0') {
        return 0;
    }
    /* Case-insensitive search for \Windows\ or /Windows/ */
    const char *p = path;
    while (*p) {
        if ((p[0] == '\\' || p[0] == '/')
                && (p[1] == 'W' || p[1] == 'w')
                && (p[2] == 'i' || p[2] == 'I')
                && (p[3] == 'n' || p[3] == 'N')
                && (p[4] == 'd' || p[4] == 'D')
                && (p[5] == 'o' || p[5] == 'O')
                && (p[6] == 'w' || p[6] == 'W')
                && (p[7] == 's' || p[7] == 'S')
                && (p[8] == '\\' || p[8] == '/' || p[8] == '\0')) {
            return 1;
        }
        p++;
    }
    return 0;
}

static int user_is_systemish(const char *user) {
    if (user == NULL) {
        return 0;
    }
    if (_stricmp(user, "SYSTEM") == 0) return 1;
    if (_stricmp(user, "LOCAL SERVICE") == 0) return 1;
    if (_stricmp(user, "NETWORK SERVICE") == 0) return 1;
    if (_stricmp(user, "LOCALSYSTEM") == 0) return 1;
    return 0;
}

static int classify_group(int win_count, const char *name, const char *path, const char *user) {
    if (win_count > 0) {
        return 1; /* Apps */
    }
    if (user_is_systemish(user) || path_is_under_windows(path)) {
        return 2; /* Windows processes */
    }
    /* Well-known system binaries without path */
    if (name != NULL) {
        if (_stricmp(name, "csrss.exe") == 0 || _stricmp(name, "smss.exe") == 0
                || _stricmp(name, "wininit.exe") == 0 || _stricmp(name, "services.exe") == 0
                || _stricmp(name, "lsass.exe") == 0 || _stricmp(name, "svchost.exe") == 0
                || _stricmp(name, "RuntimeBroker.exe") == 0
                || _stricmp(name, "dwm.exe") == 0) {
            return 2;
        }
    }
    return 0; /* Background */
}

char *jn_am_snapshot(void) {
    g_last_errno = 0;
    char *buf = NULL;
    size_t len = 0;
    size_t cap = 0;

    int64_t mono = jn_am_mono_ns();

    SYSTEM_INFO si;
    GetSystemInfo(&si);
    int ncpu = (int)si.dwNumberOfProcessors;
    if (ncpu < 1) {
        ncpu = 1;
    }
    DWORD page_size = si.dwPageSize;
    if (page_size == 0) {
        page_size = 4096;
    }

    MEMORYSTATUSEX ms;
    memset(&ms, 0, sizeof(ms));
    ms.dwLength = sizeof(ms);
    unsigned long long mem_total = 0;
    unsigned long long pages_free = 0;
    int sys_err = 0;
    if (GlobalMemoryStatusEx(&ms)) {
        mem_total = ms.ullTotalPhys;
        pages_free = ms.ullAvailPhys / page_size;
    } else {
        sys_err = 1;
    }

    FILETIME idle, kernel, user;
    unsigned int cpu_user = 0, cpu_sys = 0, cpu_idle = 0, cpu_nice = 0;
    if (GetSystemTimes(&idle, &kernel, &user)) {
        ULARGE_INTEGER iu, ku, uu;
        iu.LowPart = idle.dwLowDateTime; iu.HighPart = idle.dwHighDateTime;
        ku.LowPart = kernel.dwLowDateTime; ku.HighPart = kernel.dwHighDateTime;
        uu.LowPart = user.dwLowDateTime; uu.HighPart = user.dwHighDateTime;
        unsigned long long kbusy = (ku.QuadPart > iu.QuadPart) ? (ku.QuadPart - iu.QuadPart) : 0;
        cpu_idle = (unsigned int)(iu.QuadPart / 100000ULL);
        cpu_sys = (unsigned int)(kbusy / 100000ULL);
        cpu_user = (unsigned int)(uu.QuadPart / 100000ULL);
        cpu_nice = 0;
    } else {
        sys_err = 1;
    }

    unsigned long long pages_active = 0;
    if (mem_total > 0 && page_size > 0) {
        unsigned long long used = mem_total > ms.ullAvailPhys ? (mem_total - ms.ullAvailPhys) : 0;
        pages_active = used / page_size;
    }

    if (append_fmt(&buf, &len, &cap,
            "S\t%lld\t%d\t%llu\t%lu\t%llu\t%llu\t0\t0\t0\t%u\t%u\t%u\t%u\t%d\n",
            (long long)mono, ncpu, mem_total,
            (unsigned long)page_size,
            pages_free, pages_active,
            cpu_user, cpu_sys, cpu_idle, cpu_nice, sys_err) != 0) {
        free(buf);
        return NULL;
    }

    win_map_clear();
    EnumWindows(enum_top_windows, 0);

    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) {
        g_last_errno = (int32_t)GetLastError();
        return buf;
    }

    PROCESSENTRY32W pe;
    memset(&pe, 0, sizeof(pe));
    pe.dwSize = sizeof(pe);

    if (!Process32FirstW(snap, &pe)) {
        g_last_errno = (int32_t)GetLastError();
        CloseHandle(snap);
        return buf;
    }

    do {
        DWORD pid = pe.th32ProcessID;
        if (pid == 0) {
            continue;
        }

        int flags = 64;
        int32_t ppid = (int32_t)pe.th32ParentProcessID;
        int32_t uid = -1;
        int64_t start_sec = -1;
        int32_t start_usec = -1;
        int64_t cpu_ns = -1;
        int64_t rss = -1;
        int64_t footprint = -1;
        int32_t nthreads = (int32_t)pe.cntThreads;
        int eco = -1;
        if (nthreads >= 0) {
            flags |= 32;
        }

        char name[512];
        char user_name[128];
        char path[1024];
        char publisher[256];
        name[0] = '\0';
        user_name[0] = '\0';
        path[0] = '\0';
        publisher[0] = '\0';

        if (wide_to_utf8(pe.szExeFile, name, sizeof(name)) == 0 && name[0] != '\0') {
            flags |= 1;
        }

        query_process_extras(pid, &cpu_ns, &rss, &footprint, &start_sec, &start_usec,
                user_name, sizeof(user_name), path, sizeof(path), &flags, &eco);

        if ((flags & 128) != 0) {
            read_publisher(path, publisher, sizeof(publisher));
        }

        sanitize_field(name);
        sanitize_field(user_name);
        sanitize_field(path);
        sanitize_field(publisher);
        if (name[0] == '\0') {
            snprintf(name, sizeof(name), "pid-%lu", (unsigned long)pid);
            flags |= 1;
        }
        if (user_name[0] == '\0') {
            snprintf(user_name, sizeof(user_name), "N/A");
        }

        int win_count = win_map_count(pid);
        int group_class = classify_group(win_count, name, path, user_name);
        int status_bits = 0;
        if (eco == 1) {
            status_bits |= 1;
        }
        if (win_map_hung(pid)) {
            status_bits |= 2;
        }

        if (append_fmt(&buf, &len, &cap,
                "P\t%lu\t%d\t%d\t%lld\t%d\t%lld\t%lld\t%lld\t%d\t%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\n",
                (unsigned long)pid, (int)ppid, (int)uid,
                (long long)start_sec, (int)start_usec,
                (long long)cpu_ns, (long long)rss, (long long)footprint,
                (int)nthreads, flags, user_name, name, path,
                win_count, group_class, status_bits, eco, publisher) != 0) {
            CloseHandle(snap);
            free(buf);
            return NULL;
        }
    } while (Process32NextW(snap, &pe));

    CloseHandle(snap);
    return buf;
}
