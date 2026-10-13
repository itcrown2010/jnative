/* Author: 张晓达
 * Windows process / host sampling for JNative Task Monitor.
 * Toolhelp32 + GetProcessTimes + GetProcessMemoryInfo + GetSystemTimes.
 * No mock data; unavailable fields clear flag bits and report -1.
 */
#define WIN32_LEAN_AND_MEAN
#include "jn_am.h"

#include <windows.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <sddl.h>

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "advapi32.lib")

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
    /* ns = ticks * 1e9 / freq */
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

/* FILETIME (100ns since 1601) → Unix sec + usec. */
static void filetime_to_unix(const FILETIME *ft, int64_t *sec_out, int32_t *usec_out) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    /* 116444736000000000 = 100ns ticks from 1601 to 1970 */
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
    /* 100-ns units → ns */
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
    /* Re-check still alive before terminate. */
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
        /* Access denied often means the process still exists. */
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

static void query_process_extras(DWORD pid, int64_t *cpu_ns, int64_t *rss, int64_t *footprint,
        int64_t *start_sec, int32_t *start_usec, char *user, size_t user_cap,
        char *path, size_t path_cap, int *flags) {
    *cpu_ns = -1;
    *rss = -1;
    *footprint = -1;
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
        /* PrivateUsage ≈ commit / “memory” footprint analogue. */
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
    CloseHandle(h);
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
        /* Compress FILETIME into 32-bit tick buckets (100ns → ~0.1ms units truncated). */
        ULARGE_INTEGER iu, ku, uu;
        iu.LowPart = idle.dwLowDateTime; iu.HighPart = idle.dwHighDateTime;
        ku.LowPart = kernel.dwLowDateTime; ku.HighPart = kernel.dwHighDateTime;
        uu.LowPart = user.dwLowDateTime; uu.HighPart = user.dwHighDateTime;
        /* kernel includes idle on Windows — subtract for busy kernel. */
        unsigned long long kbusy = (ku.QuadPart > iu.QuadPart) ? (ku.QuadPart - iu.QuadPart) : 0;
        cpu_idle = (unsigned int)(iu.QuadPart / 100000ULL);
        cpu_sys = (unsigned int)(kbusy / 100000ULL);
        cpu_user = (unsigned int)(uu.QuadPart / 100000ULL);
        cpu_nice = 0;
    } else {
        sys_err = 1;
    }

    /* pages_active ≈ used; others N/A → 0 */
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

        int flags = 64; /* info_ok from Toolhelp */
        int32_t ppid = (int32_t)pe.th32ParentProcessID;
        int32_t uid = -1;
        int64_t start_sec = -1;
        int32_t start_usec = -1;
        int64_t cpu_ns = -1;
        int64_t rss = -1;
        int64_t footprint = -1;
        int32_t nthreads = (int32_t)pe.cntThreads;
        if (nthreads >= 0) {
            flags |= 32;
        }

        char name[512];
        char user[128];
        char path[1024];
        name[0] = '\0';
        user[0] = '\0';
        path[0] = '\0';

        if (wide_to_utf8(pe.szExeFile, name, sizeof(name)) == 0 && name[0] != '\0') {
            flags |= 1;
        }

        query_process_extras(pid, &cpu_ns, &rss, &footprint, &start_sec, &start_usec,
                user, sizeof(user), path, sizeof(path), &flags);

        sanitize_field(name);
        sanitize_field(user);
        sanitize_field(path);
        if (name[0] == '\0') {
            snprintf(name, sizeof(name), "pid-%lu", (unsigned long)pid);
            flags |= 1;
        }
        if (user[0] == '\0') {
            snprintf(user, sizeof(user), "N/A");
        }

        if (append_fmt(&buf, &len, &cap,
                "P\t%lu\t%d\t%d\t%lld\t%d\t%lld\t%lld\t%lld\t%d\t%d\t%s\t%s\t%s\n",
                (unsigned long)pid, (int)ppid, (int)uid,
                (long long)start_sec, (int)start_usec,
                (long long)cpu_ns, (long long)rss, (long long)footprint,
                (int)nthreads, flags, user, name, path) != 0) {
            CloseHandle(snap);
            free(buf);
            return NULL;
        }
    } while (Process32NextW(snap, &pe));

    CloseHandle(snap);
    return buf;
}
