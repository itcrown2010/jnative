// Author: 张晓达
(() => {
  const state = {
    user: null,
    csrf: "",
    view: "MONTH",
    cursor: startOfDay(new Date()),
    selectedDate: startOfDay(new Date()),
    miniCursor: startOfMonth(new Date()),
    calendars: [],
    visibleCalIds: new Set(),
    _seenCalIds: new Set(),
    _visInitialized: false,
    events: [],
    tasks: [],
    taskFilter: "today",
    editingId: 0,
    streamSeq: 0,
    searchTimer: 0,
    searchAbort: null,
    loading: false,
  };

  const $ = (id) => document.getElementById(id);

  const asArray = (v) => (Array.isArray(v) ? v : []);

  const api = async (method, path, body, signal) => {
    const headers = { "Content-Type": "application/json" };
    if (state.csrf) headers["X-CSRF-Token"] = state.csrf;
    const t0 = performance.now();
    const res = await fetch(path, {
      method,
      headers,
      credentials: "same-origin",
      body: body ? JSON.stringify(body) : undefined,
      signal,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = { message: text }; }
    const ms = Math.round(performance.now() - t0);
    if (!res.ok) {
      const msg = (data && data.message) || res.statusText || "Request failed";
      const rid = data && data.requestId;
      console.error(`[api] ${method} ${path} → ${res.status} ${ms}ms`, data);
      const err = new Error(rid ? `${msg} [${rid}]` : msg);
      err.code = data && data.code;
      err.requestId = rid;
      err.status = res.status;
      err.path = path;
      throw err;
    }
    if (path.indexOf("/api/stream") < 0) {
      console.debug(`[api] ${method} ${path} → ${res.status} ${ms}ms`);
    }
    return data;
  };

  function startOfDay(d) {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }
  function startOfMonth(d) {
    return new Date(d.getFullYear(), d.getMonth(), 1);
  }
  function addDays(d, n) {
    const x = new Date(d.getTime());
    x.setDate(x.getDate() + n);
    return x;
  }
  function startOfWeek(d) {
    const x = startOfDay(d);
    const day = x.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    return addDays(x, diff);
  }
  function sameDay(a, b) {
    return a.getFullYear() === b.getFullYear()
      && a.getMonth() === b.getMonth()
      && a.getDate() === b.getDate();
  }
  function dayKey(d) {
    return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
  }
  function fmtTitle() {
    return state.cursor.toLocaleString(undefined, { month: "long", year: "numeric" });
  }
  function pad(n) { return String(n).padStart(2, "0"); }
  function toDateInput(ms) {
    const d = new Date(ms);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
  function toTimeInput(ms) {
    const d = new Date(ms);
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  function combineLocal(dateStr, timeStr, allDay) {
    if (allDay) return new Date(`${dateStr}T00:00:00`).getTime();
    return new Date(`${dateStr}T${timeStr || "00:00"}:00`).getTime();
  }
  function fmtTime(ms) {
    const d = new Date(ms);
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  function fmtEventLabel(e) {
    if (e.allDay) return e.title;
    return `${fmtTime(e.startTime)} ${e.title}`;
  }
  function escapeHtml(s) {
    return String(s || "").replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
  }
  function hexAlpha(hex, a) {
    const h = (hex || "#3B82F6").replace("#", "");
    const full = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
    const n = parseInt(full, 16);
    const r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    return `rgba(${r},${g},${b},${a})`;
  }
  function calById(id) {
    return state.calendars.find((c) => Number(c.id) === Number(id));
  }
  function eventColor(e) {
    return e.color || (calById(e.calendarId) && calById(e.calendarId).color) || "#3B82F6";
  }

  function rangeForView() {
    if (state.view === "DAY") {
      const a = startOfDay(state.cursor).getTime();
      return { from: a, to: a + 86400000 - 1 };
    }
    if (state.view === "WEEK") {
      const a = startOfWeek(state.cursor).getTime();
      return { from: a, to: a + 7 * 86400000 - 1 };
    }
    const first = startOfMonth(state.cursor);
    const gridStart = startOfWeek(first).getTime();
    return { from: gridStart, to: gridStart + 42 * 86400000 - 1 };
  }

  function visibleEvents() {
    return asArray(state.events).filter((e) => state.visibleCalIds.has(Number(e.calendarId)));
  }

  function sortDayEvents(list) {
    return list.slice().sort((a, b) => {
      const aAll = a.allDay ? 1 : 0;
      const bAll = b.allDay ? 1 : 0;
      if (bAll !== aAll) return bAll - aAll;
      if (a.startTime !== b.startTime) return a.startTime - b.startTime;
      return String(a.title).localeCompare(String(b.title));
    });
  }

  function eventsOnDay(dayStart) {
    const dayEnd = dayStart + 86400000;
    return sortDayEvents(visibleEvents().filter((e) => e.startTime < dayEnd && e.endTime > dayStart));
  }

  async function refreshAll(opts) {
    state.loading = true;
    $("status").textContent = "";
    $("status").classList.add("loading");
    $("calendarView").classList.add("is-loading");
    try {
      const range = rangeForView();
      const [cals, events, tasks] = await Promise.all([
        api("GET", "/api/calendar/listCalendars"),
        api("GET", `/api/calendar/eventsBetween?from=${range.from}&to=${range.to}`),
        api("GET", "/api/tasks/listTasks"),
      ]);
      state.calendars = asArray(cals);
      const known = new Set(state.calendars.map((c) => Number(c.id)));
      state.visibleCalIds.forEach((id) => { if (!known.has(id)) state.visibleCalIds.delete(id); });
      if (!state._visInitialized) {
        state.calendars.forEach((c) => state.visibleCalIds.add(Number(c.id)));
        state._visInitialized = true;
      } else {
        state.calendars.forEach((c) => {
          const id = Number(c.id);
          if (!state._seenCalIds.has(id)) state.visibleCalIds.add(id);
        });
      }
      state._seenCalIds = known;
      state.events = asArray(events);
      state.tasks = asArray(tasks);
      render();
      $("status").textContent = `${visibleEvents().length} events · ${state.tasks.length} tasks`;
    } catch (err) {
      console.error("[refreshAll]", err);
      $("status").textContent = err.message || "Load failed";
      $("calendarView").innerHTML = `<div class="empty-error">
        <p>Unable to load events</p>
        <p class="error">${escapeHtml(err.message || "Load failed")}</p>
        <button type="button" class="primary" id="btnRetry">Retry</button>
      </div>`;
      const retry = $("btnRetry");
      if (retry) retry.addEventListener("click", () => refreshAll());
      /* Background stream refresh failures should not flash a toast. */
      if (!opts || !opts.silent) {
        toast(err.message || "Load failed", { error: true });
      }
    } finally {
      state.loading = false;
      $("status").classList.remove("loading");
      $("calendarView").classList.remove("is-loading");
    }
  }

  function renderUser() {
    const name = state.user ? (state.user.displayName || state.user.email || "User") : "Guest";
    $("userName").textContent = name;
    $("userAvatar").textContent = (name.trim()[0] || "?").toUpperCase();
    $("userMeta").textContent = state.user
      ? `${state.user.email || ""}\n${state.user.timezone || ""}`.trim()
      : "";
  }

  function renderCalList() {
    $("calList").innerHTML = state.calendars.map((c) => {
      const on = state.visibleCalIds.has(Number(c.id));
      return `<button type="button" class="cal-item ${on ? "" : "off"}" data-cal="${c.id}" aria-pressed="${on}">
        <span class="cal-check ${on ? "on" : ""}" style="background:${on ? c.color : "transparent"};border-color:${c.color}"></span>
        <span class="cal-dot" style="background:${c.color}"></span>
        <span>${escapeHtml(c.name)}</span>
      </button>`;
    }).join("") || `<div class="task-empty">No calendars</div>`;

    $("eventCal").innerHTML = state.calendars.map((c) =>
      `<option value="${c.id}">${escapeHtml(c.name)}</option>`
    ).join("");

    $("calList").querySelectorAll("[data-cal]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.getAttribute("data-cal"));
        if (state.visibleCalIds.has(id)) state.visibleCalIds.delete(id);
        else state.visibleCalIds.add(id);
        render();
      });
    });
  }

  function filteredTasks() {
    const now = Date.now();
    const today0 = startOfDay(new Date()).getTime();
    const today1 = today0 + 86400000;
    return asArray(state.tasks).filter((t) => {
      const done = t.status === "DONE";
      if (state.taskFilter === "completed") return done;
      if (done) return false;
      if (state.taskFilter === "today") {
        return t.dueAt > 0 && t.dueAt >= today0 && t.dueAt < today1;
      }
      /* upcoming */
      return !t.dueAt || t.dueAt >= today0;
    });
  }

  function renderTasks() {
    document.querySelectorAll(".task-filter").forEach((b) => {
      b.classList.toggle("active", b.getAttribute("data-task-filter") === state.taskFilter);
    });
    const list = filteredTasks();
    if (!list.length) {
      $("taskList").innerHTML = `<div class="task-empty">No tasks</div>`;
      return;
    }
    $("taskList").innerHTML = list.map((t) =>
      `<label class="task-item ${t.status === "DONE" ? "done" : ""}">
        <input type="checkbox" data-task="${t.id}" ${t.status === "DONE" ? "checked" : ""}>
        <span>${escapeHtml(t.title)}</span>
      </label>`
    ).join("");
    $("taskList").querySelectorAll("input[data-task]").forEach((el) => {
      el.addEventListener("change", async () => {
        const id = Number(el.getAttribute("data-task"));
        const t = state.tasks.find((x) => Number(x.id) === id);
        if (!t) return;
        try {
          await api("POST", "/api/tasks/updateTask", {
            id,
            title: t.title,
            description: t.description || "",
            dueAt: t.dueAt || 0,
            priority: t.priority || "NORMAL",
            status: el.checked ? "DONE" : "OPEN",
          });
          toast(el.checked ? "Task completed" : "Task restored");
          await refreshAll();
        } catch (err) {
          el.checked = !el.checked;
          toast(err.message || "Task update failed");
        }
      });
    });
  }

  function renderUpcoming() {
    const now = Date.now();
    const upcoming = sortDayEvents(visibleEvents().filter((e) => e.endTime >= now))
      .slice(0, 5);
    if (!upcoming.length) {
      $("upcomingList").innerHTML = `<div class="task-empty">Nothing upcoming</div>`;
      return;
    }
    $("upcomingList").innerHTML = upcoming.map((e) => {
      const color = eventColor(e);
      const when = new Date(e.startTime).toLocaleString(undefined, {
        weekday: "short", month: "short", day: "numeric", hour: e.allDay ? undefined : "2-digit", minute: e.allDay ? undefined : "2-digit",
      });
      return `<button type="button" class="up-item" data-id="${e.id}">
        <div class="when">${escapeHtml(when)}</div>
        <div class="ttl"><span class="cal-dot" style="background:${color}"></span>${escapeHtml(e.title)}</div>
      </button>`;
    }).join("");
    $("upcomingList").querySelectorAll("[data-id]").forEach((btn) => {
      btn.addEventListener("click", () => openPopover(Number(btn.getAttribute("data-id")), btn));
    });
  }

  function renderMiniCal() {
    const first = startOfMonth(state.miniCursor);
    let cur = startOfWeek(first);
    const title = state.miniCursor.toLocaleString(undefined, { month: "short", year: "numeric" });
    const dow = ["M", "T", "W", "T", "F", "S", "S"];
    let html = `<div class="mini-head">
      <button type="button" data-mini="prev" aria-label="Previous month">‹</button>
      <div class="mini-title">${escapeHtml(title)}</div>
      <button type="button" data-mini="next" aria-label="Next month">›</button>
    </div><div class="mini-grid">`;
    html += dow.map((d) => `<div class="mini-dow">${d}</div>`).join("");
    const today = startOfDay(new Date());
    for (let i = 0; i < 42; i++) {
      const out = cur.getMonth() !== first.getMonth();
      const sel = sameDay(cur, state.selectedDate);
      const isToday = sameDay(cur, today);
      html += `<button type="button" class="mini-day ${out ? "out" : ""} ${isToday ? "today" : ""} ${sel ? "selected" : ""}"
        data-day="${cur.getTime()}" aria-label="${cur.toDateString()}">${cur.getDate()}</button>`;
      cur = addDays(cur, 1);
    }
    html += "</div>";
    $("miniCal").innerHTML = html;
    $("miniCal").querySelector("[data-mini=prev]").addEventListener("click", () => {
      state.miniCursor = new Date(state.miniCursor.getFullYear(), state.miniCursor.getMonth() - 1, 1);
      renderMiniCal();
    });
    $("miniCal").querySelector("[data-mini=next]").addEventListener("click", () => {
      state.miniCursor = new Date(state.miniCursor.getFullYear(), state.miniCursor.getMonth() + 1, 1);
      renderMiniCal();
    });
    $("miniCal").querySelectorAll("[data-day]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const d = startOfDay(new Date(Number(btn.getAttribute("data-day"))));
        state.selectedDate = d;
        state.cursor = d;
        state.miniCursor = startOfMonth(d);
        if (state.view === "DAY") {
          /* stay in day */
        } else if (state.view === "WEEK") {
          /* keep week, cursor moves */
        }
        await refreshAll();
      });
    });
  }

  function eventChipHtml(e) {
    const color = eventColor(e);
    const label = fmtEventLabel(e);
    const a11y = e.allDay
      ? `${e.title}, all day`
      : `${fmtTime(e.startTime)} to ${fmtTime(e.endTime)}, ${e.title}`;
    return `<button type="button" class="ev" data-id="${e.id}"
      style="background:${hexAlpha(color, 0.14)}"
      aria-label="${escapeHtml(a11y)}">
      <span class="ev-bar" style="background:${color}"></span>
      ${e.allDay ? "" : `<span class="ev-time">${fmtTime(e.startTime)}</span>`}
      <span class="ev-title">${escapeHtml(e.title)}</span>
    </button>`;
  }

  function visibleSlotsForCell() {
    /* ~104px cell - 28 header - padding ≈ 4 lines of ~18px */
    return 3;
  }

  function renderMonth() {
    const first = startOfMonth(state.cursor);
    let cur = startOfWeek(first);
    const dow = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    const today = startOfDay(new Date());
    const maxVisible = visibleSlotsForCell();
    let html = `<div class="month-grid">${dow.map((d) => `<div class="dow">${d}</div>`).join("")}`;
    for (let i = 0; i < 42; i++) {
      const start = startOfDay(cur).getTime();
      const inMonth = cur.getMonth() === state.cursor.getMonth();
      const isToday = sameDay(cur, today);
      const isSelected = sameDay(cur, state.selectedDate);
      const weekend = cur.getDay() === 0 || cur.getDay() === 6;
      const list = eventsOnDay(start);
      const show = list.slice(0, maxVisible);
      const more = list.length - show.length;
      html += `<div class="cell ${inMonth ? "" : "out-month"} ${isToday ? "today" : ""} ${isSelected ? "selected" : ""} ${weekend ? "weekend" : ""}"
        data-day="${start}" role="gridcell" aria-label="${cur.toDateString()}">
        <div class="cell-head"><span class="num">${cur.getDate()}</span></div>
        ${show.map(eventChipHtml).join("")}
        ${more > 0 ? `<button type="button" class="more" data-more="${start}">+${more} more</button>` : ""}
      </div>`;
      cur = addDays(cur, 1);
    }
    html += "</div>";
    $("calendarView").innerHTML = html;

    $("calendarView").querySelectorAll(".ev").forEach((btn) => {
      btn.addEventListener("click", (ev) => {
        ev.stopPropagation();
        openPopover(Number(btn.getAttribute("data-id")), btn);
      });
    });
    $("calendarView").querySelectorAll(".more").forEach((btn) => {
      btn.addEventListener("click", (ev) => {
        ev.stopPropagation();
        openDayPopover(Number(btn.getAttribute("data-more")), btn);
      });
    });
    $("calendarView").querySelectorAll(".cell").forEach((cell) => {
      cell.addEventListener("click", () => {
        const d = startOfDay(new Date(Number(cell.getAttribute("data-day"))));
        state.selectedDate = d;
        state.cursor = d;
        state.miniCursor = startOfMonth(d);
        render();
      });
      cell.addEventListener("dblclick", () => {
        const day = Number(cell.getAttribute("data-day"));
        const start = day + 10 * 3600000;
        openEditor(0, start, start + 3600000);
      });
    });
  }

  function buildTimeline(start, days) {
    const cls = days === 1 ? "week-wrap day-1" : "week-wrap";
    let html = `<div class="${cls}"><div></div>`;
    for (let d = 0; d < days; d++) {
      const day = addDays(start, d);
      html += `<div class="dow" style="min-height:auto;text-align:center">${day.toLocaleDateString(undefined, { weekday: "short", day: "numeric" })}</div>`;
    }
    html += `<div></div>`;
    for (let d = 0; d < days; d++) {
      const dayStart = addDays(start, d).getTime();
      html += `<div class="day-col" style="height:720px" data-day="${dayStart}">`;
      for (let h = 0; h < 24; h++) {
        html += `<div class="hour-label" style="position:absolute;left:-56px;top:${h * 30}px;width:50px;border:0">${d === 0 ? String(h).padStart(2, "0") + ":00" : ""}</div>`;
      }
      eventsOnDay(dayStart).filter((e) => !e.allDay).forEach((e) => {
        const top = ((e.startTime - dayStart) / 60000) * 0.5;
        const height = Math.max(20, ((e.endTime - e.startTime) / 60000) * 0.5);
        const color = eventColor(e);
        html += `<button type="button" class="block" data-id="${e.id}" style="top:${top}px;height:${height}px;background:${color}">${escapeHtml(e.title)}</button>`;
      });
      html += `</div>`;
    }
    html += `</div>`;
    return html;
  }

  function renderWeekOrDay(days) {
    const start = days === 1
      ? startOfDay(state.cursor)
      : startOfWeek(state.cursor);
    $("calendarView").innerHTML = buildTimeline(start, days);
    $("calendarView").querySelectorAll(".block").forEach((b) => {
      b.addEventListener("click", () => openPopover(Number(b.getAttribute("data-id")), b));
    });
    $("calendarView").querySelectorAll(".day-col").forEach((col) => {
      col.addEventListener("dblclick", (ev) => {
        const day = Number(col.getAttribute("data-day"));
        const rect = col.getBoundingClientRect();
        const y = ev.clientY - rect.top;
        const minutes = Math.round((y / 0.5) / 15) * 15;
        const startMs = day + minutes * 60000;
        openEditor(0, startMs, startMs + 3600000);
      });
    });
  }

  function syncViewButtons() {
    document.querySelectorAll("[data-view]").forEach((btn) => {
      btn.classList.toggle("active", btn.getAttribute("data-view") === state.view);
    });
  }

  function render() {
    $("title").textContent = fmtTitle();
    renderUser();
    renderCalList();
    renderTasks();
    renderUpcoming();
    renderMiniCal();
    syncViewButtons();
    if (state.view === "MONTH") renderMonth();
    else if (state.view === "WEEK") renderWeekOrDay(7);
    else renderWeekOrDay(1);
  }

  function closePopovers() {
    $("popover").classList.add("hidden");
    $("dayPopover").classList.add("hidden");
    $("userDropdown").classList.add("hidden");
    $("userBtn").setAttribute("aria-expanded", "false");
  }

  function placePopover(el, anchor) {
    el.classList.remove("hidden");
    const r = anchor.getBoundingClientRect();
    const w = el.offsetWidth || 320;
    const h = el.offsetHeight || 200;
    let left = r.left;
    let top = r.bottom + 8;
    if (left + w > window.innerWidth - 12) left = window.innerWidth - w - 12;
    if (top + h > window.innerHeight - 12) top = Math.max(12, r.top - h - 8);
    if (left < 12) left = 12;
    el.style.left = `${left}px`;
    el.style.top = `${top}px`;
  }

  function openPopover(id, anchor) {
    const e = state.events.find((x) => Number(x.id) === Number(id));
    if (!e) return;
    closePopovers();
    const cal = calById(e.calendarId);
    const color = eventColor(e);
    const dateStr = new Date(e.startTime).toLocaleDateString(undefined, {
      weekday: "long", year: "numeric", month: "long", day: "numeric",
    });
    const timeStr = e.allDay ? "All day" : `${fmtTime(e.startTime)} – ${fmtTime(e.endTime)}`;
    $("popover").innerHTML = `
      <h3>${escapeHtml(e.title)}</h3>
      <div class="pop-cal"><span class="cal-dot" style="background:${color}"></span>${escapeHtml((cal && cal.name) || "Calendar")}</div>
      <div class="pop-row"><span class="k">Date</span>${escapeHtml(dateStr)}</div>
      <div class="pop-row"><span class="k">Time</span>${escapeHtml(timeStr)}</div>
      ${e.location ? `<div class="pop-row"><span class="k">Location</span>${escapeHtml(e.location)}</div>` : ""}
      ${e.description ? `<div class="pop-row"><span class="k">Notes</span>${escapeHtml(e.description)}</div>` : ""}
      <div class="pop-actions">
        <button type="button" class="primary" data-act="edit">Edit</button>
        <button type="button" class="danger" data-act="del">Delete</button>
      </div>`;
    placePopover($("popover"), anchor || $("calendarView"));
    $("popover").querySelector("[data-act=edit]").addEventListener("click", () => {
      closePopovers();
      openEditor(e.id);
    });
    $("popover").querySelector("[data-act=del]").addEventListener("click", async () => {
      try {
        await api("POST", "/api/calendar/deleteEvent", { id: e.id });
        closePopovers();
        toast("Event deleted");
        await refreshAll();
      } catch (err) {
        toast(err.message || "Delete failed");
      }
    });
  }

  function openDayPopover(dayStart, anchor) {
    const list = eventsOnDay(dayStart);
    const d = new Date(dayStart);
    closePopovers();
    $("dayPopover").innerHTML = `
      <h3>${escapeHtml(d.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" }))}</h3>
      ${list.map(eventChipHtml).join("") || "<div class='task-empty'>No events</div>"}`;
    placePopover($("dayPopover"), anchor);
    $("dayPopover").querySelectorAll(".ev").forEach((btn) => {
      btn.addEventListener("click", (ev) => {
        ev.stopPropagation();
        openPopover(Number(btn.getAttribute("data-id")), btn);
      });
    });
  }

  function syncAllDayFields() {
    const allDay = $("fldAllDay").checked;
    document.querySelectorAll(".time-fld").forEach((el) => el.classList.toggle("hidden", allDay));
  }

  function openEditor(id, startMs, endMs) {
    closePopovers();
    const eid = id ? Number(id) : 0;
    state.editingId = eid;
    const form = $("eventForm");
    $("editorError").textContent = "";
    $("editorTitle").textContent = eid ? "Edit Event" : "New Event";
    $("btnDelete").classList.toggle("hidden", !eid);
    const tz = (state.user && state.user.timezone) || "Asia/Shanghai";
    $("fldTz").value = tz;

    if (eid) {
      const e = state.events.find((x) => Number(x.id) === eid);
      if (!e) {
        toast("Event not found");
        return;
      }
      form.title.value = e.title;
      form.calendarId.value = String(e.calendarId);
      $("fldAllDay").checked = !!e.allDay;
      form.startDate.value = toDateInput(e.startTime);
      form.startTime.value = toTimeInput(e.startTime);
      form.endDate.value = toDateInput(e.endTime);
      form.endTime.value = toTimeInput(e.endTime);
      form.location.value = e.location || "";
      form.description.value = e.description || "";
      form.minutesBefore.value = e.minutesBefore || 10;
      form.recurrenceRule.value = e.recurrenceRule || "";
      if (e.timezone) $("fldTz").value = e.timezone;
    } else {
      form.reset();
      form.minutesBefore.value = 10;
      $("fldAllDay").checked = false;
      if (state.calendars[0]) form.calendarId.value = String(state.calendars[0].id);
      const s = startMs || state.selectedDate.getTime() + 10 * 3600000;
      const en = endMs || s + 3600000;
      form.startDate.value = toDateInput(s);
      form.startTime.value = toTimeInput(s);
      form.endDate.value = toDateInput(en);
      form.endTime.value = toTimeInput(en);
      $("fldTz").value = tz;
    }
    syncAllDayFields();
    $("editorModal").classList.remove("hidden");
    form.title.focus();
  }

  function toast(msg, opts) {
    const el = $("toast");
    el.textContent = msg;
    el.classList.toggle("toast-reminder", !!(opts && opts.reminder));
    el.classList.toggle("toast-error", !!(opts && opts.error));
    el.classList.remove("hidden");
    clearTimeout(toast._t);
    let ms = 3200;
    if (opts && opts.reminder) ms = 8000;
    if (opts && opts.error) ms = 10000;
    toast._t = setTimeout(() => el.classList.add("hidden"), ms);
    if (opts && opts.error) console.error("[toast]", msg);
  }

  function ensureNotifyPermission() {
    if (typeof Notification === "undefined") return;
    if (Notification.permission === "default") {
      Notification.requestPermission().catch(() => {});
    }
  }

  function showReminder(data) {
    const title = (data && data.title) || "Upcoming event";
    const mins = data && typeof data.minutesBefore === "number" ? data.minutesBefore : null;
    const when = mins === 0 ? "starting now" : (mins != null ? `in ${mins} min` : "soon");
    const msg = `⏰ ${title} — ${when}`;
    toast(msg, { reminder: true });
    ensureNotifyPermission();
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      try {
        const n = new Notification("JNative Calendar", {
          body: `${title} ${when}`,
          tag: data && data.eventId ? `ev-${data.eventId}` : "reminder",
        });
        setTimeout(() => n.close(), 10000);
      } catch (_) { /* ignore */ }
    }
  }

  async function boot() {
    try {
      const me = await api("GET", "/api/auth/currentUser");
      /* currentUser returns AuthSession { user, csrfToken } (same as login). */
      state.user = me.user || me;
      state.csrf = me.csrfToken || "";
      $("loginModal").classList.add("hidden");
      await refreshAll();
      ensureNotifyPermission();
      pollStream();
    } catch (_) {
      $("loginModal").classList.remove("hidden");
    }
  }

  function pollStream() {
    if (pollStream._started) return;
    pollStream._started = true;
    setInterval(async () => {
      try {
        const ev = await api("GET", `/api/stream?since=${state.streamSeq}`);
        if (ev && ev.seq > state.streamSeq) {
          state.streamSeq = ev.seq;
          if (ev.type === "REMINDER") {
            showReminder(ev.data || {});
          } else if (ev.type !== "HEARTBEAT") {
            await refreshAll({ silent: true });
          }
        }
      } catch (_) { /* ignore */ }
    }, 2000);
  }

  /* —— Events —— */
  $("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("loginError").textContent = "";
    try {
      const fd = new FormData($("loginForm"));
      const session = await api("POST", "/api/auth/login", {
        email: fd.get("email"),
        password: fd.get("password"),
      });
      state.user = session.user;
      state.csrf = session.csrfToken || "";
      $("loginModal").classList.add("hidden");
      await refreshAll();
      pollStream();
    } catch (err) {
      $("loginError").textContent = err.message || "Login failed";
    }
  });

  $("fldAllDay").addEventListener("change", syncAllDayFields);

  $("eventForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("editorError").textContent = "";
    const fd = new FormData($("eventForm"));
    const allDay = $("fldAllDay").checked ? 1 : 0;
    const payload = {
      calendarId: Number(fd.get("calendarId")),
      title: fd.get("title"),
      description: fd.get("description") || "",
      location: fd.get("location") || "",
      startTime: combineLocal(fd.get("startDate"), fd.get("startTime"), allDay),
      endTime: combineLocal(fd.get("endDate"), allDay ? "23:59" : fd.get("endTime"), false),
      allDay,
      timezone: fd.get("timezone") || "Asia/Shanghai",
      recurrenceRule: fd.get("recurrenceRule") || "",
      minutesBefore: Number(fd.get("minutesBefore") || 10),
      status: "CONFIRMED",
    };
    if (allDay) {
      payload.endTime = combineLocal(fd.get("endDate"), "23:59", false) + 60000;
    }
    try {
      if (state.editingId) {
        await api("POST", "/api/calendar/updateEvent", Object.assign({ id: state.editingId }, payload));
        toast("Event updated");
      } else {
        await api("POST", "/api/calendar/createEvent", payload);
        toast("Event created");
      }
      $("editorModal").classList.add("hidden");
      state.editingId = 0;
      await refreshAll();
    } catch (err) {
      $("editorError").textContent = err.message || "Save failed";
    }
  });

  $("btnDelete").addEventListener("click", async (ev) => {
    ev.preventDefault();
    if (!state.editingId) return;
    try {
      await api("POST", "/api/calendar/deleteEvent", { id: state.editingId });
      $("editorModal").classList.add("hidden");
      state.editingId = 0;
      toast("Event deleted");
      await refreshAll();
    } catch (err) {
      $("editorError").textContent = err.message || "Delete failed";
    }
  });

  $("btnCancel").addEventListener("click", () => $("editorModal").classList.add("hidden"));
  $("btnNew").addEventListener("click", () => {
    const s = state.selectedDate.getTime() + 10 * 3600000;
    openEditor(0, s, s + 3600000);
  });
  $("btnCreate").addEventListener("click", () => {
    const s = state.selectedDate.getTime() + 10 * 3600000;
    openEditor(0, s, s + 3600000);
  });

  $("btnToday").addEventListener("click", async () => {
    const t = startOfDay(new Date());
    state.cursor = t;
    state.selectedDate = t;
    state.miniCursor = startOfMonth(t);
    await refreshAll();
  });
  $("prev").addEventListener("click", async () => {
    if (state.view === "MONTH") state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() - 1, 1);
    else state.cursor = addDays(state.cursor, state.view === "WEEK" ? -7 : -1);
    state.selectedDate = startOfDay(state.cursor);
    state.miniCursor = startOfMonth(state.cursor);
    await refreshAll();
  });
  $("next").addEventListener("click", async () => {
    if (state.view === "MONTH") state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + 1, 1);
    else state.cursor = addDays(state.cursor, state.view === "WEEK" ? 7 : 1);
    state.selectedDate = startOfDay(state.cursor);
    state.miniCursor = startOfMonth(state.cursor);
    await refreshAll();
  });

  document.querySelectorAll("[data-view]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      state.view = btn.getAttribute("data-view");
      await refreshAll();
    });
  });

  document.querySelectorAll(".task-filter").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.taskFilter = btn.getAttribute("data-task-filter");
      renderTasks();
    });
  });

  $("btnAddTask").addEventListener("click", async () => {
    const title = window.prompt("Task title");
    if (!title) return;
    try {
      await api("POST", "/api/tasks/createTask", {
        title,
        description: "",
        dueAt: startOfDay(new Date()).getTime() + 18 * 3600000,
        priority: "NORMAL",
      });
      toast("Task created");
      await refreshAll();
    } catch (err) {
      toast(err.message || "Failed to create task");
    }
  });

  $("userBtn").addEventListener("click", () => {
    $("userDropdown").classList.toggle("hidden");
    $("userBtn").setAttribute("aria-expanded", String(!$("userDropdown").classList.contains("hidden")));
  });
  $("btnSignOut").addEventListener("click", async () => {
    try { await api("POST", "/api/auth/logout", {}); } catch (_) {}
    state.user = null;
    state.csrf = "";
    closePopovers();
    $("loginModal").classList.remove("hidden");
  });

  $("search").addEventListener("input", () => {
    clearTimeout(state.searchTimer);
    state.searchTimer = setTimeout(async () => {
      const q = $("search").value.trim();
      if (!q) {
        $("searchPanel").classList.add("hidden");
        return;
      }
      if (state.searchAbort) state.searchAbort.abort();
      state.searchAbort = new AbortController();
      try {
        const hits = await api("GET", `/api/search/search?q=${encodeURIComponent(q)}`, null, state.searchAbort.signal);
        const list = Array.isArray(hits) ? hits : (hits && hits.results) || [];
        if (!list.length) {
          $("searchPanel").innerHTML = `<div class="search-hit"><span class="meta">无结果</span></div>`;
        } else {
          $("searchPanel").innerHTML = list.map((h, i) => {
            const kind = String(h.kind || "").toLowerCase() === "task" ? "任务" : "事件";
            const when = h.whenMs ? new Date(h.whenMs).toLocaleString(undefined, {
              month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
            }) : "";
            const meta = [kind, when, h.snippet].filter(Boolean).join(" · ");
            return `<button type="button" class="search-hit" data-idx="${i}">
              <strong>${escapeHtml(h.title)}</strong>
              <span class="meta">${escapeHtml(meta)}</span>
            </button>`;
          }).join("");
          $("searchPanel").querySelectorAll(".search-hit[data-idx]").forEach((btn) => {
            btn.addEventListener("click", async () => {
              const h = list[Number(btn.getAttribute("data-idx"))];
              $("searchPanel").classList.add("hidden");
              if (h && String(h.kind).toLowerCase() === "event" && h.id) {
                if (h.whenMs) {
                  state.selectedDate = startOfDay(new Date(h.whenMs));
                  state.cursor = state.selectedDate;
                  await refreshAll();
                }
                let ev = state.events.find((x) => Number(x.id) === Number(h.id));
                if (ev) openPopover(ev.id, $("calendarView"));
                else toast(h.title);
              } else if (h && String(h.kind).toLowerCase() === "task") {
                state.taskFilter = "upcoming";
                renderTasks();
                toast(h.title || "Task");
              } else {
                toast(h.title || "Result");
              }
            });
          });
        }
        $("searchPanel").classList.remove("hidden");
      } catch (err) {
        if (err.name !== "AbortError") toast(err.message || "Search failed");
      }
    }, 300);
  });

  document.addEventListener("keydown", (ev) => {
    const tag = (ev.target && ev.target.tagName) || "";
    const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (ev.target && ev.target.isContentEditable);
    if (ev.key === "Escape") {
      closePopovers();
      $("editorModal").classList.add("hidden");
      $("searchPanel").classList.add("hidden");
      return;
    }
    if (typing) return;
    if (ev.key === "t" || ev.key === "T") $("btnToday").click();
    if (ev.key === "m" || ev.key === "M") document.querySelector('[data-view="MONTH"]').click();
    if (ev.key === "w" || ev.key === "W") document.querySelector('[data-view="WEEK"]').click();
    if (ev.key === "d" || ev.key === "D") document.querySelector('[data-view="DAY"]').click();
    if (ev.key === "n" || ev.key === "N") $("btnNew").click();
    if (ev.key === "/") {
      ev.preventDefault();
      $("search").focus();
    }
  });

  document.addEventListener("click", (ev) => {
    if (!$("popover").contains(ev.target) && !ev.target.closest(".ev") && !ev.target.closest(".block") && !ev.target.closest(".up-item")) {
      $("popover").classList.add("hidden");
    }
    if (!$("dayPopover").contains(ev.target) && !ev.target.closest(".more")) {
      $("dayPopover").classList.add("hidden");
    }
    if (!$("userMenu").contains(ev.target)) {
      $("userDropdown").classList.add("hidden");
    }
    if (!$("search-wrap") && true) {
      /* noop */
    }
    if (!$("search").contains(ev.target) && !$("searchPanel").contains(ev.target)) {
      $("searchPanel").classList.add("hidden");
    }
  });

  boot();
})();
