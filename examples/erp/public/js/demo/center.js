// Author: 张晓达
/**
 * DemoCenter - product demo scenarios (UI 1.5).
 * All mutations via JnAppService / DemoStateStore.
 */
(function (global) {
  var session = {
    running: false,
    paused: false,
    scenarioId: null,
    startedAt: null
  };

  var SCENARIOS = [
    {
      id: "executive",
      title: "Executive Demo",
      module: "Workbench",
      summary: "KPI workbench from DemoStateStore, then jump to sales and inventory.",
      steps: [
        { title: "Executive Dashboard", body: "KPIs are computed from DemoStateStore - not hardcoded.", go: "dash.executive", selector: ".jn-kpi-grid" },
        { title: "Sales Orders", body: "Open the sales order grid powered by EnterpriseDataGrid.", go: "sales.orders", selector: ".jn-enterprise-grid" },
        { title: "Inventory Value", body: "Balances and value come from unified demo inventory rules.", go: "inv.balance", selector: ".jn-enterprise-grid" }
      ]
    },
    {
      id: "sales",
      title: "Sales Demo",
      module: "Sales",
      summary: "Guided sales path: order list ? customer ? AR.",
      steps: [
        { title: "Sales Orders", body: "Sales documents share DocumentEditor + DemoStateStore.", go: "sales.orders", selector: ".jn-enterprise-grid" },
        { title: "Customers", body: "Master data list with commercial grid chrome.", go: "md.customers", selector: ".jn-enterprise-grid" },
        { title: "Accounts Receivable", body: "AR open amounts reflect ApplicationService postings.", go: "fin.ar", selector: ".jn-enterprise-grid" }
      ],
      runScript: function () {
        /* ensure store has activity without forging server DB */
        var k = JnAppService.getKpis();
        return { kpis: k };
      }
    },
    {
      id: "purchasing",
      title: "Purchasing Demo",
      module: "Purchasing",
      summary: "PO list ? goods receipts ? AP.",
      steps: [
        { title: "Purchase Orders", body: "Purchasing documents use the same editor shell.", go: "pur.orders", selector: ".jn-enterprise-grid" },
        { title: "Goods Receipts", body: "Receipts post inventory through ApplicationService.", go: "pur.gr", selector: "#jn-workspace" },
        { title: "Accounts Payable", body: "AP balances stay consistent with demo payments.", go: "fin.ap", selector: ".jn-enterprise-grid" }
      ]
    },
    {
      id: "inventory",
      title: "Inventory Demo",
      module: "Inventory",
      summary: "Balances and transactions with source-document traceability.",
      steps: [
        { title: "Inventory Balance", body: "Qty and value updated by consistent demo cost rules.", go: "inv.balance", selector: ".jn-enterprise-grid" },
        { title: "Inventory Transactions", body: "Each movement references its source document.", go: "inv.txns", selector: ".jn-enterprise-grid" }
      ]
    },
    {
      id: "finance",
      title: "Finance Demo",
      module: "Finance",
      summary: "Journals must remain debit=credit in DEMO mode.",
      steps: [
        { title: "Journal Entries", body: "Demo journals are balance-checked before post.", go: "fin.journals", selector: "#jn-workspace" },
        { title: "Trial Balance", body: "Trial balance is derived from DemoStateStore accounts.", go: "fin.tb", selector: "#jn-workspace" },
        { title: "AR / AP", body: "Open amounts drive executive finance KPIs.", go: "fin.ar", selector: ".jn-enterprise-grid" }
      ]
    },
    {
      id: "manufacturing",
      title: "Manufacturing Demo",
      module: "Manufacturing",
      summary: "BOM editor and MRP workbench on demo data.",
      steps: [
        { title: "BOM Editor", body: "Bill of materials maintained in DemoStateStore.", go: "mfg.bomEditor", selector: "#jn-workspace" },
        { title: "MRP Workbench", body: "Suggestions are DEMO planning aids only.", go: "mfg.mrp", selector: "#jn-workspace" },
        { title: "Production Orders", body: "Issue/receive flows update inventory via ApplicationService.", go: "mfg.orders", selector: ".jn-enterprise-grid" }
      ]
    }
  ];

  function getScenario(id) {
    for (var i = 0; i < SCENARIOS.length; i++) if (SCENARIOS[i].id === id) return SCENARIOS[i];
    return null;
  }

  function start(id) {
    var sc = getScenario(id);
    if (!sc) { JnUI.toast("Unknown scenario", "err"); return; }
    session.running = true;
    session.paused = false;
    session.scenarioId = id;
    session.startedAt = Date.now();
    if (typeof sc.runScript === "function") {
      try { sc.runScript(); } catch (e) { console.warn(e); }
    }
    JnDemoTour.start({
      steps: sc.steps,
      onDone: function () {
        session.running = false;
        session.scenarioId = null;
      }
    });
    JnUI.toast("Started DEMO scenario: " + sc.title, "ok");
  }

  function pause() {
    if (!session.running) return;
    session.paused = true;
    JnDemoTour.stop();
    JnUI.toast("Demo paused (DEMO)", "ok");
  }

  function resume() {
    if (!session.scenarioId) return;
    session.paused = false;
    start(session.scenarioId);
  }

  function exit() {
    JnDemoTour.stop();
    session.running = false;
    session.paused = false;
    session.scenarioId = null;
    JnUI.toast("Exited demo (DEMO MODE remains on)", "ok");
  }

  function reset() {
    if (!window.confirm("Reset DEMO data to seed before continuing?")) return;
    JnAppService.resetDemo();
    exit();
    JnUI.toast("Demo data reset", "ok");
    if (global.JnApp && JnApp.navigate) JnApp.navigate("demo.center");
  }

  function render(host) {
    var cards = SCENARIOS.map(function (s) {
      return '<div class="jn-scenario-card" data-id="' + JnUI.esc(s.id) + '">' +
        '<div class="jn-muted">' + JnUI.esc(s.module) + "</div>" +
        "<h3 style=\"margin:4px 0 8px\">" + JnUI.esc(s.title) + "</h3>" +
        "<p style=\"color:var(--jn-color-text-secondary);font-size:13px;min-height:48px\">" + JnUI.esc(s.summary) + "</p>" +
        '<div class="jn-toolbar">' +
        '<button type="button" class="jn-btn primary" data-start="' + JnUI.esc(s.id) + '">Start demo</button>' +
        "</div></div>";
    }).join("");
    host.innerHTML =
      '<div class="jn-card jn-demo-center">' +
      '<div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">Demo Center</h2>' +
      '<span class="jn-status OPEN">DEMO MODE</span><span class="spacer"></span>' +
      '<button type="button" class="jn-btn" data-act="pause">Pause</button>' +
      '<button type="button" class="jn-btn" data-act="resume">Continue</button>' +
      '<button type="button" class="jn-btn" data-act="exit">Exit demo</button>' +
      '<button type="button" class="jn-btn danger" data-act="reset">Reset demo data</button></div>' +
      '<div class="jn-demo-save-note">Product demos mutate DemoStateStore only. Never writes a formal business database. Persistence: localStorage.</div>' +
      '<div class="jn-scenario-grid" style="margin-top:16px">' + cards + "</div></div>";
    host.querySelectorAll("[data-start]").forEach(function (btn) {
      btn.onclick = function () { start(btn.getAttribute("data-start")); };
    });
    host.querySelector('[data-act="pause"]').onclick = pause;
    host.querySelector('[data-act="resume"]').onclick = resume;
    host.querySelector('[data-act="exit"]').onclick = exit;
    host.querySelector('[data-act="reset"]').onclick = reset;
  }

  global.JnDemoCenter = {
    SCENARIOS: SCENARIOS,
    start: start,
    pause: pause,
    resume: resume,
    exit: exit,
    reset: reset,
    render: render,
    session: function () { return Object.assign({}, session); }
  };
})(window);
