/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/*
 * ZoneId offset lookup via platform IANA zoneinfo (TZ + localtime_r + tm_gmtoff).
 * Returns offset minutes east of UTC at epoch_ms, or INT32_MIN on failure.
 *
 * Note: setenv("TZ") is process-global; Round 2 documents this as a known limitation
 * until Thread-local timezone APIs are available. Single-threaded tests are correct.
 */
#include "jnative_mt.h"

#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#if defined(_WIN32)
/* llvm-mingw UCRT does not provide setenv/unsetenv/timegm. */
static int jn_win_setenv(const char *name, const char *value, int overwrite) {
    (void)overwrite;
    return _putenv_s(name, value);
}

static int jn_win_unsetenv(const char *name) {
    char buf[288];
    size_t n = strlen(name);
    if (n + 2 > sizeof(buf)) {
        return -1;
    }
    memcpy(buf, name, n);
    buf[n] = '=';
    buf[n + 1] = '\0';
    return _putenv(buf);
}

#define setenv jn_win_setenv
#define unsetenv jn_win_unsetenv
#define localtime_r(tp, result) (localtime_s((result), (tp)) == 0 ? (result) : NULL)
#define gmtime_r(tp, result) (gmtime_s((result), (tp)) == 0 ? (result) : NULL)
#define timegm _mkgmtime
#endif

static int32_t jn_tz_offset_unlocked(const char *zone_id, int64_t epoch_ms) {
    char *old = getenv("TZ");
    char old_buf[256];
    int had_old = 0;
    if (old != NULL) {
        strncpy(old_buf, old, sizeof(old_buf) - 1);
        old_buf[sizeof(old_buf) - 1] = '\0';
        had_old = 1;
    }
    if (setenv("TZ", zone_id, 1) != 0) {
        return INT32_MIN;
    }
    tzset();

    time_t sec = (time_t)(epoch_ms / 1000);
    struct tm tm;
    memset(&tm, 0, sizeof(tm));
    if (localtime_r(&sec, &tm) == NULL) {
        if (had_old) {
            setenv("TZ", old_buf, 1);
        } else {
            unsetenv("TZ");
        }
        tzset();
        return INT32_MIN;
    }

#if defined(__APPLE__) || defined(__linux__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
    long gmtoff = tm.tm_gmtoff;
#else
    long gmtoff = 0;
    {
        /* Fallback: compare to gmtime */
        struct tm utc;
        memset(&utc, 0, sizeof(utc));
        gmtime_r(&sec, &utc);
        time_t local_guess = timegm(&tm);
        gmtoff = (long)(local_guess - sec);
    }
#endif

    if (had_old) {
        setenv("TZ", old_buf, 1);
    } else {
        unsetenv("TZ");
    }
    tzset();
    return (int32_t)(gmtoff / 60);
}

int32_t jnative_tz_offset_minutes_at(const char *zone_id, int64_t epoch_ms) {
    if (zone_id == NULL || zone_id[0] == '\0') {
        return INT32_MIN;
    }
    if (strcmp(zone_id, "UTC") == 0 || strcmp(zone_id, "Z") == 0) {
        return 0;
    }
    return jn_tz_offset_unlocked(zone_id, epoch_ms);
}

int32_t jnative_tz_exists(const char *zone_id) {
    if (zone_id == NULL || zone_id[0] == '\0') {
        return 0;
    }
    if (strcmp(zone_id, "UTC") == 0 || strcmp(zone_id, "Z") == 0) {
        return 1;
    }
    /* Probe with a mid-range instant; failure → zone unknown / unsupported. */
    int32_t off = jn_tz_offset_unlocked(zone_id, 1704067200000LL);
    return off == INT32_MIN ? 0 : 1;
}
