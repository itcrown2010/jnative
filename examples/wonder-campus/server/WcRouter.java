/**
 * Route table for commercial modules. One generic handler class, dispatched by route id.
 * @author 张晓达
 */
class WcRouter {
    static void get(WebApp app, EventLoop loop, String path, String route) {
        app.futureGet(path, new WcApiHandler(loop, route, 0));
    }

    static void post(WebApp app, EventLoop loop, String path, String route) {
        app.futurePost(path, new WcApiHandler(loop, route, 0));
    }

    static void register(WebApp app, EventLoop loop) {
        app.futurePost("/api/wc/auth/login", new WcApiHandler(loop, "auth.login", 1));
        app.futurePost("/api/wc/auth/register", new WcApiHandler(loop, "auth.register", 1));
        post(app, loop, "/api/wc/auth/password", "auth.password");
        get(app, loop, "/api/wc/me/profile", "me.profile");
        post(app, loop, "/api/wc/me/profile", "me.profile.save");

        get(app, loop, "/api/wc/admin/dashboard", "admin.dashboard");
        get(app, loop, "/api/wc/admin/students", "students.list");
        post(app, loop, "/api/wc/admin/students", "students.create");
        get(app, loop, "/api/wc/students/:id/detail", "students.detail");
        post(app, loop, "/api/wc/students/:id/update", "students.update");
        post(app, loop, "/api/wc/students/:id/guardians", "guardians.add");
        post(app, loop, "/api/wc/invites", "invites.create");
        get(app, loop, "/api/wc/invites", "invites.list");

        get(app, loop, "/api/wc/courses", "courses.list");
        post(app, loop, "/api/wc/courses", "courses.save");
        get(app, loop, "/api/wc/classes", "classes.list");
        post(app, loop, "/api/wc/classes", "classes.save");
        get(app, loop, "/api/wc/classes/:id/members", "classes.members");
        post(app, loop, "/api/wc/classes/:id/members", "classes.member.add");
        post(app, loop, "/api/wc/classes/:id/members/remove", "classes.member.remove");
        get(app, loop, "/api/wc/rooms", "rooms.list");
        post(app, loop, "/api/wc/rooms", "rooms.save");
        get(app, loop, "/api/wc/staff", "staff.list");
        post(app, loop, "/api/wc/staff", "staff.create");
        post(app, loop, "/api/wc/staff/:id", "staff.update");
        get(app, loop, "/api/wc/teachers", "teachers.list");
        get(app, loop, "/api/wc/products", "products.list");
        post(app, loop, "/api/wc/products", "products.save");
        post(app, loop, "/api/wc/enroll/product", "enroll.product");
        get(app, loop, "/api/wc/settings", "settings.get");
        post(app, loop, "/api/wc/settings", "settings.save");
        post(app, loop, "/api/wc/schedule/recurring", "schedule.recurring");
        get(app, loop, "/api/wc/schedule/range", "schedule.range");

        get(app, loop, "/api/wc/leads", "leads.list");
        post(app, loop, "/api/wc/leads", "leads.create");
        get(app, loop, "/api/wc/leads/:id", "leads.detail");
        post(app, loop, "/api/wc/leads/:id/follow", "leads.follow");
        post(app, loop, "/api/wc/leads/:id/trial", "leads.trial");
        post(app, loop, "/api/wc/leads/:id/lose", "leads.lose");
        post(app, loop, "/api/wc/leads/:id/convert", "leads.convert");
        post(app, loop, "/api/wc/trials/:id/result", "trials.result");

        get(app, loop, "/api/wc/announcements", "ann.list");
        post(app, loop, "/api/wc/announcements", "ann.create");
        post(app, loop, "/api/wc/announcements/:id/read", "ann.read");
        post(app, loop, "/api/wc/announcements/:id/revoke", "ann.revoke");
        get(app, loop, "/api/wc/homework", "hw.list");
        post(app, loop, "/api/wc/homework", "hw.create");
        get(app, loop, "/api/wc/homework/:id/submissions", "hw.submissions");
        post(app, loop, "/api/wc/homework/:id/submit", "hw.submit");
        post(app, loop, "/api/wc/submissions/:id/review", "hw.review");
        post(app, loop, "/api/wc/reviews", "reviews.create");
        get(app, loop, "/api/wc/reviews", "reviews.list");
        get(app, loop, "/api/wc/reviews/pending", "reviews.pending");
        post(app, loop, "/api/wc/renew/request", "renew.request");

        get(app, loop, "/api/wc/reports/overview", "reports.overview");
        get(app, loop, "/api/wc/reports/daily", "reports.daily");
        get(app, loop, "/api/wc/reports/arrears", "reports.arrears");
        get(app, loop, "/api/wc/reports/renewal", "reports.renewal");
        get(app, loop, "/api/wc/reports/funnel", "reports.funnel");
        get(app, loop, "/api/wc/reports/payroll", "reports.payroll");
        get(app, loop, "/api/wc/reports/export", "reports.export");
        get(app, loop, "/api/wc/parent/overview", "parent.overview");
        get(app, loop, "/api/wc/teacher/students", "teacher.students");
    }

    static String dispatch(WcCtx c) {
        String r = c.route;
        if (r.equals("auth.login")) { return WcBizAuth.login(c); }
        if (r.equals("auth.register")) { return WcBizAuth.register(c); }
        if (r.equals("auth.password")) { return WcBizAuth.changePassword(c); }
        if (r.equals("me.profile")) { return WcBizAuth.profile(c); }
        if (r.equals("me.profile.save")) { return WcBizAuth.updateProfile(c); }

        if (r.equals("admin.dashboard")) { return WcBizReports.adminDashboard(c); }
        if (r.equals("students.list")) { return WcBizAdmin.studentsList(c); }
        if (r.equals("students.create")) { return WcBizAdmin.studentCreate(c); }
        if (r.equals("students.detail")) { return WcBizAdmin.studentDetail(c); }
        if (r.equals("students.update")) { return WcBizAdmin.studentUpdate(c); }
        if (r.equals("guardians.add")) { return WcBizAdmin.guardianAdd(c); }
        if (r.equals("invites.create")) { return WcBizAdmin.inviteCreate(c); }
        if (r.equals("invites.list")) { return WcBizAdmin.inviteList(c); }

        if (r.equals("courses.list")) { return WcBizAdmin.coursesList(c); }
        if (r.equals("courses.save")) { return WcBizAdmin.courseSave(c); }
        if (r.equals("classes.list")) { return WcBizAdmin.classesList(c); }
        if (r.equals("classes.save")) { return WcBizAdmin.classSave(c); }
        if (r.equals("classes.members")) { return WcBizAdmin.classMembers(c); }
        if (r.equals("classes.member.add")) { return WcBizAdmin.classMemberAdd(c); }
        if (r.equals("classes.member.remove")) { return WcBizAdmin.classMemberRemove(c); }
        if (r.equals("rooms.list")) { return WcBizAdmin.roomsList(c); }
        if (r.equals("rooms.save")) { return WcBizAdmin.roomSave(c); }
        if (r.equals("staff.list")) { return WcBizAdmin.staffList(c); }
        if (r.equals("staff.create")) { return WcBizAdmin.staffCreate(c); }
        if (r.equals("staff.update")) { return WcBizAdmin.staffUpdate(c); }
        if (r.equals("teachers.list")) { return WcBizAdmin.teachersLite(c); }
        if (r.equals("products.list")) { return WcBizAdmin.productsList(c); }
        if (r.equals("products.save")) { return WcBizAdmin.productSave(c); }
        if (r.equals("enroll.product")) { return WcBizAdmin.enrollProduct(c); }
        if (r.equals("settings.get")) { return WcBizAdmin.settingsGet(c); }
        if (r.equals("settings.save")) { return WcBizAdmin.settingsSave(c); }
        if (r.equals("schedule.recurring")) { return WcBizAdmin.scheduleRecurring(c); }
        if (r.equals("schedule.range")) { return WcBizReports.scheduleRange(c); }

        if (r.equals("leads.list")) { return WcBizCrm.leadsList(c); }
        if (r.equals("leads.create")) { return WcBizCrm.leadCreate(c); }
        if (r.equals("leads.detail")) { return WcBizCrm.leadDetail(c); }
        if (r.equals("leads.follow")) { return WcBizCrm.leadFollow(c); }
        if (r.equals("leads.trial")) { return WcBizCrm.leadTrial(c); }
        if (r.equals("leads.lose")) { return WcBizCrm.leadLose(c); }
        if (r.equals("leads.convert")) { return WcBizCrm.leadConvert(c); }
        if (r.equals("trials.result")) { return WcBizCrm.trialResult(c); }

        if (r.equals("ann.list")) { return WcBizEngage.annList(c); }
        if (r.equals("ann.create")) { return WcBizEngage.annCreate(c); }
        if (r.equals("ann.read")) { return WcBizEngage.annRead(c); }
        if (r.equals("ann.revoke")) { return WcBizEngage.annRevoke(c); }
        if (r.equals("hw.list")) { return WcBizEngage.hwList(c); }
        if (r.equals("hw.create")) { return WcBizEngage.hwCreate(c); }
        if (r.equals("hw.submissions")) { return WcBizEngage.hwSubmissions(c); }
        if (r.equals("hw.submit")) { return WcBizEngage.hwSubmit(c); }
        if (r.equals("hw.review")) { return WcBizEngage.hwReview(c); }
        if (r.equals("reviews.create")) { return WcBizEngage.reviewCreate(c); }
        if (r.equals("reviews.list")) { return WcBizEngage.reviewList(c); }
        if (r.equals("reviews.pending")) { return WcBizEngage.reviewPending(c); }
        if (r.equals("renew.request")) { return WcBizEngage.renewRequest(c); }

        if (r.equals("reports.overview")) { return WcBizReports.overview(c); }
        if (r.equals("reports.daily")) { return WcBizReports.daily(c); }
        if (r.equals("reports.arrears")) { return WcBizReports.arrears(c); }
        if (r.equals("reports.renewal")) { return WcBizReports.renewal(c); }
        if (r.equals("reports.funnel")) { return WcBizReports.funnel(c); }
        if (r.equals("reports.payroll")) { return WcBizReports.payroll(c); }
        if (r.equals("reports.export")) { return WcBizReports.export(c); }
        if (r.equals("parent.overview")) { return WcBizReports.parentOverview(c); }
        if (r.equals("teacher.students")) { return WcBizReports.teacherStudents(c); }
        return c.fail(404, "NOT_FOUND", "未知接口 " + r);
    }
}
