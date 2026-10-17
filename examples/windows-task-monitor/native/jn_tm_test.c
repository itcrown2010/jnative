/* Author: 张晓达
 * Self test for jn_tm (Windows task monitor native data layer).
 *
 * Usage: jn_tm_test.exe [--full] [--ops] [--ui] [--leak]
 *   --full  print every line of services / procx output (default: first 12 lines)
 *   --leak  call the text APIs 200 times and report private-byte / handle growth
 *   --ops   exercise process operations on a spawned helper process
 *   --ui    also run API calls that open UI (properties, services.msc, explorer, clipboard...)
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jn_tm.h"

static LARGE_INTEGER g_freq;

static double ms_since(LARGE_INTEGER t0) {
    LARGE_INTEGER t1;
    QueryPerformanceCounter(&t1);
    return (double)(t1.QuadPart - t0.QuadPart) * 1000.0 / (double)g_freq.QuadPart;
}

static int g_full;

static int count_lines(const char *s) {
    int n = 0;
    for (; s && *s; s++) {
        if (*s == '\n') {
            n++;
        }
    }
    return n;
}

static int count_prefix(const char *s, char c) {
    int n = 0;
    const char *p = s;
    while (p && *p) {
        if (p[0] == c && p[1] == '\t') {
            n++;
        }
        const char *nl = strchr(p, '\n');
        if (!nl) {
            break;
        }
        p = nl + 1;
    }
    return n;
}

/* print text; limit lines unless full. Output is UTF-8 so write bytes straight to stdout. */
static void print_text(const char *label, const char *s, int limit) {
    printf("--- %s (%d lines)\n", label, count_lines(s));
    int n = 0;
    const char *p = s;
    while (p && *p) {
        const char *nl = strchr(p, '\n');
        size_t len = nl ? (size_t)(nl - p) : strlen(p);
        if (!g_full && limit > 0 && n >= limit) {
            printf("  ... (%d more)\n", count_lines(s) - n);
            break;
        }
        printf("  %.*s\n", (int)(len > 400 ? 400 : len), p);
        n++;
        if (!nl) {
            break;
        }
        p = nl + 1;
    }
}

static void test_text_api(const char *name, char *(*fn)(void), int limit, int twice) {
    for (int round = 0; round < (twice ? 2 : 1); round++) {
        LARGE_INTEGER t0;
        QueryPerformanceCounter(&t0);
        char *s = fn();
        double ms = ms_since(t0);
        char label[96];
        snprintf(label, sizeof(label), "%s call #%d  %.1f ms", name, round + 1, ms);
        if (s == NULL) {
            printf("--- %s: NULL\n", label);
        } else {
            print_text(label, s, limit);
            if (round == 1 || !twice) {
                if (strcmp(name, "jn_tm_perf") == 0) {
                    printf("    summary: H=%d C=%d T=%d I=%d D=%d N=%d G=%d\n", count_prefix(s, 'H'), count_prefix(s, 'C'),
                           count_prefix(s, 'T'), count_prefix(s, 'I'), count_prefix(s, 'D'), count_prefix(s, 'N'),
                           count_prefix(s, 'G'));
                } else if (strcmp(name, "jn_tm_services") == 0) {
                    printf("    summary: services=%d\n", count_lines(s));
                } else if (strcmp(name, "jn_tm_procx") == 0) {
                    printf("    summary: processes=%d\n", count_lines(s));
                } else if (strcmp(name, "jn_tm_sessions") == 0) {
                    printf("    summary: sessions=%d\n", count_lines(s));
                } else if (strcmp(name, "jn_tm_startup") == 0) {
                    printf("    summary: startup entries=%d\n", count_lines(s));
                }
            }
            jn_tm_free(s);
        }
        if (round == 0 && twice) {
            Sleep(1000);
        }
    }
}

/* leak / growth check: run the cheap-to-call APIs many times and watch private bytes */
#include <psapi.h>
static size_t private_bytes_now(void) {
    typedef BOOL(WINAPI * fn_gpmi)(HANDLE, PROCESS_MEMORY_COUNTERS *, DWORD);
    fn_gpmi f = (fn_gpmi)(void *)GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "K32GetProcessMemoryInfo");
    PROCESS_MEMORY_COUNTERS_EX pmc;
    memset(&pmc, 0, sizeof(pmc));
    pmc.cb = sizeof(pmc);
    if (f) {
        f(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS *)&pmc, sizeof(pmc));
    }
    return pmc.PrivateUsage;
}

static void leak_check(void) {
    printf("=== leak check (200 x perf+procx+services+sessions+startup, 20 ms apart)\n");
    DWORD h0 = 0, h1 = 0;
    GetProcessHandleCount(GetCurrentProcess(), &h0);
    size_t m0 = 0, m1 = 0, mid = 0;
    for (int i = 0; i < 200; i++) {
        char *s;
        s = jn_tm_perf();
        jn_tm_free(s);
        s = jn_tm_procx();
        jn_tm_free(s);
        s = jn_tm_services();
        jn_tm_free(s);
        s = jn_tm_sessions();
        jn_tm_free(s);
        s = jn_tm_startup();
        jn_tm_free(s);
        if (i == 19) {
            m0 = private_bytes_now(); /* after warm-up */
            GetProcessHandleCount(GetCurrentProcess(), &h0);
        }
        if (i == 109) {
            mid = private_bytes_now();
        }
        Sleep(20);
    }
    m1 = private_bytes_now();
    GetProcessHandleCount(GetCurrentProcess(), &h1);
    printf("    private bytes after warm-up=%zu  mid=%zu  end=%zu  (delta %lld KB)\n", m0, mid, m1, ((long long)m1 - (long long)m0) / 1024);
    printf("    handles after warm-up=%lu end=%lu\n", (unsigned long)h0, (unsigned long)h1);
}

static HANDLE spawn_hidden(const wchar_t *cmd_in, DWORD *pid_out) {
    wchar_t cmd[512];
    wcsncpy(cmd, cmd_in, 511);
    cmd[511] = 0;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    memset(&pi, 0, sizeof(pi));
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        return NULL;
    }
    CloseHandle(pi.hThread);
    *pid_out = pi.dwProcessId;
    return pi.hProcess;
}

static void test_ops(void) {
    printf("=== process operations on a spawned helper (ping)\n");
    DWORD pid = 0;
    HANDLE hp = spawn_hidden(L"ping.exe -n 60 127.0.0.1", &pid);
    if (!hp) {
        printf("  spawn failed\n");
        return;
    }
    Sleep(300);
    printf("  helper pid=%lu\n", (unsigned long)pid);
    printf("  get_affinity = 0x%llx\n", (unsigned long long)jn_tm_proc_get_affinity((int32_t)pid));
    long long aff = jn_tm_proc_get_affinity((int32_t)pid);
    printf("  set_affinity(1) = %d\n", jn_tm_proc_set_affinity((int32_t)pid, 1));
    printf("  get_affinity after = 0x%llx\n", (unsigned long long)jn_tm_proc_get_affinity((int32_t)pid));
    jn_tm_proc_set_affinity((int32_t)pid, aff);
    for (int cls = 5; cls >= 0; cls--) {
        if (cls == 0) {
            continue; /* skip realtime */
        }
        int r = jn_tm_proc_set_priority((int32_t)pid, cls);
        DWORD pc = GetPriorityClass(hp);
        printf("  set_priority(%d) = %d  GetPriorityClass=0x%lx\n", cls, r, (unsigned long)pc);
    }
    printf("  suspend(1) = %d\n", jn_tm_proc_suspend((int32_t)pid, 1));
    Sleep(200);
    {
        char *s = jn_tm_procx();
        char key[32];
        snprintf(key, sizeof(key), "X\t%lu\t", (unsigned long)pid);
        const char *p = s ? strstr(s, key) : NULL;
        if (p) {
            const char *nl = strchr(p, '\n');
            printf("  procx row while suspended: %.*s\n", (int)(nl ? nl - p : 200), p);
        } else {
            printf("  procx row not found\n");
        }
        jn_tm_free(s);
    }
    printf("  suspend(0) = %d\n", jn_tm_proc_suspend((int32_t)pid, 0));

    const char *dump = "jn_tm_test_dump.dmp";
    int r = jn_tm_proc_create_dump((int32_t)pid, dump);
    WIN32_FILE_ATTRIBUTE_DATA fa;
    long long sz = -1;
    if (GetFileAttributesExA(dump, GetFileExInfoStandard, &fa)) {
        sz = ((long long)fa.nFileSizeHigh << 32) | fa.nFileSizeLow;
    }
    printf("  create_dump = %d (0x%x)  size=%lld bytes\n", r, (unsigned)r, sz);
    DeleteFileA(dump);

    printf("  goto_services(pid of services.exe ~ none) = %d\n", jn_tm_proc_goto_services((int32_t)pid));
    TerminateProcess(hp, 0);
    CloseHandle(hp);

    /* kill tree: cmd /c ping -> child ping */
    DWORD cpid = 0;
    HANDLE hc = spawn_hidden(L"cmd.exe /c ping.exe -n 60 127.0.0.1", &cpid);
    if (hc) {
        Sleep(600);
        char *s = jn_tm_procx();
        jn_tm_free(s);
        int r2 = jn_tm_proc_kill_tree((int32_t)cpid);
        Sleep(300);
        DWORD ec = 0;
        GetExitCodeProcess(hc, &ec);
        printf("  kill_tree(cmd pid %lu) = %d  parent exit code=%lu (1=killed, 259=alive)\n", (unsigned long)cpid, r2, (unsigned long)ec);
        CloseHandle(hc);
    }
    printf("  kill_tree(4) = %d (expect 5)\n", jn_tm_proc_kill_tree(4));
}

static void test_services_ops(void) {
    printf("=== service queries\n");
    printf("  service_open_pid(\"Spooler\") = %d\n", jn_tm_service_open_pid("Spooler"));
    printf("  service_open_pid(\"EventLog\") = %d\n", jn_tm_service_open_pid("EventLog"));
    printf("  service_open_pid(\"no_such_service_xyz\") = %d\n", jn_tm_service_open_pid("no_such_service_xyz"));
    int pid = jn_tm_service_open_pid("EventLog");
    if (pid > 0) {
        printf("  goto_services(EventLog host pid %d) = %d\n", pid, jn_tm_proc_goto_services(pid));
    }
    printf("  service_control(\"no_such_service_xyz\", start) = %d (expect 1060)\n", jn_tm_service_control("no_such_service_xyz", 1));
    printf("  service_control(\"EventLog\", stop) = %d (expect 5 when not admin)\n", jn_tm_service_control("EventLog", 2));
}

static int startup_enabled_of(const char *id) {
    char *s = jn_tm_startup();
    int r = -2;
    if (s) {
        char key[600];
        snprintf(key, sizeof(key), "T\t%s\t", id);
        const char *p = strstr(s, key);
        if (p) {
            const char *nl = strchr(p, '\n');
            size_t len = nl ? (size_t)(nl - p) : strlen(p);
            /* enabled is the 7th field: T id name publisher command source enabled impact path */
            int tabs = 0;
            for (size_t i = 0; i < len; i++) {
                if (p[i] == '\t' && ++tabs == 6) {
                    r = atoi(p + i + 1);
                    break;
                }
            }
        }
        jn_tm_free(s);
    }
    return r;
}

static void test_startup_set(void) {
    printf("=== startup_set (toggle every HKCU_Run / StartupFolder entry, then restore)\n");
    char *s = jn_tm_startup();
    if (s) {
        const char *p = s;
        while (p && *p) {
            const char *nl = strchr(p, '\n');
            if (strncmp(p, "T\tHKCU_Run|", 11) == 0 || strncmp(p, "T\tStartupFolder|", 16) == 0) {
                char id[512] = "";
                const char *a = p + 2;
                const char *b = strchr(a, '\t');
                if (b && (size_t)(b - a) < sizeof(id)) {
                    memcpy(id, a, (size_t)(b - a));
                    id[b - a] = 0;
                    int orig = startup_enabled_of(id);
                    int r1 = jn_tm_startup_set(id, 0);
                    int st1 = startup_enabled_of(id);
                    int r2 = jn_tm_startup_set(id, 1);
                    int st2 = startup_enabled_of(id);
                    printf("  %s: orig=%d disable=%d->enabled=%d  enable=%d->enabled=%d\n", id, orig, r1, st1, r2, st2);
                    if (orig == 0) {
                        jn_tm_startup_set(id, 0);
                    }
                }
            }
            if (!nl) {
                break;
            }
            p = nl + 1;
        }
        jn_tm_free(s);
    }
    printf("  startup_set(\"HKLM_Run|__no_such__\", 0) = %d (5 when not admin)\n", jn_tm_startup_set("HKLM_Run|__no_such__", 0));
    if (jn_tm_is_elevated() == 1) {
        RegDeleteKeyValueA(HKEY_LOCAL_MACHINE, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run", "__no_such__");
    }
}
/* reads CF_UNICODETEXT into a malloc'd wide string (NULL if none) */
static wchar_t *clip_read(void) {
    wchar_t *r = NULL;
    if (OpenClipboard(NULL)) {
        HANDLE h = GetClipboardData(CF_UNICODETEXT);
        if (h) {
            const wchar_t *p = (const wchar_t *)GlobalLock(h);
            if (p) {
                r = _wcsdup(p);
                GlobalUnlock(h);
            }
        }
        CloseClipboard();
    }
    return r;
}

/* safe checks that never open any window; the clipboard is restored afterwards */
static void test_safe_ui(void) {
    printf("=== safe shell/clipboard APIs\n");
    wchar_t *saved = clip_read();
    int r = jn_tm_copy_text("jn_tm_test \xE4\xB8\xAD\xE6\x96\x87 clipboard");
    wchar_t *back = clip_read();
    int ok = back && wcscmp(back, L"jn_tm_test \x4E2D\x6587 clipboard") == 0;
    printf("  copy_text = %d, read-back %s\n", r, ok ? "matches (UTF-16 CF_UNICODETEXT incl. Chinese)" : "MISMATCH");
    free(back);
    if (saved) {
        char *u8 = NULL;
        int n = WideCharToMultiByte(CP_UTF8, 0, saved, -1, NULL, 0, NULL, NULL);
        if (n > 0 && (u8 = (char *)malloc((size_t)n)) != NULL) {
            WideCharToMultiByte(CP_UTF8, 0, saved, -1, u8, n, NULL, NULL);
            jn_tm_copy_text(u8);
            free(u8);
        }
        free(saved);
    }
    printf("  open_path(nonexistent) = %d (expect 2)\n", jn_tm_open_path("C:\\__jn_tm_no_such_path__\\x.txt"));
    printf("  proc_properties(-5) = %d (expect 87)\n", jn_tm_proc_properties(-5));
    printf("  relaunch_elevated: not invoked (would show a UAC prompt)\n");
}

static void test_ui(void) {
    printf("=== UI-opening APIs (--ui; may show windows / UAC prompts)\n");
    printf("  open_path(C:\\Windows) = %d\n", jn_tm_open_path("C:\\Windows"));
    printf("  search_online = %d\n", jn_tm_search_online("jnative \xE4\xBB\xBB\xE5\x8A\xA1\xE7\xAE\xA1\xE7\x90\x86\xE5\x99\xA8"));
    printf("  open_resource_monitor = %d\n", jn_tm_open_resource_monitor());
    printf("  open_system_settings_apps = %d\n", jn_tm_open_system_settings_apps());
    printf("  open_services_msc = %d\n", jn_tm_open_services_msc());
    printf("  proc_properties(self) = %d\n", jn_tm_proc_properties((int32_t)GetCurrentProcessId()));
}
int main(int argc, char **argv) {
    int ops = 0, ui = 0, leak = 0;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--leak") == 0) {
            leak = 1;
        } else if (strcmp(argv[i], "--full") == 0) {
            g_full = 1;
        } else if (strcmp(argv[i], "--ops") == 0) {
            ops = 1;
        } else if (strcmp(argv[i], "--ui") == 0) {
            ui = 1;
        }
    }
    SetConsoleOutputCP(CP_UTF8);
    setvbuf(stdout, NULL, _IONBF, 0);
    QueryPerformanceFrequency(&g_freq);

    printf("jn_tm self test; elevated=%d\n", jn_tm_is_elevated());
    test_text_api("jn_tm_system_info", jn_tm_system_info, 5, 0);
    test_text_api("jn_tm_perf", jn_tm_perf, 0, 1);
    test_text_api("jn_tm_procx", jn_tm_procx, 12, 1);
    test_text_api("jn_tm_services", jn_tm_services, 12, 1);
    test_text_api("jn_tm_sessions", jn_tm_sessions, 0, 1);
    test_text_api("jn_tm_startup", jn_tm_startup, 0, 1);
    test_services_ops();
    test_startup_set();
    test_safe_ui();
    if (ops) {
        test_ops();
    }
    if (ui) {
        test_ui();
    }
    if (leak) {
        leak_check();
    }
    printf("done\n");
    return 0;
}
