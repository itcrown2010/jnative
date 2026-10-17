/* Author: 张晓达
 * JNative Windows Task Monitor - native data layer. See jn_tm.h for the ABI.
 *
 * Static link deps: advapi32, iphlpapi, shell32, version.
 * Everything else (pdh, wtsapi32, ntdll Nt*, powrprof, dxgi, dbghelp, user32,
 * shlwapi) is resolved at run time with LoadLibrary/GetProcAddress.
 * No mock data: unavailable values are -1 / "".
 */
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#ifndef WINVER
#define WINVER 0x0A00
#endif
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <netioapi.h>
#include <winioctl.h>
#include <ntddstor.h>
#include <shellapi.h>
#include <shlobj.h>
#include <winsvc.h>
#include <wtsapi32.h>
#include <pdh.h>

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <wctype.h>

#include "jn_tm.h"

#ifndef IMAGE_FILE_MACHINE_ARM64
#define IMAGE_FILE_MACHINE_ARM64 0xAA64
#endif
#ifndef IMAGE_FILE_MACHINE_ARMNT
#define IMAGE_FILE_MACHINE_ARMNT 0x01C4
#endif
#ifndef CSIDL_STARTUP
#define CSIDL_STARTUP 0x0007
#endif
#ifndef CSIDL_COMMON_STARTUP
#define CSIDL_COMMON_STARTUP 0x0018
#endif

/* ======================================================================== */
/* Generic helpers                                                          */
/* ======================================================================== */

#define TM_STATUS_INFO_LENGTH_MISMATCH ((LONG)0xC0000004)
#define TM_STATUS_UNSUCCESSFUL ((LONG)0xC0000001)

void jn_tm_free(char *p) {
    free(p);
}

static int64_t now_ns(void) {
    static LARGE_INTEGER freq;
    LARGE_INTEGER c;
    if (freq.QuadPart == 0) {
        if (!QueryPerformanceFrequency(&freq) || freq.QuadPart == 0) {
            freq.QuadPart = 0;
            return 0;
        }
    }
    QueryPerformanceCounter(&c);
    /* split to avoid overflow of counter * 1e9 */
    int64_t sec = c.QuadPart / freq.QuadPart;
    int64_t rem = c.QuadPart % freq.QuadPart;
    return sec * 1000000000LL + rem * 1000000000LL / freq.QuadPart;
}

static HMODULE load_sys(const wchar_t *name) {
    HMODULE h = GetModuleHandleW(name);
    if (h) {
        return h;
    }
    h = LoadLibraryExW(name, NULL, LOAD_LIBRARY_SEARCH_SYSTEM32);
    return h;
}

static wchar_t *u2w_dup(const char *u) {
    if (u == NULL) {
        return NULL;
    }
    int n = MultiByteToWideChar(CP_UTF8, 0, u, -1, NULL, 0);
    if (n <= 0) {
        return NULL;
    }
    wchar_t *w = (wchar_t *)malloc((size_t)n * sizeof(wchar_t));
    if (w == NULL) {
        return NULL;
    }
    if (MultiByteToWideChar(CP_UTF8, 0, u, -1, w, n) <= 0) {
        free(w);
        return NULL;
    }
    return w;
}

static char *w2u_dup(const wchar_t *w) {
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
    if (WideCharToMultiByte(CP_UTF8, 0, w, -1, u, n, NULL, NULL) <= 0) {
        free(u);
        return NULL;
    }
    return u;
}

static char *dup_str(const char *s) {
    size_t n = strlen(s) + 1;
    char *p = (char *)malloc(n);
    if (p) {
        memcpy(p, s, n);
    }
    return p;
}

/* ---------------- string builder ---------------- */

typedef struct {
    char *p;
    size_t len, cap;
    int oom;
} sb_t;

static int sb_grow(sb_t *s, size_t add) {
    if (s->oom) {
        return -1;
    }
    if (s->len + add + 1 > s->cap) {
        size_t nc = s->cap ? s->cap : 4096;
        while (nc < s->len + add + 1) {
            nc *= 2;
        }
        char *np = (char *)realloc(s->p, nc);
        if (np == NULL) {
            s->oom = 1;
            return -1;
        }
        s->p = np;
        s->cap = nc;
    }
    return 0;
}

static void sb_raw(sb_t *s, const char *d, size_t n) {
    if (sb_grow(s, n)) {
        return;
    }
    memcpy(s->p + s->len, d, n);
    s->len += n;
    s->p[s->len] = '\0';
}

static void sb_tab(sb_t *s) { sb_raw(s, "\t", 1); }
static void sb_nl(sb_t *s) { sb_raw(s, "\n", 1); }

/* UTF-8 text, tab/CR/LF -> space */
static void sb_str(sb_t *s, const char *u) {
    if (u == NULL) {
        return;
    }
    size_t n = strlen(u);
    if (sb_grow(s, n)) {
        return;
    }
    for (size_t i = 0; i < n; i++) {
        char c = u[i];
        if (c == '\t' || c == '\r' || c == '\n') {
            c = ' ';
        }
        s->p[s->len + i] = c;
    }
    s->len += n;
    s->p[s->len] = '\0';
}

static void sb_w(sb_t *s, const wchar_t *w) {
    if (w == NULL || w[0] == 0) {
        return;
    }
    char *u = w2u_dup(w);
    if (u) {
        sb_str(s, u);
        free(u);
    }
}

static void sb_i(sb_t *s, int64_t v) {
    char t[40];
    int n = snprintf(t, sizeof(t), "%lld", (long long)v);
    if (n > 0) {
        sb_raw(s, t, (size_t)n);
    }
}

static void sb_tab_i(sb_t *s, int64_t v) {
    sb_tab(s);
    sb_i(s, v);
}

static void sb_tab_s(sb_t *s, const char *u) {
    sb_tab(s);
    sb_str(s, u);
}

static void sb_tab_w(sb_t *s, const wchar_t *w) {
    sb_tab(s);
    sb_w(s, w);
}

static char *sb_done(sb_t *s) {
    if (s->oom) {
        free(s->p);
        return NULL;
    }
    if (s->p == NULL) {
        s->p = (char *)malloc(1);
        if (s->p) {
            s->p[0] = '\0';
        }
    }
    return s->p;
}

/* ---------------- case-insensitive wide-string hash map ---------------- */

typedef struct {
    wchar_t *key;
    void *val;
    uint32_t h;
} sm_slot;

typedef struct {
    sm_slot *s;
    size_t cap, n;
} smap;

static uint32_t sm_hash(const wchar_t *k) {
    uint32_t h = 2166136261u;
    for (; *k; k++) {
        h ^= (uint32_t)towlower((wint_t)*k);
        h *= 16777619u;
    }
    return h ? h : 1u;
}

static int sm_eq(const wchar_t *a, const wchar_t *b) {
    while (*a && *b) {
        if (towlower((wint_t)*a) != towlower((wint_t)*b)) {
            return 0;
        }
        a++;
        b++;
    }
    return *a == *b;
}

static void *sm_get(smap *m, const wchar_t *k) {
    if (m->cap == 0) {
        return NULL;
    }
    uint32_t h = sm_hash(k);
    size_t i = h & (m->cap - 1);
    while (m->s[i].key) {
        if (m->s[i].h == h && sm_eq(m->s[i].key, k)) {
            return m->s[i].val;
        }
        i = (i + 1) & (m->cap - 1);
    }
    return NULL;
}

static int sm_put(smap *m, const wchar_t *k, void *v) {
    if ((m->n + 1) * 2 > m->cap) {
        size_t nc = m->cap ? m->cap * 2 : 256;
        sm_slot *ns = (sm_slot *)calloc(nc, sizeof(sm_slot));
        if (ns == NULL) {
            return -1;
        }
        for (size_t i = 0; i < m->cap; i++) {
            if (m->s[i].key) {
                size_t j = m->s[i].h & (nc - 1);
                while (ns[j].key) {
                    j = (j + 1) & (nc - 1);
                }
                ns[j] = m->s[i];
            }
        }
        free(m->s);
        m->s = ns;
        m->cap = nc;
    }
    uint32_t h = sm_hash(k);
    size_t i = h & (m->cap - 1);
    while (m->s[i].key) {
        if (m->s[i].h == h && sm_eq(m->s[i].key, k)) {
            m->s[i].val = v;
            return 0;
        }
        i = (i + 1) & (m->cap - 1);
    }
    wchar_t *kd = _wcsdup(k);
    if (kd == NULL) {
        return -1;
    }
    m->s[i].key = kd;
    m->s[i].val = v;
    m->s[i].h = h;
    m->n++;
    return 0;
}

/* ---------------- registry helpers ---------------- */

static int reg_get_sz(HKEY root, const wchar_t *sub, const wchar_t *name, wchar_t *out, DWORD cap_chars) {
    HKEY k;
    out[0] = 0;
    if (RegOpenKeyExW(root, sub, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
        return -1;
    }
    DWORD type = 0, sz = (cap_chars - 1) * (DWORD)sizeof(wchar_t);
    LONG r = RegQueryValueExW(k, name, NULL, &type, (LPBYTE)out, &sz);
    RegCloseKey(k);
    if (r != ERROR_SUCCESS || (type != REG_SZ && type != REG_EXPAND_SZ)) {
        out[0] = 0;
        return -1;
    }
    out[sz / sizeof(wchar_t)] = 0;
    return 0;
}

static int reg_get_dword(HKEY root, const wchar_t *sub, const wchar_t *name, DWORD *out) {
    HKEY k;
    if (RegOpenKeyExW(root, sub, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
        return -1;
    }
    DWORD type = 0, sz = sizeof(DWORD);
    LONG r = RegQueryValueExW(k, name, NULL, &type, (LPBYTE)out, &sz);
    RegCloseKey(k);
    return (r == ERROR_SUCCESS && type == REG_DWORD) ? 0 : -1;
}

static void wtrim(wchar_t *s) {
    size_t n = wcslen(s);
    while (n > 0 && iswspace((wint_t)s[n - 1])) {
        s[--n] = 0;
    }
    size_t a = 0;
    while (s[a] && iswspace((wint_t)s[a])) {
        a++;
    }
    if (a) {
        memmove(s, s + a, (n - a + 1) * sizeof(wchar_t));
    }
}

/* ======================================================================== */
/* NT / dynamic function pointers                                           */
/* ======================================================================== */

typedef struct {
    USHORT Length, MaximumLength;
    PWSTR Buffer;
} tm_ustr;

typedef struct {
    ULONG NextEntryOffset, NumberOfThreads;
    LARGE_INTEGER WorkingSetPrivateSize;
    ULONG HardFaultCount, NumberOfThreadsHighWatermark;
    ULONGLONG CycleTime;
    LARGE_INTEGER CreateTime, UserTime, KernelTime;
    tm_ustr ImageName;
    LONG BasePriority;
    HANDLE UniqueProcessId, InheritedFromUniqueProcessId;
    ULONG HandleCount, SessionId;
    ULONG_PTR UniqueProcessKey;
    SIZE_T PeakVirtualSize, VirtualSize;
    ULONG PageFaultCount;
    SIZE_T PeakWorkingSetSize, WorkingSetSize;
    SIZE_T QuotaPeakPagedPoolUsage, QuotaPagedPoolUsage;
    SIZE_T QuotaPeakNonPagedPoolUsage, QuotaNonPagedPoolUsage;
    SIZE_T PagefileUsage, PeakPagefileUsage, PrivatePageCount;
    LARGE_INTEGER ReadOperationCount, WriteOperationCount, OtherOperationCount;
    LARGE_INTEGER ReadTransferCount, WriteTransferCount, OtherTransferCount;
} tm_sysproc;

typedef struct {
    LARGE_INTEGER KernelTime, UserTime, CreateTime;
    ULONG WaitTime;
    PVOID StartAddress;
    HANDLE ClientIdProcess, ClientIdThread;
    LONG Priority, BasePriority;
    ULONG ContextSwitches, ThreadState, WaitReason;
} tm_systhread;

#ifdef _WIN64
_Static_assert(sizeof(tm_sysproc) == 0x100, "SYSTEM_PROCESS_INFORMATION layout");
_Static_assert(sizeof(tm_systhread) == 80, "SYSTEM_THREAD_INFORMATION layout");
#endif

typedef struct {
    LARGE_INTEGER IdleTime, KernelTime, UserTime, DpcTime, InterruptTime;
    ULONG InterruptCount;
} tm_sppi;

typedef struct {
    ULONG Number, MaxMhz, CurrentMhz, MhzLimit, MaxIdleState, CurrentIdleState;
} tm_ppi;

typedef LONG(NTAPI *fn_NtQuerySystemInformation)(ULONG, PVOID, ULONG, PULONG);
typedef LONG(NTAPI *fn_NtQueryInformationProcess)(HANDLE, ULONG, PVOID, ULONG, PULONG);
typedef LONG(NTAPI *fn_NtProcRes)(HANDLE);
typedef ULONG(NTAPI *fn_RtlNtStatusToDosError)(LONG);
typedef PWSTR(NTAPI *fn_RtlIpv6AddressToStringW)(const void *, PWSTR);
typedef LONG(WINAPI *fn_CallNtPowerInformation)(int, PVOID, ULONG, PVOID, ULONG);
typedef BOOL(WINAPI *fn_IsWow64Process2)(HANDLE, USHORT *, USHORT *);
typedef BOOL(WINAPI *fn_GetPerformanceInfo)(void *, DWORD);
typedef DWORD(WINAPI *fn_GetGuiResources)(HANDLE, DWORD);

static fn_NtQuerySystemInformation p_NtQSI;
static fn_NtQueryInformationProcess p_NtQIP;
static fn_NtProcRes p_NtSuspend, p_NtResume;
static fn_RtlNtStatusToDosError p_NtStatusToDos;
static fn_RtlIpv6AddressToStringW p_Ipv6ToStr;
static fn_CallNtPowerInformation p_CallNtPower;
static fn_IsWow64Process2 p_IsWow64Process2;
static fn_GetPerformanceInfo p_GetPerfInfo;
static fn_GetGuiResources p_GetGuiResources;

static void dyn_init(void) {
    static int done;
    if (done) {
        return;
    }
    done = 1;
    HMODULE nt = GetModuleHandleW(L"ntdll.dll");
    if (nt) {
        p_NtQSI = (fn_NtQuerySystemInformation)(void *)GetProcAddress(nt, "NtQuerySystemInformation");
        p_NtQIP = (fn_NtQueryInformationProcess)(void *)GetProcAddress(nt, "NtQueryInformationProcess");
        p_NtSuspend = (fn_NtProcRes)(void *)GetProcAddress(nt, "NtSuspendProcess");
        p_NtResume = (fn_NtProcRes)(void *)GetProcAddress(nt, "NtResumeProcess");
        p_NtStatusToDos = (fn_RtlNtStatusToDosError)(void *)GetProcAddress(nt, "RtlNtStatusToDosError");
        p_Ipv6ToStr = (fn_RtlIpv6AddressToStringW)(void *)GetProcAddress(nt, "RtlIpv6AddressToStringW");
    }
    HMODULE k32 = GetModuleHandleW(L"kernel32.dll");
    if (k32) {
        p_IsWow64Process2 = (fn_IsWow64Process2)(void *)GetProcAddress(k32, "IsWow64Process2");
        p_GetPerfInfo = (fn_GetPerformanceInfo)(void *)GetProcAddress(k32, "K32GetPerformanceInfo");
    }
    HMODULE pp = load_sys(L"powrprof.dll");
    if (pp) {
        p_CallNtPower = (fn_CallNtPowerInformation)(void *)GetProcAddress(pp, "CallNtPowerInformation");
    }
    HMODULE u32 = load_sys(L"user32.dll");
    if (u32) {
        p_GetGuiResources = (fn_GetGuiResources)(void *)GetProcAddress(u32, "GetGuiResources");
    }
}

static int32_t nt_to_win32(LONG st) {
    if (st >= 0) {
        return 0;
    }
    if (p_NtStatusToDos) {
        ULONG e = p_NtStatusToDos(st);
        if (e != 0) {
            return (int32_t)e;
        }
    }
    return (int32_t)st;
}

/* ---- SystemProcessInformation snapshot buffer (shared) ---- */

static BYTE *g_sp_buf;
static ULONG g_sp_cap;

static LONG sp_query(void) {
    dyn_init();
    if (p_NtQSI == NULL) {
        return TM_STATUS_UNSUCCESSFUL;
    }
    for (int i = 0; i < 12; i++) {
        if (g_sp_buf == NULL) {
            g_sp_cap = 1u << 20;
            g_sp_buf = (BYTE *)malloc(g_sp_cap);
            if (g_sp_buf == NULL) {
                g_sp_cap = 0;
                return TM_STATUS_UNSUCCESSFUL;
            }
        }
        ULONG ret = 0;
        LONG st = p_NtQSI(5 /* SystemProcessInformation */, g_sp_buf, g_sp_cap, &ret);
        if (st >= 0) {
            return st;
        }
        if (st == TM_STATUS_INFO_LENGTH_MISMATCH) {
            ULONG nc = ret + (ret >> 2) + 65536u;
            if (nc <= g_sp_cap) {
                nc = g_sp_cap * 2;
            }
            BYTE *nb = (BYTE *)realloc(g_sp_buf, nc);
            if (nb == NULL) {
                return TM_STATUS_UNSUCCESSFUL;
            }
            g_sp_buf = nb;
            g_sp_cap = nc;
            continue;
        }
        return st;
    }
    return TM_STATUS_INFO_LENGTH_MISMATCH;
}

#define SP_FOREACH(var)                                                                   \
    for (tm_sysproc *var = (tm_sysproc *)g_sp_buf; var != NULL;                           \
         var = (var->NextEntryOffset ? (tm_sysproc *)((BYTE *)var + var->NextEntryOffset) \
                                      : NULL))

/* ======================================================================== */
/* File version info cache (FileDescription / CompanyName)                  */
/* ======================================================================== */

typedef struct {
    char *desc;
    char *company;
} vi_t;

static smap g_vi;

static char *vi_query(const void *blk, WORD lang, WORD cp, const wchar_t *field) {
    wchar_t sub[96];
    _snwprintf(sub, 96, L"\\StringFileInfo\\%04x%04x\\%ls", lang, cp, field);
    sub[95] = 0;
    LPVOID p = NULL;
    UINT len = 0;
    if (VerQueryValueW(blk, sub, &p, &len) && p && len > 0) {
        wchar_t tmp[512];
        size_t n = wcsnlen((const wchar_t *)p, len);
        if (n >= 511) {
            n = 511;
        }
        memcpy(tmp, p, n * sizeof(wchar_t));
        tmp[n] = 0;
        wtrim(tmp);
        if (tmp[0]) {
            return w2u_dup(tmp);
        }
    }
    return NULL;
}

static vi_t *vi_load(const wchar_t *path) {
    vi_t *e = (vi_t *)calloc(1, sizeof(vi_t));
    if (e == NULL) {
        return NULL;
    }
    DWORD dummy = 0;
    DWORD sz = GetFileVersionInfoSizeW(path, &dummy);
    if (sz > 0 && sz < (8u << 20)) {
        void *blk = malloc(sz);
        if (blk && GetFileVersionInfoW(path, 0, sz, blk)) {
            struct {
                WORD lang, cp;
            } *tr = NULL;
            UINT trlen = 0;
            WORD tried[8][2];
            int nt = 0;
            if (VerQueryValueW(blk, L"\\VarFileInfo\\Translation", (LPVOID *)&tr, &trlen) && tr) {
                for (UINT i = 0; i < trlen / 4 && nt < 4; i++) {
                    tried[nt][0] = tr[i].lang;
                    tried[nt][1] = tr[i].cp;
                    nt++;
                }
            }
            tried[nt][0] = 0x0409;
            tried[nt][1] = 0x04B0;
            nt++;
            tried[nt][0] = 0x0409;
            tried[nt][1] = 0x04E4;
            nt++;
            tried[nt][0] = 0x0000;
            tried[nt][1] = 0x04B0;
            nt++;
            for (int i = 0; i < nt && (e->desc == NULL || e->company == NULL); i++) {
                if (e->desc == NULL) {
                    e->desc = vi_query(blk, tried[i][0], tried[i][1], L"FileDescription");
                }
                if (e->company == NULL) {
                    e->company = vi_query(blk, tried[i][0], tried[i][1], L"CompanyName");
                }
            }
        }
        free(blk);
    }
    if (e->desc == NULL) {
        e->desc = dup_str("");
    }
    if (e->company == NULL) {
        e->company = dup_str("");
    }
    return e;
}

/* Returns the cached entry, loading it unless the deadline (mono ns, 0 = none) passed. */
static vi_t *vi_get(const wchar_t *path, int64_t deadline_ns) {
    if (path == NULL || path[0] == 0) {
        return NULL;
    }
    vi_t *e = (vi_t *)sm_get(&g_vi, path);
    if (e) {
        return e;
    }
    if (deadline_ns != 0 && now_ns() > deadline_ns) {
        return NULL;
    }
    e = vi_load(path);
    if (e && sm_put(&g_vi, path, e) != 0) {
        free(e->desc);
        free(e->company);
        free(e);
        return NULL;
    }
    return e;
}

/* ======================================================================== */
/* PDH (dynamic)                                                            */
/* ======================================================================== */

typedef PDH_STATUS(WINAPI *fn_PdhOpenQueryW)(LPCWSTR, DWORD_PTR, PDH_HQUERY *);
typedef PDH_STATUS(WINAPI *fn_PdhAddEnglishCounterW)(PDH_HQUERY, LPCWSTR, DWORD_PTR, PDH_HCOUNTER *);
typedef PDH_STATUS(WINAPI *fn_PdhCollectQueryData)(PDH_HQUERY);
typedef PDH_STATUS(WINAPI *fn_PdhGetFormattedCounterValue)(PDH_HCOUNTER, DWORD, LPDWORD, PDH_FMT_COUNTERVALUE *);
typedef PDH_STATUS(WINAPI *fn_PdhGetFormattedCounterArrayW)(PDH_HCOUNTER, DWORD, LPDWORD, LPDWORD, PDH_FMT_COUNTERVALUE_ITEM_W *);

static fn_PdhOpenQueryW pdh_OpenQuery;
static fn_PdhAddEnglishCounterW pdh_AddEnglish;
static fn_PdhCollectQueryData pdh_Collect;
static fn_PdhGetFormattedCounterValue pdh_GetValue;
static fn_PdhGetFormattedCounterArrayW pdh_GetArray;

#define TM_PDH_MORE_DATA ((PDH_STATUS)0x800007D2L)

static int pdh_load(void) {
    static int tried, ok;
    if (tried) {
        return ok;
    }
    tried = 1;
    HMODULE h = load_sys(L"pdh.dll");
    if (h == NULL) {
        return 0;
    }
    pdh_OpenQuery = (fn_PdhOpenQueryW)(void *)GetProcAddress(h, "PdhOpenQueryW");
    pdh_AddEnglish = (fn_PdhAddEnglishCounterW)(void *)GetProcAddress(h, "PdhAddEnglishCounterW");
    pdh_Collect = (fn_PdhCollectQueryData)(void *)GetProcAddress(h, "PdhCollectQueryData");
    pdh_GetValue = (fn_PdhGetFormattedCounterValue)(void *)GetProcAddress(h, "PdhGetFormattedCounterValue");
    pdh_GetArray = (fn_PdhGetFormattedCounterArrayW)(void *)GetProcAddress(h, "PdhGetFormattedCounterArrayW");
    ok = pdh_OpenQuery && pdh_AddEnglish && pdh_Collect && pdh_GetValue && pdh_GetArray;
    return ok;
}

typedef struct {
    PDH_FMT_COUNTERVALUE_ITEM_W *buf;
    DWORD cap; /* bytes */
    DWORD count;
} pdh_arr;

/* Fetch wildcard instances; returns 1 on success (arr->buf/count valid). */
static int pdh_fetch_array(PDH_HCOUNTER c, DWORD fmt, pdh_arr *a) {
    a->count = 0;
    for (int i = 0; i < 4; i++) {
        DWORD sz = a->cap, cnt = 0;
        PDH_STATUS st = pdh_GetArray(c, fmt, &sz, &cnt, a->buf);
        if (st == ERROR_SUCCESS) {
            a->count = cnt;
            return 1;
        }
        if (st == TM_PDH_MORE_DATA) {
            DWORD nc = sz + 4096;
            PDH_FMT_COUNTERVALUE_ITEM_W *nb = (PDH_FMT_COUNTERVALUE_ITEM_W *)realloc(a->buf, nc);
            if (nb == NULL) {
                return 0;
            }
            a->buf = nb;
            a->cap = nc;
            continue;
        }
        return 0;
    }
    return 0;
}

static int pdh_large(PDH_HCOUNTER c, int64_t *out) {
    PDH_FMT_COUNTERVALUE v;
    if (c == NULL) {
        return 0;
    }
    if (pdh_GetValue(c, PDH_FMT_LARGE, NULL, &v) != ERROR_SUCCESS) {
        return 0;
    }
    if (v.CStatus != 0 && v.CStatus != 1) {
        return 0;
    }
    *out = (int64_t)v.largeValue;
    return 1;
}

/* ======================================================================== */
/* SMBIOS (memory speed / slots / form factor)                              */
/* ======================================================================== */

static int g_mem_speed = -1, g_mem_used = -1, g_mem_total = -1;
static char g_mem_ff[32];

static const char *smbios_ff_name(unsigned ff) {
    switch (ff) {
    case 3: return "SIMM";
    case 4: return "SIP";
    case 5: return "Chip";
    case 6: return "DIP";
    case 7: return "ZIP";
    case 8: return "Proprietary Card";
    case 9: return "DIMM";
    case 10: return "TSOP";
    case 11: return "Row of chips";
    case 12: return "RIMM";
    case 13: return "SODIMM";
    case 14: return "SRIMM";
    case 15: return "FB-DIMM";
    case 16: return "Die";
    default: return "";
    }
}

static void smbios_init(void) {
    static int done;
    if (done) {
        return;
    }
    done = 1;
    UINT sz = GetSystemFirmwareTable('RSMB', 0, NULL, 0);
    if (sz < 8 || sz > (4u << 20)) {
        return;
    }
    BYTE *buf = (BYTE *)malloc(sz);
    if (buf == NULL) {
        return;
    }
    UINT got = GetSystemFirmwareTable('RSMB', 0, buf, sz);
    if (got < 8 || got > sz) {
        free(buf);
        return;
    }
    DWORD tlen = *(DWORD *)(buf + 4);
    const BYTE *p = buf + 8;
    const BYTE *end = p + ((tlen <= got - 8) ? tlen : (got - 8));
    int total = 0, used = 0, speed = 0;
    unsigned ff = 0;
    int have17 = 0;
    while (p + 4 <= end) {
        BYTE type = p[0], l = p[1];
        if (l < 4 || p + l > end || type == 127) {
            break;
        }
        if (type == 17 && l >= 0x15) {
            have17 = 1;
            total++;
            unsigned size = *(const WORD *)(p + 0x0C);
            if (size != 0) {
                used++;
                if (ff == 0 && p[0x0E] != 0) {
                    ff = p[0x0E];
                }
                unsigned sp = 0;
                if (l >= 0x22) {
                    sp = *(const WORD *)(p + 0x20); /* configured memory speed */
                }
                if ((sp == 0 || sp == 0xFFFF) && l >= 0x17) {
                    sp = *(const WORD *)(p + 0x15);
                }
                if (sp != 0 && sp != 0xFFFF && (int)sp > speed) {
                    speed = (int)sp;
                }
            }
        }
        const BYTE *q = p + l;
        while (q + 1 < end && !(q[0] == 0 && q[1] == 0)) {
            q++;
        }
        p = q + 2;
    }
    free(buf);
    if (have17) {
        g_mem_total = total;
        g_mem_used = used;
        g_mem_speed = speed > 0 ? speed : -1;
        snprintf(g_mem_ff, sizeof(g_mem_ff), "%s", smbios_ff_name(ff));
    }
}

/* ======================================================================== */
/* CPU static info                                                          */
/* ======================================================================== */

static char g_cpu_I_line[512];
static int g_cpu_base_mhz = -1;
static int g_cpu_info_done;
static int g_ncpu;

static int popcount_u64(ULONG_PTR m) {
    int n = 0;
    while (m) {
        n += (int)(m & 1);
        m >>= 1;
    }
    return n;
}

static void cpu_static_init(void) {
    if (g_cpu_info_done) {
        return;
    }
    g_cpu_info_done = 1;
    g_ncpu = (int)GetActiveProcessorCount(ALL_PROCESSOR_GROUPS);
    if (g_ncpu <= 0) {
        SYSTEM_INFO si;
        GetSystemInfo(&si);
        g_ncpu = (int)si.dwNumberOfProcessors;
    }

    wchar_t name[256];
    const wchar_t *ck = L"HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0";
    reg_get_sz(HKEY_LOCAL_MACHINE, ck, L"ProcessorNameString", name, 256);
    wtrim(name);
    DWORD mhz = 0;
    if (reg_get_dword(HKEY_LOCAL_MACHINE, ck, L"~MHz", &mhz) == 0 && mhz > 0) {
        g_cpu_base_mhz = (int)mhz;
    }

    int sockets = 0, cores = 0, logical = 0;
    long l1 = 0, l2 = 0, l3 = 0;
    DWORD len = 0;
    GetLogicalProcessorInformationEx(RelationAll, NULL, &len);
    if (len > 0 && len < (16u << 20)) {
        BYTE *buf = (BYTE *)malloc(len);
        if (buf && GetLogicalProcessorInformationEx(RelationAll, (PSYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX)buf, &len)) {
            for (BYTE *p = buf; p < buf + len;) {
                PSYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX e = (PSYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX)p;
                if (e->Size == 0) {
                    break;
                }
                if (e->Relationship == RelationProcessorPackage) {
                    sockets++;
                } else if (e->Relationship == RelationProcessorCore) {
                    cores++;
                    for (WORD g = 0; g < e->Processor.GroupCount; g++) {
                        logical += popcount_u64(e->Processor.GroupMask[g].Mask);
                    }
                } else if (e->Relationship == RelationCache) {
                    long kb = (long)(e->Cache.CacheSize / 1024u);
                    if (e->Cache.Level == 1) {
                        l1 += kb;
                    } else if (e->Cache.Level == 2) {
                        l2 += kb;
                    } else if (e->Cache.Level == 3) {
                        l3 += kb;
                    }
                }
                p += e->Size;
            }
        }
        free(buf);
    }
    if (logical <= 0) {
        logical = g_ncpu;
    }

    SYSTEM_INFO nsi;
    GetNativeSystemInfo(&nsi);
    /* PF_VIRT_FIRMWARE_ENABLED is an x86 VT-x/AMD-V notion; not meaningful on ARM64 -> unknown */
    int virt = -1;
    if (nsi.wProcessorArchitecture != PROCESSOR_ARCHITECTURE_ARM64) {
        virt = IsProcessorFeaturePresent(21 /* PF_VIRT_FIRMWARE_ENABLED */) ? 1 : 0;
    }
    const char *arch = "";
    switch (nsi.wProcessorArchitecture) {
    case PROCESSOR_ARCHITECTURE_AMD64: arch = "x64"; break;
    case PROCESSOR_ARCHITECTURE_ARM64: arch = "ARM64"; break;
    case PROCESSOR_ARCHITECTURE_INTEL: arch = "x86"; break;
    default: break;
    }

    sb_t sb = {0};
    sb_raw(&sb, "I", 1);
    sb_tab_w(&sb, name);
    sb_tab_i(&sb, g_cpu_base_mhz);
    sb_tab_i(&sb, sockets > 0 ? sockets : -1);
    sb_tab_i(&sb, cores > 0 ? cores : -1);
    sb_tab_i(&sb, logical);
    sb_tab_i(&sb, l1 > 0 ? l1 : -1);
    sb_tab_i(&sb, l2 > 0 ? l2 : -1);
    sb_tab_i(&sb, l3 > 0 ? l3 : -1);
    sb_tab_i(&sb, virt);
    sb_tab_s(&sb, arch);
    sb_nl(&sb);
    if (sb.p && !sb.oom) {
        snprintf(g_cpu_I_line, sizeof(g_cpu_I_line), "%s", sb.p);
    }
    free(sb.p);
}

/* ======================================================================== */
/* Performance snapshot                                                     */
/* ======================================================================== */

#define MAX_CPU 1024

static struct {
    /* PDH: cpu utility + memory lists */
    int cpu_tried;
    PDH_HQUERY q_cpu;
    PDH_HCOUNTER c_util, c_sb_norm, c_sb_res, c_sb_core, c_mod;
    int collects;
    pdh_arr util_arr;
    /* NT cpu times */
    tm_sppi prev[MAX_CPU];
    int prev_n;
    int have_prev;
    tm_sppi cur[MAX_CPU];
} P;

static void perf_pdh_setup(void) {
    if (P.cpu_tried) {
        return;
    }
    P.cpu_tried = 1;
    if (!pdh_load()) {
        return;
    }
    if (pdh_OpenQuery(NULL, 0, &P.q_cpu) != ERROR_SUCCESS) {
        P.q_cpu = NULL;
        return;
    }
    if (pdh_AddEnglish(P.q_cpu, L"\\Processor Information(*)\\% Processor Utility", 0, &P.c_util) != ERROR_SUCCESS) {
        P.c_util = NULL;
    }
    if (pdh_AddEnglish(P.q_cpu, L"\\Memory\\Standby Cache Normal Priority Bytes", 0, &P.c_sb_norm) != ERROR_SUCCESS) {
        P.c_sb_norm = NULL;
    }
    if (pdh_AddEnglish(P.q_cpu, L"\\Memory\\Standby Cache Reserve Bytes", 0, &P.c_sb_res) != ERROR_SUCCESS) {
        P.c_sb_res = NULL;
    }
    if (pdh_AddEnglish(P.q_cpu, L"\\Memory\\Standby Cache Core Bytes", 0, &P.c_sb_core) != ERROR_SUCCESS) {
        P.c_sb_core = NULL;
    }
    if (pdh_AddEnglish(P.q_cpu, L"\\Memory\\Modified Page List Bytes", 0, &P.c_mod) != ERROR_SUCCESS) {
        P.c_mod = NULL;
    }
}

static void perf_pdh_collect(void) {
    if (P.q_cpu && pdh_Collect(P.q_cpu) == ERROR_SUCCESS) {
        P.collects++;
    }
}

static int64_t cur_compressed_bytes(void) {
    if (sp_query() < 0) {
        return -1;
    }
    SP_FOREACH(p) {
        if (p->ImageName.Length == 36 && p->ImageName.Buffer &&
            _wcsnicmp(p->ImageName.Buffer, L"Memory Compression", 18) == 0) {
            return (int64_t)p->WorkingSetSize;
        }
    }
    return -1;
}

static void perf_h(sb_t *sb, int64_t t) {
    dyn_init();
    smbios_init();
    cpu_static_init();

    int64_t mem_total = -1, mem_avail = -1, commit_used = -1, commit_limit = -1, cached = -1;
    int64_t paged = -1, nonpaged = -1, procs = -1, threads = -1, handles = -1;
    struct {
        DWORD cb;
        SIZE_T CommitTotal, CommitLimit, CommitPeak, PhysicalTotal, PhysicalAvailable, SystemCache, KernelTotal, KernelPaged,
            KernelNonpaged, PageSize;
        DWORD HandleCount, ProcessCount, ThreadCount;
    } pi;
    memset(&pi, 0, sizeof(pi));
    pi.cb = sizeof(pi);
    if (p_GetPerfInfo && p_GetPerfInfo(&pi, sizeof(pi)) && pi.PageSize) {
        int64_t ps = (int64_t)pi.PageSize;
        mem_total = (int64_t)pi.PhysicalTotal * ps;
        mem_avail = (int64_t)pi.PhysicalAvailable * ps;
        commit_used = (int64_t)pi.CommitTotal * ps;
        commit_limit = (int64_t)pi.CommitLimit * ps;
        cached = (int64_t)pi.SystemCache * ps;
        paged = (int64_t)pi.KernelPaged * ps;
        nonpaged = (int64_t)pi.KernelNonpaged * ps;
        procs = pi.ProcessCount;
        threads = pi.ThreadCount;
        handles = pi.HandleCount;
    } else {
        MEMORYSTATUSEX ms;
        ms.dwLength = sizeof(ms);
        if (GlobalMemoryStatusEx(&ms)) {
            mem_total = (int64_t)ms.ullTotalPhys;
            mem_avail = (int64_t)ms.ullAvailPhys;
            commit_limit = (int64_t)ms.ullTotalPageFile;
            commit_used = (int64_t)(ms.ullTotalPageFile - ms.ullAvailPageFile);
        }
    }

    /* Cached = standby (3 priority lists) + modified, as in Task Manager */
    if (P.collects >= 1) {
        int64_t a = 0, v = 0;
        int any = 0;
        if (pdh_large(P.c_sb_norm, &v)) { a += v; any = 1; }
        if (pdh_large(P.c_sb_res, &v)) { a += v; any = 1; }
        if (pdh_large(P.c_sb_core, &v)) { a += v; any = 1; }
        if (pdh_large(P.c_mod, &v)) { a += v; any = 1; }
        if (any) {
            cached = a;
        }
    }

    int64_t uptime = (int64_t)(GetTickCount64() / 1000ULL);

    sb_raw(sb, "H", 1);
    sb_tab_i(sb, t);
    sb_tab_i(sb, g_ncpu);
    sb_tab_i(sb, uptime);
    sb_tab_i(sb, procs);
    sb_tab_i(sb, threads);
    sb_tab_i(sb, handles);
    sb_tab_i(sb, mem_total);
    sb_tab_i(sb, mem_avail);
    sb_tab_i(sb, (mem_total >= 0 && mem_avail >= 0) ? mem_total - mem_avail : -1);
    sb_tab_i(sb, commit_used);
    sb_tab_i(sb, commit_limit);
    sb_tab_i(sb, cached);
    sb_tab_i(sb, paged);
    sb_tab_i(sb, nonpaged);
    sb_tab_i(sb, cur_compressed_bytes());
    sb_tab_i(sb, g_mem_speed);
    sb_tab_i(sb, g_mem_used);
    sb_tab_i(sb, g_mem_total);
    sb_tab_s(sb, g_mem_ff);
    sb_nl(sb);
}

static int clamp_i(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static void perf_cpu(sb_t *sb) {
    int n = g_ncpu > MAX_CPU ? MAX_CPU : g_ncpu;
    if (n <= 0) {
        n = 1;
    }
    static int busy[MAX_CPU];
    static int mhz[MAX_CPU];
    for (int i = 0; i < n; i++) {
        busy[i] = -1;
        mhz[i] = -1;
    }
    int total_busy = -1, kernel = -1;

    /* NT idle-time deltas */
    int nt_ok = 0;
    if (p_NtQSI) {
        ULONG ret = 0;
        LONG st = p_NtQSI(8 /* SystemProcessorPerformanceInformation */, P.cur, (ULONG)(sizeof(tm_sppi) * (size_t)n), &ret);
        if (st >= 0) {
            nt_ok = 1;
            int got = (int)(ret / sizeof(tm_sppi));
            if (got > n) {
                got = n;
            }
            if (P.have_prev && P.prev_n == got) {
                unsigned long long sidle = 0, stot = 0, skern = 0;
                for (int i = 0; i < got; i++) {
                    unsigned long long di = (unsigned long long)(P.cur[i].IdleTime.QuadPart - P.prev[i].IdleTime.QuadPart);
                    unsigned long long dk = (unsigned long long)(P.cur[i].KernelTime.QuadPart - P.prev[i].KernelTime.QuadPart);
                    unsigned long long du = (unsigned long long)(P.cur[i].UserTime.QuadPart - P.prev[i].UserTime.QuadPart);
                    unsigned long long dt = dk + du;
                    if (dt > 0) {
                        double b = 1.0 - (double)di / (double)dt;
                        busy[i] = clamp_i((int)(b * 1000.0 + 0.5), 0, 1000);
                    }
                    sidle += di;
                    stot += dt;
                    skern += (dk > di) ? (dk - di) : 0;
                }
                if (stot > 0) {
                    total_busy = clamp_i((int)((1.0 - (double)sidle / (double)stot) * 1000.0 + 0.5), 0, 1000);
                    kernel = clamp_i((int)((double)skern / (double)stot * 1000.0 + 0.5), 0, 1000);
                }
            }
            memcpy(P.prev, P.cur, sizeof(tm_sppi) * (size_t)got);
            P.prev_n = got;
            P.have_prev = 1;
        }
    }
    (void)nt_ok;

    /* PDH processor utility (Task Manager's metric) overrides when available */
    if (P.c_util && P.collects >= 2 && pdh_fetch_array(P.c_util, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100, &P.util_arr)) {
        for (DWORD i = 0; i < P.util_arr.count; i++) {
            PDH_FMT_COUNTERVALUE_ITEM_W *it = &P.util_arr.buf[i];
            if (it->FmtValue.CStatus != 0 && it->FmtValue.CStatus != 1) {
                continue;
            }
            int v = clamp_i((int)(it->FmtValue.doubleValue * 10.0 + 0.5), 0, 1000);
            int g = -1, c = -1;
            if (it->szName && wcscmp(it->szName, L"_Total") == 0) {
                total_busy = v;
            } else if (it->szName && swscanf(it->szName, L"%d,%d", &g, &c) == 2 && g >= 0 && c >= 0) {
                int idx = c;
                for (int k = 0; k < g; k++) {
                    idx += (int)GetActiveProcessorCount((WORD)k);
                }
                if (idx < n) {
                    busy[idx] = v;
                }
            }
        }
    }
    if (kernel >= 0 && total_busy >= 0 && kernel > total_busy) {
        kernel = total_busy;
    }

    /* current frequency */
    int cur_avg = -1;
    int base_mhz = g_cpu_base_mhz;
    if (p_CallNtPower) {
        tm_ppi *ppi = (tm_ppi *)calloc((size_t)n, sizeof(tm_ppi));
        if (ppi) {
            if (p_CallNtPower(11 /* ProcessorInformation */, NULL, 0, ppi, (ULONG)((size_t)n * sizeof(tm_ppi))) >= 0) {
                long long sum = 0;
                int cnt = 0;
                unsigned maxmhz = 0;
                for (int i = 0; i < n; i++) {
                    unsigned c = ppi[i].CurrentMhz;
                    if (c > 0) {
                        mhz[i] = (int)c;
                        sum += c;
                        cnt++;
                    }
                    if (ppi[i].MaxMhz > maxmhz) {
                        maxmhz = ppi[i].MaxMhz;
                    }
                }
                if (cnt > 0) {
                    cur_avg = (int)(sum / cnt);
                }
                if (base_mhz <= 0 && maxmhz > 0) {
                    base_mhz = (int)maxmhz;
                }
            }
            free(ppi);
        }
    }

    for (int i = 0; i < n; i++) {
        sb_raw(sb, "C", 1);
        sb_tab_i(sb, i);
        sb_tab_i(sb, busy[i]);
        sb_tab_i(sb, mhz[i]);
        sb_nl(sb);
    }
    sb_raw(sb, "T", 1);
    sb_tab_i(sb, total_busy);
    sb_tab_i(sb, kernel);
    sb_tab_i(sb, cur_avg);
    sb_tab_i(sb, base_mhz > 0 ? base_mhz : -1);
    sb_nl(sb);

    if (g_cpu_I_line[0]) {
        sb_raw(sb, g_cpu_I_line, strlen(g_cpu_I_line));
    }
}

/* ---------------- disks ---------------- */

typedef struct {
    int open_ok;   /* last sample opened */
    int static_ok; /* model/media/size resolved */
    char model[160];
    char media[8];
    int64_t size;
    int have_prev;
    DISK_PERFORMANCE prev;
    int64_t prev_ns;
} dsk_t;

#define MAX_DISK 32
static dsk_t g_dsk[MAX_DISK];

static unsigned g_vol_mask = 0xFFFFFFFFu;
static unsigned long long g_vol_disks[26];

static void vol_map_refresh(void) {
    DWORD mask = GetLogicalDrives();
    if (mask == g_vol_mask) {
        return;
    }
    g_vol_mask = mask;
    memset(g_vol_disks, 0, sizeof(g_vol_disks));
    for (int i = 0; i < 26; i++) {
        if (!(mask & (1u << i))) {
            continue;
        }
        wchar_t root[4] = {(wchar_t)(L'A' + i), L':', L'\\', 0};
        UINT dt = GetDriveTypeW(root);
        if (dt != DRIVE_FIXED && dt != DRIVE_REMOVABLE) {
            continue;
        }
        if (i < 2 && dt == DRIVE_REMOVABLE) {
            continue; /* floppy letters */
        }
        wchar_t path[8] = {L'\\', L'\\', L'.', L'\\', (wchar_t)(L'A' + i), L':', 0};
        HANDLE h = CreateFileW(path, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);
        if (h == INVALID_HANDLE_VALUE) {
            continue;
        }
        BYTE buf[1024];
        DWORD ret = 0;
        if (DeviceIoControl(h, IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS, NULL, 0, buf, sizeof(buf), &ret, NULL) ||
            GetLastError() == ERROR_MORE_DATA) {
            VOLUME_DISK_EXTENTS *ve = (VOLUME_DISK_EXTENTS *)buf;
            for (DWORD e = 0; e < ve->NumberOfDiskExtents && e < 24; e++) {
                if (ve->Extents[e].DiskNumber < 64) {
                    g_vol_disks[i] |= 1ULL << ve->Extents[e].DiskNumber;
                }
            }
        }
        CloseHandle(h);
    }
}

static void trim_ascii(char *s) {
    size_t n = strlen(s);
    while (n > 0 && (s[n - 1] == ' ' || s[n - 1] == '\t')) {
        s[--n] = 0;
    }
    size_t a = 0;
    while (s[a] == ' ') {
        a++;
    }
    if (a) {
        memmove(s, s + a, n - a + 1);
    }
}

static void disk_static_load(HANDLE h, dsk_t *d) {
    d->model[0] = 0;
    d->media[0] = 0;
    d->size = -1;

    STORAGE_PROPERTY_QUERY q;
    memset(&q, 0, sizeof(q));
    q.PropertyId = StorageDeviceProperty;
    q.QueryType = PropertyStandardQuery;
    BYTE buf[1024];
    DWORD ret = 0;
    if (DeviceIoControl(h, IOCTL_STORAGE_QUERY_PROPERTY, &q, sizeof(q), buf, sizeof(buf), &ret, NULL) && ret >= sizeof(STORAGE_DEVICE_DESCRIPTOR)) {
        STORAGE_DEVICE_DESCRIPTOR *sd = (STORAGE_DEVICE_DESCRIPTOR *)buf;
        char vendor[64] = "", product[128] = "";
        if (sd->VendorIdOffset && sd->VendorIdOffset < ret) {
            snprintf(vendor, sizeof(vendor), "%s", (const char *)buf + sd->VendorIdOffset);
        }
        if (sd->ProductIdOffset && sd->ProductIdOffset < ret) {
            snprintf(product, sizeof(product), "%s", (const char *)buf + sd->ProductIdOffset);
        }
        trim_ascii(vendor);
        trim_ascii(product);
        /* generic bus vendors are noise; real brand names (SanDisk, Kingston...) are kept */
        if (vendor[0] && _stricmp(vendor, "ATA") != 0 && _stricmp(vendor, "NVMe") != 0 && _stricmp(vendor, "SCSI") != 0 &&
            _stricmp(vendor, "Msft") != 0) {
            snprintf(d->model, sizeof(d->model), "%s %s", vendor, product);
        } else {
            snprintf(d->model, sizeof(d->model), "%s", product);
        }
        trim_ascii(d->model);
    }

    memset(&q, 0, sizeof(q));
    q.PropertyId = (STORAGE_PROPERTY_ID)7; /* StorageDeviceSeekPenaltyProperty */
    q.QueryType = PropertyStandardQuery;
    struct {
        DWORD Version, Size;
        BOOLEAN IncursSeekPenalty;
    } sp;
    memset(&sp, 0, sizeof(sp));
    if (DeviceIoControl(h, IOCTL_STORAGE_QUERY_PROPERTY, &q, sizeof(q), &sp, sizeof(sp), &ret, NULL) && ret >= sizeof(sp)) {
        snprintf(d->media, sizeof(d->media), "%s", sp.IncursSeekPenalty ? "HDD" : "SSD");
    }

    BYTE gbuf[256];
    if (DeviceIoControl(h, IOCTL_DISK_GET_DRIVE_GEOMETRY_EX, NULL, 0, gbuf, sizeof(gbuf), &ret, NULL)) {
        d->size = (int64_t)((DISK_GEOMETRY_EX *)gbuf)->DiskSize.QuadPart;
    }
    d->static_ok = 1;
}

static void perf_disks(sb_t *sb) {
    vol_map_refresh();
    int64_t t = now_ns();
    for (int n = 0; n < MAX_DISK; n++) {
        dsk_t *d = &g_dsk[n];
        wchar_t path[40];
        _snwprintf(path, 40, L"\\\\.\\PhysicalDrive%d", n);
        path[39] = 0;
        HANDLE h = CreateFileW(path, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);
        if (h == INVALID_HANDLE_VALUE) {
            d->open_ok = 0;
            d->static_ok = 0;
            d->have_prev = 0;
            continue;
        }
        if (!d->static_ok) {
            disk_static_load(h, d);
        }
        DISK_PERFORMANCE dp;
        DWORD ret = 0;
        int perf_ok = DeviceIoControl(h, IOCTL_DISK_PERFORMANCE, NULL, 0, &dp, sizeof(dp), &ret, NULL) ? 1 : 0;
        CloseHandle(h);
        d->open_ok = 1;

        int act = -1, resp = -1;
        int64_t rb = -1, wb = -1;
        if (perf_ok) {
            if (d->have_prev && t > d->prev_ns) {
                double dt_s = (double)(t - d->prev_ns) / 1e9;
                double dq = (double)(dp.QueryTime.QuadPart - d->prev.QueryTime.QuadPart);
                double di = (double)(dp.IdleTime.QuadPart - d->prev.IdleTime.QuadPart);
                if (dq > 0) {
                    act = clamp_i((int)((1.0 - di / dq) * 1000.0 + 0.5), 0, 1000);
                }
                rb = (int64_t)((double)(dp.BytesRead.QuadPart - d->prev.BytesRead.QuadPart) / dt_s + 0.5);
                wb = (int64_t)((double)(dp.BytesWritten.QuadPart - d->prev.BytesWritten.QuadPart) / dt_s + 0.5);
                double cnt = (double)((int64_t)dp.ReadCount - (int64_t)d->prev.ReadCount) +
                             (double)((int64_t)dp.WriteCount - (int64_t)d->prev.WriteCount);
                double tm = (double)(dp.ReadTime.QuadPart - d->prev.ReadTime.QuadPart) +
                            (double)(dp.WriteTime.QuadPart - d->prev.WriteTime.QuadPart);
                if (cnt > 0) {
                    resp = (int)(tm / cnt / 1000.0 + 0.5);
                } else {
                    resp = 0;
                }
            }
            d->prev = dp;
            d->prev_ns = t;
            d->have_prev = 1;
        } else {
            d->have_prev = 0;
        }

        char letters[128];
        letters[0] = 0;
        size_t ll = 0;
        for (int i = 0; i < 26 && ll + 4 < sizeof(letters); i++) {
            if (n < 64 && (g_vol_disks[i] & (1ULL << n))) {
                ll += (size_t)snprintf(letters + ll, sizeof(letters) - ll, "%s%c:", ll ? " " : "", 'A' + i);
            }
        }

        sb_raw(sb, "D", 1);
        sb_tab_i(sb, n);
        sb_tab(sb);
        sb_raw(sb, "\xE7\xA3\x81\xE7\x9B\x98 ", 7); /* "磁盘 " */
        sb_i(sb, n);
        sb_tab_s(sb, d->model);
        sb_tab_s(sb, d->media);
        sb_tab_i(sb, d->size);
        sb_tab_i(sb, act);
        sb_tab_i(sb, rb);
        sb_tab_i(sb, wb);
        sb_tab_i(sb, resp);
        sb_tab_s(sb, letters);
        sb_nl(sb);
    }
}

/* ---------------- network ---------------- */

typedef struct {
    ULONG64 luid;
    int have_prev, seen;
    ULONG64 in, out;
    int64_t ns;
} net_state;

#define MAX_NET 64
static net_state g_net[MAX_NET];
static int g_nnet;

static void fmt_ipv4(const struct sockaddr *sa, char *out, size_t cap) {
    const unsigned char *b = (const unsigned char *)&((const struct sockaddr_in *)sa)->sin_addr;
    snprintf(out, cap, "%u.%u.%u.%u", b[0], b[1], b[2], b[3]);
}

static void perf_net(sb_t *sb) {
    ULONG size = 32768;
    IP_ADAPTER_ADDRESSES *aa = NULL;
    ULONG rc = ERROR_BUFFER_OVERFLOW;
    ULONG flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER;
    for (int i = 0; i < 4 && rc == ERROR_BUFFER_OVERFLOW; i++) {
        free(aa);
        aa = (IP_ADAPTER_ADDRESSES *)malloc(size);
        if (aa == NULL) {
            return;
        }
        rc = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, aa, &size);
    }
    if (rc != NO_ERROR) {
        free(aa);
        return;
    }
    int64_t t = now_ns();
    for (int i = 0; i < g_nnet; i++) {
        g_net[i].seen = 0;
    }
    for (IP_ADAPTER_ADDRESSES *a = aa; a; a = a->Next) {
        if (a->OperStatus != IfOperStatusUp || a->IfType == IF_TYPE_SOFTWARE_LOOPBACK || a->IfType == IF_TYPE_TUNNEL) {
            continue;
        }
        MIB_IF_ROW2 row;
        memset(&row, 0, sizeof(row));
        row.InterfaceLuid = a->Luid;
        int row_ok = (GetIfEntry2(&row) == NO_ERROR);

        net_state *st = NULL;
        for (int i = 0; i < g_nnet; i++) {
            if (g_net[i].luid == a->Luid.Value) {
                st = &g_net[i];
                break;
            }
        }
        if (st == NULL && g_nnet < MAX_NET) {
            st = &g_net[g_nnet++];
            memset(st, 0, sizeof(*st));
            st->luid = a->Luid.Value;
        }
        int64_t snd = -1, rcv = -1;
        if (st) {
            st->seen = 1;
            if (row_ok) {
                if (st->have_prev && t > st->ns) {
                    double dt_s = (double)(t - st->ns) / 1e9;
                    snd = (int64_t)((double)(row.OutOctets - st->out) / dt_s + 0.5);
                    rcv = (int64_t)((double)(row.InOctets - st->in) / dt_s + 0.5);
                }
                st->in = row.InOctets;
                st->out = row.OutOctets;
                st->ns = t;
                st->have_prev = 1;
            } else {
                st->have_prev = 0;
            }
        }

        int64_t link = -1;
        if (row_ok && row.TransmitLinkSpeed != 0 && row.TransmitLinkSpeed != (ULONG64)-1) {
            link = (int64_t)row.TransmitLinkSpeed;
        } else if (a->TransmitLinkSpeed != 0 && a->TransmitLinkSpeed != (ULONG64)-1) {
            link = (int64_t)a->TransmitLinkSpeed;
        }

        char v4[32] = "";
        wchar_t v6[64] = L"";
        wchar_t v6ll[64] = L"";
        for (IP_ADAPTER_UNICAST_ADDRESS *u = a->FirstUnicastAddress; u; u = u->Next) {
            if (u->DadState == IpDadStateTentative || u->DadState == IpDadStateDuplicate) {
                continue;
            }
            if (u->Address.lpSockaddr == NULL) {
                continue;
            }
            if (u->Address.lpSockaddr->sa_family == AF_INET && v4[0] == 0) {
                fmt_ipv4(u->Address.lpSockaddr, v4, sizeof(v4));
            } else if (u->Address.lpSockaddr->sa_family == AF_INET6 && p_Ipv6ToStr) {
                const struct sockaddr_in6 *s6 = (const struct sockaddr_in6 *)u->Address.lpSockaddr;
                const unsigned char *b = (const unsigned char *)&s6->sin6_addr;
                int ll = (b[0] == 0xFE && (b[1] & 0xC0) == 0x80);
                wchar_t tmp[64];
                tmp[0] = 0;
                p_Ipv6ToStr(&s6->sin6_addr, tmp);
                if (ll) {
                    if (v6ll[0] == 0) {
                        wcsncpy(v6ll, tmp, 63);
                        v6ll[63] = 0;
                    }
                } else if (v6[0] == 0) {
                    wcsncpy(v6, tmp, 63);
                    v6[63] = 0;
                }
            }
        }

        const char *kind = "other";
        if (a->IfType == IF_TYPE_ETHERNET_CSMACD) {
            kind = "eth";
        } else if (a->IfType == IF_TYPE_IEEE80211) {
            kind = "wifi";
        }

        sb_raw(sb, "N", 1);
        sb_tab_s(sb, a->AdapterName);
        sb_tab_w(sb, a->FriendlyName);
        sb_tab_w(sb, a->Description);
        sb_tab_s(sb, kind);
        sb_tab_i(sb, link);
        sb_tab_i(sb, snd);
        sb_tab_i(sb, rcv);
        sb_tab_s(sb, v4);
        sb_tab_w(sb, v6[0] ? v6 : v6ll);
        sb_tab_i(sb, 1);
        sb_nl(sb);
    }
    free(aa);
    int w = 0;
    for (int i = 0; i < g_nnet; i++) {
        if (g_net[i].seen) {
            g_net[w++] = g_net[i];
        }
    }
    g_nnet = w;
}

/* ---------------- GPU ---------------- */

typedef struct {
    WCHAR Description[128];
    UINT VendorId, DeviceId, SubSysId, Revision;
    SIZE_T DedicatedVideoMemory, DedicatedSystemMemory, SharedSystemMemory;
    LUID AdapterLuid;
    UINT Flags;
} tm_dxgi_desc1;

typedef struct {
    int has_luid;
    char key[40]; /* luid_0xHHHHHHHH_0xLLLLLLLL (lowercase) */
    char id[48];
    char name[160];
    char drv[48];
    UINT vendor, device;
    int64_t ded, shr;
} gpu_t;

#define MAX_GPU 8
static gpu_t g_gpu[MAX_GPU];
static int g_ngpu;
static int g_gpu_init;

static struct {
    int tried;
    PDH_HQUERY q;
    PDH_HCOUNTER c_eng, c_ded, c_shr;
    int collects;
    pdh_arr a_eng, a_ded, a_shr;
} PG;

/* Find "luid_0x........_0x........" inside an instance name; copies lowercase into out[28]. */
static int extract_luid(const wchar_t *name, char *out) {
    const wchar_t *p = wcsstr(name, L"luid_0x");
    if (p == NULL) {
        return 0;
    }
    int i = 0;
    for (; i < 26 && p[i]; i++) {
        wchar_t c = p[i];
        if (c > 127) {
            return 0;
        }
        out[i] = (char)towlower((wint_t)c);
    }
    out[i] = 0;
    return i == 26;
}

static void gpu_driver_from_registry(gpu_t *g) {
    HKEY cls;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, L"SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}", 0,
                      KEY_ENUMERATE_SUB_KEYS | KEY_WOW64_64KEY, &cls) != ERROR_SUCCESS) {
        return;
    }
    char pat[32];
    snprintf(pat, sizeof(pat), "ven_%04x&dev_%04x", g->vendor, g->device);
    for (DWORD i = 0; i < 64; i++) {
        wchar_t sub[64];
        DWORD sl = 64;
        if (RegEnumKeyExW(cls, i, sub, &sl, NULL, NULL, NULL, NULL) != ERROR_SUCCESS) {
            break;
        }
        HKEY k;
        if (RegOpenKeyExW(cls, sub, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
            continue;
        }
        wchar_t mid[256], ver[64];
        mid[0] = ver[0] = 0;
        DWORD sz = sizeof(mid) - 2, type = 0;
        RegQueryValueExW(k, L"MatchingDeviceId", NULL, &type, (LPBYTE)mid, &sz);
        mid[sz / 2 < 255 ? sz / 2 : 255] = 0;
        sz = sizeof(ver) - 2;
        RegQueryValueExW(k, L"DriverVersion", NULL, &type, (LPBYTE)ver, &sz);
        ver[sz / 2 < 63 ? sz / 2 : 63] = 0;
        RegCloseKey(k);
        char mu[256];
        int j = 0;
        for (; mid[j] && j < 255; j++) {
            mu[j] = (char)(mid[j] < 128 ? tolower((int)mid[j]) : '?');
        }
        mu[j] = 0;
        if (ver[0] && strstr(mu, pat)) {
            char *u = w2u_dup(ver);
            if (u) {
                snprintf(g->drv, sizeof(g->drv), "%s", u);
                free(u);
            }
            break;
        }
    }
    RegCloseKey(cls);
}

static void gpu_enum_registry_fallback(void) {
    HKEY cls;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, L"SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}", 0,
                      KEY_ENUMERATE_SUB_KEYS | KEY_WOW64_64KEY, &cls) != ERROR_SUCCESS) {
        return;
    }
    for (DWORD i = 0; i < 64 && g_ngpu < MAX_GPU; i++) {
        wchar_t sub[64];
        DWORD sl = 64;
        if (RegEnumKeyExW(cls, i, sub, &sl, NULL, NULL, NULL, NULL) != ERROR_SUCCESS) {
            break;
        }
        HKEY k;
        if (RegOpenKeyExW(cls, sub, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
            continue;
        }
        wchar_t desc[160], ver[64];
        desc[0] = ver[0] = 0;
        DWORD sz = sizeof(desc) - 2, type = 0;
        RegQueryValueExW(k, L"DriverDesc", NULL, &type, (LPBYTE)desc, &sz);
        desc[sz / 2 < 159 ? sz / 2 : 159] = 0;
        sz = sizeof(ver) - 2;
        RegQueryValueExW(k, L"DriverVersion", NULL, &type, (LPBYTE)ver, &sz);
        ver[sz / 2 < 63 ? sz / 2 : 63] = 0;
        RegCloseKey(k);
        if (desc[0] == 0) {
            continue;
        }
        gpu_t *g = &g_gpu[g_ngpu++];
        memset(g, 0, sizeof(*g));
        snprintf(g->id, sizeof(g->id), "reg%d", (int)i);
        char *u = w2u_dup(desc);
        if (u) {
            snprintf(g->name, sizeof(g->name), "%s", u);
            free(u);
        }
        u = w2u_dup(ver);
        if (u) {
            snprintf(g->drv, sizeof(g->drv), "%s", u);
            free(u);
        }
        g->ded = g->shr = -1;
    }
    RegCloseKey(cls);
}

static void gpu_init(void) {
    if (g_gpu_init) {
        return;
    }
    g_gpu_init = 1;
    HMODULE dx = load_sys(L"dxgi.dll");
    if (dx) {
        typedef HRESULT(WINAPI * fn_CreateFactory1)(const GUID *, void **);
        fn_CreateFactory1 create = (fn_CreateFactory1)(void *)GetProcAddress(dx, "CreateDXGIFactory1");
        /* IID_IDXGIFactory1 {770aae78-f26f-4dba-a829-253c83d1b387} */
        static const GUID iid_factory1 = {0x770aae78, 0xf26f, 0x4dba, {0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87}};
        /* IID_IDXGIDevice {54ec77fa-1377-44e6-8c32-88fd5f44c84c} */
        static const GUID iid_device = {0x54ec77fa, 0x1377, 0x44e6, {0x8c, 0x32, 0x88, 0xfd, 0x5f, 0x44, 0xc8, 0x4c}};
        void *factory = NULL;
        if (create && SUCCEEDED(create(&iid_factory1, &factory)) && factory) {
            typedef HRESULT(STDMETHODCALLTYPE * fn_EnumAdapters1)(void *, UINT, void **);
            typedef HRESULT(STDMETHODCALLTYPE * fn_GetDesc1)(void *, tm_dxgi_desc1 *);
            typedef HRESULT(STDMETHODCALLTYPE * fn_CheckIface)(void *, const GUID *, LARGE_INTEGER *);
            typedef ULONG(STDMETHODCALLTYPE * fn_Release)(void *);
            void **fvt = *(void ***)factory;
            for (UINT i = 0; i < 16 && g_ngpu < MAX_GPU; i++) {
                void *ad = NULL;
                if (FAILED(((fn_EnumAdapters1)fvt[12])(factory, i, &ad)) || ad == NULL) {
                    break;
                }
                void **avt = *(void ***)ad;
                tm_dxgi_desc1 d;
                memset(&d, 0, sizeof(d));
                if (SUCCEEDED(((fn_GetDesc1)avt[10])(ad, &d)) && !(d.Flags & 2 /* DXGI_ADAPTER_FLAG_SOFTWARE */)) {
                    gpu_t *g = &g_gpu[g_ngpu++];
                    memset(g, 0, sizeof(*g));
                    g->has_luid = 1;
                    snprintf(g->key, sizeof(g->key), "luid_0x%08x_0x%08x", (unsigned)d.AdapterLuid.HighPart, (unsigned)d.AdapterLuid.LowPart);
                    snprintf(g->id, sizeof(g->id), "%s", g->key);
                    char *u = w2u_dup(d.Description);
                    if (u) {
                        snprintf(g->name, sizeof(g->name), "%s", u);
                        free(u);
                    }
                    g->vendor = d.VendorId;
                    g->device = d.DeviceId;
                    g->ded = (int64_t)d.DedicatedVideoMemory;
                    g->shr = (int64_t)d.SharedSystemMemory;
                    LARGE_INTEGER umd;
                    memset(&umd, 0, sizeof(umd));
                    if (SUCCEEDED(((fn_CheckIface)avt[9])(ad, &iid_device, &umd))) {
                        snprintf(g->drv, sizeof(g->drv), "%u.%u.%u.%u", (unsigned)(umd.HighPart >> 16) & 0xFFFF,
                                 (unsigned)umd.HighPart & 0xFFFF, (unsigned)(umd.LowPart >> 16) & 0xFFFF, (unsigned)umd.LowPart & 0xFFFF);
                    } else {
                        gpu_driver_from_registry(g);
                    }
                }
                ((fn_Release)avt[2])(ad);
            }
            ((fn_Release)fvt[2])(factory);
        }
    }
    if (g_ngpu == 0) {
        gpu_enum_registry_fallback();
    }
}

static void gpu_pdh_setup(void) {
    if (PG.tried) {
        return;
    }
    PG.tried = 1;
    int have_luid = 0;
    for (int i = 0; i < g_ngpu; i++) {
        have_luid |= g_gpu[i].has_luid;
    }
    if (!have_luid || !pdh_load()) {
        return;
    }
    if (pdh_OpenQuery(NULL, 0, &PG.q) != ERROR_SUCCESS) {
        PG.q = NULL;
        return;
    }
    if (pdh_AddEnglish(PG.q, L"\\GPU Engine(*)\\Utilization Percentage", 0, &PG.c_eng) != ERROR_SUCCESS) {
        PG.c_eng = NULL;
    }
    if (pdh_AddEnglish(PG.q, L"\\GPU Adapter Memory(*)\\Dedicated Usage", 0, &PG.c_ded) != ERROR_SUCCESS) {
        PG.c_ded = NULL;
    }
    if (pdh_AddEnglish(PG.q, L"\\GPU Adapter Memory(*)\\Shared Usage", 0, &PG.c_shr) != ERROR_SUCCESS) {
        PG.c_shr = NULL;
    }
    if (!PG.c_eng && !PG.c_ded && !PG.c_shr) {
        PG.q = NULL;
    }
}

typedef struct {
    wchar_t type[24];
    double sum;
} eng_acc;

static void perf_gpu(sb_t *sb) {
    gpu_init();
    if (g_ngpu == 0) {
        return;
    }
    gpu_pdh_setup();
    if (PG.q && pdh_Collect(PG.q) == ERROR_SUCCESS) {
        PG.collects++;
    }

    for (int gi = 0; gi < g_ngpu; gi++) {
        gpu_t *g = &g_gpu[gi];
        int util = -1;
        int64_t vused = -1, sused = -1;

        if (g->has_luid && PG.q && PG.collects >= 1) {
            if (PG.c_ded && pdh_fetch_array(PG.c_ded, PDH_FMT_LARGE, &PG.a_ded)) {
                int64_t sum = 0;
                int any = 0;
                for (DWORD i = 0; i < PG.a_ded.count; i++) {
                    char lk[32];
                    PDH_FMT_COUNTERVALUE_ITEM_W *it = &PG.a_ded.buf[i];
                    if (it->szName && extract_luid(it->szName, lk) && strcmp(lk, g->key) == 0 &&
                        (it->FmtValue.CStatus == 0 || it->FmtValue.CStatus == 1)) {
                        sum += (int64_t)it->FmtValue.largeValue;
                        any = 1;
                    }
                }
                if (any) {
                    vused = sum;
                }
            }
            if (PG.c_shr && pdh_fetch_array(PG.c_shr, PDH_FMT_LARGE, &PG.a_shr)) {
                int64_t sum = 0;
                int any = 0;
                for (DWORD i = 0; i < PG.a_shr.count; i++) {
                    char lk[32];
                    PDH_FMT_COUNTERVALUE_ITEM_W *it = &PG.a_shr.buf[i];
                    if (it->szName && extract_luid(it->szName, lk) && strcmp(lk, g->key) == 0 &&
                        (it->FmtValue.CStatus == 0 || it->FmtValue.CStatus == 1)) {
                        sum += (int64_t)it->FmtValue.largeValue;
                        any = 1;
                    }
                }
                if (any) {
                    sused = sum;
                }
            }
        }
        if (g->has_luid && PG.c_eng && PG.collects >= 2 && pdh_fetch_array(PG.c_eng, PDH_FMT_DOUBLE, &PG.a_eng)) {
            eng_acc acc[16];
            int na = 0;
            int matched = 0;
            for (DWORD i = 0; i < PG.a_eng.count; i++) {
                char lk[32];
                PDH_FMT_COUNTERVALUE_ITEM_W *it = &PG.a_eng.buf[i];
                if (!it->szName || !extract_luid(it->szName, lk) || strcmp(lk, g->key) != 0) {
                    continue;
                }
                if (it->FmtValue.CStatus != 0 && it->FmtValue.CStatus != 1) {
                    continue;
                }
                matched = 1;
                const wchar_t *et = wcsstr(it->szName, L"engtype_");
                et = et ? et + 8 : L"";
                int k = 0;
                for (; k < na; k++) {
                    if (wcscmp(acc[k].type, et) == 0) {
                        break;
                    }
                }
                if (k == na) {
                    if (na >= 16) {
                        continue;
                    }
                    wcsncpy(acc[na].type, et, 23);
                    acc[na].type[23] = 0;
                    acc[na].sum = 0;
                    na++;
                }
                acc[k].sum += it->FmtValue.doubleValue;
            }
            if (matched) {
                double mx = 0;
                for (int k = 0; k < na; k++) {
                    double v = acc[k].sum > 100.0 ? 100.0 : acc[k].sum;
                    if (v > mx) {
                        mx = v;
                    }
                }
                util = clamp_i((int)(mx * 10.0 + 0.5), 0, 1000);
            } else {
                util = 0; /* adapter known, no engine instance reported activity */
            }
        }

        sb_raw(sb, "G", 1);
        sb_tab_s(sb, g->id);
        sb_tab_s(sb, g->name);
        sb_tab_i(sb, util);
        sb_tab_i(sb, vused);
        sb_tab_i(sb, g->ded);
        sb_tab_i(sb, sused);
        sb_tab_i(sb, g->shr);
        sb_tab_i(sb, -1);
        sb_tab_s(sb, g->drv);
        sb_nl(sb);
    }
}

#ifdef JN_TM_TRACE
static int64_t g_tr0;
#define TM_TRACE_T0() (g_tr0 = now_ns())
#define TM_TRACE(tag) do { int64_t n_ = now_ns(); fprintf(stderr, "  [trace] %s %.1f ms\n", tag, (double)(n_ - g_tr0) / 1e6); g_tr0 = n_; } while (0)
#else
#define TM_TRACE_T0() ((void)0)
#define TM_TRACE(tag) ((void)0)
#endif

char *jn_tm_perf(void) {
    sb_t sb = {0};
    dyn_init();
    TM_TRACE_T0();
    cpu_static_init();
    TM_TRACE("cpu_static");
    perf_pdh_setup();
    TM_TRACE("pdh_setup");
    perf_pdh_collect();
    TM_TRACE("pdh_collect");
    int64_t t = now_ns();
    TM_TRACE_T0();
    perf_h(&sb, t);
    TM_TRACE("H");
    perf_cpu(&sb);
    TM_TRACE("cpu");
    perf_disks(&sb);
    TM_TRACE("disk");
    perf_net(&sb);
    TM_TRACE("net");
    perf_gpu(&sb);
    TM_TRACE("gpu");
    return sb_done(&sb);
}

/* ======================================================================== */
/* Process extras                                                           */
/* ======================================================================== */

/* NT path -> DOS path via drive-letter device mapping */
static wchar_t g_dev_map[26][64];
static ULONGLONG g_dev_map_tick;

static void dev_map_build(void) {
    for (int i = 0; i < 26; i++) {
        wchar_t drv[3] = {(wchar_t)(L'A' + i), L':', 0};
        g_dev_map[i][0] = 0;
        QueryDosDeviceW(drv, g_dev_map[i], 64);
    }
    g_dev_map_tick = GetTickCount64();
}

static int nt_to_dos(const wchar_t *nt, wchar_t *out, size_t cap) {
    for (int pass = 0; pass < 2; pass++) {
        if (g_dev_map_tick == 0 || pass == 1) {
            if (pass == 1 && GetTickCount64() - g_dev_map_tick < 5000) {
                break;
            }
            dev_map_build();
        }
        for (int i = 0; i < 26; i++) {
            size_t l = wcslen(g_dev_map[i]);
            if (l > 0 && _wcsnicmp(nt, g_dev_map[i], l) == 0 && (nt[l] == L'\\' || nt[l] == 0)) {
                _snwprintf(out, cap, L"%c:%ls", (wchar_t)(L'A' + i), nt + l);
                out[cap - 1] = 0;
                return 1;
            }
        }
    }
    return 0;
}

static int pid_image_path_nt(DWORD pid, wchar_t *out, size_t cap) {
    if (p_NtQSI == NULL) {
        return 0;
    }
    struct {
        PVOID ProcessId;
        tm_ustr ImageName;
    } info;
    BYTE *buf = (BYTE *)malloc(4096);
    if (buf == NULL) {
        return 0;
    }
    info.ProcessId = (PVOID)(ULONG_PTR)pid;
    info.ImageName.Length = 0;
    info.ImageName.MaximumLength = 4096;
    info.ImageName.Buffer = (PWSTR)buf;
    LONG st = p_NtQSI(88 /* SystemProcessIdInformation */, &info, sizeof(info), NULL);
    int ok = 0;
    if (st >= 0 && info.ImageName.Length > 0 && info.ImageName.Buffer) {
        wchar_t tmp[1024];
        size_t n = info.ImageName.Length / sizeof(wchar_t);
        if (n > 1023) {
            n = 1023;
        }
        memcpy(tmp, info.ImageName.Buffer, n * sizeof(wchar_t));
        tmp[n] = 0;
        ok = nt_to_dos(tmp, out, cap);
    }
    free(buf);
    return ok;
}

typedef struct {
    DWORD pid;
    ULONGLONG ctime;
    unsigned gen;
    int init;
    int canopen;
    char arch[8];
    char *cmd;
    wchar_t *path;
    const vi_t *vi;
    signed char elev, virt;
} px_ent;

static px_ent *g_px;
static size_t g_px_n, g_px_cap;
static unsigned g_px_gen;
static int *g_px_idx;
static size_t g_px_idx_cap;

static const char *machine_name(USHORT m) {
    switch (m) {
    case IMAGE_FILE_MACHINE_AMD64: return "x64";
    case IMAGE_FILE_MACHINE_ARM64: return "ARM64";
    case IMAGE_FILE_MACHINE_I386: return "x86";
    case IMAGE_FILE_MACHINE_ARMNT: return "ARM";
    default: return "";
    }
}

static void px_init_static(px_ent *e) {
    e->init = 1;
    e->arch[0] = 0;
    e->elev = e->virt = -1;
    HANDLE h = e->pid ? OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, e->pid) : NULL;
    e->canopen = (h != NULL);

    wchar_t path[1024];
    path[0] = 0;
    int have_path = 0;
    if (h) {
        DWORD n = 1024;
        if (QueryFullProcessImageNameW(h, 0, path, &n) && path[0]) {
            have_path = 1;
        }
    }
    if (!have_path && e->pid > 4) {
        have_path = pid_image_path_nt(e->pid, path, 1024);
    }
    if (have_path) {
        e->path = _wcsdup(path);
    }

    if (h) {
        if (p_IsWow64Process2) {
            USHORT pm = 0, nm = 0;
            if (p_IsWow64Process2(h, &pm, &nm)) {
                snprintf(e->arch, sizeof(e->arch), "%s", machine_name(pm ? pm : nm));
            }
        } else {
            BOOL wow = FALSE;
            if (IsWow64Process(h, &wow)) {
                if (wow) {
                    snprintf(e->arch, sizeof(e->arch), "x86");
                } else {
                    SYSTEM_INFO si;
                    GetNativeSystemInfo(&si);
                    snprintf(e->arch, sizeof(e->arch), "%s",
                             si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64   ? "x64"
                             : si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64 ? "ARM64"
                                                                                          : "x86");
                }
            }
        }

        HANDLE tok = NULL;
        if (OpenProcessToken(h, TOKEN_QUERY, &tok)) {
            DWORD v = 0, rl = 0;
            if (GetTokenInformation(tok, TokenElevation, &v, sizeof(v), &rl)) {
                e->elev = v ? 1 : 0;
            }
            v = 0;
            if (GetTokenInformation(tok, TokenVirtualizationEnabled, &v, sizeof(v), &rl)) {
                e->virt = v ? 1 : 0;
            }
            CloseHandle(tok);
        }

        if (p_NtQIP) {
            ULONG cap = 8192, ret = 0;
            BYTE *buf = (BYTE *)malloc(cap);
            for (int tries = 0; buf && tries < 2; tries++) {
                LONG st = p_NtQIP(h, 60 /* ProcessCommandLineInformation */, buf, cap, &ret);
                if (st >= 0) {
                    tm_ustr *us = (tm_ustr *)buf;
                    size_t n = us->Length / sizeof(wchar_t);
                    if (us->Buffer && n > 0) {
                        if (n > 1024) {
                            n = 1024;
                        }
                        wchar_t *w = (wchar_t *)malloc((n + 1) * sizeof(wchar_t));
                        if (w) {
                            memcpy(w, us->Buffer, n * sizeof(wchar_t));
                            w[n] = 0;
                            e->cmd = w2u_dup(w);
                            free(w);
                        }
                    }
                    break;
                }
                if ((st == TM_STATUS_INFO_LENGTH_MISMATCH || st == (LONG)0x80000005 || st == (LONG)0xC0000023) && ret > cap && ret < (1u << 20)) {
                    BYTE *nb = (BYTE *)realloc(buf, ret);
                    if (nb == NULL) {
                        break;
                    }
                    buf = nb;
                    cap = ret;
                    continue;
                }
                break;
            }
            free(buf);
        }
        CloseHandle(h);
    }
}

static void px_free(px_ent *e) {
    free(e->cmd);
    free(e->path);
    e->cmd = NULL;
    e->path = NULL;
}

static void px_index_build(void) {
    size_t cap = 1024;
    while (cap < g_px_n * 3) {
        cap *= 2;
    }
    if (cap != g_px_idx_cap) {
        free(g_px_idx);
        g_px_idx = (int *)malloc(cap * sizeof(int));
        g_px_idx_cap = g_px_idx ? cap : 0;
    }
    if (g_px_idx == NULL) {
        return;
    }
    for (size_t i = 0; i < g_px_idx_cap; i++) {
        g_px_idx[i] = -1;
    }
    for (size_t i = 0; i < g_px_n; i++) {
        size_t j = ((size_t)g_px[i].pid * 2654435761u) & (g_px_idx_cap - 1);
        while (g_px_idx[j] >= 0) {
            j = (j + 1) & (g_px_idx_cap - 1);
        }
        g_px_idx[j] = (int)i;
    }
}

static px_ent *px_lookup(DWORD pid, ULONGLONG ctime) {
    if (g_px_idx == NULL) {
        return NULL;
    }
    size_t j = ((size_t)pid * 2654435761u) & (g_px_idx_cap - 1);
    while (g_px_idx[j] >= 0) {
        px_ent *e = &g_px[g_px_idx[j]];
        if (e->pid == pid && e->ctime == ctime) {
            return e;
        }
        j = (j + 1) & (g_px_idx_cap - 1);
    }
    return NULL;
}

static px_ent *px_add(DWORD pid, ULONGLONG ctime) {
    if (g_px_n == g_px_cap) {
        size_t nc = g_px_cap ? g_px_cap * 2 : 512;
        px_ent *np = (px_ent *)realloc(g_px, nc * sizeof(px_ent));
        if (np == NULL) {
            return NULL;
        }
        g_px = np;
        g_px_cap = nc;
    }
    px_ent *e = &g_px[g_px_n++];
    memset(e, 0, sizeof(*e));
    e->pid = pid;
    e->ctime = ctime;
    return e;
}

static DWORD prio_from_base(LONG bp) {
    if (bp <= 4) {
        return 0x40;
    }
    if (bp <= 6) {
        return 0x4000;
    }
    if (bp <= 8) {
        return 0x20;
    }
    if (bp <= 10) {
        return 0x8000;
    }
    if (bp <= 15) {
        return 0x80;
    }
    return 0x100;
}

#define VI_BUDGET_NS (60LL * 1000000LL)

char *jn_tm_procx(void) {
    sb_t sb = {0};
    LONG st = sp_query();
    if (st < 0) {
        return sb_done(&sb);
    }
    g_px_gen++;
    px_index_build();
    int64_t deadline = now_ns() + VI_BUDGET_NS;

    SP_FOREACH(p) {
        DWORD pid = (DWORD)(ULONG_PTR)p->UniqueProcessId;
        ULONGLONG ct = (ULONGLONG)p->CreateTime.QuadPart;
        px_ent *e = px_lookup(pid, ct);
        if (e == NULL) {
            e = px_add(pid, ct);
        }
        if (e == NULL) {
            continue;
        }
        e->gen = g_px_gen;
        if (!e->init) {
            px_init_static(e);
        }
        if (e->vi == NULL && e->path) {
            e->vi = vi_get(e->path, deadline);
        }

        int64_t gdi = -1, usr = -1;
        DWORD prio = 0;
        int prio_ok = 0;
        if (e->canopen && pid != 0) {
            HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
            if (h) {
                prio = GetPriorityClass(h);
                prio_ok = prio != 0;
                if (p_GetGuiResources) {
                    gdi = (int64_t)p_GetGuiResources(h, 0);
                    usr = (int64_t)p_GetGuiResources(h, 1);
                }
                CloseHandle(h);
            }
        }
        if (!prio_ok && pid != 0) {
            prio = prio_from_base(p->BasePriority);
            prio_ok = 1;
        }

        int susp = 0;
        if (p->NumberOfThreads > 0) {
            tm_systhread *th = (tm_systhread *)((BYTE *)p + sizeof(tm_sysproc));
            susp = 1;
            for (ULONG i = 0; i < p->NumberOfThreads; i++) {
                if (th[i].ThreadState != 5 || th[i].WaitReason != 5) {
                    susp = 0;
                    break;
                }
            }
        }

        sb_raw(&sb, "X", 1);
        sb_tab_i(&sb, pid);
        sb_tab_i(&sb, p->SessionId);
        sb_tab_s(&sb, e->arch);
        sb_tab_s(&sb, e->vi ? e->vi->desc : "");
        sb_tab_s(&sb, e->cmd ? e->cmd : "");
        sb_tab_i(&sb, p->ReadTransferCount.QuadPart);
        sb_tab_i(&sb, p->WriteTransferCount.QuadPart);
        sb_tab_i(&sb, p->OtherTransferCount.QuadPart);
        sb_tab_i(&sb, p->HandleCount);
        sb_tab_i(&sb, (int64_t)p->PrivatePageCount);
        sb_tab_i(&sb, (int64_t)p->PeakWorkingSetSize);
        sb_tab_i(&sb, p->PageFaultCount);
        sb_tab_i(&sb, prio_ok ? (int64_t)prio : -1);
        sb_tab_i(&sb, gdi);
        sb_tab_i(&sb, usr);
        sb_tab_i(&sb, e->elev);
        sb_tab_i(&sb, e->virt);
        sb_tab_i(&sb, susp);
        sb_tab_i(&sb, (int64_t)p->WorkingSetSize);
        sb_tab_i(&sb, (int64_t)(p->UserTime.QuadPart + p->KernelTime.QuadPart));
        sb_tab_i(&sb, (int64_t)p->CreateTime.QuadPart);
        sb_nl(&sb);
    }

    /* drop entries of processes that are gone */
    size_t w = 0;
    for (size_t i = 0; i < g_px_n; i++) {
        if (g_px[i].gen == g_px_gen) {
            if (w != i) {
                g_px[w] = g_px[i];
            }
            w++;
        } else {
            px_free(&g_px[i]);
        }
    }
    g_px_n = w;
    return sb_done(&sb);
}

/* ======================================================================== */
/* Process operations                                                       */
/* ======================================================================== */

int32_t jn_tm_proc_kill_tree(int32_t pid) {
    if (pid <= 4) {
        return ERROR_ACCESS_DENIED;
    }
    if (sp_query() < 0) {
        return ERROR_GEN_FAILURE;
    }
    size_t n = 0, cap = 1024;
    typedef struct {
        DWORD pid, ppid;
        ULONGLONG ct;
    } rec;
    rec *all = (rec *)malloc(cap * sizeof(rec));
    if (all == NULL) {
        return ERROR_NOT_ENOUGH_MEMORY;
    }
    SP_FOREACH(p) {
        if (n == cap) {
            cap *= 2;
            rec *na = (rec *)realloc(all, cap * sizeof(rec));
            if (na == NULL) {
                free(all);
                return ERROR_NOT_ENOUGH_MEMORY;
            }
            all = na;
        }
        all[n].pid = (DWORD)(ULONG_PTR)p->UniqueProcessId;
        all[n].ppid = (DWORD)(ULONG_PTR)p->InheritedFromUniqueProcessId;
        all[n].ct = (ULONGLONG)p->CreateTime.QuadPart;
        n++;
    }
    size_t root = (size_t)-1;
    for (size_t i = 0; i < n; i++) {
        if (all[i].pid == (DWORD)pid) {
            root = i;
            break;
        }
    }
    if (root == (size_t)-1) {
        free(all);
        return ERROR_INVALID_PARAMETER;
    }
    /* BFS order list of indices */
    size_t *order = (size_t *)malloc(n * sizeof(size_t));
    char *mark = (char *)calloc(n, 1);
    if (order == NULL || mark == NULL) {
        free(order);
        free(mark);
        free(all);
        return ERROR_NOT_ENOUGH_MEMORY;
    }
    size_t head = 0, tail = 0;
    order[tail++] = root;
    mark[root] = 1;
    DWORD self = GetCurrentProcessId();
    while (head < tail) {
        size_t cur = order[head++];
        for (size_t i = 0; i < n; i++) {
            if (!mark[i] && all[i].ppid == all[cur].pid && all[i].pid != all[cur].pid && all[i].ct >= all[cur].ct && all[i].pid > 4 &&
                all[i].pid != self) {
                mark[i] = 1;
                order[tail++] = i;
            }
        }
    }
    int32_t result = 0;
    for (size_t k = tail; k > 0; k--) {
        DWORD target = all[order[k - 1]].pid;
        HANDLE h = OpenProcess(PROCESS_TERMINATE, FALSE, target);
        int32_t err = 0;
        if (h == NULL) {
            err = (int32_t)GetLastError();
        } else {
            if (!TerminateProcess(h, 1)) {
                err = (int32_t)GetLastError();
            }
            CloseHandle(h);
        }
        if (err != 0 && result == 0) {
            result = err;
        }
    }
    free(order);
    free(mark);
    free(all);
    return result;
}

int32_t jn_tm_proc_set_priority(int32_t pid, int32_t cls) {
    static const DWORD map[6] = {REALTIME_PRIORITY_CLASS, HIGH_PRIORITY_CLASS, ABOVE_NORMAL_PRIORITY_CLASS, NORMAL_PRIORITY_CLASS,
                                 BELOW_NORMAL_PRIORITY_CLASS, IDLE_PRIORITY_CLASS};
    if (cls < 0 || cls > 5 || pid <= 0) {
        return ERROR_INVALID_PARAMETER;
    }
    HANDLE h = OpenProcess(PROCESS_SET_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        return (int32_t)GetLastError();
    }
    int32_t r = SetPriorityClass(h, map[cls]) ? 0 : (int32_t)GetLastError();
    CloseHandle(h);
    return r;
}

int32_t jn_tm_proc_suspend(int32_t pid, int32_t on) {
    dyn_init();
    fn_NtProcRes fn = on ? p_NtSuspend : p_NtResume;
    if (fn == NULL || pid <= 0) {
        return fn == NULL ? -1 : ERROR_INVALID_PARAMETER;
    }
    HANDLE h = OpenProcess(PROCESS_SUSPEND_RESUME, FALSE, (DWORD)pid);
    if (h == NULL) {
        return (int32_t)GetLastError();
    }
    LONG st = fn(h);
    CloseHandle(h);
    return nt_to_win32(st);
}

int32_t jn_tm_proc_set_affinity(int32_t pid, int64_t mask) {
    if (pid <= 0 || mask == 0) {
        return ERROR_INVALID_PARAMETER;
    }
    HANDLE h = OpenProcess(PROCESS_SET_INFORMATION | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        return (int32_t)GetLastError();
    }
    int32_t r = SetProcessAffinityMask(h, (DWORD_PTR)mask) ? 0 : (int32_t)GetLastError();
    CloseHandle(h);
    return r;
}

int64_t jn_tm_proc_get_affinity(int32_t pid) {
    if (pid <= 0) {
        return -1;
    }
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    if (h == NULL) {
        return -1;
    }
    DWORD_PTR pm = 0, sm = 0;
    int64_t r = GetProcessAffinityMask(h, &pm, &sm) ? (int64_t)pm : -1;
    CloseHandle(h);
    return r;
}

int32_t jn_tm_proc_create_dump(int32_t pid, const char *out_path_utf8) {
    if (pid <= 0 || out_path_utf8 == NULL || out_path_utf8[0] == 0) {
        return ERROR_INVALID_PARAMETER;
    }
    HMODULE dbg = load_sys(L"dbghelp.dll");
    if (dbg == NULL) {
        return (int32_t)GetLastError();
    }
    typedef BOOL(WINAPI * fn_MiniDumpWriteDump)(HANDLE, DWORD, HANDLE, int, void *, void *, void *);
    fn_MiniDumpWriteDump dump = (fn_MiniDumpWriteDump)(void *)GetProcAddress(dbg, "MiniDumpWriteDump");
    if (dump == NULL) {
        return ERROR_PROC_NOT_FOUND;
    }
    wchar_t *wp = u2w_dup(out_path_utf8);
    if (wp == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    HANDLE hp = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, (DWORD)pid);
    if (hp == NULL) {
        int32_t e = (int32_t)GetLastError();
        free(wp);
        return e;
    }
    HANDLE hf = CreateFileW(wp, GENERIC_READ | GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hf == INVALID_HANDLE_VALUE) {
        int32_t e = (int32_t)GetLastError();
        CloseHandle(hp);
        free(wp);
        return e;
    }
    int32_t r = 0;
    if (!dump(hp, (DWORD)pid, hf, 0x00000002 /* MiniDumpWithFullMemory */, NULL, NULL, NULL)) {
        r = (int32_t)GetLastError();
        if (r == 0) {
            r = ERROR_GEN_FAILURE;
        }
    }
    CloseHandle(hf);
    CloseHandle(hp);
    if (r != 0) {
        DeleteFileW(wp);
    }
    free(wp);
    return r;
}

static int shell_ok(HINSTANCE r) {
    return (INT_PTR)r > 32 ? 0 : (int32_t)((INT_PTR)r == 0 ? ERROR_OUTOFMEMORY : (INT_PTR)r);
}

int32_t jn_tm_proc_properties(int32_t pid) {
    if (pid <= 0) {
        return ERROR_INVALID_PARAMETER;
    }
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, (DWORD)pid);
    wchar_t path[1024];
    DWORD n = 1024;
    if (h == NULL) {
        int32_t e = (int32_t)GetLastError();
        dyn_init();
        if (!pid_image_path_nt((DWORD)pid, path, 1024)) {
            return e;
        }
    } else {
        int ok = QueryFullProcessImageNameW(h, 0, path, &n);
        int32_t e = (int32_t)GetLastError();
        CloseHandle(h);
        if (!ok) {
            return e;
        }
    }
    SHELLEXECUTEINFOW sei;
    memset(&sei, 0, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_INVOKEIDLIST | SEE_MASK_FLAG_NO_UI;
    sei.lpVerb = L"properties";
    sei.lpFile = path;
    sei.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&sei)) {
        return (int32_t)GetLastError();
    }
    return 0;
}

int32_t jn_tm_search_online(const char *query_utf8) {
    if (query_utf8 == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    size_t n = strlen(query_utf8);
    char *enc = (char *)malloc(n * 3 + 1);
    if (enc == NULL) {
        return ERROR_NOT_ENOUGH_MEMORY;
    }
    size_t o = 0;
    static const char hex[] = "0123456789ABCDEF";
    for (size_t i = 0; i < n; i++) {
        unsigned char c = (unsigned char)query_utf8[i];
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
            enc[o++] = (char)c;
        } else if (c == ' ') {
            enc[o++] = '+';
        } else {
            enc[o++] = '%';
            enc[o++] = hex[c >> 4];
            enc[o++] = hex[c & 15];
        }
    }
    enc[o] = 0;
    size_t ul = o + 40;
    char *url = (char *)malloc(ul);
    if (url == NULL) {
        free(enc);
        return ERROR_NOT_ENOUGH_MEMORY;
    }
    snprintf(url, ul, "https://www.bing.com/search?q=%s", enc);
    free(enc);
    wchar_t *wu = u2w_dup(url);
    free(url);
    if (wu == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    int32_t r = shell_ok(ShellExecuteW(NULL, L"open", wu, NULL, NULL, SW_SHOWNORMAL));
    free(wu);
    return r;
}

int32_t jn_tm_open_path(const char *path_utf8) {
    if (path_utf8 == NULL || path_utf8[0] == 0) {
        return ERROR_INVALID_PARAMETER;
    }
    wchar_t *w = u2w_dup(path_utf8);
    if (w == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    DWORD attr = GetFileAttributesW(w);
    const wchar_t *verb = (attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY)) ? L"explore" : L"open";
    int32_t r = shell_ok(ShellExecuteW(NULL, verb, w, NULL, NULL, SW_SHOWNORMAL));
    free(w);
    return r;
}

int32_t jn_tm_copy_text(const char *utf8) {
    if (utf8 == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    HMODULE u32 = load_sys(L"user32.dll");
    if (u32 == NULL) {
        return -1;
    }
    typedef BOOL(WINAPI * fn_Open)(HWND);
    typedef BOOL(WINAPI * fn_Void)(void);
    typedef HANDLE(WINAPI * fn_Set)(UINT, HANDLE);
    fn_Open open_cb = (fn_Open)(void *)GetProcAddress(u32, "OpenClipboard");
    fn_Void close_cb = (fn_Void)(void *)GetProcAddress(u32, "CloseClipboard");
    fn_Void empty_cb = (fn_Void)(void *)GetProcAddress(u32, "EmptyClipboard");
    fn_Set set_cb = (fn_Set)(void *)GetProcAddress(u32, "SetClipboardData");
    if (!open_cb || !close_cb || !empty_cb || !set_cb) {
        return -1;
    }
    wchar_t *w = u2w_dup(utf8);
    if (w == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    size_t bytes = (wcslen(w) + 1) * sizeof(wchar_t);
    HGLOBAL g = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (g == NULL) {
        free(w);
        return ERROR_NOT_ENOUGH_MEMORY;
    }
    void *dst = GlobalLock(g);
    if (dst == NULL) {
        GlobalFree(g);
        free(w);
        return (int32_t)GetLastError();
    }
    memcpy(dst, w, bytes);
    GlobalUnlock(g);
    free(w);
    int opened = 0;
    for (int i = 0; i < 10; i++) {
        if (open_cb(NULL)) {
            opened = 1;
            break;
        }
        Sleep(20);
    }
    if (!opened) {
        int32_t e = (int32_t)GetLastError();
        GlobalFree(g);
        return e ? e : ERROR_ACCESS_DENIED;
    }
    int32_t r = 0;
    empty_cb();
    if (set_cb(13 /* CF_UNICODETEXT */, g) == NULL) {
        r = (int32_t)GetLastError();
        GlobalFree(g);
    }
    close_cb();
    return r;
}

int32_t jn_tm_open_resource_monitor(void) {
    return shell_ok(ShellExecuteW(NULL, L"open", L"resmon.exe", NULL, NULL, SW_SHOWNORMAL));
}

int32_t jn_tm_open_system_settings_apps(void) {
    return shell_ok(ShellExecuteW(NULL, L"open", L"ms-settings:startupapps", NULL, NULL, SW_SHOWNORMAL));
}

int32_t jn_tm_open_services_msc(void) {
    return shell_ok(ShellExecuteW(NULL, L"open", L"services.msc", NULL, NULL, SW_SHOWNORMAL));
}

/* ======================================================================== */
/* Services                                                                 */
/* ======================================================================== */

typedef struct {
    char *desc, *start, *path, *group, *account;
    ULONGLONG next_refresh; /* GetTickCount64 */
    int has_cfg;
} svc_t;

static smap g_svc;

static void svc_free_fields(svc_t *e) {
    free(e->desc);
    free(e->start);
    free(e->path);
    free(e->group);
    free(e->account);
    e->desc = e->start = e->path = e->group = e->account = NULL;
}

static void *svc_query2(SC_HANDLE s, DWORD level) {
    DWORD need = 0;
    QueryServiceConfig2W(s, level, NULL, 0, &need);
    if (need == 0 || need > (1u << 20)) {
        return NULL;
    }
    BYTE *b = (BYTE *)malloc(need);
    if (b && !QueryServiceConfig2W(s, level, b, need, &need)) {
        free(b);
        return NULL;
    }
    return b;
}

static char *resolve_indirect(const wchar_t *s) {
    if (s == NULL) {
        return dup_str("");
    }
    if (s[0] == L'@') {
        HMODULE sh = load_sys(L"shlwapi.dll");
        if (sh) {
            typedef HRESULT(WINAPI * fn_SHLoadIndirectString)(PCWSTR, PWSTR, UINT, void **);
            fn_SHLoadIndirectString f = (fn_SHLoadIndirectString)(void *)GetProcAddress(sh, "SHLoadIndirectString");
            wchar_t out[1024];
            if (f && SUCCEEDED(f(s, out, 1024, NULL))) {
                return w2u_dup(out);
            }
        }
        return dup_str("");
    }
    return w2u_dup(s);
}

static void svc_load_cfg(SC_HANDLE scm, const wchar_t *name, svc_t *e) {
    svc_free_fields(e);
    e->has_cfg = 0;
    SC_HANDLE s = OpenServiceW(scm, name, SERVICE_QUERY_CONFIG);
    if (s) {
        DWORD need = 0;
        QueryServiceConfigW(s, NULL, 0, &need);
        if (need > 0 && need <= (256u << 10)) {
            QUERY_SERVICE_CONFIGW *cfg = (QUERY_SERVICE_CONFIGW *)malloc(need);
            if (cfg && QueryServiceConfigW(s, cfg, need, &need)) {
                e->has_cfg = 1;
                const char *st = "";
                switch (cfg->dwStartType) {
                case SERVICE_BOOT_START: st = "boot"; break;
                case SERVICE_SYSTEM_START: st = "system"; break;
                case SERVICE_AUTO_START: st = "auto"; break;
                case SERVICE_DEMAND_START: st = "manual"; break;
                case SERVICE_DISABLED: st = "disabled"; break;
                default: break;
                }
                if (cfg->dwStartType == SERVICE_AUTO_START) {
                    SERVICE_DELAYED_AUTO_START_INFO *di = (SERVICE_DELAYED_AUTO_START_INFO *)svc_query2(s, SERVICE_CONFIG_DELAYED_AUTO_START_INFO);
                    if (di) {
                        if (di->fDelayedAutostart) {
                            st = "auto_delayed";
                        }
                        free(di);
                    }
                }
                e->start = dup_str(st);
                e->path = cfg->lpBinaryPathName ? w2u_dup(cfg->lpBinaryPathName) : dup_str("");
                e->account = cfg->lpServiceStartName ? w2u_dup(cfg->lpServiceStartName) : dup_str("");
                /* group: svchost -k <group>, else load-order group */
                char *grp = NULL;
                if (cfg->lpBinaryPathName) {
                    const wchar_t *p = cfg->lpBinaryPathName;
                    const wchar_t *k = NULL;
                    for (const wchar_t *q = p; *q; q++) {
                        if ((q == p || iswspace((wint_t)q[-1])) && q[0] == L'-' && (q[1] == L'k' || q[1] == L'K') && iswspace((wint_t)q[2])) {
                            k = q + 3;
                            break;
                        }
                    }
                    if (k) {
                        while (*k && iswspace((wint_t)*k)) {
                            k++;
                        }
                        wchar_t g[128];
                        size_t gl = 0;
                        while (k[gl] && !iswspace((wint_t)k[gl]) && gl < 127) {
                            g[gl] = k[gl];
                            gl++;
                        }
                        g[gl] = 0;
                        if (gl > 0) {
                            grp = w2u_dup(g);
                        }
                    }
                }
                if (grp == NULL) {
                    grp = (cfg->lpLoadOrderGroup && cfg->lpLoadOrderGroup[0]) ? w2u_dup(cfg->lpLoadOrderGroup) : dup_str("");
                }
                e->group = grp;
            }
            free(cfg);
        }
        SERVICE_DESCRIPTIONW *d = (SERVICE_DESCRIPTIONW *)svc_query2(s, SERVICE_CONFIG_DESCRIPTION);
        e->desc = resolve_indirect(d ? d->lpDescription : NULL);
        free(d);
        CloseServiceHandle(s);
    }
    if (e->desc == NULL) {
        e->desc = dup_str("");
    }
    if (e->start == NULL) {
        e->start = dup_str("");
    }
    if (e->path == NULL) {
        e->path = dup_str("");
    }
    if (e->group == NULL) {
        e->group = dup_str("");
    }
    if (e->account == NULL) {
        e->account = dup_str("");
    }
    uint32_t h = sm_hash(name);
    e->next_refresh = GetTickCount64() + 30000ULL + (ULONGLONG)(h % 30000u);
}

static const char *svc_state_name(DWORD s) {
    switch (s) {
    case SERVICE_RUNNING: return "running";
    case SERVICE_STOPPED: return "stopped";
    case SERVICE_PAUSED: return "paused";
    case SERVICE_START_PENDING: return "starting";
    case SERVICE_STOP_PENDING: return "stopping";
    default: return "pending";
    }
}

/* Enumerate Win32 services into a malloc'd buffer. */
static ENUM_SERVICE_STATUS_PROCESSW *svc_enum(SC_HANDLE scm, DWORD *count_out) {
    DWORD need = 0, count = 0, resume = 0;
    *count_out = 0;
    EnumServicesStatusExW(scm, SC_ENUM_PROCESS_INFO, SERVICE_WIN32, SERVICE_STATE_ALL, NULL, 0, &need, &count, &resume, NULL);
    if (need == 0) {
        return NULL;
    }
    for (int i = 0; i < 4; i++) {
        DWORD cap = need + 8192;
        BYTE *buf = (BYTE *)malloc(cap);
        if (buf == NULL) {
            return NULL;
        }
        resume = 0;
        if (EnumServicesStatusExW(scm, SC_ENUM_PROCESS_INFO, SERVICE_WIN32, SERVICE_STATE_ALL, buf, cap, &need, &count, &resume, NULL)) {
            *count_out = count;
            return (ENUM_SERVICE_STATUS_PROCESSW *)buf;
        }
        free(buf);
        if (GetLastError() != ERROR_MORE_DATA) {
            return NULL;
        }
    }
    return NULL;
}

char *jn_tm_services(void) {
    sb_t sb = {0};
    SC_HANDLE scm = OpenSCManagerW(NULL, NULL, SC_MANAGER_CONNECT | SC_MANAGER_ENUMERATE_SERVICE);
    if (scm == NULL) {
        return sb_done(&sb);
    }
    DWORD count = 0;
    ENUM_SERVICE_STATUS_PROCESSW *list = svc_enum(scm, &count);
    ULONGLONG tick = GetTickCount64();
    int refreshed = 0;
    for (DWORD i = 0; list && i < count; i++) {
        ENUM_SERVICE_STATUS_PROCESSW *s = &list[i];
        svc_t *e = (svc_t *)sm_get(&g_svc, s->lpServiceName);
        if (e == NULL) {
            e = (svc_t *)calloc(1, sizeof(svc_t));
            if (e) {
                svc_load_cfg(scm, s->lpServiceName, e);
                if (sm_put(&g_svc, s->lpServiceName, e) != 0) {
                    svc_free_fields(e);
                    free(e);
                    e = NULL;
                }
            }
        } else if (tick >= e->next_refresh && refreshed < 8) {
            svc_load_cfg(scm, s->lpServiceName, e);
            refreshed++;
        }
        sb_raw(&sb, "V", 1);
        sb_tab_w(&sb, s->lpServiceName);
        sb_tab_w(&sb, s->lpDisplayName);
        sb_tab_i(&sb, (int64_t)s->ServiceStatusProcess.dwProcessId);
        sb_tab_s(&sb, svc_state_name(s->ServiceStatusProcess.dwCurrentState));
        sb_tab_s(&sb, e ? e->start : "");
        sb_tab_s(&sb, e ? e->desc : "");
        sb_tab_s(&sb, e ? e->group : "");
        sb_tab_s(&sb, e ? e->path : "");
        sb_tab_s(&sb, e ? e->account : "");
        sb_nl(&sb);
    }
    free(list);
    CloseServiceHandle(scm);
    return sb_done(&sb);
}

static void svc_invalidate(const wchar_t *name) {
    svc_t *e = (svc_t *)sm_get(&g_svc, name);
    if (e) {
        e->next_refresh = 0;
    }
}

int32_t jn_tm_service_control(const char *name, int32_t op) {
    if (name == NULL || name[0] == 0 || op < 1 || op > 3) {
        return ERROR_INVALID_PARAMETER;
    }
    wchar_t *wn = u2w_dup(name);
    if (wn == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    SC_HANDLE scm = OpenSCManagerW(NULL, NULL, SC_MANAGER_CONNECT);
    if (scm == NULL) {
        int32_t e = (int32_t)GetLastError();
        free(wn);
        return e;
    }
    SC_HANDLE s = OpenServiceW(scm, wn, SERVICE_START | SERVICE_STOP | SERVICE_QUERY_STATUS);
    if (s == NULL) {
        int32_t e = (int32_t)GetLastError();
        CloseServiceHandle(scm);
        free(wn);
        return e;
    }
    int32_t r = 0;
    if (op == 1) {
        if (!StartServiceW(s, 0, NULL)) {
            r = (int32_t)GetLastError();
        }
    } else {
        SERVICE_STATUS ss;
        if (!ControlService(s, SERVICE_CONTROL_STOP, &ss)) {
            r = (int32_t)GetLastError();
            if (op == 3 && r == ERROR_SERVICE_NOT_ACTIVE) {
                r = 0; /* already stopped: just start */
            }
        }
        if (op == 3 && r == 0) {
            ULONGLONG t0 = GetTickCount64();
            for (;;) {
                if (!QueryServiceStatus(s, &ss)) {
                    r = (int32_t)GetLastError();
                    break;
                }
                if (ss.dwCurrentState == SERVICE_STOPPED) {
                    break;
                }
                if (GetTickCount64() - t0 > 5000ULL) {
                    r = ERROR_SERVICE_REQUEST_TIMEOUT;
                    break;
                }
                Sleep(100);
            }
            if (r == 0 && !StartServiceW(s, 0, NULL)) {
                r = (int32_t)GetLastError();
            }
        }
    }
    CloseServiceHandle(s);
    CloseServiceHandle(scm);
    svc_invalidate(wn);
    free(wn);
    return r;
}

int32_t jn_tm_service_open_pid(const char *name) {
    if (name == NULL || name[0] == 0) {
        return -1;
    }
    wchar_t *wn = u2w_dup(name);
    if (wn == NULL) {
        return -1;
    }
    int32_t r = -1;
    SC_HANDLE scm = OpenSCManagerW(NULL, NULL, SC_MANAGER_CONNECT);
    if (scm) {
        SC_HANDLE s = OpenServiceW(scm, wn, SERVICE_QUERY_STATUS);
        if (s) {
            SERVICE_STATUS_PROCESS ssp;
            DWORD need = 0;
            if (QueryServiceStatusEx(s, SC_STATUS_PROCESS_INFO, (LPBYTE)&ssp, sizeof(ssp), &need)) {
                r = (int32_t)ssp.dwProcessId;
            }
            CloseServiceHandle(s);
        }
        CloseServiceHandle(scm);
    }
    free(wn);
    return r;
}

int32_t jn_tm_proc_goto_services(int32_t pid) {
    if (pid <= 0) {
        return 0;
    }
    SC_HANDLE scm = OpenSCManagerW(NULL, NULL, SC_MANAGER_CONNECT | SC_MANAGER_ENUMERATE_SERVICE);
    if (scm == NULL) {
        return -1;
    }
    DWORD count = 0;
    ENUM_SERVICE_STATUS_PROCESSW *list = svc_enum(scm, &count);
    CloseServiceHandle(scm);
    if (list == NULL) {
        return -1;
    }
    int32_t n = 0;
    for (DWORD i = 0; i < count; i++) {
        if (list[i].ServiceStatusProcess.dwProcessId == (DWORD)pid) {
            n++;
        }
    }
    free(list);
    return n;
}

/* ======================================================================== */
/* Sessions                                                                 */
/* ======================================================================== */

typedef BOOL(WINAPI *fn_WTSEnumerateSessionsW)(HANDLE, DWORD, DWORD, PWTS_SESSION_INFOW *, DWORD *);
typedef BOOL(WINAPI *fn_WTSQuerySessionInformationW)(HANDLE, DWORD, int, LPWSTR *, DWORD *);
typedef void(WINAPI *fn_WTSFreeMemory)(PVOID);
typedef BOOL(WINAPI *fn_WTSDisconnectSession)(HANDLE, DWORD, BOOL);
typedef BOOL(WINAPI *fn_WTSLogoffSession)(HANDLE, DWORD, BOOL);

static fn_WTSEnumerateSessionsW wts_Enum;
static fn_WTSQuerySessionInformationW wts_Query;
static fn_WTSFreeMemory wts_Free;
static fn_WTSDisconnectSession wts_Disconnect;
static fn_WTSLogoffSession wts_Logoff;

static int wts_load(void) {
    static int tried, ok;
    if (tried) {
        return ok;
    }
    tried = 1;
    HMODULE h = load_sys(L"wtsapi32.dll");
    if (h == NULL) {
        return 0;
    }
    wts_Enum = (fn_WTSEnumerateSessionsW)(void *)GetProcAddress(h, "WTSEnumerateSessionsW");
    wts_Query = (fn_WTSQuerySessionInformationW)(void *)GetProcAddress(h, "WTSQuerySessionInformationW");
    wts_Free = (fn_WTSFreeMemory)(void *)GetProcAddress(h, "WTSFreeMemory");
    wts_Disconnect = (fn_WTSDisconnectSession)(void *)GetProcAddress(h, "WTSDisconnectSession");
    wts_Logoff = (fn_WTSLogoffSession)(void *)GetProcAddress(h, "WTSLogoffSession");
    ok = wts_Enum && wts_Query && wts_Free;
    return ok;
}

/* Returns malloc'd copy of a string session property or NULL. */
static wchar_t *wts_str(DWORD sid, int cls) {
    LPWSTR p = NULL;
    DWORD n = 0;
    wchar_t *r = NULL;
    if (wts_Query(WTS_CURRENT_SERVER_HANDLE, sid, cls, &p, &n) && p) {
        r = _wcsdup(p);
        wts_Free(p);
    }
    return r;
}

char *jn_tm_sessions(void) {
    sb_t sb = {0};
    if (!wts_load()) {
        return sb_done(&sb);
    }
    PWTS_SESSION_INFOW si = NULL;
    DWORD count = 0;
    if (!wts_Enum(WTS_CURRENT_SERVER_HANDLE, 0, 1, &si, &count)) {
        return sb_done(&sb);
    }
    for (DWORD i = 0; i < count; i++) {
        DWORD sid = si[i].SessionId;
        if (sid == 0) {
            continue;
        }
        wchar_t *user = wts_str(sid, 5 /* WTSUserName */);
        if (user == NULL || user[0] == 0) {
            free(user);
            continue;
        }
        wchar_t *domain = wts_str(sid, 7 /* WTSDomainName */);
        wchar_t *client = wts_str(sid, 10 /* WTSClientName */);
        int64_t logon = -1;
        LPWSTR raw = NULL;
        DWORD rn = 0;
        if (wts_Query(WTS_CURRENT_SERVER_HANDLE, sid, 24 /* WTSSessionInfo */, &raw, &rn) && raw) {
            if (rn >= sizeof(WTSINFOW)) {
                const WTSINFOW *wi = (const WTSINFOW *)raw;
                long long ft = wi->LogonTime.QuadPart;
                if (ft > 116444736000000000LL) {
                    logon = (int64_t)((ft - 116444736000000000LL) / 10000000LL);
                }
            }
            wts_Free(raw);
        }
        const char *state = "other";
        switch (si[i].State) {
        case WTSActive: state = "active"; break;
        case WTSDisconnected: state = "disconnected"; break;
        case WTSIdle: state = "idle"; break;
        default: break;
        }
        sb_raw(&sb, "U", 1);
        sb_tab_i(&sb, sid);
        sb_tab_w(&sb, user);
        sb_tab_w(&sb, domain);
        sb_tab_s(&sb, state);
        sb_tab_w(&sb, client);
        sb_tab_w(&sb, si[i].pWinStationName);
        sb_tab_i(&sb, logon);
        sb_nl(&sb);
        free(user);
        free(domain);
        free(client);
    }
    wts_Free(si);
    return sb_done(&sb);
}

int32_t jn_tm_session_action(int32_t session_id, int32_t op) {
    if (session_id < 0) {
        return ERROR_INVALID_PARAMETER;
    }
    if (op == 3) {
        return -1;
    }
    if (!wts_load()) {
        return -1;
    }
    BOOL ok;
    if (op == 1) {
        if (!wts_Disconnect) {
            return -1;
        }
        ok = wts_Disconnect(WTS_CURRENT_SERVER_HANDLE, (DWORD)session_id, FALSE);
    } else if (op == 2) {
        if (!wts_Logoff) {
            return -1;
        }
        ok = wts_Logoff(WTS_CURRENT_SERVER_HANDLE, (DWORD)session_id, FALSE);
    } else {
        return ERROR_INVALID_PARAMETER;
    }
    return ok ? 0 : (int32_t)GetLastError();
}

/* ======================================================================== */
/* Startup apps                                                             */
/* ======================================================================== */

typedef struct {
    const char *token;
    int hklm;
    int folder;
    const wchar_t *runkey;
    const wchar_t *approved; /* sub key under ...\Explorer\StartupApproved */
    int csidl;
} st_src;

static const st_src g_st_src[5] = {
    {"HKCU_Run", 0, 0, L"Software\\Microsoft\\Windows\\CurrentVersion\\Run", L"Run", 0},
    {"HKLM_Run", 1, 0, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", L"Run", 0},
    {"HKLM_Run32", 1, 0, L"SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Run", L"Run32", 0},
    {"StartupFolder", 0, 1, NULL, L"StartupFolder", CSIDL_STARTUP},
    {"CommonStartupFolder", 1, 1, NULL, L"StartupFolder", CSIDL_COMMON_STARTUP},
};

static void approved_path(const st_src *s, wchar_t *out, size_t cap) {
    _snwprintf(out, cap, L"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\%ls", s->approved);
    out[cap - 1] = 0;
}

/* 1 enabled, 0 disabled; missing => 1 */
static int approved_state(const st_src *s, const wchar_t *valname) {
    wchar_t path[256];
    approved_path(s, path, 256);
    HKEY roots[2];
    int nr = 0;
    if (s->hklm) {
        roots[nr++] = HKEY_LOCAL_MACHINE;
    }
    roots[nr++] = HKEY_CURRENT_USER;
    for (int i = 0; i < nr; i++) {
        HKEY k;
        if (RegOpenKeyExW(roots[i], path, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
            continue;
        }
        BYTE data[16];
        DWORD type = 0, sz = sizeof(data);
        LONG r = RegQueryValueExW(k, valname, NULL, &type, data, &sz);
        RegCloseKey(k);
        if ((r == ERROR_SUCCESS || r == ERROR_MORE_DATA) && type == REG_BINARY && sz >= 1) {
            return (data[0] & 1) ? 0 : 1;
        }
    }
    return 1;
}

static int file_exists(const wchar_t *p) {
    DWORD a = GetFileAttributesW(p);
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

/* Resolve the image path out of a command line. Returns 1 if the file exists. */
static int resolve_exe_path(const wchar_t *cmd, wchar_t *out, size_t cap) {
    wchar_t exp[2048];
    out[0] = 0;
    DWORD n = ExpandEnvironmentStringsW(cmd, exp, 2048);
    if (n == 0 || n > 2048) {
        _snwprintf(exp, 2048, L"%ls", cmd);
        exp[2047] = 0;
    }
    wchar_t *s = exp;
    while (*s && iswspace((wint_t)*s)) {
        s++;
    }
    wchar_t cand[1024];
    cand[0] = 0;
    int found = 0;
    if (*s == L'"') {
        s++;
        const wchar_t *q = wcschr(s, L'"');
        size_t l = q ? (size_t)(q - s) : wcslen(s);
        if (l > 1023) {
            l = 1023;
        }
        memcpy(cand, s, l * sizeof(wchar_t));
        cand[l] = 0;
    } else {
        size_t len = wcslen(s);
        size_t first_end = 0;
        for (size_t i = 0; i <= len; i++) {
            if (s[i] == 0 || s[i] == L' ') {
                if (first_end == 0) {
                    first_end = i;
                }
                if (i == 0 || i > 1023) {
                    continue;
                }
                memcpy(cand, s, i * sizeof(wchar_t));
                cand[i] = 0;
                if (file_exists(cand)) {
                    found = 1;
                    break;
                }
                wchar_t withexe[1030];
                _snwprintf(withexe, 1030, L"%ls.exe", cand);
                withexe[1029] = 0;
                if (file_exists(withexe)) {
                    wcsncpy(cand, withexe, 1023);
                    cand[1023] = 0;
                    found = 1;
                    break;
                }
                if (i > 260) {
                    break;
                }
            }
        }
        if (!found) {
            size_t l = first_end > 1023 ? 1023 : first_end;
            memcpy(cand, s, l * sizeof(wchar_t));
            cand[l] = 0;
        }
    }
    if (cand[0] == 0) {
        return 0;
    }
    if (!found && file_exists(cand)) {
        found = 1;
    }
    if (!found && wcschr(cand, L'\\') == NULL && wcschr(cand, L'/') == NULL) {
        wchar_t sp[1024];
        if (SearchPathW(NULL, cand, L".exe", 1024, sp, NULL) > 0) {
            wcsncpy(cand, sp, 1023);
            cand[1023] = 0;
            found = 1;
        }
    }
    wcsncpy(out, cand, cap - 1);
    out[cap - 1] = 0;
    return found;
}

/* Minimal .lnk reader: local base path from the LinkInfo block. */
static int lnk_target(const wchar_t *lnk, wchar_t *out, size_t cap) {
    out[0] = 0;
    HANDLE h = CreateFileW(lnk, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL, OPEN_EXISTING, 0, NULL);
    if (h == INVALID_HANDLE_VALUE) {
        return 0;
    }
    DWORD fsz = GetFileSize(h, NULL);
    if (fsz < 0x4C || fsz > (1u << 20)) {
        CloseHandle(h);
        return 0;
    }
    BYTE *b = (BYTE *)malloc(fsz);
    DWORD rd = 0;
    int ok = 0;
    if (b && ReadFile(h, b, fsz, &rd, NULL) && rd == fsz && *(DWORD *)b == 0x4C) {
        DWORD flags = *(DWORD *)(b + 0x14);
        DWORD pos = 0x4C;
        if (flags & 0x1) {
            if (pos + 2 <= fsz) {
                pos += 2 + *(WORD *)(b + pos);
            } else {
                pos = fsz;
            }
        }
        if ((flags & 0x2) && pos + 0x1C <= fsz) {
            const BYTE *li = b + pos;
            DWORD lisz = *(const DWORD *)li;
            DWORD hdr = *(const DWORD *)(li + 4);
            DWORD lflags = *(const DWORD *)(li + 8);
            DWORD base_off = *(const DWORD *)(li + 16);
            DWORD suf_off = *(const DWORD *)(li + 24);
            if (pos + lisz <= fsz && (lflags & 1)) {
                wchar_t base[1024], suf[1024];
                base[0] = suf[0] = 0;
                if (hdr >= 0x24 && lisz >= 0x24) {
                    DWORD base_u = *(const DWORD *)(li + 28);
                    DWORD suf_u = *(const DWORD *)(li + 32);
                    if (base_u && base_u < lisz) {
                        wcsncpy(base, (const wchar_t *)(li + base_u), 1023);
                        base[1023] = 0;
                    }
                    if (suf_u && suf_u < lisz) {
                        wcsncpy(suf, (const wchar_t *)(li + suf_u), 1023);
                        suf[1023] = 0;
                    }
                }
                if (base[0] == 0 && base_off && base_off < lisz) {
                    MultiByteToWideChar(CP_ACP, 0, (const char *)(li + base_off), -1, base, 1024);
                }
                if (suf[0] == 0 && suf_off && suf_off < lisz) {
                    MultiByteToWideChar(CP_ACP, 0, (const char *)(li + suf_off), -1, suf, 1024);
                }
                if (base[0]) {
                    _snwprintf(out, cap, L"%ls%ls", base, suf);
                    out[cap - 1] = 0;
                    ok = out[0] != 0;
                }
            }
        }
    }
    free(b);
    CloseHandle(h);
    return ok;
}

static void startup_emit(sb_t *sb, int src, const wchar_t *name, const wchar_t *valname, const wchar_t *command,
                         const wchar_t *lnk_path) {
    const st_src *s = &g_st_src[src];
    wchar_t path[1024];
    path[0] = 0;
    if (lnk_path) {
        wchar_t tgt[1024];
        if (lnk_target(lnk_path, tgt, 1024) && file_exists(tgt)) {
            wcsncpy(path, tgt, 1023);
            path[1023] = 0;
        } else {
            wcsncpy(path, lnk_path, 1023);
            path[1023] = 0;
        }
    } else {
        resolve_exe_path(command, path, 1024);
    }
    const char *company = "";
    if (path[0] && !(lnk_path && _wcsicmp(path, lnk_path) == 0)) {
        vi_t *vi = vi_get(path, 0);
        if (vi) {
            company = vi->company;
        }
    }
    int enabled = approved_state(s, valname);

    sb_raw(sb, "T", 1);
    sb_tab(sb);
    sb_str(sb, s->token);
    sb_raw(sb, "|", 1);
    sb_w(sb, name);
    sb_tab_w(sb, name);
    sb_tab_s(sb, company);
    sb_tab_w(sb, command);
    sb_tab_s(sb, s->token);
    sb_tab_i(sb, enabled);
    sb_tab_i(sb, -1);
    sb_tab_w(sb, path);
    sb_nl(sb);
}

static void startup_run_key(sb_t *sb, int src) {
    const st_src *s = &g_st_src[src];
    HKEY k;
    HKEY root = s->hklm ? HKEY_LOCAL_MACHINE : HKEY_CURRENT_USER;
    if (RegOpenKeyExW(root, s->runkey, 0, KEY_QUERY_VALUE | KEY_WOW64_64KEY, &k) != ERROR_SUCCESS) {
        return;
    }
    for (DWORD i = 0;; i++) {
        wchar_t name[512];
        DWORD nl = 512, type = 0, dl = 0;
        LONG r = RegEnumValueW(k, i, name, &nl, NULL, &type, NULL, &dl);
        if (r == ERROR_NO_MORE_ITEMS) {
            break;
        }
        if (r != ERROR_SUCCESS && r != ERROR_MORE_DATA) {
            break;
        }
        if ((type != REG_SZ && type != REG_EXPAND_SZ) || dl > (64u << 10)) {
            continue;
        }
        wchar_t *data = (wchar_t *)calloc(1, dl + 4);
        if (data == NULL) {
            continue;
        }
        DWORD sz = dl;
        nl = 512;
        if (RegQueryValueExW(k, name, NULL, &type, (LPBYTE)data, &sz) == ERROR_SUCCESS) {
            data[sz / 2] = 0;
            wchar_t exp[2048];
            DWORD n = ExpandEnvironmentStringsW(data, exp, 2048);
            const wchar_t *cmd = (n > 0 && n <= 2048) ? exp : data;
            startup_emit(sb, src, name, name, cmd, NULL);
        }
        free(data);
    }
    RegCloseKey(k);
}

static void startup_folder(sb_t *sb, int src) {
    const st_src *s = &g_st_src[src];
    wchar_t dir[MAX_PATH];
    if (FAILED(SHGetFolderPathW(NULL, s->csidl, NULL, 0, dir))) {
        return;
    }
    wchar_t pat[MAX_PATH + 4];
    _snwprintf(pat, MAX_PATH + 4, L"%ls\\*", dir);
    pat[MAX_PATH + 3] = 0;
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pat, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return;
    }
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            continue;
        }
        if (_wcsicmp(fd.cFileName, L"desktop.ini") == 0) {
            continue;
        }
        wchar_t full[MAX_PATH * 2];
        _snwprintf(full, MAX_PATH * 2, L"%ls\\%ls", dir, fd.cFileName);
        full[MAX_PATH * 2 - 1] = 0;
        wchar_t base[MAX_PATH];
        wcsncpy(base, fd.cFileName, MAX_PATH - 1);
        base[MAX_PATH - 1] = 0;
        wchar_t *dot = wcsrchr(base, L'.');
        if (dot && dot != base) {
            *dot = 0;
        }
        startup_emit(sb, src, base, fd.cFileName, full, full);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

char *jn_tm_startup(void) {
    sb_t sb = {0};
    startup_run_key(&sb, 0);
    startup_run_key(&sb, 1);
    startup_run_key(&sb, 2);
    startup_folder(&sb, 3);
    startup_folder(&sb, 4);
    return sb_done(&sb);
}

int32_t jn_tm_startup_set(const char *id, int32_t enable) {
    if (id == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    const char *bar = strchr(id, '|');
    if (bar == NULL || bar[1] == 0) {
        return ERROR_INVALID_PARAMETER;
    }
    int src = -1;
    for (int i = 0; i < 5; i++) {
        size_t tl = strlen(g_st_src[i].token);
        if ((size_t)(bar - id) == tl && strncmp(id, g_st_src[i].token, tl) == 0) {
            src = i;
            break;
        }
    }
    if (src < 0) {
        return ERROR_INVALID_PARAMETER;
    }
    const st_src *s = &g_st_src[src];
    wchar_t *name = u2w_dup(bar + 1);
    if (name == NULL) {
        return ERROR_INVALID_PARAMETER;
    }
    wchar_t valname[MAX_PATH];
    wcsncpy(valname, name, MAX_PATH - 1);
    valname[MAX_PATH - 1] = 0;
    if (s->folder) {
        /* approval value is the file name including extension: find it */
        wchar_t dir[MAX_PATH];
        int found = 0;
        if (SUCCEEDED(SHGetFolderPathW(NULL, s->csidl, NULL, 0, dir))) {
            wchar_t pat[MAX_PATH + 4];
            _snwprintf(pat, MAX_PATH + 4, L"%ls\\*", dir);
            pat[MAX_PATH + 3] = 0;
            WIN32_FIND_DATAW fd;
            HANDLE h = FindFirstFileW(pat, &fd);
            if (h != INVALID_HANDLE_VALUE) {
                do {
                    if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                        continue;
                    }
                    wchar_t base[MAX_PATH];
                    wcsncpy(base, fd.cFileName, MAX_PATH - 1);
                    base[MAX_PATH - 1] = 0;
                    wchar_t *dot = wcsrchr(base, L'.');
                    if (dot && dot != base) {
                        *dot = 0;
                    }
                    if (_wcsicmp(base, name) == 0 || _wcsicmp(fd.cFileName, name) == 0) {
                        wcsncpy(valname, fd.cFileName, MAX_PATH - 1);
                        valname[MAX_PATH - 1] = 0;
                        found = 1;
                        break;
                    }
                } while (FindNextFileW(h, &fd));
                FindClose(h);
            }
        }
        if (!found) {
            free(name);
            return ERROR_FILE_NOT_FOUND;
        }
    }
    free(name);

    BYTE data[12];
    memset(data, 0, sizeof(data));
    if (enable) {
        data[0] = 2;
    } else {
        data[0] = 3;
        FILETIME ft;
        GetSystemTimeAsFileTime(&ft);
        memcpy(data + 4, &ft, 8);
    }
    wchar_t path[256];
    approved_path(s, path, 256);
    HKEY k;
    LONG r = RegCreateKeyExW(s->hklm ? HKEY_LOCAL_MACHINE : HKEY_CURRENT_USER, path, 0, NULL, 0, KEY_SET_VALUE | KEY_QUERY_VALUE | KEY_WOW64_64KEY, NULL, &k, NULL);
    if (r != ERROR_SUCCESS) {
        return (int32_t)r;
    }
    r = RegSetValueExW(k, valname, 0, REG_BINARY, data, sizeof(data));
    RegCloseKey(k);
    if (r != ERROR_SUCCESS) {
        return (int32_t)r;
    }
    if (s->hklm) {
        /* keep a pre-existing per-user override in sync (best effort) */
        HKEY uk;
        if (RegOpenKeyExW(HKEY_CURRENT_USER, path, 0, KEY_SET_VALUE | KEY_QUERY_VALUE | KEY_WOW64_64KEY, &uk) == ERROR_SUCCESS) {
            DWORD type = 0, sz = 0;
            if (RegQueryValueExW(uk, valname, NULL, &type, NULL, &sz) == ERROR_SUCCESS) {
                RegSetValueExW(uk, valname, 0, REG_BINARY, data, sizeof(data));
            }
            RegCloseKey(uk);
        }
    }
    return 0;
}

/* ======================================================================== */
/* Misc                                                                     */
/* ======================================================================== */

int32_t jn_tm_is_elevated(void) {
    HANDLE tok = NULL;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &tok)) {
        return -1;
    }
    DWORD v = 0, rl = 0;
    int32_t r = -1;
    if (GetTokenInformation(tok, TokenElevation, &v, sizeof(v), &rl)) {
        r = v ? 1 : 0;
    }
    CloseHandle(tok);
    return r;
}

static int is_admin_member(void) {
    SID_IDENTIFIER_AUTHORITY nt = SECURITY_NT_AUTHORITY;
    PSID sid = NULL;
    BOOL member = FALSE;
    if (!AllocateAndInitializeSid(&nt, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, &sid)) {
        return 0;
    }
    if (!CheckTokenMembership(NULL, sid, &member)) {
        member = FALSE;
    }
    FreeSid(sid);
    return member ? 1 : 0;
}

char *jn_tm_system_info(void) {
    sb_t sb = {0};
    wchar_t host[256];
    DWORD hl = 256;
    host[0] = 0;
    if (!GetComputerNameExW(ComputerNameDnsHostname, host, &hl)) {
        hl = 256;
        GetComputerNameW(host, &hl);
    }

    const wchar_t *ck = L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
    wchar_t product[128], dispver[64], buildstr[32];
    reg_get_sz(HKEY_LOCAL_MACHINE, ck, L"ProductName", product, 128);
    reg_get_sz(HKEY_LOCAL_MACHINE, ck, L"DisplayVersion", dispver, 64);
    if (dispver[0] == 0) {
        reg_get_sz(HKEY_LOCAL_MACHINE, ck, L"ReleaseId", dispver, 64);
    }
    reg_get_sz(HKEY_LOCAL_MACHINE, ck, L"CurrentBuildNumber", buildstr, 32);
    DWORD ubr = 0;
    int have_ubr = (reg_get_dword(HKEY_LOCAL_MACHINE, ck, L"UBR", &ubr) == 0);
    int build = (int)wcstol(buildstr, NULL, 10);
    if (build >= 22000 && _wcsnicmp(product, L"Windows 10", 10) == 0) {
        wchar_t tmp[128];
        _snwprintf(tmp, 128, L"Windows 11%ls", product + 10);
        tmp[127] = 0;
        wcscpy(product, tmp);
    }
    wchar_t caption[256];
    if (dispver[0]) {
        _snwprintf(caption, 256, L"%ls %ls", product, dispver);
    } else {
        _snwprintf(caption, 256, L"%ls", product);
    }
    caption[255] = 0;
    wchar_t bs[64];
    if (have_ubr) {
        _snwprintf(bs, 64, L"%ls.%lu", buildstr, (unsigned long)ubr);
    } else {
        _snwprintf(bs, 64, L"%ls", buildstr);
    }
    bs[63] = 0;

    wchar_t user[256];
    DWORD ul = 256;
    user[0] = 0;
    if (!GetUserNameW(user, &ul)) {
        user[0] = 0;
    }

    sb_raw(&sb, "M", 1);
    sb_tab_w(&sb, host);
    sb_tab_w(&sb, caption);
    sb_tab_w(&sb, bs);
    sb_tab_w(&sb, user);
    sb_tab_i(&sb, is_admin_member());
    sb_nl(&sb);
    return sb_done(&sb);
}

int32_t jn_tm_relaunch_elevated(void) {
    wchar_t exe[1024];
    DWORD n = GetModuleFileNameW(NULL, exe, 1024);
    if (n == 0 || n >= 1024) {
        return (int32_t)(GetLastError() ? GetLastError() : ERROR_INSUFFICIENT_BUFFER);
    }
    /* arguments = command line without argv[0] */
    const wchar_t *cl = GetCommandLineW();
    const wchar_t *args = L"";
    if (cl) {
        const wchar_t *p = cl;
        while (*p == L' ') {
            p++;
        }
        if (*p == L'"') {
            p++;
            while (*p && *p != L'"') {
                p++;
            }
            if (*p == L'"') {
                p++;
            }
        } else {
            while (*p && *p != L' ') {
                p++;
            }
        }
        while (*p == L' ') {
            p++;
        }
        args = p;
    }
    SHELLEXECUTEINFOW sei;
    memset(&sei, 0, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_NOCLOSEPROCESS;
    sei.lpVerb = L"runas";
    sei.lpFile = exe;
    sei.lpParameters = args[0] ? args : NULL;
    sei.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&sei)) {
        return (int32_t)GetLastError();
    }
    if (sei.hProcess) {
        CloseHandle(sei.hProcess);
    }
    return 0;
}
