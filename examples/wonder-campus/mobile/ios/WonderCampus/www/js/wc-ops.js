/* 运营：课程班级/员工/商品、规则设置、公告、经营报表、课酬、排课日历、课后评价。 */
(function () {
  var WC = window.WC = window.WC || {};
  var K = WC.kit;
  function core() { return WC.core; }
  function params() { return core().state.params || {}; }
  function ident() { return core().state.me.identity; }
  function isAdmin() { return ident() === "ADMIN"; }
  var DAY = 86400000;

  function opts(items, vf, tf, blank) {
    var o = items.map(function (x) { return { v: vf(x), t: tf(x) }; });
    return blank ? [blank].concat(o) : o;
  }
  function post(url, body, okMsg, after) {
    K.submit(function () {
      return K.api(url, { method: "POST", body: body }).then(function (j) {
        if (okMsg) core().toast(okMsg);
        if (after) after(j);
      });
    });
  }
  function addBox(title, inner) {
    return '<details class="add"><summary>＋ ' + K.esc(title) + "</summary><div class=\"card\">" + inner + "</div></details>";
  }

  /* ================= 课程班级（机构管理） ================= */
  var orgTab = "courses";
  WC.pages.org = function () {
    if (params().tab) orgTab = params().tab;
    var tabs = [{ k: "courses", t: "课程" }, { k: "classes", t: "班级" }, { k: "rooms", t: "教室" }, { k: "staff", t: "员工" }, { k: "products", t: "商品" }];
    core().shell(K.page("课程与机构", K.tabs(tabs, orgTab) + '<div id="orgBody">' + K.loading() + "</div>"));
    K.bind(null, { tab: function (k) { orgTab = k; core().state.params = {}; WC.pages.org(); } });
    var fn = { courses: orgCourses, classes: orgClasses, rooms: orgRooms, staff: orgStaff, products: orgProducts }[orgTab];
    fn().catch(function (e) { K.body("orgBody", K.error(e)); });
  };
  function reload() { WC.pages.org(); }

  function orgCourses() {
    return K.api("/api/wc/courses").then(function (j) {
      var items = j.items || [];
      var h = addBox("新增课程", K.field("课程名称 *", "coName") + K.field("学科", "coSubject", { placeholder: "如 美术 / 英语 / 数学" }) +
        K.field("简介", "coDesc", { type: "textarea" }) + '<button type="button" data-act="addCourse">保存</button>');
      h += items.length ? '<ul class="list">' + items.map(function (c) {
        return '<li><div class="row"><div class="title">' + K.esc(c.name) + " " + K.pill(c.subject || "—") + "</div>" +
          K.pill(c.status === "ACTIVE" ? "开设中" : "已停", c.status === "ACTIVE" ? "ok" : "bad") + "</div>" +
          (c.description ? '<div class="meta">' + K.esc(c.description) + "</div>" : "") +
          '<div class="inline-actions"><button type="button" class="mini alt" data-act="toggleCourse" data-id="' + c.id + '" data-st="' + c.status + '" data-n="' + K.esc(c.name) + '" data-s="' + K.esc(c.subject) + '" data-d="' + K.esc(c.description) + '">' +
          (c.status === "ACTIVE" ? "停用" : "启用") + "</button></div></li>";
      }).join("") + "</ul>" : K.empty("暂无课程");
      K.body("orgBody", h);
      K.bind(null, {
        addCourse: function () {
          if (!K.val("coName")) { core().toast("请填写课程名称"); return; }
          post("/api/wc/courses", { name: K.val("coName"), subject: K.val("coSubject"), description: K.val("coDesc") }, "课程已创建", reload);
        },
        toggleCourse: function (el) {
          post("/api/wc/courses", { id: parseInt(el.getAttribute("data-id"), 10), name: el.getAttribute("data-n"), subject: el.getAttribute("data-s"), description: el.getAttribute("data-d"),
            status: el.getAttribute("data-st") === "ACTIVE" ? "INACTIVE" : "ACTIVE" }, "已更新", reload);
        }
      });
    });
  }

  function orgClasses() {
    return Promise.all([K.api("/api/wc/classes"), K.api("/api/wc/courses"), K.api("/api/wc/campuses"), K.api("/api/wc/teachers")]).then(function (res) {
      var items = res[0].items || [], courses = res[1].items || [], camps = res[2].items || [], teachers = res[3].items || [];
      var h = addBox("新建班级", K.field("班级名称 *", "clName") + K.select("课程 *", "clCourse", opts(courses, function (c) { return c.id; }, function (c) { return c.name; })) +
        K.select("校区 *", "clCampus", opts(camps, function (c) { return c.id; }, function (c) { return c.name; })) +
        K.select("授课老师", "clTeacher", opts(teachers, function (t) { return t.id; }, function (t) { return t.name; }, { v: 0, t: "待定" })) +
        K.field("容量（人）", "clCap", { type: "number", value: "8" }) + '<button type="button" data-act="addClass">保存</button>');
      h += items.length ? '<ul class="list">' + items.map(function (c) {
        return '<li class="tap" data-go="classMembers" data-id="' + c.id + '"><div class="row"><div class="title">' + K.esc(c.name) + "</div>" +
          K.pill(c.memberCount + "/" + c.capacity + " 人", c.memberCount >= c.capacity ? "bad" : "") + "</div>" +
          '<div class="meta">' + K.esc(c.courseName) + " · " + K.esc(c.campusName) + " · " + (K.esc(c.teacherName) || "未指定老师") + "</div></li>";
      }).join("") + "</ul>" : K.empty("暂无班级");
      K.body("orgBody", h);
      K.bind(null, {
        addClass: function () {
          if (!K.val("clName")) { core().toast("请填写班级名称"); return; }
          post("/api/wc/classes", { name: K.val("clName"), courseId: K.num("clCourse"), campusId: K.num("clCampus"), teacherUserId: K.num("clTeacher"), capacity: K.num("clCap") || 8 }, "班级已创建", reload);
        }
      });
    });
  }

  WC.pages.classMembers = function () {
    var id = params().id;
    core().shell(K.page("班级成员", '<div id="cm">' + K.loading() + "</div>"));
    K.bind(null, {});
    function load() {
      Promise.all([K.api("/api/wc/classes/" + id + "/members"), K.api("/api/wc/admin/students?status=ACTIVE")]).then(function (res) {
        var ms = res[0].items || [], all = res[1].items || [];
        var inIds = ms.map(function (m) { return m.id; });
        var cand = all.filter(function (s) { return inIds.indexOf(s.id) < 0; });
        var h = '<ul class="list">' + (ms.length ? ms.map(function (m) {
          return '<li><div class="row"><div class="title">' + K.esc(m.name) + "</div>" +
            (isAdmin() ? '<button type="button" class="mini alt" data-act="rm" data-sid="' + m.id + '">移出</button>' : "") + "</div></li>";
        }).join("") : "<li>" + K.empty("暂无成员") + "</li>") + "</ul>";
        if (isAdmin()) {
          h += K.select("添加学员", "cmStu", opts(cand, function (s) { return s.id; }, function (s) { return s.name; }, { v: 0, t: "请选择" })) +
            '<button type="button" data-act="add">加入班级</button>';
        }
        K.body("cm", h);
        K.bind(null, {
          add: function () {
            if (!K.num("cmStu")) { core().toast("请选择学员"); return; }
            post("/api/wc/classes/" + id + "/members", { studentId: K.num("cmStu") }, "已加入", load);
          },
          rm: function (el) {
            if (!window.confirm("确认移出该学员？")) return;
            post("/api/wc/classes/" + id + "/members/remove", { studentId: parseInt(el.getAttribute("data-sid"), 10) }, "已移出", load);
          }
        });
      }).catch(function (e) { K.body("cm", K.error(e)); });
    }
    load();
  };

  function orgRooms() {
    return Promise.all([K.api("/api/wc/rooms"), K.api("/api/wc/campuses")]).then(function (res) {
      var items = res[0].items || [], camps = res[1].items || [];
      var h = addBox("新增教室", K.field("教室名称 *", "rmName") + K.select("校区 *", "rmCampus", opts(camps, function (c) { return c.id; }, function (c) { return c.name; })) +
        K.field("容量（人）", "rmCap", { type: "number", value: "8" }) + '<button type="button" data-act="addRoom">保存</button>');
      h += items.length ? '<ul class="list">' + items.map(function (r) {
        return "<li><div class=\"row\"><div class=\"title\">" + K.esc(r.name) + "</div>" + K.pill(r.capacity + " 人") + "</div><div class=\"meta\">" + K.esc(r.campusName) + "</div></li>";
      }).join("") + "</ul>" : K.empty("暂无教室");
      K.body("orgBody", h);
      K.bind(null, {
        addRoom: function () {
          if (!K.val("rmName")) { core().toast("请填写教室名称"); return; }
          post("/api/wc/rooms", { name: K.val("rmName"), campusId: K.num("rmCampus"), capacity: K.num("rmCap") || 8 }, "教室已创建", reload);
        }
      });
    });
  }

  var ROLE_LABEL = { TEACHER: "老师", ADMIN: "教务", DIRECTOR: "负责人", PARENT: "家长" };
  function orgStaff() {
    return Promise.all([K.api("/api/wc/staff"), K.api("/api/wc/campuses")]).then(function (res) {
      var items = res[0].items || [], camps = res[1].items || [];
      var h = isAdmin() ? addBox("新增员工账号",
        K.field("登录账号 *（≥3 位）", "stLogin") + K.field("姓名 *", "stName") + K.field("手机", "stPhone", { type: "tel" }) +
        K.select("角色", "stRole", [{ v: "TEACHER", t: "老师" }, { v: "ADMIN", t: "教务" }, { v: "DIRECTOR", t: "负责人" }], "TEACHER") +
        K.select("校区", "stCampus", opts(camps, function (c) { return c.id; }, function (c) { return c.name; })) +
        K.field("课酬（元/课次，老师）", "stRate", { value: "0", attrs: 'inputmode="decimal"' }) +
        K.field("初始密码 *（≥8 位，含字母和数字）", "stPw", { type: "text" }) + '<button type="button" data-act="addStaff">创建账号</button>') : "";
      h += items.length ? '<ul class="list">' + items.map(function (u) {
        var off = u.status === "DISABLED";
        return '<li><div class="row"><div class="title">' + K.esc(u.displayName) + " " + K.pill((u.identities || "").split(",").map(function (x) { return ROLE_LABEL[x] || x; }).join("/")) + "</div>" +
          K.pill(off ? "已停用" : "在职", off ? "bad" : "ok") + "</div>" +
          '<div class="meta">账号 ' + K.esc(u.login) + (u.phone ? " · " + K.esc(u.phone) : "") + (u.perSessionCents ? " · 课酬 " + K.money(u.perSessionCents) + "/次" : "") +
          (u.lastLoginAt ? " · 最近登录 " + K.dt(u.lastLoginAt) : " · 从未登录") + "</div>" +
          (isAdmin() ? '<div class="inline-actions"><button type="button" class="mini alt" data-act="stToggle" data-id="' + u.id + '" data-off="' + (off ? 1 : 0) + '" data-n="' + K.esc(u.displayName) + '" data-p="' + K.esc(u.phone) + '">' + (off ? "启用" : "停用") + "</button>" +
            '<button type="button" class="mini alt" data-act="stRate" data-id="' + u.id + '" data-n="' + K.esc(u.displayName) + '" data-p="' + K.esc(u.phone) + '" data-off="' + (off ? 1 : 0) + '">改课酬</button>' +
            '<button type="button" class="mini alt" data-act="stPw" data-id="' + u.id + '" data-n="' + K.esc(u.displayName) + '" data-p="' + K.esc(u.phone) + '" data-off="' + (off ? 1 : 0) + '">重置密码</button></div>' : "") + "</li>";
      }).join("") + "</ul>" : K.empty("暂无员工");
      K.body("orgBody", h);
      function upd(el, extra, msg) {
        var b = { displayName: el.getAttribute("data-n"), phone: el.getAttribute("data-p"), status: el.getAttribute("data-off") === "1" ? "DISABLED" : "ACTIVE" };
        for (var k in extra) b[k] = extra[k];
        post("/api/wc/staff/" + el.getAttribute("data-id"), b, msg, reload);
      }
      K.bind(null, {
        addStaff: function () {
          var rate = K.toCents(K.val("stRate") || "0");
          if (isNaN(rate)) { core().toast("课酬金额不正确"); return; }
          post("/api/wc/staff", { login: K.val("stLogin"), displayName: K.val("stName"), phone: K.val("stPhone"), identity: K.val("stRole"), campusId: K.num("stCampus"), perSessionCents: rate, password: K.val("stPw") }, "账号已创建", reload);
        },
        stToggle: function (el) {
          var off = el.getAttribute("data-off") === "1";
          if (!off && !window.confirm("停用后该员工将无法登录，确认？")) return;
          el.setAttribute("data-off", off ? "0" : "1");
          upd(el, {}, off ? "已启用" : "已停用");
        },
        stRate: function (el) {
          var v = window.prompt("课酬（元/课次）", "0");
          if (v === null) return;
          var c = K.toCents(v);
          if (isNaN(c)) { core().toast("金额不正确"); return; }
          upd(el, { perSessionCents: c }, "课酬已更新");
        },
        stPw: function (el) {
          var v = window.prompt("输入新密码（≥8 位，含字母和数字）", "");
          if (!v) return;
          upd(el, { newPassword: v }, "密码已重置");
        }
      });
    });
  }

  function orgProducts() {
    return Promise.all([K.api("/api/wc/products"), K.api("/api/wc/courses")]).then(function (res) {
      var items = res[0].items || [], courses = res[1].items || [];
      var h = addBox("新增商品",
        K.field("商品名称 *", "pdName", { placeholder: "如 美术 24 课时包" }) +
        K.select("课程 *", "pdCourse", opts(courses, function (c) { return c.id; }, function (c) { return c.name; })) +
        K.select("类型", "pdKind", [{ v: "PACK", t: "课时包（按次扣）" }, { v: "TERM", t: "学期制（固定课次）" }], "PACK") +
        K.field("售价（元）*", "pdPrice", { attrs: 'inputmode="decimal"' }) +
        '<div id="pdPack">' + K.field("购买课时", "pdPaid", { type: "number", value: "24" }) + K.field("赠送课时", "pdBonus", { type: "number", value: "0" }) +
        K.field("有效天数", "pdDays", { type: "number", value: "180" }) + '</div>' +
        '<div id="pdTerm" style="display:none">' + K.field("承诺课次", "pdSess", { type: "number", value: "20" }) + K.field("学期天数", "pdTDays", { type: "number", value: "90" }) + "</div>" +
        '<button type="button" data-act="addProduct">保存</button>');
      h += items.length ? '<ul class="list">' + items.map(function (p) {
        var info = p.kind === "PACK" ? K.lessons(p.paidMilli) + "+" + K.lessons(p.bonusMilli) + " 课时 · 有效 " + p.validDays + " 天" : p.termSessions + " 次 · " + p.termDays + " 天";
        return '<li><div class="row"><div class="title">' + K.esc(p.name) + "</div><b>" + K.money(p.priceCents) + "</b></div>" +
          '<div class="meta">' + K.esc(p.courseName) + " · " + (p.kind === "PACK" ? "课时包" : "学期制") + " · " + info + "</div>" +
          '<div class="inline-actions">' + K.pill(p.status === "ACTIVE" ? "在售" : "下架", p.status === "ACTIVE" ? "ok" : "bad") +
          '<button type="button" class="mini alt" data-act="pdToggle" data-i="' + items.indexOf(p) + '">' + (p.status === "ACTIVE" ? "下架" : "上架") + "</button></div></li>";
      }).join("") + "</ul>" : K.empty("暂无商品");
      K.body("orgBody", h);
      var kindSel = document.getElementById("pdKind");
      kindSel.onchange = function () {
        document.getElementById("pdPack").style.display = kindSel.value === "PACK" ? "" : "none";
        document.getElementById("pdTerm").style.display = kindSel.value === "TERM" ? "" : "none";
      };
      K.bind(null, {
        addProduct: function () {
          var price = K.toCents(K.val("pdPrice"));
          if (!K.val("pdName") || isNaN(price)) { core().toast("请填写名称和正确售价"); return; }
          var kind = K.val("pdKind");
          var b = { name: K.val("pdName"), courseId: K.num("pdCourse"), kind: kind, priceCents: price };
          if (kind === "PACK") { b.paidMilli = K.num("pdPaid") * 1000; b.bonusMilli = K.num("pdBonus") * 1000; b.validDays = K.num("pdDays"); }
          else { b.termSessions = K.num("pdSess"); b.termDays = K.num("pdTDays"); }
          post("/api/wc/products", b, "商品已创建", reload);
        },
        pdToggle: function (el) {
          var p = items[parseInt(el.getAttribute("data-i"), 10)];
          post("/api/wc/products", { id: p.id, courseId: p.courseId, name: p.name, kind: p.kind, paidMilli: p.paidMilli, bonusMilli: p.bonusMilli, validDays: p.validDays,
            termSessions: p.termSessions, termDays: p.termDays, priceCents: p.priceCents, status: p.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" }, "已更新", reload);
        }
      });
    });
  }

  /* ================= 机构规则设置 ================= */
  WC.pages.settings = function () {
    core().shell(K.page("机构规则设置", '<div id="stBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    K.api("/api/wc/settings").then(function (s) {
      var h = '<p class="muted">规则变更仅对「之后」产生的请假、消课生效，已有记录不变。</p>' +
        K.field("机构联系电话（家长端展示）", "sContact", { value: s.contactPhone, type: "tel" }) +
        K.field("请假需提前（小时）", "sDeadline", { type: "number", value: s.leaveDeadlineHours }) +
        K.select("请假是否扣课时", "sLeaveC", [{ v: "0", t: "不扣" }, { v: "1", t: "扣课时" }], s.leaveConsumes) +
        K.select("旷课是否扣课时", "sAbsentC", [{ v: "1", t: "扣课时" }, { v: "0", t: "不扣" }], s.absentConsumes) +
        K.select("允许补课", "sMakeup", [{ v: "1", t: "允许" }, { v: "0", t: "不允许" }], s.allowMakeup) +
        K.field("补课额度有效天数", "sMkDays", { type: "number", value: s.makeupValidDays }) +
        K.select("扣课时顺序", "sOrder", [{ v: "PAID_FIRST", t: "先扣购买课时" }, { v: "BONUS_FIRST", t: "先扣赠送课时" }], s.deductOrder) +
        K.field("续费预警：剩余课时 ≤", "sThr", { type: "number", value: Math.round(Number(s.renewMilliThreshold) / 1000) }) +
        K.field("续费预警：到期前（天）", "sRenewDays", { type: "number", value: s.renewDays }) +
        (isAdmin() ? '<button type="button" data-act="save">保存规则</button>' : "");
      K.body("stBody", h);
      K.bind(null, {
        save: function () {
          post("/api/wc/settings", {
            contactPhone: K.val("sContact"), leaveDeadlineHours: K.val("sDeadline"), leaveConsumes: K.val("sLeaveC"), absentConsumes: K.val("sAbsentC"),
            allowMakeup: K.val("sMakeup"), makeupValidDays: K.val("sMkDays"), deductOrder: K.val("sOrder"), renewMilliThreshold: String(K.num("sThr") * 1000), renewDays: K.val("sRenewDays")
          }, "规则已保存");
        }
      });
    }).catch(function (e) { K.body("stBody", K.error(e)); });
  };

  /* ================= 公告 ================= */
  WC.pages.announce = function () {
    core().shell(K.page("公告", '<div id="anBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    function load() {
      Promise.all([K.api("/api/wc/announcements"), isAdmin() ? K.api("/api/wc/campuses") : Promise.resolve({ items: [] })]).then(function (res) {
        var items = res[0].items || [], camps = res[1].items || [];
        var AUD = { ALL: "全员", PARENT: "家长", TEACHER: "老师" };
        var h = isAdmin() ? addBox("发布新公告", K.field("标题 *", "anTitle") + K.field("内容 *", "anBody2", { type: "textarea", attrs: 'rows="4"' }) +
          K.select("发给", "anAud", [{ v: "ALL", t: "全员" }, { v: "PARENT", t: "仅家长" }, { v: "TEACHER", t: "仅老师" }], "PARENT") +
          K.select("校区", "anCampus", opts(camps, function (c) { return c.id; }, function (c) { return c.name; }, { v: 0, t: "全部校区" }), 0) +
          '<label class="check"><input id="anPin" type="checkbox" /> 置顶</label><button type="button" data-act="pub">发布并通知</button>') : "";
        h += items.length ? '<ul class="list">' + items.map(function (a) {
          return '<li class="ann' + (a.read || isAdmin() ? "" : " unread") + '"><div class="row"><div class="title">' + (a.pinned ? "📌 " : "") + K.esc(a.title) + "</div>" +
            (a.status === "REVOKED" ? K.pill("已撤回", "bad") : K.pill(AUD[a.audience] || a.audience)) + "</div>" +
            '<div class="abody">' + K.esc(a.body).replace(/\n/g, "<br>") + '</div><div class="meta">' + K.esc(a.author) + " · " + K.dt(a.createdAt) + "</div>" +
            (isAdmin() && a.status !== "REVOKED" ? '<div class="inline-actions"><button type="button" class="mini alt" data-act="revoke" data-id="' + a.id + '">撤回</button></div>' : "") + "</li>";
        }).join("") + "</ul>" : K.empty("暂无公告");
        K.body("anBody", h);
        K.bind(null, {
          pub: function () {
            if (!K.val("anTitle") || !K.val("anBody2")) { core().toast("请填写标题和内容"); return; }
            post("/api/wc/announcements", { title: K.val("anTitle"), body: K.val("anBody2"), audience: K.val("anAud"), campusId: K.num("anCampus"), pinned: document.getElementById("anPin").checked ? 1 : 0 },
              "已发布", load);
          },
          revoke: function (el) {
            if (!window.confirm("撤回后用户将不再看到该公告，确认？")) return;
            post("/api/wc/announcements/" + el.getAttribute("data-id") + "/revoke", {}, "已撤回", load);
          }
        });
      }).catch(function (e) { K.body("anBody", K.error(e)); });
    }
    load();
  };

  /* ================= 经营报表 ================= */
  var repState = { tab: "overview", range: "month" };
  function rangeOf(r) {
    var now = Date.now(), today = K.dayStart(now);
    if (r === "7") return { from: today - 6 * DAY, to: now + 1 };
    if (r === "30") return { from: today - 29 * DAY, to: now + 1 };
    var d = new Date(today + 8 * 3600000);
    var first = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) - 8 * 3600000;
    if (r === "last") {
      var prev = Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - 1, 1) - 8 * 3600000;
      return { from: prev, to: first };
    }
    return { from: first, to: now + 1 };
  }
  function rangeChips() {
    var rs = [["month", "本月"], ["last", "上月"], ["7", "近7天"], ["30", "近30天"]];
    return '<div class="chips">' + rs.map(function (r) {
      return '<button type="button" class="chip' + (repState.range === r[0] ? " on" : "") + '" data-rg="' + r[0] + '">' + r[1] + "</button>";
    }).join("") + "</div>";
  }
  function bindRange(again) {
    var cs = document.querySelectorAll("[data-rg]");
    for (var i = 0; i < cs.length; i++) cs[i].onclick = function (ev) { repState.range = ev.currentTarget.getAttribute("data-rg"); again(); };
  }
  function fetchText(path) {
    return fetch(core().apiBase() + path, { credentials: "include" }).then(function (r) { return r.text(); });
  }
  function exportBox(type, label, from, to) {
    return '<button type="button" class="secondary" data-act="export" data-type="' + type + '" data-label="' + label + '">导出 ' + label + " CSV</button>";
  }
  function bindExport(range) {
    var out = {
      export: function (el) {
        var type = el.getAttribute("data-type"), label = el.getAttribute("data-label");
        var q = "/api/wc/reports/export?type=" + type + (range ? "&from=" + range.from + "&to=" + range.to : "");
        K.submit(function () {
          return fetchText(q).then(function (t) {
            if (t.charAt(0) === "{") { var j = {}; try { j = JSON.parse(t); } catch (e) { /* ignore */ } throw new Error(j.message || "导出失败"); }
            var ok = K.download("wonder-" + type + "-" + new Date().toISOString().slice(0, 10) + ".csv", t);
            K.body("exBox", '<div class="card"><div class="muted">' + label + " 共 " + Math.max(0, t.split("\n").length - 2) + ' 行。若未自动下载，请复制后粘贴到 Excel / 表格：</div>' +
              '<textarea id="exText" rows="6" readonly>' + K.esc(t) + '</textarea><button type="button" class="mini" data-act="cpy">复制全部</button></div>');
            K.bind(null, { cpy: function () { K.copy(document.getElementById("exText").value).then(function (c) { core().toast(c ? "已复制" : "请长按文本框全选复制"); }); }, export: out.export });
            if (ok) core().toast("已生成文件");
          });
        });
      }
    };
    return out;
  }

  WC.pages.reports = function () {
    if (params().tab) { repState.tab = params().tab; core().state.params = {}; }
    var tabs = [{ k: "overview", t: "概览" }, { k: "daily", t: "趋势" }, { k: "arrears", t: "欠费" }, { k: "renewal", t: "续费预警" }, { k: "funnel", t: "招生漏斗" }];
    core().shell(K.page("经营报表", K.tabs(tabs, repState.tab) + (repState.tab === "overview" || repState.tab === "funnel" ? rangeChips() : "") +
      '<div id="rpBody">' + K.loading() + '</div><div id="exBox"></div>'));
    K.bind(null, { tab: function (k) { repState.tab = k; WC.pages.reports(); } });
    bindRange(WC.pages.reports);
    var rg = rangeOf(repState.range);
    var t = repState.tab;
    if (t === "overview") {
      K.api("/api/wc/reports/overview?from=" + rg.from + "&to=" + rg.to).then(function (o) {
        var pct = function (pm) { return (pm / 10).toFixed(1) + "%"; };
        var h = '<div class="kpis">' + K.kpi(K.money(o.receivedCents), "收款") + K.kpi(K.money(o.refundedCents), "退款", { warn: o.refundedCents > 0 }) +
          K.kpi(K.money(o.netCents), "净收入") + K.kpi(K.money(o.consumeRevenueCents), "确认课消收入") + "</div>" +
          "<h2>学员与教学</h2>" + '<div class="kpis">' + K.kpi(o.activeStudents, "在读学员") + K.kpi(o.newStudents, "新增学员") + K.kpi(o.newContracts, "新签合同") +
          K.kpi(o.sessionsCompleted, "完成课次") + K.kpi(pct(o.attendanceRatePermille), "出勤率") + K.kpi(o.leaves, "请假人次") + "</div>" +
          "<h2>招生与应收</h2>" + '<div class="kpis">' + K.kpi(o.leadsNew, "新增线索") + K.kpi(o.leadsWon, "成交") + K.kpi(pct(o.conversionPermille), "转化率") +
          K.kpi(o.leadsOpen, "跟进中", { go: "leads" }) + K.kpi(K.money(o.arrearsCents), "欠费合计", { warn: o.arrearsCents > 0, go: "reports", p: "tab=arrears" }) +
          K.kpi(o.renewalDue, "待续费", { warn: o.renewalDue > 0, go: "reports", p: "tab=renewal" }) + "</div>" +
          '<div class="actions">' + exportBox("payments", "收款流水", rg.from, rg.to) + exportBox("students", "学员名单") + "</div>";
        K.body("rpBody", h);
        K.bind(null, Object.assign({ tab: function (k) { repState.tab = k; WC.pages.reports(); } }, bindExport(rg)));
      }).catch(function (e) { K.body("rpBody", K.error(e)); });
    } else if (t === "daily") {
      K.api("/api/wc/reports/daily?days=14").then(function (j) {
        var items = j.items || [], max = 1;
        items.forEach(function (d) { max = Math.max(max, d.receivedCents, d.consumeCents); });
        K.body("rpBody", '<p class="muted">近 14 天：蓝=收款，橙=课消</p>' + (items.length ? '<ul class="list">' + items.map(function (d) {
          return '<li><div class="row"><div class="title">' + K.dayLabel(d.day) + '</div><div class="muted">' + d.sessions + " 课次</div></div>" +
            '<div class="bar"><i style="width:' + Math.round(d.receivedCents * 100 / max) + '%"></i></div><div class="meta">收款 ' + K.money(d.receivedCents) + (d.refundedCents ? " · 退款 " + K.money(d.refundedCents) : "") + "</div>" +
            '<div class="bar alt"><i style="width:' + Math.round(d.consumeCents * 100 / max) + '%"></i></div><div class="meta">课消 ' + K.money(d.consumeCents) + "</div></li>";
        }).join("") + "</ul>" : K.empty("暂无数据")));
      }).catch(function (e) { K.body("rpBody", K.error(e)); });
    } else if (t === "arrears") {
      K.api("/api/wc/reports/arrears").then(function (j) {
        var items = j.items || [], sum = 0;
        items.forEach(function (x) { sum += x.owedCents; });
        K.body("rpBody", '<div class="kpis">' + K.kpi(items.length, "欠费合同") + K.kpi(K.money(sum), "欠费合计", { warn: sum > 0 }) + "</div>" +
          (items.length ? '<ul class="list">' + items.map(function (x) {
            return '<li class="tap" data-go="studentDetail" data-id="' + x.studentId + '"><div class="row"><div class="title">' + K.esc(x.studentName) + " · " + K.esc(x.courseName) + "</div>" + K.pill("欠 " + K.money(x.owedCents), "bad") + "</div>" +
              '<div class="meta">应收 ' + K.money(x.receivableCents) + " · 已收 " + K.money(x.receivedCents) + ' · <a href="tel:' + K.esc(x.phone) + '">' + K.esc(x.phone) + "</a></div></li>";
          }).join("") + "</ul>" : K.empty("没有欠费，太棒了")) + '<div class="actions">' + exportBox("arrears", "欠费名单") + "</div>");
        K.bind(null, Object.assign({}, bindExport(null)));
      }).catch(function (e) { K.body("rpBody", K.error(e)); });
    } else if (t === "renewal") {
      K.api("/api/wc/reports/renewal").then(function (j) {
        var packs = j.packs || [], terms = j.terms || [];
        var h = '<p class="muted">按「机构规则设置」中的阈值计算。联系家长后可直接去售卖续费。</p><h2>课时包 · 余额不足/即将到期</h2>';
        h += packs.length ? '<ul class="list">' + packs.map(function (x) {
          return '<li><div class="row"><div class="title">' + K.esc(x.studentName) + " · " + K.esc(x.courseName) + "</div>" + K.pill("剩 " + K.lessons(x.remainingMilli) + " 课时", x.remainingMilli <= 2000 ? "bad" : "") + "</div>" +
            '<div class="meta">到期 ' + K.dateFull(x.expireAt) + ' · <a href="tel:' + K.esc(x.phone) + '">' + K.esc(x.phone) + "</a></div>" +
            '<div class="inline-actions"><button type="button" class="mini" data-go="sell" data-id="' + x.studentId + '">去续费</button></div></li>';
        }).join("") + "</ul>" : K.empty("暂无");
        h += "<h2>学期制 · 即将到期</h2>";
        h += terms.length ? '<ul class="list">' + terms.map(function (x) {
          return '<li><div class="row"><div class="title">' + K.esc(x.studentName) + " · " + K.esc(x.courseName) + "</div>" + K.pill("到期 " + K.date(x.termEnd)) + "</div>" +
            '<div class="meta"><a href="tel:' + K.esc(x.phone) + '">' + K.esc(x.phone) + '</a></div><div class="inline-actions"><button type="button" class="mini" data-go="sell" data-id="' + x.studentId + '">去续费</button></div></li>';
        }).join("") + "</ul>" : K.empty("暂无");
        K.body("rpBody", h);
        K.bind(null, {});
      }).catch(function (e) { K.body("rpBody", K.error(e)); });
    } else {
      K.api("/api/wc/reports/funnel").then(function (j) {
        var ST = { NEW: "新线索", CONTACTED: "已联系", TRIAL_BOOKED: "已约试听", TRIAL_DONE: "试听完成", WON: "已成交", LOST: "已流失" };
        var by = j.byStatus || [], max = 1;
        by.forEach(function (x) { max = Math.max(max, x.count); });
        var h = "<h2>各阶段线索数</h2>" + (by.length ? '<ul class="list">' + by.map(function (x) {
          return '<li><div class="row"><div>' + (ST[x.status] || x.status) + "</div><b>" + x.count + '</b></div><div class="bar"><i style="width:' + Math.round(x.count * 100 / max) + '%"></i></div></li>';
        }).join("") + "</ul>" : K.empty("暂无线索"));
        h += "<h2>渠道转化</h2>" + ((j.bySource || []).length ? '<ul class="list">' + j.bySource.map(function (x) {
          var s = SOURCE_NAME[x.source] || x.source;
          return '<li><div class="row"><div>' + s + "</div><div>" + x.count + " 条 · 成交 " + x.won + " · " + (x.count ? Math.round(x.won * 100 / x.count) : 0) + "%</div></div></li>";
        }).join("") + "</ul>" : K.empty("暂无"));
        h += "<h2>流失原因</h2>" + ((j.lostReasons || []).length ? '<ul class="list">' + j.lostReasons.map(function (x) {
          return '<li><div class="row"><div>' + K.esc(x.reason) + "</div><b>" + x.count + "</b></div></li>";
        }).join("") + "</ul>" : K.empty("暂无"));
        h += '<div class="actions">' + exportBox("leads", "线索名单") + "</div>";
        K.body("rpBody", h);
        K.bind(null, bindExport(null));
      }).catch(function (e) { K.body("rpBody", K.error(e)); });
    }
  };
  var SOURCE_NAME = { WALK_IN: "到店", REFERRAL: "转介绍", ONLINE: "线上", PHONE: "电话", EVENT: "活动", OTHER: "其他" };

  /* ================= 课酬 ================= */
  WC.pages.payroll = function () {
    core().shell(K.page(ident() === "TEACHER" ? "我的课酬" : "课酬统计", rangeChips() + '<div id="pyBody">' + K.loading() + '</div><div id="exBox"></div>'));
    K.bind(null, {});
    bindRange(WC.pages.payroll);
    var rg = rangeOf(repState.range);
    K.api("/api/wc/reports/payroll?from=" + rg.from + "&to=" + rg.to).then(function (j) {
      var items = j.items || [], total = 0;
      items.forEach(function (x) { total += x.payCents; });
      K.body("pyBody", '<div class="kpis">' + K.kpi(K.money(total), "课酬合计") + K.kpi(items.reduce(function (a, x) { return a + x.sessions; }, 0), "完成课次") + "</div>" +
        (items.length ? '<ul class="list">' + items.map(function (x) {
          return '<li><div class="row"><div class="title">' + K.esc(x.teacherName) + "</div><b>" + K.money(x.payCents) + "</b></div>" +
            '<div class="meta">' + x.sessions + " 课次 · " + Math.round(x.minutes / 60 * 10) / 10 + " 小时 · 单价 " + K.money(x.perSessionCents) + "</div></li>";
        }).join("") + "</ul>" : K.empty("该时段没有已完成课次")) +
        (ident() !== "TEACHER" ? '<p class="muted">仅统计「已完成」课次；单价在「课程与机构 → 员工」设置。</p><div class="actions">' + exportBox("payroll", "课酬明细", rg.from, rg.to) + "</div>" : ""));
      K.bind(null, bindExport(rg));
    }).catch(function (e) { K.body("pyBody", K.error(e)); });
  };

  /* ================= 课表日历（管理 / 老师 / 家长共用） ================= */
  var calStart = 0;
  function weekStartOf(ms) {
    var ds = K.dayStart(ms);
    var dow = new Date(ds + 8 * 3600000).getUTCDay();
    return ds - ((dow + 6) % 7) * DAY;
  }
  function calendarPage(title) {
    if (!calStart) calStart = weekStartOf(Date.now());
    var role = ident();
    var showAdd = role === "ADMIN";
    var kidId = role === "PARENT" ? core().state.childId : 0;
    core().shell(K.page(title, '<div id="kidBar"></div><div class="row weeknav"><button type="button" class="ghost" data-act="prev">‹ 上周</button><b>' +
      K.date(calStart) + " – " + K.date(calStart + 6 * DAY) + '</b><button type="button" class="ghost" data-act="next">下周 ›</button></div>' +
      '<div class="actions"><button type="button" class="secondary" data-act="thisWeek">回到本周</button>' + (showAdd ? '<button type="button" data-act="addOpen">＋ 排课</button>' : "") + "</div>" +
      '<div id="calAdd"></div><div id="calBody">' + K.loading() + "</div>"));
    var acts = {
      prev: function () { calStart -= 7 * DAY; calendarPage(title); },
      next: function () { calStart += 7 * DAY; calendarPage(title); },
      thisWeek: function () { calStart = weekStartOf(Date.now()); calendarPage(title); },
      addOpen: function () { openAdd(function () { calendarPage(title); }); },
      cancel: function (el) {
        var reason = window.prompt("停课原因（家长会收到通知，不扣课时）", "老师临时有事");
        if (!reason) return;
        post("/api/wc/sessions/" + el.getAttribute("data-id") + "/cancel", { reason: reason }, "已停课", function () { calendarPage(title); });
      }
    };
    K.bind(null, acts);
    function render() {
      var q = "/api/wc/schedule/range?from=" + calStart + "&to=" + (calStart + 7 * DAY) + (kidId ? "&studentId=" + kidId : "");
      K.api(q).then(function (j) {
        var items = j.items || [];
        var days = [];
        for (var i = 0; i < 7; i++) days.push(calStart + i * DAY);
        var today = K.dayStart(Date.now());
        K.body("calBody", days.map(function (d) {
          var its = items.filter(function (x) { return x.startAt >= d && x.startAt < d + DAY; });
          return '<div class="day' + (d === today ? " today" : "") + '"><h3>' + K.dayLabel(d) + "</h3>" + (its.length ? '<ul class="list">' + its.map(function (x) {
            return "<li><div class=\"row\"><div class=\"title\">" + K.hm(x.startAt) + "–" + K.hm(x.endAt) + " " + K.esc(x.className || x.courseName || "课程") + "</div>" +
              K.pill(K.sessionStatus(x.status), K.sessionKind(x.status)) + "</div><div class=\"meta\">" +
              (x.studentName ? K.esc(x.studentName) + " · " : "") + K.esc(x.teacherName) + (x.roomName ? " · " + K.esc(x.roomName) : "") + " · " + K.esc(x.campusName) + "</div>" +
              (showAdd && (x.status === "SCHEDULED" || x.status === "RESCHEDULED") ? '<div class="inline-actions"><button type="button" class="mini alt" data-act="cancel" data-id="' + x.id + '">停课</button></div>' : "") + "</li>";
          }).join("") + "</ul>" : '<p class="muted">无课</p>') + "</div>";
        }).join(""));
        K.bind(null, acts);
      }).catch(function (e) { K.body("calBody", K.error(e)); });
    }
    if (role === "PARENT") {
      WC.loadKids().then(function (kids) {
        kidId = core().state.childId;
        K.body("kidBar", WC.kidSwitcher(kids));
        WC.bindKids(function () { calendarPage(title); });
        if (!kids.length) { K.body("calBody", K.empty("还没有绑定孩子")); return; }
        render();
      }).catch(function (e) { K.body("calBody", K.error(e)); });
    } else {
      render();
    }
  }
  WC.pages.calendar = function () { calendarPage("排课日历"); };
  WC.pages.tWeek = function () { calendarPage("我的课表"); };
  WC.pages.pWeek = function () { calendarPage("课程表"); };

  function openAdd(done) {
    K.body("calAdd", '<div class="card">' + K.loading() + "</div>");
    Promise.all([K.api("/api/wc/classes"), K.api("/api/wc/rooms"), K.api("/api/wc/admin/students?status=ACTIVE")]).then(function (res) {
      var classes = (res[0].items || []).filter(function (c) { return c.status === "ACTIVE"; }), rooms = res[1].items || [], students = res[2].items || [];
      if (!classes.length) { K.body("calAdd", K.empty("请先创建班级（课程与机构 → 班级）")); return; }
      var first = K.toLocalInput(K.dayStart(Date.now()) + DAY + 17 * 3600000);
      K.body("calAdd", '<div class="card"><h3>排课（1 对 1 / 固定学员，可按周重复）</h3>' +
        K.select("班级 *", "scClass", opts(classes, function (c) { return c.id; }, function (c) { return c.name + " · " + (c.teacherName || "无老师"); })) +
        K.select("学员 *", "scStu", opts(students, function (s) { return s.id; }, function (s) { return s.name; }, { v: 0, t: "请选择" })) +
        K.select("扣费合同 *", "scCt", [{ v: 0, t: "先选学员" }]) +
        K.select("教室", "scRoom", opts(rooms, function (r) { return r.id; }, function (r) { return r.campusName + " · " + r.name; })) +
        K.field("首次上课时间 *", "scAt", { type: "datetime-local", value: first }) +
        K.field("时长（分钟）", "scDur", { type: "number", value: "60" }) +
        K.field("重复周数（1=只排一次）", "scWeeks", { type: "number", value: "1" }) +
        '<button type="button" data-act="scGo">确认排课</button><div id="scResult"></div></div>');
      document.getElementById("scStu").onchange = function () {
        var sid = K.num("scStu");
        if (!sid) return;
        K.api("/api/wc/students/" + sid + "/detail").then(function (d) {
          var cs = (d.contracts || []).filter(function (c) { return c.status === "ACTIVE"; });
          document.getElementById("scCt").innerHTML = cs.length ? cs.map(function (c) {
            return '<option value="' + c.id + '">' + K.esc(c.courseName) + (c.mode === "PACK" ? " · 剩 " + K.lessons(c.remainingMilli) + " 课时" : " · 学期制") + "</option>";
          }).join("") : '<option value="0">该学员无有效合同，请先售卖</option>';
        }).catch(K.fail);
      };
      K.bind(null, {
        scGo: function () {
          var cls = classes.filter(function (c) { return c.id === K.num("scClass"); })[0];
          if (!K.num("scStu") || !K.num("scCt")) { core().toast("请选择学员和有效合同"); return; }
          var at = K.fromLocalInput(K.val("scAt"));
          if (!at) { core().toast("请选择上课时间"); return; }
          K.submit(function () {
            return K.api("/api/wc/schedule/recurring", { method: "POST", body: {
              campusId: cls.campusId, classId: cls.id, teacherUserId: cls.teacherUserId, roomId: K.num("scRoom"), studentId: K.num("scStu"), contractId: K.num("scCt"),
              firstStartAt: at, durationMin: K.num("scDur") || 60, weeks: K.num("scWeeks") || 1 } }).then(function (j) {
              var h = '<p class="alert' + (j.skipped ? "" : " ok") + '">已排 ' + j.created + " 节" + (j.skipped ? "，跳过 " + j.skipped + " 节（冲突/不可用）" : "") + "</p>";
              if (j.problems && j.problems.length) h += "<ul class=\"list\">" + j.problems.map(function (p) { return "<li>" + K.dt(p.startAt) + "：" + K.esc(p.reason) + "</li>"; }).join("") + "</ul>";
              K.body("scResult", h + (j.created ? '<button type="button" class="mini" data-act="scDone">刷新课表</button>' : ""));
              K.bind(null, { scGo: function () {}, scDone: done });
            });
          });
        }
      });
    }).catch(function (e) { K.body("calAdd", K.error(e)); });
  }

  /* ================= 课后评价（负责人/教务/老师查看） ================= */
  WC.pages.reviews = function () {
    core().shell(K.page("家长评价", '<div id="rvBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    K.api("/api/wc/reviews").then(function (j) {
      var sum = j.summary || [], items = j.items || [];
      var h = "<h2>老师评分</h2>" + (sum.length ? '<ul class="list">' + sum.map(function (s) {
        var avg = s.count ? s.ratingSum / s.count : 0;
        return '<li><div class="row"><div class="title">' + K.esc(s.teacherName) + "</div><div>" + K.stars(Math.round(avg)) + " " + avg.toFixed(1) + " <span class=\"muted\">(" + s.count + " 条)</span></div></div></li>";
      }).join("") + "</ul>" : K.empty("暂无评价"));
      h += "<h2>最新评价</h2>" + (items.length ? '<ul class="list">' + items.map(function (r) {
        return '<li><div class="row"><div>' + K.stars(r.rating) + '</div><div class="muted">' + K.dt(r.createdAt) + "</div></div>" +
          '<div class="meta">' + K.esc(r.teacherName) + (r.studentName ? " · " + K.esc(r.studentName) : "") + "</div>" + (r.comment ? "<div>" + K.esc(r.comment) + "</div>" : "") + "</li>";
      }).join("") + "</ul>" : K.empty("暂无"));
      K.body("rvBody", h);
    }).catch(function (e) { K.body("rvBody", K.error(e)); });
  };
})();
