/**
 * DemoTourEngine - highlight + step coachmarks (UI 1.5).
 * IME-safe: ignores composing keys.
 */
(function (global) {
  var active = null;

  function clearDom() {
    document.querySelectorAll(".jn-tour-overlay,.jn-tour-spotlight,.jn-tour-card").forEach(function (el) {
      el.remove();
    });
  }

  function placeCard(card, rect) {
    var top = rect.bottom + 12;
    var left = Math.min(window.innerWidth - 380, Math.max(12, rect.left));
    if (top + 180 > window.innerHeight) top = Math.max(12, rect.top - 190);
    card.style.top = top + "px";
    card.style.left = left + "px";
  }

  function showStep() {
    if (!active) return;
    clearDom();
    var step = active.steps[active.index];
    if (!step) { finish(true); return; }
    var target = document.querySelector(step.selector);
    var spot = document.createElement("div");
    spot.className = "jn-tour-spotlight";
    var card = document.createElement("div");
    card.className = "jn-tour-card";
    card.setAttribute("role", "dialog");
    card.setAttribute("aria-label", "Demo tour");
    var rect;
    if (target) {
      rect = target.getBoundingClientRect();
      spot.style.top = (rect.top - 4) + "px";
      spot.style.left = (rect.left - 4) + "px";
      spot.style.width = (rect.width + 8) + "px";
      spot.style.height = (rect.height + 8) + "px";
    } else {
      rect = { top: 80, left: 80, bottom: 120, width: 200, height: 40 };
      spot.style.top = "80px"; spot.style.left = "80px";
      spot.style.width = "200px"; spot.style.height = "40px";
    }
    card.innerHTML =
      "<h3>" + JnUI.esc(step.title || ("Step " + (active.index + 1))) + "</h3>" +
      "<p>" + JnUI.esc(step.body || "") + "</p>" +
      '<div class="jn-muted" style="margin-bottom:8px">Step ' + (active.index + 1) + " / " + active.steps.length + " | DEMO MODE</div>" +
      '<div class="jn-tour-actions">' +
      '<button type="button" class="jn-btn" data-tour="skip">Skip</button>' +
      '<button type="button" class="jn-btn" data-tour="prev"' + (active.index === 0 ? " disabled" : "") + ">Back</button>" +
      '<button type="button" class="jn-btn primary" data-tour="next">' +
      (active.index >= active.steps.length - 1 ? "Finish" : "Next") + "</button></div>";
    document.body.appendChild(spot);
    document.body.appendChild(card);
    placeCard(card, rect);
    card.querySelector('[data-tour="skip"]').onclick = function () { finish(false); };
    card.querySelector('[data-tour="prev"]').onclick = function () {
      if (active.index > 0) { active.index--; runActionThenShow(); }
    };
    card.querySelector('[data-tour="next"]').onclick = function () {
      if (active.index >= active.steps.length - 1) finish(true);
      else { active.index++; runActionThenShow(); }
    };
  }

  function runActionThenShow() {
    if (!active) return;
    var step = active.steps[active.index];
    if (step && step.go && global.JnApp) {
      JnApp.navigate(step.go);
      setTimeout(showStep, 120);
    } else if (step && typeof step.action === "function") {
      Promise.resolve(step.action()).then(function () { setTimeout(showStep, 80); });
    } else {
      showStep();
    }
  }

  function finish(completed) {
    clearDom();
    var cb = active && active.onDone;
    active = null;
    document.removeEventListener("keydown", onKey, true);
    if (cb) cb(completed);
    JnUI.toast(completed ? "Demo tour completed (DEMO)" : "Demo tour skipped", "ok");
  }

  function onKey(e) {
    if (!active) return;
    if (e.isComposing || e.keyCode === 229) return;
    if (e.key === "Escape") { e.preventDefault(); finish(false); }
    else if (e.key === "Enter") {
      var t = e.target;
      if (t && typeof t.closest === "function" && t.closest("input,textarea,select")) return;
      e.preventDefault();
      if (active.index >= active.steps.length - 1) finish(true);
      else { active.index++; runActionThenShow(); }
    }
  }

  function start(opts) {
    stop();
    active = {
      steps: (opts && opts.steps) || [],
      index: 0,
      onDone: opts && opts.onDone
    };
    document.addEventListener("keydown", onKey, true);
    runActionThenShow();
  }

  function stop() {
    if (active) finish(false);
    else clearDom();
  }

  function isRunning() { return !!active; }

  global.JnDemoTour = { start: start, stop: stop, isRunning: isRunning };
})(window);
