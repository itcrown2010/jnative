// Author: 张晓达
/**
 * Business selectors - search, keyboard, clear, confirm, loading/empty/error.
 * Distinguishes request failure vs empty results; ignores stale/closed responses.
 */
(function (global) {
  function openPicker(opts) {
    var state = {
      q: "",
      page: 1,
      pageSize: 8,
      selected: null,
      loading: true,
      rows: [],
      total: 0,
      error: null,
      errorKind: null
    };
    var closed = false;
    var loadSeq = 0;
    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop";
    backdrop.setAttribute("data-picker", opts.kind || "picker");

    function collectionName() {
      return opts.collection || null;
    }

    function close() {
      closed = true;
      loadSeq++; /* invalidate in-flight handlers */
      if (backdrop && backdrop.parentNode) backdrop.remove();
    }

    function alive(seq) {
      return !closed && seq === loadSeq && backdrop.parentNode;
    }

    function paint() {
      if (closed || !backdrop.parentNode) return;
      var body = backdrop.querySelector("[data-body]");
      if (!body) return;
      if (state.loading) {
        body.innerHTML = '<div class="jn-loading">' + JnUI.esc((window.JnI18n && JnI18n.t("common.loading", null, null)) || "加载中…") + "</div>";
        return;
      }
      if (state.error) {
        body.innerHTML =
          '<div class="jn-error" role="alert">' + JnUI.esc(state.error) + "</div>" +
          '<div class="jn-muted" style="margin-top:6px;font-size:12px">请求失败，不是「无查询结果」。请重试或检查服务端。</div>' +
          '<div class="jn-toolbar" style="margin-top:8px">' +
          '<button type="button" class="jn-btn primary" data-retry>重试</button></div>';
        var rb = body.querySelector("[data-retry]");
        if (rb) rb.onclick = function () { load({ forceRefresh: true }); };
        return;
      }
      if (!state.rows.length) {
        body.innerHTML =
          '<div class="jn-empty">' +
          JnUI.esc((window.JnI18n && JnI18n.t("common.empty", null, null)) || "无匹配结果") +
          "</div>" +
          '<div class="jn-muted" style="margin-top:6px;font-size:12px">当前查询无数据（请求已成功）。</div>' +
          '<div class="jn-toolbar" style="margin-top:8px">' +
          '<button type="button" class="jn-btn" data-retry>刷新</button></div>';
        var refresh = body.querySelector("[data-retry]");
        if (refresh) refresh.onclick = function () { load({ forceRefresh: true }); };
        return;
      }
      var cols = opts.columns || [];
      var head = cols.map(function (c) { return "<th>" + JnUI.esc(c.label) + "</th>"; }).join("");
      var rows = state.rows.map(function (r, idx) {
        var sel = state.selected && String(state.selected.id) === String(r.id);
        var cells = cols.map(function (c) {
          var v = r[c.key];
          if (c.type === "money") return '<td class="jn-money">' + JnUI.esc(JnDocumentCalc.formatCents(v)) + "</td>";
          if (c.type === "qty") return '<td class="jn-qty">' + JnUI.esc(v) + "</td>";
          return "<td>" + JnUI.esc(v) + "</td>";
        }).join("");
        return '<tr tabindex="0" data-idx="' + idx + '" class="' + (sel ? "selected" : "") + '">' + cells + "</tr>";
      }).join("");
      body.innerHTML =
        '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + rows + "</tbody></table></div>" +
        '<div class="jn-pagination"><span>' + state.rows.length + " / " + state.total + "</span>" +
        '<span class="spacer" style="flex:1"></span>' +
        '<button type="button" class="jn-btn" data-prev ' + (state.page <= 1 ? "disabled" : "") + ">上一页</button>" +
        '<button type="button" class="jn-btn" data-next ' + (state.page * state.pageSize >= state.total ? "disabled" : "") + ">下一页</button></div>";
      bindRows();
    }

    function bindRows() {
      var body = backdrop.querySelector("[data-body]");
      if (!body) return;
      body.querySelectorAll("tr[data-idx]").forEach(function (tr) {
        tr.addEventListener("click", function () {
          if (closed) return;
          state.selected = state.rows[Number(tr.getAttribute("data-idx"))] || null;
          paint();
        });
        tr.addEventListener("dblclick", function () {
          if (closed) return;
          state.selected = state.rows[Number(tr.getAttribute("data-idx"))] || null;
          confirm();
        });
      });
      var prev = body.querySelector("[data-prev]");
      var next = body.querySelector("[data-next]");
      if (prev) {
        prev.onclick = function () {
          if (state.page <= 1) return;
          state.page--;
          state.selected = null;
          load();
        };
      }
      if (next) {
        next.onclick = function () {
          if (state.page * state.pageSize >= state.total) return;
          state.page++;
          state.selected = null;
          load();
        };
      }
    }

    function load(loadOpts) {
      loadOpts = loadOpts || {};
      if (closed) return;
      state.loading = true;
      state.error = null;
      state.errorKind = null;
      if (loadOpts.clearSelection) state.selected = null;
      paint();
      var seq = ++loadSeq;
      var qAtStart = state.q;
      var pageAtStart = state.page;
      var coll = collectionName();
      if (loadOpts.forceRefresh && coll && window.JnData && JnData.active && JnData.active.invalidate) {
        JnData.active.invalidate(coll);
      }
      var loadArg = Object.assign({}, state, {
        forceRefresh: !!loadOpts.forceRefresh
      });
      opts.load(loadArg).then(function (res) {
        if (!alive(seq)) return;
        if (state.q !== qAtStart || state.page !== pageAtStart) return;
        if (!res || !Array.isArray(res.rows)) {
          state.rows = [];
          state.total = 0;
          state.selected = null;
          state.loading = false;
          state.error = "集合响应格式错误（期望 { rows: [] }）";
          state.errorKind = "INVALID_COLLECTION";
          paint();
          return;
        }
        state.rows = res.rows;
        state.total = res.total != null ? res.total : res.rows.length;
        state.loading = false;
        state.error = null;
        state.errorKind = null;
        if (state.selected) {
          var still = state.rows.some(function (r) { return String(r.id) === String(state.selected.id); });
          if (!still) state.selected = null;
        }
        if (state.rows.length && !state.selected) state.selected = state.rows[0];
        paint();
        var search = backdrop.querySelector("[data-search]");
        if (search && !closed) search.focus();
      }).catch(function (err) {
        if (!alive(seq)) return;
        state.rows = [];
        state.total = 0;
        state.selected = null;
        state.loading = false;
        state.error = (err && err.message) ? err.message : "加载失败";
        state.errorKind = (err && err.code) || "LOAD_FAILED";
        paint();
        if (window.JnUI && JnUI.toast) {
          JnUI.toast(state.error + (err && err.retryable ? "（可重试）" : ""), "err");
        }
      }).finally(function () {
        if (!alive(seq)) return;
        state.loading = false;
      });
    }

    function confirm() {
      if (closed) return;
      if (state.error) {
        JnUI.toast("当前为错误状态，请先重试加载", "err");
        return;
      }
      if (!state.selected) {
        JnUI.toast((window.JnI18n && JnI18n.t("doc.selectRow", null, null)) || "请先选择一行", "err");
        return;
      }
      var ok = state.rows.some(function (r) { return String(r.id) === String(state.selected.id); });
      if (!ok) {
        JnUI.toast("所选行已不在当前结果中，请重新选择", "err");
        state.selected = null;
        paint();
        return;
      }
      if (opts.onSelect) opts.onSelect(state.selected);
      close();
    }

    backdrop.innerHTML =
      '<div class="jn-modal" role="dialog" style="width:min(860px,96vw)">' +
      '<div class="jn-modal-header"><strong>' + JnUI.esc(opts.title) + "</strong>" +
      '<span class="spacer" style="flex:1"></span><button type="button" class="jn-icon-btn" data-close>×</button></div>' +
      '<div class="jn-modal-body">' +
      '<div class="jn-toolbar"><input class="jn-input" data-search placeholder="搜索…" style="max-width:320px" />' +
      '<button type="button" class="jn-btn" data-clear>清除</button>' +
      '<button type="button" class="jn-btn" data-reload>刷新</button></div>' +
      '<div data-body></div>' +
      '<div class="jn-muted" style="margin-top:8px;font-size:12px">数据来源：服务端 RPC（失败不缓存）</div></div>' +
      '<div class="jn-modal-footer">' +
      '<button type="button" class="jn-btn" data-close>取消</button>' +
      '<button type="button" class="jn-btn primary" data-ok>选择</button></div></div>';

    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop || e.target.getAttribute("data-close") != null) close();
    });
    backdrop.querySelector("[data-ok]").onclick = confirm;
    backdrop.querySelector("[data-clear]").onclick = function () {
      state.q = "";
      state.page = 1;
      state.selected = null;
      backdrop.querySelector("[data-search]").value = "";
      load({ clearSelection: true });
    };
    var reloadBtn = backdrop.querySelector("[data-reload]");
    if (reloadBtn) {
      reloadBtn.onclick = function () { load({ forceRefresh: true, clearSelection: true }); };
    }
    var search = backdrop.querySelector("[data-search]");
    search.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        state.q = search.value;
        state.page = 1;
        state.selected = null;
        load({ clearSelection: true });
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
    load({ forceRefresh: true });
    return backdrop;
  }

  function listWithRefresh(collection, st) {
    return JnData.active.list(collection, {
      q: st.q,
      page: st.page,
      pageSize: st.pageSize,
      forceRefresh: !!st.forceRefresh
    });
  }

  function openItemSelector(onSelect) {
    return openPicker({
      kind: "item",
      collection: "items",
      title: "商品选择",
      columns: [
        { key: "sku", label: "编码" },
        { key: "name", label: "名称" },
        { key: "category", label: "规格/分类" },
        { key: "uom", label: "单位" },
        { key: "warehouse", label: "仓库" },
        { key: "onHand", label: "库存", type: "qty" },
        { key: "price", label: "参考价", type: "money" }
      ],
      load: function (st) {
        return listWithRefresh("items", st).then(function (res) {
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
      collection: collection,
      title: kind === "SUPPLIER" ? "供应商选择" : "客户选择",
      columns: [
        { key: "code", label: "编码" },
        { key: "name", label: "名称" },
        { key: "city", label: "城市" },
        { key: "email", label: "邮箱" },
        { key: "status", label: "状态" }
      ],
      load: function (st) { return listWithRefresh(collection, st); },
      onSelect: onSelect
    });
  }

  function openWarehouseSelector(onSelect) {
    return openPicker({
      kind: "warehouse",
      collection: "warehouses",
      title: "仓库选择",
      columns: [
        { key: "code", label: "编码" },
        { key: "name", label: "名称" },
        { key: "city", label: "城市" },
        { key: "status", label: "状态" }
      ],
      load: function (st) { return listWithRefresh("warehouses", st); },
      onSelect: onSelect
    });
  }

  function openEmployeeSelector(onSelect) {
    return openPicker({
      kind: "employee",
      collection: "employees",
      title: "员工选择",
      columns: [
        { key: "code", label: "编码" },
        { key: "name", label: "名称" },
        { key: "department", label: "部门" },
        { key: "title", label: "职务" }
      ],
      load: function (st) { return listWithRefresh("employees", st); },
      onSelect: onSelect
    });
  }

  function openAccountSelector(onSelect) {
    return openPicker({
      kind: "account",
      collection: "accounts",
      title: "科目选择",
      columns: [
        { key: "code", label: "编码" },
        { key: "name", label: "名称" },
        { key: "type", label: "类型" },
        { key: "balance", label: "余额", type: "money" }
      ],
      load: function (st) {
        return listWithRefresh("accounts", st).then(function (res) {
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

  function openContactSelector(partnerId, onSelect) {
    return openPicker({
      kind: "contact",
      title: "联系人选择",
      columns: [
        { key: "name", label: "名称" },
        { key: "title", label: "职务" }
      ],
      load: function (st) {
        if (!partnerId) {
          return Promise.resolve({ rows: [], total: 0 });
        }
        return fetch("/api/erp/partners/" + encodeURIComponent(partnerId) + "/contacts", {
          credentials: "same-origin",
          headers: { Accept: "application/json" }
        }).then(function (res) {
          return res.text().then(function (text) {
            if (!res.ok) {
              var err = new Error("联系人加载失败（HTTP " + res.status + "）");
              err.retryable = true;
              throw err;
            }
            if (!text || !String(text).trim()) {
              var emptyErr = new Error("响应格式错误：空响应");
              emptyErr.retryable = true;
              throw emptyErr;
            }
            var arr;
            try {
              arr = JSON.parse(text);
            } catch (e) {
              var parseErr = new Error("响应格式错误：不是合法 JSON");
              parseErr.retryable = true;
              throw parseErr;
            }
            if (!Array.isArray(arr)) {
              var shapeErr = new Error("集合响应格式错误（期望数组）");
              shapeErr.retryable = true;
              throw shapeErr;
            }
            var rows = arr.filter(function (r) {
              if (!st.q) return true;
              return JSON.stringify(r).toLowerCase().indexOf(String(st.q).toLowerCase()) >= 0;
            });
            var start = ((st.page || 1) - 1) * (st.pageSize || 8);
            return { rows: rows.slice(start, start + (st.pageSize || 8)), total: rows.length };
          });
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
    openAccountSelector: openAccountSelector,
    openContactSelector: openContactSelector
  };
})(window);
