/*
 * JNative 0.8 — RuntimeCrashDiagnostics (POSIX)
 * Signal handlers stay async-signal-safe: write(2) + _exit only.
 * Prefer OS crash reporters (macOS Crash Reporter / core dumps) for full dumps.
 */
#ifndef JNATIVE_CRASH_H
#define JNATIVE_CRASH_H

#ifdef __cplusplus
extern "C" {
#endif

/** Install SIGSEGV/SIGABRT/SIGBUS/SIGILL handlers (once). Safe to call repeatedly. */
void jn_crash_install_handlers(void);

/** Best-effort build id stamped at link time / env override. */
void jn_crash_set_build_id(const char *build_id);

/** Append a short runtime log line to a fixed ring (not async-signal-safe). */
void jn_crash_log(const char *msg);

/** Classify wait/exit status for parent processes / test harnesses. */
const char *jn_crash_classify_status(int status);

#ifdef __cplusplus
}
#endif

#endif /* JNATIVE_CRASH_H */