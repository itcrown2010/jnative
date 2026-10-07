(() => {
  const state = {
    user: null,
    csrf: "",
    view: "MONTH",
    cursor: new Date(),
    calendars: [],
    events: [],
    tasks: [],
    editingId: 0,
    streamSeq: 0,
    searchTimer: 0,
  };

  const $ = (id) => document.getElementById(id);
  const api = async (method, path, body) => {
    const headers = { "Content-Type": "application/json" };
    if (state.csrf) headers["X-CSRF-Token"] = state.csrf;
    const res = await fetch(path, {
      method,
      headers,
      credentials: "same-origin",
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = { message: text }; }
    if (!res.ok) {
      const err = new Error((data && data.message) || res.statusText);
      err.code = data && data.code;
      throw err;
    }
    return data;
  };

  function startOfMonth(d) {
    return new Date(d.getFullYear(), d.getMonth(), 1);
  }
  function addDays(d, n) {
    const x = new Date(d.getTime());
    x.setDate(x.getDate() + n);
    return x;
  }
  function startOfWeek(d) {
    const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const day = x.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    return addDays(x, diff);
  }
  function fmtTitle() {
    return state.cursor.toLocaleString(undefined, { month: "long", year: "numeric" });
  }
  function toLocalInput(ms) {
    const d = new Date(ms);
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }
  function fromLocalInput(s) {
    return new Date(s).getTime();
  }
  function rangeForView() {
    if (state.view === "DAY") {
      const a = new Date(state.cursor.getFullYear(), state.cursor.getMonth(), state.cursor.getDate()).getTime();
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

  async function refreshAll() {
    $("status").textContent = "Loading…";
    const range = rangeForView();
    const [cals, events, tasks] = await Promise.all([
      api("GET", "/api/calendar/listCalendars"),
      api("GET", `/api/calendar/eventsBetween?from=${range.from}&to=${range.to}`),
      api("GET", "/api/tasks/listTasks"),
    ]);
    state.calendars = cals || [];
    state.events = events || [];
    state.tasks = tasks || [];
    render();
    $("status").textContent = `${state.events.length} events · ${state.tasks.length} tasks`;
  }

  function renderCalList() {
    $("calList").innerHTML = state.calendars.map((c) =>
      `<div class="cal-item"><span class="swatch" style="background:${c.color}"></span>${escapeHtml(c.name)}</div>`
    ).join("");
    $("eventCal").innerHTML = state.calendars.map((c) =>
      `<option value="${c.id}">${escapeHtml(c.name)}</option>`
    ).join("");
  }

  function renderTasks() {
    $("taskList").innerHTML = state.tasks.map((t) =>
      `<label class="task-item"><input type="checkbox" data-task="${t.id}" ${t.status === "DONE" ? "checked" : ""}> ${escapeHtml(t.title)}</label>`
    ).join("");
    $("taskList").querySelectorAll("input[data-task]").forEach((el) => {
      el.addEventListener("change", async () => {
        const id = Number(el.getAttribute("data-task"));
        const t = state.tasks.find((x) => x.id === id);
        if (!t) return;
        await api("POST", "/api/tasks/updateTask", {
          id,
          title: t.title,
          description: t.description,
          dueAt: t.dueAt,
          priority: t.priority,
          status: el.checked ? "DONE" : "OPEN",
        });
        await refreshAll();
      });
    });
  }

  function eventsOnDay(dayStart) {
    const dayEnd = dayStart + 86400000;
    return state.events.filter((e) => e.startTime < dayEnd && e.endTime > dayStart);
  }

  function renderMonth() {
    const first = startOfMonth(state.cursor);
    let cur = startOfWeek(first);
    const dow = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    let html = `<div class="month-grid">${dow.map((d) => `<div class="dow">${d}</div>`).join("")}`;
    for (let i = 0; i < 42; i++) {
      const start = new Date(cur.getFullYear(), cur.getMonth(), cur.getDate()).getTime();
      const today = new Date();
      const isToday = cur.toDateString() === today.toDateString();
      const inMonth = cur.getMonth() === state.cursor.getMonth();
      const list = eventsOnDay(start);
      html += `<div class="cell ${isToday ? "today" : ""}" data-day="${start}" style="opacity:${inMonth ? 1 : 0.45}">
        <div class="num">${cur.getDate()}</div>
        ${list.slice(0, 3).map((e) => `<button type="button" class="chip" data-id="${e.id}" style="background:${e.color}22;color:${e.color}">${escapeHtml(e.title)}</button>`).join("")}
      </div>`;
      cur = addDays(cur, 1);
    }
    html += "</div>";
    $("calendarView").innerHTML = html;
    $("calendarView").querySelectorAll(".chip").forEach((btn) => {
      btn.addEventListener("click", (ev) => {
        ev.stopPropagation();
        openEditor(Number(btn.getAttribute("data-id")));
      });
    });
    $("calendarView").querySelectorAll(".cell").forEach((cell) => {
      cell.addEventListener("dblclick", () => {
        const start = Number(cell.getAttribute("data-day")) + 10 * 3600000;
        openEditor(0, start, start + 3600000);
      });
    });
  }

  function renderWeekOrDay(days) {
    const start = days === 1
      ? new Date(state.cursor.getFullYear(), state.cursor.getMonth(), state.cursor.getDate())
      : startOfWeek(state.cursor);
    let html = `<div class="week-wrap"><div></div>`;
    for (let d = 0; d < days; d++) {
      const day = addDays(start, d);
      html += `<div class="dow" style="min-height:auto;text-align:center">${day.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" })}</div>`;
    }
    for (let h = 0; h < 24; h++) {
      html += `<div class="hour-label">${String(h).padStart(2, "0")}:00</div>`;
      for (let d = 0; d < days; d++) html += `<div class="day-col" data-day="${addDays(start, d).getTime()}" data-hour="${h}" style="height:30px"></div>`;
    }
    html += "</div>";
    $("calendarView").innerHTML = html;
    const cols = $("calendarView").querySelectorAll(".day-col");
    const colMap = {};
    cols.forEach((c) => {
      const key = c.getAttribute("data-day");
      if (!colMap[key]) colMap[key] = c.parentElement
        ? Array.from($("calendarView").querySelectorAll(`.day-col[data-day="${key}"]`))
        : [];
    });
    // Place blocks in first hour cell container via absolute parent trick: wrap each day
    $("calendarView").innerHTML = buildTimeline(start, days);
    $("calendarView").querySelectorAll(".block").forEach((b) => {
      b.addEventListener("click", () => openEditor(Number(b.getAttribute("data-id"))));
    });
  }

  function buildTimeline(start, days) {
    let html = `<div class="week-wrap"><div></div>`;
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
      const list = eventsOnDay(dayStart).filter((e) => !e.allDay);
      list.forEach((e) => {
        const top = ((e.startTime - dayStart) / 60000) * 0.5;
        const height = Math.max(20, ((e.endTime - e.startTime) / 60000) * 0.5);
        html += `<button type="button" class="block" data-id="${e.id}" style="top:${top}px;height:${height}px;background:${e.color}">${escapeHtml(e.title)}</button>`;
      });
      html += `</div>`;
    }
    html += `</div>`;
    return html;
  }

  function render() {
    $("title").textContent = fmtTitle();
    $("userBox").textContent = state.user ? state.user.displayName : "Guest";
    renderCalList();
    renderTasks();
    if (state.view === "MONTH") renderMonth();
    else if (state.view === "WEEK") renderWeekOrDay(7);
    else renderWeekOrDay(1);
  }

  function escapeHtml(s) {
    return String(s || "").replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
  }

  function openEditor(id, startMs, endMs) {
    state.editingId = id || 0;
    const form = $("eventForm");
    $("editorError").textContent = "";
    $("btnDelete").classList.toggle("hidden", !id);
    if (id) {
      const e = state.events.find((x) => x.id === id);
      if (!e) return;
      form.title.value = e.title;
      form.calendarId.value = String(e.calendarId);
      form.start.value = toLocalInput(e.startTime);
      form.end.value = toLocalInput(e.endTime);
      form.location.value = e.location || "";
      form.description.value = e.description || "";
      form.minutesBefore.value = e.minutesBefore || 10;
      form.recurrenceRule.value = e.recurrenceRule || "";
    } else {
      form.reset();
      form.minutesBefore.value = 10;
      if (state.calendars[0]) form.calendarId.value = String(state.calendars[0].id);
      form.start.value = toLocalInput(startMs || Date.now());
      form.end.value = toLocalInput(endMs || (Date.now() + 3600000));
    }
    $("editorModal").classList.remove("hidden");
  }

  function toast(msg) {
    const el = $("toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    setTimeout(() => el.classList.add("hidden"), 4000);
  }

  async function boot() {
    try {
      const me = await api("GET", "/api/auth/currentUser");
      state.user = me;
      $("loginModal").classList.add("hidden");
      await refreshAll();
      pollStream();
    } catch (_) {
      $("loginModal").classList.remove("hidden");
    }
  }

  function pollStream() {
    setInterval(async () => {
      try {
        const ev = await api("GET", `/api/stream?since=${state.streamSeq}`);
        if (ev && ev.seq > state.streamSeq) {
          state.streamSeq = ev.seq;
          if (ev.type === "REMINDER") {
            toast(`Reminder: ${(ev.data && ev.data.title) || "upcoming event"}`);
          } else if (ev.type !== "HEARTBEAT") {
            await refreshAll();
          }
        }
      } catch (_) { /* ignore */ }
    }, 5000);
  }

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

  $("eventForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("editorError").textContent = "";
    const fd = new FormData($("eventForm"));
    const payload = {
      calendarId: Number(fd.get("calendarId")),
      title: fd.get("title"),
      description: fd.get("description") || "",
      location: fd.get("location") || "",
      startTime: fromLocalInput(fd.get("start")),
      endTime: fromLocalInput(fd.get("end")),
      allDay: 0,
      timezone: (state.user && state.user.timezone) || "Asia/Shanghai",
      recurrenceRule: fd.get("recurrenceRule") || "",
      minutesBefore: Number(fd.get("minutesBefore") || 10),
      status: "CONFIRMED",
    };
    try {
      if (state.editingId) {
        await api("POST", "/api/calendar/updateEvent", Object.assign({ id: state.editingId }, payload));
      } else {
        await api("POST", "/api/calendar/createEvent", payload);
      }
      $("editorModal").classList.add("hidden");
      await refreshAll();
    } catch (err) {
      $("editorError").textContent = err.message || "Save failed";
    }
  });

  $("btnDelete").addEventListener("click", async () => {
    if (!state.editingId) return;
    await api("POST", "/api/calendar/deleteEvent", { id: state.editingId });
    $("editorModal").classList.add("hidden");
    await refreshAll();
  });
  $("btnCancel").addEventListener("click", () => $("editorModal").classList.add("hidden"));
  $("btnNew").addEventListener("click", () => openEditor(0));
  $("btnToday").addEventListener("click", async () => { state.cursor = new Date(); await refreshAll(); });
  $("prev").addEventListener("click", async () => {
    if (state.view === "MONTH") state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() - 1, 1);
    else state.cursor = addDays(state.cursor, state.view === "WEEK" ? -7 : -1);
    await refreshAll();
  });
  $("next").addEventListener("click", async () => {
    if (state.view === "MONTH") state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + 1, 1);
    else state.cursor = addDays(state.cursor, state.view === "WEEK" ? 7 : 1);
    await refreshAll();
  });
  document.querySelectorAll("[data-view]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      state.view = btn.getAttribute("data-view");
      await refreshAll();
    });
  });
  $("search").addEventListener("input", () => {
    clearTimeout(state.searchTimer);
    state.searchTimer = setTimeout(async () => {
      const q = $("search").value.trim();
      if (!q) { $("status").textContent = `${state.events.length} events · ${state.tasks.length} tasks`; return; }
      const hits = await api("GET", `/api/search/search?q=${encodeURIComponent(q)}`);
      $("status").textContent = `Search: ${(hits || []).length} hits`;
      $("calendarView").innerHTML = `<div style="padding:16px">${(hits || []).map((h) =>
        `<div style="padding:8px 0;border-bottom:1px solid #e5e7eb"><strong>${escapeHtml(h.kind)}</strong> ${escapeHtml(h.title)} <span style="color:#64748b">${escapeHtml(h.snippet || "")}</span></div>`
      ).join("")}</div>`;
    }, 300);
  });

  boot();
})();
