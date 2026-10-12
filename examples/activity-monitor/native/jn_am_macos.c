/* Author: 张晓达 */
#include "jn_am_macos.h"

#include <errno.h>
#include <libproc.h>
#include <mach/mach.h>
#include <mach/mach_host.h>
#include <pwd.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/sysctl.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

static int32_t g_last_errno = 0;

void jn_am_free(char *p) {
    free(p);
}

int32_t jn_am_last_errno(void) {
    return g_last_errno;
}

int32_t jn_am_self_pid(void) {
    return (int32_t)getpid();
}

int64_t jn_am_mono_ns(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC_RAW, &ts) != 0) {
        if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
            return 0;
        }
    }
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
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
    char tmp[1024];
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

int32_t jn_am_check_identity(int32_t pid, int64_t start_sec, int32_t start_usec) {
    g_last_errno = 0;
    if (pid <= 0) {
        return 0;
    }
    struct proc_bsdinfo bsd;
    memset(&bsd, 0, sizeof(bsd));
    int n = proc_pidinfo(pid, PROC_PIDTBSDINFO, 0, &bsd, (int)sizeof(bsd));
    if (n <= 0) {
        g_last_errno = errno;
        return 0;
    }
    if ((int64_t)bsd.pbi_start_tvsec == start_sec && (int32_t)bsd.pbi_start_tvusec == start_usec) {
        return 1;
    }
    return 0;
}

int32_t jn_am_signal(int32_t pid, int32_t sig) {
    g_last_errno = 0;
    if (pid <= 1 || sig <= 0) {
        g_last_errno = EINVAL;
        return EINVAL;
    }
    if (kill((pid_t)pid, sig) == 0) {
        return 0;
    }
    g_last_errno = errno;
    return (int32_t)errno;
}

int32_t jn_am_alive(int32_t pid) {
    g_last_errno = 0;
    if (pid <= 0) {
        return 0;
    }
    if (kill((pid_t)pid, 0) == 0) {
        return 1;
    }
    g_last_errno = errno;
    if (errno == ESRCH) {
        return 0;
    }
    /* EPERM means process exists but we cannot signal it */
    if (errno == EPERM) {
        return 1;
    }
    return -1;
}

char *jn_am_snapshot(void) {
    g_last_errno = 0;
    char *buf = NULL;
    size_t len = 0;
    size_t cap = 0;

    int64_t mono = jn_am_mono_ns();

    int ncpu = 1;
    size_t sz = sizeof(ncpu);
    if (sysctlbyname("hw.logicalcpu", &ncpu, &sz, NULL, 0) != 0) {
        sz = sizeof(ncpu);
        if (sysctlbyname("hw.ncpu", &ncpu, &sz, NULL, 0) != 0) {
            ncpu = 1;
        }
    }

    uint64_t mem_total = 0;
    sz = sizeof(mem_total);
    if (sysctlbyname("hw.memsize", &mem_total, &sz, NULL, 0) != 0) {
        mem_total = 0;
    }

    vm_size_t page_size = 0;
    host_page_size(mach_host_self(), &page_size);

    natural_t pages_free = 0;
    natural_t pages_active = 0;
    natural_t pages_inactive = 0;
    natural_t pages_wired = 0;
    natural_t pages_compressible = 0;
    int sys_err = 0;

    vm_statistics64_data_t vmstat;
    mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
    kern_return_t kr = host_statistics64(mach_host_self(), HOST_VM_INFO64,
            (host_info64_t)&vmstat, &count);
    if (kr == KERN_SUCCESS) {
        pages_free = vmstat.free_count;
        pages_active = vmstat.active_count;
        pages_inactive = vmstat.inactive_count;
        pages_wired = vmstat.wire_count;
        pages_compressible = (natural_t)vmstat.compressor_page_count;
    } else {
        sys_err = 1;
    }

    unsigned int cpu_user = 0, cpu_sys = 0, cpu_idle = 0, cpu_nice = 0;
    host_cpu_load_info_data_t cpuinfo;
    mach_msg_type_number_t cpu_count = HOST_CPU_LOAD_INFO_COUNT;
    kr = host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO,
            (host_info_t)&cpuinfo, &cpu_count);
    if (kr == KERN_SUCCESS) {
        cpu_user = cpuinfo.cpu_ticks[CPU_STATE_USER];
        cpu_sys = cpuinfo.cpu_ticks[CPU_STATE_SYSTEM];
        cpu_idle = cpuinfo.cpu_ticks[CPU_STATE_IDLE];
        cpu_nice = cpuinfo.cpu_ticks[CPU_STATE_NICE];
    } else {
        sys_err = 1;
    }

    if (append_fmt(&buf, &len, &cap,
            "S\t%lld\t%d\t%llu\t%lu\t%u\t%u\t%u\t%u\t%u\t%u\t%u\t%u\t%u\t%d\n",
            (long long)mono, ncpu, (unsigned long long)mem_total,
            (unsigned long)page_size,
            (unsigned)pages_free, (unsigned)pages_active, (unsigned)pages_inactive,
            (unsigned)pages_wired, (unsigned)pages_compressible,
            cpu_user, cpu_sys, cpu_idle, cpu_nice, sys_err) != 0) {
        free(buf);
        return NULL;
    }

    int bytes = proc_listpids(PROC_ALL_PIDS, 0, NULL, 0);
    if (bytes <= 0) {
        g_last_errno = errno;
        return buf;
    }
    int max_pids = bytes / (int)sizeof(pid_t);
    if (max_pids < 1) {
        return buf;
    }
    /* allocate a little headroom */
    max_pids += 32;
    pid_t *pids = (pid_t *)calloc((size_t)max_pids, sizeof(pid_t));
    if (pids == NULL) {
        free(buf);
        return NULL;
    }
    int got = proc_listpids(PROC_ALL_PIDS, 0, pids, max_pids * (int)sizeof(pid_t));
    if (got <= 0) {
        g_last_errno = errno;
        free(pids);
        return buf;
    }
    int np = got / (int)sizeof(pid_t);

    for (int i = 0; i < np; i++) {
        pid_t pid = pids[i];
        if (pid <= 0) {
            continue;
        }

        int flags = 0;
        int32_t ppid = -1;
        int32_t uid = -1;
        int64_t start_sec = -1;
        int32_t start_usec = -1;
        int64_t cpu_ns = -1;
        int64_t rss = -1;
        int64_t footprint = -1;
        int32_t nthreads = -1;
        char name[PROC_PIDPATHINFO_MAXSIZE];
        char user[64];
        char path[PROC_PIDPATHINFO_MAXSIZE];
        name[0] = '\0';
        user[0] = '\0';
        path[0] = '\0';

        struct proc_bsdinfo bsd;
        memset(&bsd, 0, sizeof(bsd));
        int bn = proc_pidinfo(pid, PROC_PIDTBSDINFO, 0, &bsd, (int)sizeof(bsd));
        if (bn > 0) {
            flags |= 64;
            ppid = (int32_t)bsd.pbi_ppid;
            uid = (int32_t)bsd.pbi_uid;
            start_sec = (int64_t)bsd.pbi_start_tvsec;
            start_usec = (int32_t)bsd.pbi_start_tvusec;
            if (bsd.pbi_name[0] != '\0') {
                strncpy(name, bsd.pbi_name, sizeof(name) - 1);
                name[sizeof(name) - 1] = '\0';
                flags |= 1;
            }
            struct passwd pwd;
            struct passwd *pw = NULL;
            char pwbuf[1024];
            if (getpwuid_r((uid_t)bsd.pbi_uid, &pwd, pwbuf, sizeof(pwbuf), &pw) == 0
                    && pw != NULL && pw->pw_name != NULL) {
                strncpy(user, pw->pw_name, sizeof(user) - 1);
                user[sizeof(user) - 1] = '\0';
                flags |= 2;
            }
        }

        if ((flags & 1) == 0) {
            char tmp[256];
            int nn = proc_name(pid, tmp, (uint32_t)sizeof(tmp));
            if (nn > 0 && tmp[0] != '\0') {
                strncpy(name, tmp, sizeof(name) - 1);
                name[sizeof(name) - 1] = '\0';
                flags |= 1;
            }
        }

        struct proc_taskinfo task;
        memset(&task, 0, sizeof(task));
        int tn = proc_pidinfo(pid, PROC_PIDTASKINFO, 0, &task, (int)sizeof(task));
        if (tn > 0) {
            /* pti_total_user / system are in nanoseconds on Darwin */
            cpu_ns = (int64_t)task.pti_total_user + (int64_t)task.pti_total_system;
            flags |= 4;
            rss = (int64_t)task.pti_resident_size;
            flags |= 8;
            nthreads = (int32_t)task.pti_threadnum;
            flags |= 32;
        }

        struct rusage_info_v2 ru;
        memset(&ru, 0, sizeof(ru));
        if (proc_pid_rusage(pid, RUSAGE_INFO_V2, (rusage_info_t *)&ru) == 0) {
            footprint = (int64_t)ru.ri_phys_footprint;
            flags |= 16;
        }

        /* Process may have exited between listpids and pidinfo — skip empty shells */
        if ((flags & 64) == 0 && (flags & 4) == 0 && (flags & 1) == 0) {
            continue;
        }

        int pn = proc_pidpath(pid, path, sizeof(path));
        if (pn > 0 && path[0] != '\0') {
            flags |= 128;
        } else {
            path[0] = '\0';
        }

        sanitize_field(name);
        sanitize_field(user);
        sanitize_field(path);
        if (name[0] == '\0') {
            snprintf(name, sizeof(name), "pid-%d", (int)pid);
        }
        if (user[0] == '\0') {
            snprintf(user, sizeof(user), "%d", (int)uid);
        }

        if (append_fmt(&buf, &len, &cap,
                "P\t%d\t%d\t%d\t%lld\t%d\t%lld\t%lld\t%lld\t%d\t%d\t%s\t%s\t%s\n",
                (int)pid, (int)ppid, (int)uid,
                (long long)start_sec, (int)start_usec,
                (long long)cpu_ns, (long long)rss, (long long)footprint,
                (int)nthreads, flags, user, name, path) != 0) {
            free(pids);
            free(buf);
            return NULL;
        }
    }

    free(pids);
    return buf;
}
