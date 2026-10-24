/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Windows implementation of the jnative_proc ABI (CreateProcess + anonymous pipes).
 * Same contract as native/posix/jnative_proc.c: no shell, stdout/stderr captured via CRT fds. */
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <fcntl.h>
#include <io.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jnative_proc.h"

#define JN_PROC_MAX 128

static HANDLE g_procs[JN_PROC_MAX];
static DWORD g_pids[JN_PROC_MAX];
static CRITICAL_SECTION g_lock;
static volatile LONG g_lock_ready = 0;

static void proc_lock_init(void) {
    if (InterlockedCompareExchange(&g_lock_ready, 1, 0) == 0) {
        InitializeCriticalSection(&g_lock);
        InterlockedExchange(&g_lock_ready, 2);
    } else {
        while (g_lock_ready != 2) {
            Sleep(0);
        }
    }
}

static wchar_t *utf8_to_wide_n(const char *s, size_t extra) {
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    if (n <= 0) {
        return NULL;
    }
    wchar_t *w = (wchar_t *)malloc(((size_t)n + extra) * sizeof(wchar_t));
    if (w == NULL) {
        return NULL;
    }
    MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n);
    return w;
}

/* Quote one argument per the MSVCRT command-line rules. */
static void append_quoted(wchar_t **buf, size_t *len, size_t *cap, const wchar_t *arg) {
    size_t need = wcslen(arg) * 2 + 4;
    if (*len + need + 2 > *cap) {
        *cap = (*len + need + 2) * 2;
        *buf = (wchar_t *)realloc(*buf, *cap * sizeof(wchar_t));
    }
    wchar_t *o = *buf + *len;
    int quote = (arg[0] == 0) || wcspbrk(arg, L" \t\n\v\"") != NULL;
    if (*len > 0) {
        *o++ = L' ';
    }
    if (!quote) {
        size_t n = wcslen(arg);
        memcpy(o, arg, n * sizeof(wchar_t));
        o += n;
    } else {
        *o++ = L'"';
        for (const wchar_t *p = arg;; p++) {
            size_t bs = 0;
            while (*p == L'\\') {
                p++;
                bs++;
            }
            if (*p == 0) {
                for (size_t i = 0; i < bs * 2; i++) {
                    *o++ = L'\\';
                }
                break;
            } else if (*p == L'"') {
                for (size_t i = 0; i < bs * 2 + 1; i++) {
                    *o++ = L'\\';
                }
                *o++ = L'"';
            } else {
                for (size_t i = 0; i < bs; i++) {
                    *o++ = L'\\';
                }
                *o++ = *p;
            }
        }
        *o++ = L'"';
    }
    *o = 0;
    *len = (size_t)(o - *buf);
}

char *jnative_proc_spawn_s(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd
) {
    if (file == NULL || argv_joined == NULL || argc <= 0) {
        return NULL;
    }
    proc_lock_init();

    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    HANDLE outR = NULL, outW = NULL, errR = NULL, errW = NULL;
    if (!CreatePipe(&outR, &outW, &sa, 0) || !CreatePipe(&errR, &errW, &sa, 0)) {
        return NULL;
    }
    SetHandleInformation(outR, HANDLE_FLAG_INHERIT, 0);
    SetHandleInformation(errR, HANDLE_FLAG_INHERIT, 0);

    /* Build the command line from '\n'-joined argv. */
    wchar_t *cmd = NULL;
    size_t clen = 0, ccap = 0;
    char *copy = _strdup(argv_joined);
    if (copy == NULL) {
        return NULL;
    }
    char *p = copy;
    for (int32_t i = 0; i < argc && p != NULL; i++) {
        char *nl = strchr(p, '\n');
        if (nl != NULL) {
            *nl = '\0';
        }
        wchar_t *w = utf8_to_wide_n(p, 0);
        if (w != NULL) {
            append_quoted(&cmd, &clen, &ccap, w);
            free(w);
        }
        p = (nl != NULL) ? nl + 1 : NULL;
    }
    free(copy);
    wchar_t *wfile = utf8_to_wide_n(file, 0);
    wchar_t *wcwd = (cwd != NULL && cwd[0] != '\0') ? utf8_to_wide_n(cwd, 0) : NULL;
    if (cmd == NULL || wfile == NULL) {
        free(cmd);
        free(wfile);
        free(wcwd);
        return NULL;
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    ZeroMemory(&pi, sizeof(pi));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = outW;
    si.hStdError = errW;
    BOOL ok = CreateProcessW(wfile, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, wcwd, &si, &pi);
    free(cmd);
    free(wfile);
    free(wcwd);
    CloseHandle(outW);
    CloseHandle(errW);
    if (!ok) {
        CloseHandle(outR);
        CloseHandle(errR);
        return NULL;
    }
    CloseHandle(pi.hThread);

    int slot = -1;
    EnterCriticalSection(&g_lock);
    for (int i = 0; i < JN_PROC_MAX; i++) {
        if (g_procs[i] == NULL) {
            g_procs[i] = pi.hProcess;
            g_pids[i] = pi.dwProcessId;
            slot = i;
            break;
        }
    }
    LeaveCriticalSection(&g_lock);
    if (slot < 0) {
        CloseHandle(pi.hProcess);
    }

    int ofd = _open_osfhandle((intptr_t)outR, _O_RDONLY | _O_BINARY);
    int efd = _open_osfhandle((intptr_t)errR, _O_RDONLY | _O_BINARY);
    char *desc = (char *)malloc(64);
    if (desc == NULL) {
        return NULL;
    }
    snprintf(desc, 64, "%d\n%d\n%d", (int)pi.dwProcessId, ofd, efd);
    return desc;
}

static HANDLE take_slot(int32_t pid, int release) {
    HANDLE h = NULL;
    proc_lock_init();
    EnterCriticalSection(&g_lock);
    for (int i = 0; i < JN_PROC_MAX; i++) {
        if (g_procs[i] != NULL && g_pids[i] == (DWORD)pid) {
            h = g_procs[i];
            if (release) {
                g_procs[i] = NULL;
            }
            break;
        }
    }
    LeaveCriticalSection(&g_lock);
    return h;
}

int32_t jnative_proc_wait(int32_t pid) {
    if (pid <= 0) {
        return -1;
    }
    HANDLE h = take_slot(pid, 0);
    if (h == NULL) {
        return -1;
    }
    WaitForSingleObject(h, INFINITE);
    DWORD code = 0;
    GetExitCodeProcess(h, &code);
    take_slot(pid, 1);
    CloseHandle(h);
    return (int32_t)code;
}

int32_t jnative_proc_kill(int32_t pid) {
    if (pid <= 0) {
        return 0;
    }
    HANDLE h = take_slot(pid, 0);
    if (h == NULL) {
        return 0;
    }
    return TerminateProcess(h, 1) ? 1 : 0;
}

char *jnative_proc_read_string(int32_t fd, int32_t max_bytes) {
    if (fd < 0 || max_bytes <= 0) {
        return NULL;
    }
    if (max_bytes > 8 * 1024 * 1024) {
        max_bytes = 8 * 1024 * 1024;
    }
    char *buf = (char *)malloc((size_t)max_bytes + 1);
    if (buf == NULL) {
        return NULL;
    }
    size_t total = 0;
    while ((int32_t)total < max_bytes) {
        int n = _read(fd, buf + total, (unsigned int)((size_t)max_bytes - total));
        if (n <= 0) {
            break;
        }
        total += (size_t)n;
    }
    buf[total] = '\0';
    return buf;
}

void jnative_proc_close_fd(int32_t fd) {
    if (fd >= 0) {
        _close(fd);
    }
}

void jnative_proc_free(char *p) {
    free(p);
}


/* Streaming helpers: simple fallbacks (blocking) until a Win32 overlapped implementation lands. */
char *jnative_proc_read_nb(int32_t fd, int32_t max_bytes, int32_t timeout_ms) {
    (void)timeout_ms;
    char *s = jnative_proc_read_string(fd, max_bytes);
    if (s != NULL && s[0] == '\0') {
        free(s);
        return NULL;
    }
    return s;
}

int32_t jnative_proc_try_wait(int32_t pid) {
    return jnative_proc_wait(pid);
}
