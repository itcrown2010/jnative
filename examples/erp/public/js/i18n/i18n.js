// Author: 张晓达
/**
 * JnI18n - lightweight locale module for ERP public SPA.
 * Keys are semantic; business codes/routes stay English.
 */
(function (global) {
  var STORAGE_KEY = "jn_erp_locale";
  var DEFAULT_LOCALE = "zh-CN";
  var SUPPORTED = { "zh-CN": 1, en: 1 };
  var catalogs = {};
  var locale = DEFAULT_LOCALE;
  var missing = {};

  function loadStored() {
    try {
      var v = localStorage.getItem(STORAGE_KEY);
      if (v && SUPPORTED[v]) return v;
    } catch (e) {}
    return DEFAULT_LOCALE;
  }

  function applyHtmlLang() {
    try {
      document.documentElement.lang = locale === "zh-CN" ? "zh-CN" : "en";
      var title = t("app.title");
      if (title) document.title = title;
    } catch (e) {}
  }

  function interpolate(str, params) {
    if (!params || str == null) return str;
    return String(str).replace(/\{(\w+)\}/g, function (_, k) {
      return params[k] != null ? String(params[k]) : "{" + k + "}";
    });
  }

  function t(key, params, fallback) {
    if (!key) return fallback != null ? fallback : "";
    var cat = catalogs[locale] || {};
    var en = catalogs.en || {};
    var raw = cat[key];
    if (raw == null) raw = en[key];
    if (raw == null) {
      if (!missing[key]) {
        missing[key] = 1;
        if (typeof console !== "undefined" && console.warn) {
          console.warn("[JnI18n] missing key:", key);
        }
      }
      raw = fallback != null ? fallback : key;
    }
    return interpolate(raw, params);
  }

  function register(loc, dict) {
    catalogs[loc] = dict || {};
  }

  /** Merge extra keys into a locale catalog (used by feature modules such as JnB1). */
  function extend(loc, dict) {
    var cat = catalogs[loc] || (catalogs[loc] = {});
    var k;
    for (k in (dict || {})) {
      if (Object.prototype.hasOwnProperty.call(dict, k)) cat[k] = dict[k];
    }
  }

  function setLocale(next) {
    if (!SUPPORTED[next]) next = DEFAULT_LOCALE;
    if (next === locale) {
      applyHtmlLang();
      return locale;
    }
    locale = next;
    try { localStorage.setItem(STORAGE_KEY, locale); } catch (e) {}
    applyHtmlLang();
    try {
      document.dispatchEvent(new CustomEvent("jn:localechange", { detail: { locale: locale } }));
    } catch (e) {
      var ev = document.createEvent("Event");
      ev.initEvent("jn:localechange", true, true);
      document.dispatchEvent(ev);
    }
    return locale;
  }

  function getLocale() { return locale; }

  function statusLabel(code) {
    if (code == null || code === "") return "";
    var k = "status." + String(code).toLowerCase();
    var mapped = t(k, null, null);
    if (mapped === k || mapped == null) return String(code);
    return mapped;
  }

  function pageTitle(page) {
    if (!page) return "";
    return t("page." + page.pageId, null, page.title || page.pageId);
  }

  function moduleTitle(moduleOrPage) {
    var name = typeof moduleOrPage === "string" ? moduleOrPage : (moduleOrPage && moduleOrPage.module);
    if (!name) return "";
    var slug = String(name).toLowerCase();
    var keyDot = "module." + slug.replace(/\s+/g, ".");
    var keySpace = "module." + slug;
    var cat = catalogs[locale] || {};
    var en = catalogs.en || {};
    var raw = cat[keyDot];
    if (raw == null) raw = en[keyDot];
    if (raw == null) raw = cat[keySpace];
    if (raw == null) raw = en[keySpace];
    if (raw == null) return name;
    return String(raw);
  }

  function colTitle(col) {
    if (!col) return "";
    if (col.titleKey) return t(col.titleKey, null, col.title || col.key);
    return t("col." + col.key, null, col.title || col.key);
  }

  function langToggleHtml(extraClass) {
    var zhActive = locale === "zh-CN" ? " active" : "";
    var enActive = locale === "en" ? " active" : "";
    return (
      '<div class="jn-lang-toggle' + (extraClass ? " " + extraClass : "") + '" role="group" aria-label="' +
      escAttr(t("lang.switch")) + '">' +
      '<button type="button" class="jn-btn jn-lang-btn' + zhActive + '" data-locale="zh-CN">\u4e2d\u6587</button>' +
      '<button type="button" class="jn-btn jn-lang-btn' + enActive + '" data-locale="en">English</button>' +
      "</div>"
    );
  }

  function catalogSize(loc) {
    var c = catalogs[loc];
    if (!c) return 0;
    var n = 0;
    for (var k in c) {
      if (Object.prototype.hasOwnProperty.call(c, k)) n = n + 1;
    }
    return n;
  }

  function bindLangToggle(root, onChange) {
    if (!root) return;
    root.querySelectorAll("[data-locale]").forEach(function (btn) {
      btn.onclick = function () {
        setLocale(btn.getAttribute("data-locale"));
        if (onChange) onChange(getLocale());
      };
    });
  }

  function escAttr(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function formatMoney(amount, currency) {
    var n = Number(amount);
    if (isNaN(n)) n = 0;
    var cur = currency || "USD";
    try {
      return new Intl.NumberFormat(locale === "zh-CN" ? "zh-CN" : "en-US", {
        style: "currency",
        currency: cur,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(n);
    } catch (e) {
      return cur + " " + n.toFixed(2);
    }
  }

  function formatNumber(n, opts) {
    opts = opts || {};
    try {
      return new Intl.NumberFormat(locale === "zh-CN" ? "zh-CN" : "en-US", opts).format(Number(n) || 0);
    } catch (e) {
      return String(Number(n) || 0);
    }
  }

  function formatDate(msOrStr) {
    if (msOrStr == null || msOrStr === "") return "";
    var d = msOrStr instanceof Date ? msOrStr : new Date(msOrStr);
    if (isNaN(d.getTime())) return String(msOrStr);
    try {
      return new Intl.DateTimeFormat(locale === "zh-CN" ? "zh-CN" : "en-US", {
        year: "numeric", month: "2-digit", day: "2-digit"
      }).format(d);
    } catch (e) {
      return d.toISOString().slice(0, 10);
    }
  }

  locale = loadStored();

  global.JnI18n = {
    STORAGE_KEY: STORAGE_KEY,
    register: register,
    extend: extend,
    t: t,
    setLocale: setLocale,
    getLocale: getLocale,
    statusLabel: statusLabel,
    pageTitle: pageTitle,
    moduleTitle: moduleTitle,
    colTitle: colTitle,
    langToggleHtml: langToggleHtml,
    bindLangToggle: bindLangToggle,
    formatMoney: formatMoney,
    formatNumber: formatNumber,
    formatDate: formatDate,
    applyHtmlLang: applyHtmlLang,
    catalogSize: catalogSize,
    missingKeys: function () { return Object.keys(missing); },
    ensureReady: function () {
      if (catalogSize("en") === 0 && catalogSize("zh-CN") === 0) {
        return 0;
      }
      applyHtmlLang();
      return 1;
    }
  };
})(window);
