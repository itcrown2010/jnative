/*
 * RuntimeCrashDiagnostics — minimal async-signal-safe crash path.
 */

#include "jnative_crash.h"

#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

#if defined(__APPLE__)
#include <mach-o/dyld.h>
#endif

static char g_build_id[96] = "unknown";
static volatile sig_atomic_t g_installed = 0;

/* Fixed ring for recent logs (NOT touched from signal handler). */
#define JN_CRASH_LOG_SLOTS 16
#define JN_CRASH_LOG_LEN 120
static char g_logs[JN_CRASH_LOG_SLOTS][JN_CRASH_LOG_LEN];
static int g_log_i = 0;

static void jn_crash_write(const char *s) {
    if (!s) return;
    size_t n = 0;
    while (s[n]) n++;
    (void)write(STDERR_FILENO, s, n);
}

static void jn_crash_write_int(int v) {
    char buf[16];
    int i = 0;
    int neg = 0;
    if (v < 0) { neg = 1; v = -v; }
    if (v == 0) { buf[i++] = '0'; }
    else {
        char tmp[16];
        int t = 0;
        while (v > 0 && t < 15) { tmp[t++] = (char)('0' + (v % 10)); v /= 10; }
        while (t > 0) buf[i++] = tmp[--t];
    }
    if (neg) {
        char out[17];
        out[0] = '-';
        for (int k = 0; k < i; k++) out[k + 1] = buf[k];
        out[i + 1] = 0;
        jn_crash_write(out);
    } else {
        buf[i] = 0;
        jn_crash_write(buf);
    }
}

static void jn_crash_handler(int sig) {
    jn_crash_write("\n=== JNative RuntimeCrashDiagnostics ===\n");
    jn_crash_write("signal=");
    jn_crash_write_int(sig);
    jn_crash_write("\nbuild_id=");
    jn_crash_write(g_build_id);
    jn_crash_write("\nnote=prefer OS crash report / core dump for native stack\n");
    jn_crash_write("hint=rebuild with -g and use lldb/gdb on the binary\n");
    jn_crash_write("========================================\n");
    /* Re-raise with default disposition for OS reporter / core. */
    signal(sig, SIG_DFL);
    raise(sig);
}

void jn_crash_set_build_id(const char *build_id) {
    if (!build_id || !build_id[0]) return;
    size_t n = strlen(build_id);
    if (n >= sizeof(g_build_id)) n = sizeof(g_build_id) - 1;
    memcpy(g_build_id, build_id, n);
    g_build_id[n] = 0;
}

void jn_crash_log(const char *msg) {
    if (!msg) return;
    int i = g_log_i % JN_CRASH_LOG_SLOTS;
    g_log_i++;
    strncpy(g_logs[i], msg, JN_CRASH_LOG_LEN - 1);
    g_logs[i][JN_CRASH_LOG_LEN - 1] = 0;
}

void jn_crash_install_handlers(void) {
    if (g_installed) return;
    g_installed = 1;

    const char *env = getenv("JNATIVE_BUILD_ID");
    if (env && env[0]) jn_crash_set_build_id(env);
    else {
#if defined(__APPLE__)
        char path[512];
        uint32_t sz = sizeof(path);
        if (_NSGetExecutablePath(path, &sz) == 0) {
            const char *base = strrchr(path, '/');
            jn_crash_set_build_id(base ? base + 1 : path);
        }
#endif
    }

    if (getenv("JNATIVE_NO_CRASH_HANDLER") != NULL) {
        return;
    }

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = jn_crash_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESETHAND;
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
    jn_crash_log("crash handlers installed");
}

const char *jn_crash_classify_status(int status) {
    if (WIFEXITED(status)) {
        int code = WEXITSTATUS(status);
        if (code == 0) return "NORMAL_EXIT";
        return "NONZERO_EXIT";
    }
    if (WIFSIGNALED(status)) {
        int sig = WTERMSIG(status);
        if (sig == SIGSEGV) return "SIGSEGV";
        if (sig == SIGABRT) return "SIGABRT";
        if (sig == SIGBUS) return "SIGBUS";
        if (sig == SIGILL) return "SIGILL";
        if (sig == SIGTERM) return "OS_TERMINATED";
        if (sig == SIGKILL) return "OS_KILLED";
        return "SIGNALED";
    }
    return "UNKNOWN_STATUS";
}