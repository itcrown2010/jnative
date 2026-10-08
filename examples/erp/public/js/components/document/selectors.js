/**
 * Business selectors - search, keyboard, clear, confirm, loading/empty.
 */
(function (global) {
  function openPicker(opts) {
    var state = { q: "", page: 1, pageSize: 8, selected: null, loading: true, rows: [], total: 0 };
    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop";
    backdrop.setAttribute("data-picker", opts.kind || "picker");

    function close() { backdrop.remove(); }
    function paint() {
      var body = backdrop.querySelector("[data-body]");
      if (!body) return;
      if (state.loading) {
        body.innerHTML = '<div class="jn-loading">Loading...</div>';
        return;
      }
      if (!state.rows.length) {
        body.innerHTML = '<div class="jn-empty">No results</div>';
        return;
      }
      var cols = opts.columns || [];
      var head = cols.map(function (c) { return "<th>" + JnUI.esc(c.label) + "</th>"; }).join("");
      var rows = state.rows.map(function (r, idx) {
        var sel = state.selected && state.selected.id === r.id;
        var cells = cols.map(function (c) {
          var v = r[c.key];
          /* ItemSelector normalizes ref price to integer cents already */
          if (c.type === "money") return '<td class="jn-money">' + JnUI.esc(JnDocumentCalc.formatCents(v)) + "</td>";
          if (c.type === "qty") return '<td class="jn-qty">' + JnUI.esc(v) + "</td>";
          return "<td>" + JnUI.esc(v) + "</td>";
        }).join("");
        return '<tr tabindex="0" data-idx="' + idx + '" class="' + (sel ? "selected" : "") + '">' + cells + "</tr>";
      }).join("");
      body.innerHTML =
        '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + rows + "</tbody></table></div>" +
        '<div class="jn-pagination"><span>' + state.rows.length + " / " + state.total + ' (DEMO)</span>' +
        '<span class="spacer" style="flex:1"></span>' +
        '<button type="button" class="jn-btn" data-prev ' + (state.page <= 1 ? "disabled" : "") + ">Prev</button>" +
        '<button type="button" class="jn-btn" data-next ' + (state.page * state.pageSize >= state.total ? "disabled" : "") + ">Next</button></div>";
      bindRows();
    }

    function bindRows() {
      var body = backdrop.querySelector("[data-body]");
      body.querySelectorAll("tr[data-idx]").forEach(function (tr) {
        tr.addEventListener("click", function () {
          state.selected = state.rows[Number(tr.getAttribute("data-idx"))];
          paint();
        });
        tr.addEventListener("dblclick", function () {
          state.selected = state.rows[Number(tr.getAttribute("data-idx"))];
          confirm();
        });
      });
      var prev = body.querySelector("[data-prev]");
      var next = body.querySelector("[data-next]");
      if (prev) prev.onclick = function () { state.page--; load(); };
      if (next) next.onclick = function () { state.page++; load(); };
    }

    function load() {
      state.loading = true;
      paint();
      opts.load(state).then(function (res) {
        state.rows = res.rows || [];
        state.total = res.total || 0;
        state.loading = false;
        if (state.rows.length && !state.selected) state.selected = state.rows[0];
        paint();
        var search = backdrop.querySelector("[data-search]");
        if (search) search.focus();
      });
    }

    function confirm() {
      if (!state.selected) {
        JnUI.toast("Select a row", "err");
        return;
      }
      if (opts.onSelect) opts.onSelect(state.selected);
      close();
    }

    backdrop.innerHTML =
      '<div class="jn-modal" role="dialog" style="width:min(860px,96vw)">' +
      '<div class="jn-modal-header"><strong>' + JnUI.esc(opts.title) + "</strong>" +
      '<span class="spacer" style="flex:1"></span><button type="button" class="jn-icon-btn" data-close>x</button></div>' +
      '<div class="jn-modal-body">' +
      '<div class="jn-toolbar"><input class="jn-input" data-search placeholder="Search (DEMO)..." style="max-width:320px" />' +
      '<button type="button" class="jn-btn" data-clear>Clear</button></div>' +
      '<div data-body></div>' +
      '<div class="jn-demo-save-note">Selector uses MockDataProvider DEMO DATA.</div></div>' +
      '<div class="jn-modal-footer">' +
      '<button type="button" class="jn-btn" data-close>Cancel</button>' +
      '<button type="button" class="jn-btn primary" data-ok>Select</button></div></div>';

    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop || e.target.getAttribute("data-close") != null) close();
    });
    backdrop.querySelector("[data-ok]").onclick = confirm;
    backdrop.querySelector("[data-clear]").onclick = function () {
      state.q = "";
      state.page = 1;
      backdrop.querySelector("[data-search]").value = "";
      load();
    };
    var search = backdrop.querySelector("[data-search]");
    search.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        state.q = search.value;
        state.page = 1;
        load();
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        var tbody = backdrop.querySelector("tbody");
        if (tbody && tbody.rows[0]) tbody.rows[0].focus();
      } else if (e.key === "Escape") {
        close();
      }
    });
    backdrop.addEventListener("keydown", function (e) {
      if (e.key === "Escape") close();
      var t = e.target;
      var tr = (t && typeof t.closest === "function") ? t.closest("tr[data-idx]") : null;
      if (!tr) return;
      var idx = Number(tr.getAttribute("data-idx"));
      if (e.key === "ArrowDown") {
        e.preventDefault();
        var next = tr.parentNode.rows[idx + 1];
        if (next) { next.focus(); state.selected = state.rows[idx + 1]; }
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        var prev = tr.parentNode.rows[idx - 1];
        if (prev) { prev.focus(); state.selected = state.rows[idx - 1]; }
        else search.focus();
      } else if (e.key === "Enter") {
        e.preventDefault();
        state.selected = state.rows[idx];
        confirm();
      }
    });

    document.body.appendChild(backdrop);
    load();
    return backdrop;
  }

  function openItemSelector(onSelect) {
    return openPicker({
      kind: "item",
      title: "Item Selector",
      columns: [
        { key: "sku", label: "Code" },
        { key: "name", label: "Name" },
        { key: "category", label: "Spec/Category" },
        { key: "uom", label: "UoM" },
        { key: "warehouse", label: "Warehouse" },
        { key: "onHand", label: "Stock", type: "qty" },
        { key: "price", label: "Ref Price", type: "money" }
      ],
      load: function (st) {
        return JnData.active.list("items", { q: st.q, page: st.page, pageSize: st.pageSize }).then(function (res) {
          /* normalize price to cents display via parse */
          res.rows = (res.rows || []).map(function (r) {
            var x = Object.assign({}, r);
            x.price = Math.round(Number(r.price || 0) * 100);
            return x;
          });
          return res;
        });
      },
      onSelect: onSelect
    });
  }

  function openPartnerSelector(kind, onSelect) {
    var collection = kind === "SUPPLIER" ? "suppliers" : "customers";
    return openPicker({
      kind: "partner",
      title: kind === "SUPPLIER" ? "Supplier Selector" : "Customer Selector",
      columns: [
        { key: "code", label: "Code" },
        { key: "name", label: "Name" },
        { key: "city", label: "City" },
        { key: "email", label: "Email" },
        { key: "status", label: "Status" }
      ],
      load: function (st) {
        return JnData.active.list(collection, { q: st.q, page: st.page, pageSize: st.pageSize });
      },
      onSelect: onSelect
    });
  }

  function openWarehouseSelector(onSelect) {
    return openPicker({
      kind: "warehouse",
      title: "Warehouse Selector",
      columns: [
        { key: "code", label: "Code" },
        { key: "name", label: "Name" },
        { key: "city", label: "City" },
        { key: "status", label: "Status" }
      ],
      load: function (st) {
        return JnData.active.list("warehouses", { q: st.q, page: st.page, pageSize: st.pageSize });
      },
      onSelect: onSelect
    });
  }

  function openEmployeeSelector(onSelect) {
    return openPicker({
      kind: "employee",
      title: "Employee Selector",
      columns: [
        { key: "code", label: "Code" },
        { key: "name", label: "Name" },
        { key: "department", label: "Dept" },
        { key: "title", label: "Title" }
      ],
      load: function (st) {
        return JnData.active.list("employees", { q: st.q, page: st.page, pageSize: st.pageSize });
      },
      onSelect: onSelect
    });
  }

  function openAccountSelector(onSelect) {
    return openPicker({
      kind: "account",
      title: "Account Selector",
      columns: [
        { key: "code", label: "Code" },
        { key: "name", label: "Name" },
        { key: "type", label: "Type" },
        { key: "balance", label: "Balance", type: "money" }
      ],
      load: function (st) {
        return JnData.active.list("accounts", { q: st.q, page: st.page, pageSize: st.pageSize }).then(function (res) {
          res.rows = (res.rows || []).map(function (r) {
            var x = Object.assign({}, r);
            x.balance = Math.round(Number(r.balance || 0) * 100);
            return x;
          });
          return res;
        });
      },
      onSelect: onSelect
    });
  }

  global.JnSelectors = {
    openItemSelector: openItemSelector,
    openPartnerSelector: openPartnerSelector,
    openCustomerSelector: function (onSelect) { return openPartnerSelector("CUSTOMER", onSelect); },
    openSupplierSelector: function (onSelect) { return openPartnerSelector("SUPPLIER", onSelect); },
    openWarehouseSelector: openWarehouseSelector,
    openEmployeeSelector: openEmployeeSelector,
    openAccountSelector: openAccountSelector
  };
})(window);
