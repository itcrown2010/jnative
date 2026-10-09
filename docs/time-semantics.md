# Time Semantics (Round 2)

作者：张晓达

## Clocks

| API | Clock type | Backend |
|---|---|---|
| `Instant.now()` / `Clock.systemUTC()` | **Wall** | `CLOCK_REALTIME` (`jnative_wall_millis`) |
| `Clock.monotonicMillis()` / `monotonicNanos()` | **Monotonic** | `CLOCK_MONOTONIC` |
| `Duration` measurement | Prefer monotonic | — |

Do **not** use wall clock for elapsed-time measurement across NTP steps.

## ZoneId

Named zones (`Asia/Shanghai`, `America/Los_Angeles`, `Europe/London`, …) resolve offsets via **platform IANA zoneinfo** (`TZ` + `localtime_r` + `tm_gmtoff`).

Fixed offsets (`+08:00`, `-08:00`, `Z`) are parsed without OS.

**Limitation:** `setenv("TZ")` is process-global (not thread-safe). Acceptable for Round 2 single-threaded calendar paths.

## DST policy (local → instant)

When converting ambiguous/invalid local times (Round 2 documents Instant→Local first; reverse conversion TBD):

| Case | Policy |
|---|---|
| Spring-forward **gap** | Prefer post-transition offset when resolving via Instant |
| Fall-back **overlap** | Prefer earlier offset (first occurrence) when resolving via Instant |

Verified: America/Los_Angeles 2024-03-10 offset −480 → −420 across transition.

## DateTimeFormatter

ISO basics only: `yyyy-MM-dd`, `HH:mm:ss`, `yyyy-MM-dd'T'HH:mm:ss`.
