/* Author: 张晓达
 * JNative Windows Task Monitor - native data layer (C ABI).
 *
 * Complements jn_am (process snapshot / terminate / efficiency mode / run task),
 * which is NOT duplicated here.
 *
 * ---------------------------------------------------------------------------
 * Final static link dependencies (everything else is LoadLibrary/GetProcAddress
 * at runtime: pdh, wtsapi32, ntdll Nt*, powrprof, dxgi, dbghelp, user32, ...):
 *
 *     -l advapi32  -l iphlpapi  -l shell32  -l version
 *
 * (kernel32 / UCRT are implicit.  `version` is already used by jn_am.)
 * Build: the code compiles on aarch64 and x86_64 (no inline asm / intrinsics).
 * ---------------------------------------------------------------------------
 *
 * General conventions
 *   - Text APIs return malloc'd UTF-8 (release with jn_tm_free). Never NULL on
 *     success; an empty string means "no rows". NULL only on out-of-memory.
 *   - One record per line ('\n'), fields separated by '\t'. A tab / CR / LF inside
 *     a text field is replaced by a single space.
 *   - Unavailable numeric field  -> -1.          Unavailable string field -> "".
 *   - Rate / percentage fields that need two samples are -1 on the first call
 *     (each text API keeps its own previous sample internally).
 *   - Units:  *_bytes / mem_* / vram_* / *_pool / cached / compressed : bytes
 *             read_bps / write_bps / send_bps / recv_bps                : BYTES per second
 *             link_bps                                                  : BITS per second
 *             *_pct_x10 : percent x 10 (0..1000)      *_ms_x10 : milliseconds x 10
 *             *_mhz     : MHz                         *_kb     : KiB
 *   - Threading: assume a single caller thread (UI thread). Not thread-safe.
 *     COM is not required; no CoInitialize is performed anywhere.
 *   - Int32 function results: 0 = success, otherwise a Win32 error code
 *     (ERROR_ACCESS_DENIED = 5, ERROR_CANCELLED = 1223, ...). -1 = not supported
 *     / unknown. No message boxes are ever shown by the API itself.
 *
 * =============================== jn_tm_perf ================================
 * Record types (field order is fixed):
 *
 *  H  (1 line)
 *     H mono_ns ncpu uptime_sec proc_count thread_count handle_count
 *       mem_total mem_avail mem_in_use commit_used commit_limit cached
 *       paged_pool nonpaged_pool compressed mem_speed_mhz mem_slots_used
 *       mem_slots_total mem_form_factor
 *     mem_in_use = mem_total - mem_avail.  cached = standby + modified lists
 *     (PDH \Memory\Standby Cache * + Modified Page List Bytes; falls back to
 *     system cache size).  compressed = working set of "Memory Compression".
 *     mem_speed_mhz / slots / form factor come from SMBIOS type 17 (configured
 *     speed preferred); form factor is text ("DIMM", "SODIMM", "Row of chips"...).
 *
 *  C  (one line per logical CPU, index order)
 *     C index busy_pct_x10 cur_mhz
 *     busy uses PDH "% Processor Utility" (same metric as Windows 11 Task
 *     Manager, clamped to 100%) when available, otherwise idle-time deltas from
 *     NtQuerySystemInformation(SystemProcessorPerformanceInformation).
 *
 *  T  (1 line)  whole-machine summary
 *     T total_busy_pct_x10 kernel_pct_x10 cur_mhz base_mhz
 *     kernel_pct_x10 = kernel (non-idle) time share, never above total_busy.
 *     cur_mhz = average of CallNtPowerInformation CurrentMhz over all CPUs.
 *
 *  I  (1 line)  static CPU info
 *     I cpu_name base_mhz sockets cores logical l1_kb l2_kb l3_kb
 *       virtualization arch
 *     virtualization: 1 enabled in firmware, 0 not enabled, -1 unknown.
 *     arch: "x64" / "ARM64" / "x86" (native machine architecture).
 *
 *  D  (one line per physical disk that could be opened)
 *     D id label model media size_bytes active_pct_x10 read_bps write_bps
 *       avg_resp_ms_x10 drive_letters
 *     id = disk number N of \\.\PhysicalDriveN.  label is UTF-8 "磁盘 N".
 *     media = "SSD" | "HDD" | "" (seek-penalty property).  drive_letters like
 *     "C: D:" (space separated, empty if none).  Rates: IOCTL_DISK_PERFORMANCE
 *     deltas.  avg_resp_ms_x10 = 0 when there was no I/O during the interval.
 *     Disks that cannot be opened are skipped.
 *
 *  N  (one line per connected network adapter)
 *     N id name desc kind link_bps send_bps recv_bps ipv4 ipv6 up
 *     id = adapter GUID name, name = FriendlyName (localized, e.g. "以太网"),
 *     kind = "eth" | "wifi" | "other"; loopback / tunnel / not-up are filtered.
 *     ipv6 prefers a non-link-local address. up is always 1.
 *
 *  G  (one line per hardware GPU adapter; zero lines when none)
 *     G id name util_pct_x10 vram_used vram_total shared_used shared_total
 *       temp_c driver_version
 *     id = "luid_0xHHHHHHHH_0xLLLLLLLL".  util = max over engine types of the
 *     summed per-process "GPU Engine" utilization (clamped to 100%).
 *     temp_c is always -1 (no unprivileged vendor-neutral API).
 *
 * ============================== jn_tm_procx ================================
 *  X  (one line per process, including Idle/System)
 *     X pid session_id arch description cmdline io_read_bytes io_write_bytes
 *       io_other_bytes handles private_bytes peak_ws page_faults priority_class
 *       gdi_objects user_objects elevated virtualization suspended
 *       working_set cpu_time_100ns create_time_filetime
 *     arch: "x64"|"x86"|"ARM64"|"ARM"| "" (unknown / not openable).
 *     description: FileDescription of the image (cached by path; at most a
 *       bounded amount of version-info loading happens per call, so on the very
 *       first call a few rows may still be empty and get filled in later calls).
 *     cmdline: max 1024 characters, "" when it cannot be read.
 *     priority_class: GetPriorityClass() raw value (0x20 normal, 0x40 idle,
 *       0x4000 below normal, 0x8000 above normal, 0x80 high, 0x100 realtime);
 *       derived from the kernel base priority when the process cannot be opened.
 *     elevated / virtualization: token flags 1/0, -1 when token not readable.
 *     suspended: 1 when every thread is in Wait/Suspended, else 0.
 *     gdi_objects / user_objects: -1 when the process cannot be opened.
 *
 * ============================== jn_tm_services =============================
 *  V  name display pid state start_type description group image_path account
 *     pid 0 = not running.  state: running|stopped|paused|starting|stopping|pending
 *     start_type: auto|auto_delayed|manual|disabled|boot|system ("" if config
 *     could not be read).  group = svchost "-k" group, else load-order group.
 *     Static fields are cached (refreshed lazily in the background, staggered).
 *
 * ============================== jn_tm_sessions =============================
 *  U  session_id user domain state client station logon_time_unix
 *     state: active|disconnected|idle|other.  logon_time_unix -1 if unknown.
 *     Session 0 and sessions without a user name are omitted.
 *
 * ============================== jn_tm_startup ==============================
 *  T  id name publisher command source enabled impact path
 *     id = "source|name".  source tokens:
 *       HKCU_Run | HKLM_Run | HKLM_Run32 | StartupFolder | CommonStartupFolder
 *     name = registry value name, or file name without extension (folders).
 *     enabled: 1/0 from Explorer\StartupApproved (missing => 1).
 *     impact: always -1 (not measured).
 *     path: resolved image path ("" if it cannot be resolved); for .lnk files the
 *       link target is read from the LinkInfo block when present, else the .lnk.
 *     publisher: CompanyName of the resolved image ("" if unavailable).
 *
 * ============================== jn_tm_system_info ==========================
 *  M  host_name os_caption os_build user_name is_admin
 *     os_caption = ProductName (+ " " + DisplayVersion), "Windows 10" corrected to
 *     "Windows 11" when build >= 22000.  os_build = "<build>.<UBR>" (or "<build>").
 *     is_admin = current token is a member of Administrators (elevated admin).
 */
#ifndef JN_TM_H
#define JN_TM_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void jn_tm_free(char *p);

/* ---- performance page ---- */
char *jn_tm_perf(void);

/* ---- details-page extras ---- */
char *jn_tm_procx(void);

/* ---- services ---- */
char *jn_tm_services(void);
/* op: 1 start, 2 stop, 3 restart (stop, wait up to 5 s, start). 0 or Win32 error. */
int32_t jn_tm_service_control(const char *name, int32_t op);
/* Current PID of the service (0 = not running, -1 = error). */
int32_t jn_tm_service_open_pid(const char *name);
int32_t jn_tm_open_services_msc(void);

/* ---- users / sessions ---- */
char *jn_tm_sessions(void);
/* op: 1 disconnect, 2 logoff, 3 send message (unsupported -> -1). 0 or Win32 error. */
int32_t jn_tm_session_action(int32_t session_id, int32_t op);

/* ---- startup apps ---- */
char *jn_tm_startup(void);
/* Enable/disable via StartupApproved. 0 or Win32 error (5 when HKLM is not writable). */
int32_t jn_tm_startup_set(const char *id, int32_t enable);

/* ---- process operations (terminate / efficiency mode live in jn_am) ---- */
int32_t jn_tm_proc_kill_tree(int32_t pid);
/* cls: 0 realtime, 1 high, 2 above normal, 3 normal, 4 below normal, 5 low (idle) */
int32_t jn_tm_proc_set_priority(int32_t pid, int32_t cls);
int32_t jn_tm_proc_suspend(int32_t pid, int32_t on);
int32_t jn_tm_proc_set_affinity(int32_t pid, int64_t mask);
int64_t jn_tm_proc_get_affinity(int32_t pid); /* -1 on failure */
int32_t jn_tm_proc_create_dump(int32_t pid, const char *out_path_utf8);
int32_t jn_tm_proc_properties(int32_t pid);
/* Number of services hosted by the process (0 = none, -1 = enumeration error). */
int32_t jn_tm_proc_goto_services(int32_t pid);
int32_t jn_tm_search_online(const char *query_utf8);
int32_t jn_tm_open_path(const char *path_utf8);
int32_t jn_tm_copy_text(const char *utf8);
int32_t jn_tm_open_resource_monitor(void);
int32_t jn_tm_open_system_settings_apps(void);

/* ---- misc ---- */
char *jn_tm_system_info(void);
int32_t jn_tm_is_elevated(void);
int32_t jn_tm_relaunch_elevated(void); /* 0 when the elevated instance was started (caller then exits) */

#ifdef __cplusplus
}
#endif

#endif
