/**
 * GlobalSearch - disabled until a real server search API is wired.
 */
(function (global) {
  function bind(input) {
    if (!input || input._jnGsBound) return;
    input._jnGsBound = true;
    input.disabled = true;
    var msg = window.JnI18n ? JnI18n.t("common.searchUnavailable") : "Global search is not available yet";
    input.placeholder = msg;
    input.title = msg;
  }

  global.JnGlobalSearch = {
    search: function () { return Promise.resolve([]); },
    openPanel: function () {},
    bind: bind,
    CATEGORIES: []
  };
})(window);
