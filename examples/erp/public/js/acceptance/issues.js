/**
 * UIIssue tracker for acceptance (UI 1.6).
 */
(function (global) {
  var issues = [];

  function add(issue) {
    var row = {
      id: issue.id || ("UI-" + (issues.length + 1)),
      page: issue.page || "",
      severity: issue.severity || "P2",
      category: issue.category || "general",
      description: issue.description || "",
      reproductionSteps: issue.reproductionSteps || [],
      expectedResult: issue.expectedResult || "",
      actualResult: issue.actualResult || "",
      status: issue.status || "OPEN"
    };
    issues.push(row);
    return row;
  }

  function bySeverity() {
    return issues.reduce(function (a, i) {
      a[i.severity] = (a[i.severity] || 0) + 1;
      return a;
    }, {});
  }

  function openP0P1() {
    return issues.filter(function (i) {
      return i.status !== "FIXED" && i.status !== "WONTFIX" && (i.severity === "P0" || i.severity === "P1");
    });
  }

  global.JnUIIssues = {
    add: add,
    list: function () { return issues.slice(); },
    clear: function () { issues = []; },
    bySeverity: bySeverity,
    openP0P1: openP0P1,
    markFixed: function (id) {
      issues.forEach(function (i) { if (i.id === id) i.status = "FIXED"; });
    }
  };
})(window);