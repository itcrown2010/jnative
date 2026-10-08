/**
 * JNativeOneTheme + density (UI 1.5).
 * Light theme default; dark tokens reserved on [data-theme=dark].
 */
(function (global) {
  var THEME_KEY = "jn_ui_theme_v15";
  var DENSITY_KEY = "jn_ui_density_v15";

  function apply() {
    var theme = localStorage.getItem(THEME_KEY) || "light";
    var density = localStorage.getItem(DENSITY_KEY) || "comfortable";
    document.documentElement.setAttribute("data-theme", theme);
    document.documentElement.setAttribute("data-density", density);
    document.body && document.body.setAttribute("data-theme", theme);
    document.body && document.body.setAttribute("data-density", density);
    return { theme: theme, density: density };
  }

  function setTheme(theme) {
    localStorage.setItem(THEME_KEY, theme === "dark" ? "dark" : "light");
    return apply();
  }

  function setDensity(density) {
    localStorage.setItem(DENSITY_KEY, density === "compact" ? "compact" : "comfortable");
    return apply();
  }

  function toggleDensity() {
    var cur = localStorage.getItem(DENSITY_KEY) || "comfortable";
    return setDensity(cur === "compact" ? "comfortable" : "compact");
  }

  global.JnTheme = {
    apply: apply,
    setTheme: setTheme,
    setDensity: setDensity,
    toggleDensity: toggleDensity,
    get: function () {
      return {
        theme: localStorage.getItem(THEME_KEY) || "light",
        density: localStorage.getItem(DENSITY_KEY) || "comfortable"
      };
    }
  };

  apply();
})(window);
