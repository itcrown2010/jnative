/**
 * BusinessNavigationService - drill-down via WorkspaceTabs (DEMO).
 */
(function (global) {
  var DRILLS = {
    "md.customers": function (rec) {
      return { pageId: "md.customers", openId: rec.id, label: "Customer " + (rec.code || rec.id) };
    },
    "sales.orders": function (rec) {
      return { pageId: "sales.orders", openId: rec.id, label: "SO " + (rec.docNo || rec.id) };
    },
    "pur.orders": function (rec) {
      return { pageId: "pur.orders", openId: rec.id, label: "PO " + (rec.docNo || rec.id) };
    },
    "inv.txns": function (rec) {
      /* inventory transaction -> source document */
      var ref = rec.ref || "";
      if (String(ref).indexOf("SO") === 0 || String(ref).indexOf("DL") === 0) {
        return { pageId: "sales.orders", openId: null, q: ref, label: "Source " + ref };
      }
      if (String(ref).indexOf("PO") === 0 || String(ref).indexOf("GR") === 0) {
        return { pageId: "pur.orders", openId: null, q: ref, label: "Source " + ref };
      }
      return { pageId: "inv.txns", openId: rec.id, label: "Txn " + rec.id };
    },
    "fin.ar": function (rec) {
      return { pageId: "sales.orders", openId: null, q: rec.references && rec.references.source ? rec.references.source : rec.docNo, label: "AR -> SO" };
    },
    "sales.ar": function (rec) {
      return { pageId: "sales.orders", openId: null, q: (rec.references && rec.references.source) || "", label: "AR -> SO " + ((rec.references && rec.references.source) || "") };
    },
    "fin.ap": function (rec) {
      return { pageId: "pur.orders", openId: null, q: (rec.references && rec.references.source) || "", label: "AP -> PO" };
    },
    "pur.ap": function (rec) {
      return { pageId: "pur.orders", openId: null, q: (rec.references && rec.references.source) || "", label: "AP -> PO" };
    }
  };

  function openInTab(pageId) {
    if (global.JnApp && JnApp.navigate) JnApp.navigate(pageId);
  }

  function openRecord(page, id) {
    openInTab(page.pageId);
    setTimeout(function () {
      /* trigger existing list Open / editor */
      var btn = document.querySelector('#jn-workspace [data-open="' + id + '"], #jn-workspace [data-action="view"][data-id="' + id + '"]');
      if (btn) btn.click();
      else if (typeof page._gridOpen === "function") page._gridOpen(id);
      else {
        /* fallback: use templates openEditor path via synthetic */
        if (global.JnTemplates && JnTemplates.openEditor) JnTemplates.openEditor(page, id);
        else JnUI.toast("Open " + page.pageId + " / " + id + " (DEMO)", "ok");
      }
    }, 80);
  }

  function drillFromRow(page, id) {
    JnData.active.get(page.collection, id).then(function (rec) {
      if (!rec) { JnUI.toast("Record not found", "err"); return; }
      var fn = DRILLS[page.pageId];
      if (!fn) {
        openRecord(page, id);
        return;
      }
      var target = fn(rec);
      JnUI.toast("Drill DEMO: " + (target.label || target.pageId) + " (WorkspaceTabs)", "ok");
      openInTab(target.pageId);
      setTimeout(function () {
        if (target.openId) {
          var btn = document.querySelector('#jn-workspace [data-open="' + target.openId + '"]');
          if (btn) btn.click();
        } else if (target.q) {
          var input = document.querySelector('#jn-workspace [data-role="quick-q"], #jn-workspace [data-role="search"]');
          if (input) {
            input.value = target.q;
            var apply = document.querySelector('#jn-workspace [data-g="apply-quick"], #jn-workspace [data-action="apply-filter"]');
            if (apply) apply.click();
          }
        }
      }, 100);
    });
  }

  global.JnBusinessNav = {
    openRecord: openRecord,
    drillFromRow: drillFromRow,
    openInTab: openInTab
  };
})(window);
