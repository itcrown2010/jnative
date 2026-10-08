/**
 * GlobalSearch UI - DEMO index across Customers / Suppliers / Items / SO / PO.
 */
(function (global) {
  var CATEGORIES = [
    { id: "customers", label: "Customers", collection: "customers", pageId: "md.customers", fields: ["code", "name", "city"] },
    { id: "suppliers", label: "Suppliers", collection: "suppliers", pageId: "md.suppliers", fields: ["code", "name", "city"] },
    { id: "items", label: "Items", collection: "items", pageId: "md.items", fields: ["sku", "name", "category"] },
    { id: "salesOrders", label: "Sales Orders", collection: "salesOrders", pageId: "sales.orders", fields: ["docNo", "customerName", "status"] },
    { id: "purchaseOrders", label: "Purchase Orders", collection: "purchaseOrders", pageId: "pur.orders", fields: ["docNo", "supplierName", "status"] }
  ];

  function search(q) {
    q = String(q || "").toLowerCase().trim();
    if (!q) return Promise.resolve([]);
    return Promise.all(CATEGORIES.map(function (cat) {
      return JnData.active.list(cat.collection, { q: q, page: 1, pageSize: 5 }).then(function (res) {
        return {
          category: cat,
          rows: (res.rows || []).map(function (r) {
            var title = r.name || r.docNo || r.sku || r.code || r.id;
            var sub = cat.fields.map(function (f) { return r[f]; }).filter(Boolean).join(" · ");
            return { id: r.id, title: title, sub: sub, pageId: cat.pageId, raw: r };
          })
        };
      });
    }));
  }

  function openPanel(anchor, q) {
    var existing = document.getElementById("jn-global-search-panel");
    if (existing) existing.remove();
    var panel = document.createElement("div");
    panel.id = "jn-global-search-panel";
    panel.className = "jn-global-search-panel";
    panel.innerHTML = '<div class="jn-loading">Searching DEMO index...</div>';
    document.body.appendChild(panel);
    position(panel, anchor);
    search(q).then(function (groups) {
      var html = '<div class="jn-demo-save-note">GlobalSearch DEMO index - not enterprise search server.</div>';
      var any = false;
      groups.forEach(function (g) {
        if (!g.rows.length) return;
        any = true;
        html += '<div class="jn-gs-cat"><div class="jn-gs-cat-title">' + JnUI.esc(g.category.label) + "</div>";
        g.rows.forEach(function (r) {
          html += '<button type="button" class="jn-gs-item" data-page="' + JnUI.esc(r.pageId) + '" data-id="' + JnUI.esc(r.id) + '">' +
            "<strong>" + JnUI.esc(r.title) + "</strong><span>" + JnUI.esc(r.sub) + "</span></button>";
        });
        html += "</div>";
      });
      if (!any) html += '<div class="jn-empty">No results for "' + JnUI.esc(q) + '"</div>';
      panel.innerHTML = html;
      panel.querySelectorAll(".jn-gs-item").forEach(function (btn) {
        btn.onclick = function () {
          var pageId = btn.getAttribute("data-page");
          var id = btn.getAttribute("data-id");
          panel.remove();
          JnApp.navigate(pageId);
          setTimeout(function () {
            var openBtn = document.querySelector('#jn-workspace [data-open="' + id + '"], #jn-workspace [data-action="view"][data-id="' + id + '"]');
            if (openBtn) openBtn.click();
            else JnUI.toast("Opened " + pageId + " (DEMO search hit " + id + ")", "ok");
          }, 120);
        };
      });
    });
    function onDoc(e) {
      if (!panel.contains(e.target) && e.target !== anchor) {
        panel.remove();
        document.removeEventListener("mousedown", onDoc);
      }
    }
    setTimeout(function () { document.addEventListener("mousedown", onDoc); }, 0);
    return panel;
  }

  function position(panel, anchor) {
    var r = anchor.getBoundingClientRect();
    panel.style.left = Math.max(8, r.left) + "px";
    panel.style.top = (r.bottom + 6) + "px";
    panel.style.width = Math.max(360, r.width) + "px";
  }

  function bind(input) {
    if (!input || input._jnGsBound) return;
    input._jnGsBound = true;
    input.addEventListener("keydown", function (e) {
      if (e.isComposing || e.keyCode === 229) return;
      if (e.key === "Enter") {
        e.preventDefault();
        openPanel(input, input.value);
      } else if (e.key === "Escape") {
        var p = document.getElementById("jn-global-search-panel");
        if (p) p.remove();
      }
    });
    input.addEventListener("input", function () {
      /* debounce light */
      clearTimeout(input._gsTimer);
      input._gsTimer = setTimeout(function () {
        if (input.value.trim().length >= 2) openPanel(input, input.value);
      }, 280);
    });
  }

  global.JnGlobalSearch = { search: search, openPanel: openPanel, bind: bind, CATEGORIES: CATEGORIES };
})(window);
