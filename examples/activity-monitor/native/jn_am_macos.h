/* Author: 张晓达
 * macOS process / host sampling ABI for Activity Monitor example.
 * Uses libproc + Mach host statistics — not ps/top text parsing.
 */
#ifndef JN_AM_MACOS_H
#define JN_AM_MACOS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Free a string returned by this library. */
void jn_am_free(char *p);

/**
 * Capture one host+process snapshot.
 * Returns malloc'd UTF-8 text (caller frees with jn_am_free), or NULL on hard failure.
 *
 * Line 0 (system):
 *   S\tmono_ns\tncpu\tmem_total\tpage_size\t
 *     pages_free\tpages_active\tpages_inactive\tpages_wired\tpages_compressible\t
 *     cpu_ticks_user\tcpu_ticks_system\tcpu_ticks_idle\tcpu_ticks_nice\terr
 *
 * Process lines:
 *   P\tpid\tppid\tuid\tstart_sec\tstart_usec\tcpu_ns\trss\tfootprint\tnthreads\tflags\tuser\tname\tpath
 *
 * flags bits:
 *   1 name  2 user  4 cpu_ns  8 rss  16 footprint  32 nthreads  64 bsd_ok  128 path
 * Tab/newline in name/user/path are replaced with space. Missing fields use -1 and clear flag bits.
 */
char *jn_am_snapshot(void);

/** Monotonic nanoseconds (CLOCK_MONOTONIC_RAW). */
int64_t jn_am_mono_ns(void);

/** This process PID. */
int32_t jn_am_self_pid(void);

/**
 * Verify pid still maps to the same start time (PID-reuse guard).
 * Returns 1 match, 0 mismatch/gone, -1 API error.
 */
int32_t jn_am_check_identity(int32_t pid, int64_t start_sec, int32_t start_usec);

/**
 * Send signal (SIGTERM / SIGKILL). Does not shell out.
 * Returns 0 on success, else positive errno (EPERM, ESRCH, …).
 */
int32_t jn_am_signal(int32_t pid, int32_t sig);

/** kill(pid, 0): 1 alive, 0 gone, -1 error (errno in jn_am_last_errno). */
int32_t jn_am_alive(int32_t pid);

int32_t jn_am_last_errno(void);

#ifdef __cplusplus
}
#endif

#endif
