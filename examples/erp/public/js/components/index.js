// Author: 张晓达
/**
 * UI 1.1 component builders - Design System / Component First.
 * Used by templates; keeps markup consistent.
 */
(function (global) {
  var C = {
    button: function (label, opts) {
      opts = opts || {};
      return '<button type="button" class="jn-btn' + (opts.primary ? " primary" : "") +
        (opts.danger ? " danger" : "") + '"' +
        (opts.action ? ' data-action="' + JnUI.esc(opts.action) + '"' : "") +
        (opts.go ? ' data-go="' + JnUI.esc(opts.go) + '"' : "") +
        (opts.tip ? ' data-tip="' + JnUI.esc(opts.tip) + '" class="jn-btn jn-tooltip' + (opts.primary ? " primary" : "") + '"' : "") +
        ">" + JnUI.esc(label) + "</button>";
    },
    textInput: function (name, value, opts) {
      opts = opts || {};
      return '<input class="jn-input" data-field="' + JnUI.esc(name) + '" type="' + (opts.type || "text") +
        '" value="' + JnUI.esc(value == null ? "" : value) + '"' + (opts.placeholder ? ' placeholder="' + JnUI.esc(opts.placeholder) + '"' : "") + " />";
    },
    numberInput: function (name, value) {
      return C.textInput(name, value, { type: "number" });
    },
    moneyInput: function (name, value) {
      return C.textInput(name, value, { type: "number" });
    },
    datePicker: function (name, value) {
      return C.textInput(name, value, { type: "date" });
    },
    checkbox: function (name, checked, label) {
      return '<label class="jn-checkbox-row"><input type="checkbox" data-field="' + JnUI.esc(name) + '"' +
        (checked ? " checked" : "") + " /> " + JnUI.esc(label || name) + "</label>";
    },
    badge: function (text, cls) {
      return '<span class="jn-status ' + JnUI.esc(cls || "") + '">' + JnUI.esc(text) + "</span>";
    },
    documentStatus: function (status) {
      return JnUI.statusHtml(status);
    },
    documentNumber: function (docNo) {
      return '<strong class="jn-docno">' + JnUI.esc(docNo || "(new)") + "</strong>";
    },
    filterBar: function () {
      return '<div class="jn-filter-bar"><div class="jn-field"><label>Search</label><input class="jn-input" data-role="search" /></div></div>';
    },
    empty: function (msg) {
      return '<div class="jn-empty">' + JnUI.esc(msg || "No data") + "</div>";
    },
    loading: function () {
      return '<div class="jn-loading">Loading...</div>';
    },
    error: function (msg) {
      return '<div class="jn-error">' + JnUI.esc(msg || "Error") + "</div>";
    }
  };
  global.JnComponents = C;
})(window);
