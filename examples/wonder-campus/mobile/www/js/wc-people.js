/* 老师与家长专属页面：我的学员、作业、课后评价、续费咨询。 */
(function () {
  var WC = window.WC = window.WC || {};
  var K = WC.kit;
  function core() { return WC.core; }
  function params() { return core().state.params || {}; }
  function post(url, body, okMsg, after) {
    K.submit(function () {
      return K.api(url, { method: "POST", body: body }).then(function (j) {
        if (okMsg) core().toast(okMsg);
        if (after) after(j);
      });
    });
  }
  /* 家长页统一头部：孩子切换 + 内容容器 */
  function kidPage(title, build) {
    core().shell(K.page(title, '<div id="kidBar"></div><div id="kpBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    WC.loadKids().then(function (kids) {
      K.body("kidBar", WC.kidSwitcher(kids));
      WC.bindKids(function () { kidPage(title, build); });
      if (!kids.length) { K.body("kpBody", K.empty("还没有绑定孩子，请联系机构")); return; }
      build(core().state.childId, kids);
    }).catch(function (e) { K.body("kpBody", K.error(e)); });
  }

  /* ================= 老师：我的学员 ================= */
  WC.pages.tStudents = function () {
    core().shell(K.page("我的学员", '<div id="tsBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    K.api("/api/wc/teacher/students").then(function (j) {
      var items = j.items || [];
      K.body("tsBody", items.length ? '<ul class="list">' + items.map(function (s) {
        return '<li><div class="row"><div class="title">' + K.esc(s.name) + (s.ageBand ? " " + K.pill(s.ageBand) : "") + "</div>" +
          '<div class="muted">' + (s.lastLessonAt ? "上次 " + K.date(s.lastLessonAt) : "未上过课") + "</div></div>" +
          '<div class="meta">出勤 ' + s.present + " · 缺勤 " + s.absent + " · 已写反馈 " + s.feedbacks + "</div>" +
          (s.note ? '<div class="note">' + K.esc(s.note) + "</div>" : "") + "</li>";
      }).join("") + "</ul>" : K.empty("还没有分配给你的学员"));
    }).catch(function (e) { K.body("tsBody", K.error(e)); });
  };

  /* ================= 老师/教务：作业 ================= */
  WC.pages.tHomework = function () {
    core().shell(K.page("作业", '<div id="hwBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    function load() {
      Promise.all([K.api("/api/wc/homework"), K.api("/api/wc/classes")]).then(function (res) {
        var items = res[0].items || [], classes = (res[1].items || []).filter(function (c) { return c.status === "ACTIVE"; });
        var dueDefault = K.toLocalInput(K.dayStart(Date.now()) + 3 * 86400000 + 20 * 3600000);
        var h = '<details class="add"><summary>＋ 布置作业</summary><div class="card">' +
          K.field("标题 *", "hwTitle") + K.select("布置给 *", "hwClass", classes.map(function (c) { return { v: c.id, t: c.name + "（" + c.memberCount + " 人）" }; })) +
          K.field("作业要求", "hwText", { type: "textarea", attrs: 'rows="3"' }) +
          K.field("截止时间", "hwDue", { type: "datetime-local", value: dueDefault }) +
          '<button type="button" data-act="hwSave">发布并通知家长</button></div></details>';
        h += items.length ? '<ul class="list">' + items.map(function (x) {
          return '<li class="tap" data-go="hwDetail" data-id="' + x.id + '"><div class="row"><div class="title">' + K.esc(x.title) + "</div>" +
            (x.pendingReview ? K.pill(x.pendingReview + " 份待点评", "bad") : K.pill("无待办", "ok")) + "</div>" +
            '<div class="meta">' + K.esc(x.className || x.studentName || "—") + " · 截止 " + K.dt(x.dueAt) + " · 已交 " + x.submitted + "</div></li>";
        }).join("") + "</ul>" : K.empty("还没有布置过作业");
        K.body("hwBody", h);
        K.bind(null, {
          hwSave: function () {
            if (!K.val("hwTitle")) { core().toast("请填写作业标题"); return; }
            post("/api/wc/homework", { title: K.val("hwTitle"), classId: K.num("hwClass"), body: K.val("hwText"), dueAt: K.fromLocalInput(K.val("hwDue")) }, "作业已发布", load);
          }
        });
      }).catch(function (e) { K.body("hwBody", K.error(e)); });
    }
    load();
  };

  WC.pages.hwDetail = function () {
    var id = params().id;
    core().shell(K.page("作业提交", '<div id="hdBody">' + K.loading() + "</div>"));
    K.bind(null, {});
    var ST = { TODO: "未提交", SUBMITTED: "待点评", REVIEWED: "已点评" };
    function load() {
      K.api("/api/wc/homework/" + id + "/submissions").then(function (j) {
        var items = j.items || [];
        var done = items.filter(function (x) { return x.status !== "TODO"; }).length;
        K.body("hdBody", '<div class="kpis">' + K.kpi(items.length, "应交") + K.kpi(done, "已交") + K.kpi(items.length - done, "未交", { warn: items.length - done > 0 }) + "</div>" +
          '<ul class="list">' + items.map(function (x) {
            var box = "";
            if (x.status === "SUBMITTED" || x.status === "REVIEWED") {
              box = '<div class="note">' + K.esc(x.content) + "</div>" + (x.status === "REVIEWED" ? '<div>' + K.stars(x.stars) + " " + K.esc(x.comment) + "</div>" : "") +
                '<div class="inline-actions"><button type="button" class="mini" data-act="rv" data-sid="' + x.submissionId + '">' + (x.status === "REVIEWED" ? "修改点评" : "点评") + '</button></div><div id="rv' + x.submissionId + '"></div>';
            }
            return '<li><div class="row"><div class="title">' + K.esc(x.studentName) + "</div>" + K.pill(ST[x.status] || x.status, x.status === "REVIEWED" ? "ok" : (x.status === "TODO" ? "bad" : "")) + "</div>" + box + "</li>";
          }).join("") + "</ul>");
        K.bind(null, {
          rv: function (el) {
            var sid = el.getAttribute("data-sid");
            K.body("rv" + sid, '<div class="card">' + K.select("评分", "rvStars", [{ v: 5, t: "★★★★★ 优秀" }, { v: 4, t: "★★★★ 很好" }, { v: 3, t: "★★★ 良好" }, { v: 2, t: "★★ 需努力" }, { v: 1, t: "★ 待改进" }], 5) +
              K.field("评语 *", "rvText", { type: "textarea", attrs: 'rows="2"', placeholder: "具体指出做得好的地方和一个改进建议" }) +
              '<button type="button" data-act="rvSave" data-sid="' + sid + '">提交点评</button></div>');
            K.bind(null, {
              rvSave: function () {
                if (!K.val("rvText")) { core().toast("请填写评语"); return; }
                post("/api/wc/submissions/" + sid + "/review", { stars: K.num("rvStars"), comment: K.val("rvText") }, "已点评，家长会收到通知", load);
              }
            });
          }
        });
      }).catch(function (e) { K.body("hdBody", K.error(e)); });
    }
    load();
  };

  /* ================= 家长：作业 ================= */
  WC.pages.pHomework = function () {
    kidPage("作业", function (kid) {
      var ST = { TODO: "待完成", SUBMITTED: "已提交", REVIEWED: "已点评" };
      function load() {
        K.api("/api/wc/homework?studentId=" + kid).then(function (j) {
          var items = j.items || [], now = Date.now();
          K.body("kpBody", items.length ? '<ul class="list">' + items.map(function (x) {
            var late = x.status === "TODO" && x.dueAt && x.dueAt < now;
            var h = '<li><div class="row"><div class="title">' + K.esc(x.title) + "</div>" + K.pill(ST[x.status] || x.status, x.status === "REVIEWED" ? "ok" : (late ? "bad" : "")) + "</div>" +
              '<div class="meta">' + K.esc(x.teacher) + " · 截止 " + K.dt(x.dueAt) + (late ? "（已过期，仍可补交）" : "") + "</div>" + (x.body ? "<div>" + K.esc(x.body) + "</div>" : "");
            if (x.status === "REVIEWED") h += '<div class="card">' + K.stars(x.stars) + "<div>" + K.esc(x.comment) + "</div></div>";
            if (x.status !== "REVIEWED") {
              if (x.content) h += '<div class="note">我的提交：' + K.esc(x.content) + "</div>";
              h += '<div class="inline-actions"><button type="button" class="mini" data-act="sub" data-id="' + x.id + '">' + (x.status === "TODO" ? "提交作业" : "重新提交") + '</button></div><div id="sb' + x.id + '"></div>';
            }
            return h + "</li>";
          }).join("") + "</ul>" : K.empty("老师还没有布置作业"));
          K.bind(null, {
            sub: function (el) {
              var hid = el.getAttribute("data-id");
              K.body("sb" + hid, '<div class="card">' + K.field("完成情况 *", "sbText", { type: "textarea", attrs: 'rows="3"', placeholder: "例如：已完成 3 幅速写，孩子对色彩搭配很感兴趣" }) +
                '<button type="button" data-act="sbGo" data-id="' + hid + '">确认提交</button></div>');
              K.bind(null, {
                sbGo: function () {
                  if (!K.val("sbText")) { core().toast("请填写完成情况"); return; }
                  post("/api/wc/homework/" + hid + "/submit", { studentId: kid, content: K.val("sbText") }, "已提交", load);
                }
              });
            }
          });
        }).catch(function (e) { K.body("kpBody", K.error(e)); });
      }
      load();
    });
  };

  /* ================= 家长：课后评价 ================= */
  WC.pages.pReview = function () {
    kidPage("课后评价", function (kid) {
      function load() {
        K.api("/api/wc/reviews/pending?studentId=" + kid).then(function (j) {
          var items = j.items || [];
          K.body("kpBody", '<p class="muted">您的评价仅机构管理层可看到姓名，老师看到的是匿名评分。</p>' + (items.length ? '<ul class="list">' + items.map(function (x) {
            return '<li><div class="row"><div class="title">' + K.dt(x.startAt) + " " + K.esc(x.className) + '</div></div><div class="meta">' + K.esc(x.teacherName) + " 老师</div>" +
              '<div class="stars-pick" data-sess="' + x.sessionId + '">' + [1, 2, 3, 4, 5].map(function (n) { return '<button type="button" class="star" data-act="star" data-sess="' + x.sessionId + '" data-n="' + n + '">☆</button>'; }).join("") + "</div>" +
              '<textarea id="cm' + x.sessionId + '" rows="2" placeholder="说说这节课的感受（可选）"></textarea>' +
              '<button type="button" class="mini" data-act="rate" data-sess="' + x.sessionId + '">提交评价</button></li>';
          }).join("") + "</ul>" : K.empty("近 30 天的课都已评价，谢谢！")));
          var picked = {};
          K.bind(null, {
            star: function (el) {
              var s = el.getAttribute("data-sess"), n = parseInt(el.getAttribute("data-n"), 10);
              picked[s] = n;
              var sibs = document.querySelectorAll('.star[data-sess="' + s + '"]');
              for (var i = 0; i < sibs.length; i++) sibs[i].textContent = (i + 1) <= n ? "★" : "☆";
            },
            rate: function (el) {
              var s = el.getAttribute("data-sess");
              if (!picked[s]) { core().toast("请先点星星打分"); return; }
              post("/api/wc/reviews", { sessionId: parseInt(s, 10), studentId: kid, rating: picked[s], comment: K.val("cm" + s) }, "感谢您的评价", load);
            }
          });
        }).catch(function (e) { K.body("kpBody", K.error(e)); });
      }
      load();
    });
  };

  /* ================= 家长：续费咨询 ================= */
  WC.pages.pRenew = function () {
    kidPage("续费咨询", function (kid) {
      Promise.all([K.api("/api/wc/products"), K.api("/api/wc/parent/overview?studentId=" + kid)]).then(function (res) {
        var ps = res[0].items || [], ov = res[1];
        var h = '<div class="card"><div class="muted">当前剩余课时</div><div class="big">' + K.lessons(ov.remainingMilli) + " 课时</div>" +
          (ov.renewHint ? '<p class="alert">课时即将用完，建议提前续费，避免耽误上课。</p>' : "") + "</div>";
        h += "<h2>在售课程套餐</h2>" + (ps.length ? '<ul class="list">' + ps.map(function (p) {
          var info = p.kind === "PACK" ? K.lessons(p.paidMilli) + (p.bonusMilli ? " + 赠 " + K.lessons(p.bonusMilli) : "") + " 课时，有效 " + p.validDays + " 天" : p.termSessions + " 次，" + p.termDays + " 天";
          return '<li><div class="row"><div class="title">' + K.esc(p.name) + "</div><b>" + K.money(p.priceCents) + "</b></div><div class=\"meta\">" + K.esc(p.courseName) + " · " + info + "</div>" +
            '<div class="inline-actions"><button type="button" class="mini" data-act="ask" data-id="' + p.id + '">我想续这个</button></div></li>';
        }).join("") + "</ul>" : K.empty("暂无在售套餐")) +
          K.field("给教务留言（可选）", "rnNote", { type: "textarea", attrs: 'rows="2"' }) +
          '<p class="muted">提交后教务老师会联系您确认并完成缴费，本应用不直接收款。</p>';
        K.body("kpBody", h);
        K.bind(null, {
          ask: function (el) {
            post("/api/wc/renew/request", { studentId: kid, productId: parseInt(el.getAttribute("data-id"), 10), note: K.val("rnNote") }, "已通知教务，请留意来电", null);
          }
        });
      }).catch(function (e) { K.body("kpBody", K.error(e)); });
    });
  };
})();
