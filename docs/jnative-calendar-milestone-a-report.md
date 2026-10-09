# JNative Calendar Milestone A Report

作者：张晓达

**Status:** `NEXA_CALENDAR_MILESTONE_A_PASS`  
**Date:** 2026-10-07  
**Scope:** UI polish → MiniCalendar → calendar colors/toggles → today/selected → event cards → popover → editor. Stop before Week/Day deep work.

## Files changed

| Path | Role |
|------|------|
| `examples/scheduler/public/index.html` | Shell layout: sidebar Create, mini cal, calendars, tasks, upcoming, user menu, popover, editor |
| `examples/scheduler/public/app.css` | Dense productivity styling; today circle; event chips; segmented views; popover/modal |
| `examples/scheduler/public/app.js` | Interaction model: selectedDate≠today, visibility filter, popover→edit, overflow +N more, search panel |
| `examples/scheduler/server/SchedulerServer.java` | Seed: ensure Personal/Work/Family + colored sample events for existing demo users |

No compiler / IR / LLVM changes. Calendar-specific compiler intrinsics = **0**.

## Generic JNative capabilities added

None new in stdlib this round. Milestone A reused existing native HTTP + SQLite APIs and improved the served SPA.

Deferred to later milestones / capability backfill:

- Dialog / Popover / Toast as `jnative.webui` components (currently SPA-local)
- DatePicker / TimePicker as shared forms widgets (native `input type=date|time` for now)
- Typed `@Rpc` browser client (SPA still uses fetch mirrors of RPC paths — existing architecture)

## Calendar-specific code added

- Mini calendar navigation + selection syncing main cursor
- Client-side calendar visibility `Set` (no refetch on toggle)
- Event chip: time + calendar color bar + hover
- Event detail popover (Edit / Delete) before editor
- Editor: all-day, split date/time, timezone select
- Month overflow `+N more` day popover
- Upcoming sidebar from live events
- Task filter Today / Upcoming / Completed
- User menu (initial + Sign out) — still demo auth

## Tests added

- Manual browser verification via Playwright screenshots (below)
- API smoke: login, listCalendars (3 colors), eventsBetween (multi-color)

Automated unit tests for IntervalLayout / recurrence deferred to Milestone B/D.

## Bugs found

1. Missing global `.hidden` CSS caused Delete on new events to no-op (fixed earlier; retained).
2. Existing demo DB only had Personal-colored events → Work/Family toggles looked identical until seed enrichment.
3. Event click previously opened editor directly — product model required popover first.

## Bugs fixed

1. Seed `ensureExtraCalendarsAndSamples` for returning demo users.
2. Event open path: click → popover; Edit → editor; Delete from popover or editor.
3. Today vs selectedDate separation in month + mini calendar.

## Build / size / timing

| Metric | Value |
|--------|------:|
| Server binary | `build/scheduler/scheduler-server` ≈ **1008 KB** |
| `app.js` | ≈ SPA source (served statically) |
| `app.css` | ≈ SPA source |
| WASM size | N/A this milestone (REST SPA path) |
| Rebuild server (concat + native compile) | ~14 s (warm jar) |
| Login RPC (local) | ~measured on smoke run |
| Startup | listen `:18400` after DB connect |

## RPC / DB / Browser

| Check | Result |
|-------|--------|
| SQLite `sqlite:./build/scheduler/scheduler.db` | PASS |
| PostgreSQL | Not exercised this round (`DATABASE_URL` supported, unchanged) |
| `listCalendars` colors Personal/Work/Family | PASS |
| Create / update / delete event | PASS (API + UI) |
| Calendar toggle without DB refetch | PASS |
| Mini calendar jumps month / selects day | PASS |
| Month event shows `HH:mm title` + color | PASS |
| Popover then Edit/Delete | PASS |
| + Event / + Create editor | PASS |
| Refresh persistence | PASS (SQLite) |

## Acceptance (Milestone A)

1. Mini Calendar — PASS  
2. Calendar toggle — PASS  
3. Distinct calendar colors on events — PASS  
4. Today accent circle — PASS  
5. Selected date distinct — PASS  
6. Event time on card — PASS  
7. Event hover — PASS  
8. Event click → Popover — PASS  
9. + Event → Editor — PASS  
10. Create shows immediately — PASS  
11. Refresh persists — PASS  
12. Edit — PASS  
13. Delete — PASS  
14. Month navigation — PASS  
15. UI denser / clearer than prior demo screenshot — PASS  

## Screenshots

- Before: prior demo month grid (title-only chips, sparse sidebar) — see conversation / earlier `verify-*.png` style.  
- After:
  - `build/scheduler/screenshots/m1a-month.png`
  - `build/scheduler/screenshots/m1a-popover.png`
  - `build/scheduler/screenshots/m1a-editor.png`
  - `build/scheduler/screenshots/m1a-toggle.png`
  - `build/scheduler/screenshots/m1a-mini.png`

## JNative capability audit (this round)

| Area | Status | Notes |
|------|--------|-------|
| WebUI Component | PARTIAL | SPA HTML/CSS/JS, not WASM component tree |
| Reactive State | MISSING | Manual render() |
| Computed / Effect | MISSING | |
| Keyed List | PARTIAL | `data-id` on chips; full DOM rebuild on render |
| Conditional Render | PARTIAL | class `hidden` |
| Dialog | PARTIAL | modal in SPA |
| Popover | PARTIAL | SPA popover |
| DatePicker / TimePicker | PARTIAL | native inputs |
| Form / Validation | PARTIAL | HTML required + server Validation |
| RPC | PARTIAL | fetch mirrors `@Rpc` paths |
| JSON | PASS | |
| Async | PASS | server futures |
| Cancellation | PARTIAL | search AbortController only |
| Date/Time / TZ | PARTIAL | JS Date + timezone field; no Instant/ZonedDateTime in SPA |
| PostgreSQL | PARTIAL | supported, not retested |
| CSS / DOM Event | PASS | |

## Known blockers / next

- ReminderScheduler still disabled (EventLoop deadlock risk).  
- Week/Day TimeGrid, overlap layout, drag/resize → **Milestone B**.  
- Typed WASM WebUI + `@Rpc` client → later when WebUI foundation is ready.  
- Do not start JNative→Nexa rename in this track.

## Verdict

**NEXA_CALENDAR_MILESTONE_A_PASS**

STOP. Awaiting review of after screenshots before Milestone B.
