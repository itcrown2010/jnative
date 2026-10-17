/**
 * JNative Windows Task Monitor - small dialogs built from stock UiLib controls.
 *
 * @author 张晓达
 */
class TmDialogs {
    static Window runWin = null;
    static TextField runField = null;
    static Window mainWin = null;

    static void showRun(Window owner) {
        if (runWin != null && runWin.disposed == 0) {
            runWin.activate();
            return;
        }
        mainWin = owner;
        Window d = new Window("新建任务", 460, 200);
        d.setOwnerWindow(owner);
        Panel p = new Panel();
        Label l = new Label("Windows 将根据你所输入的名称，为你打开相应的程序、文件夹、文档或 Internet 资源。");
        l.setBounds(16, 14, 428, 44);
        Label l2 = new Label("打开(O):");
        l2.setBounds(16, 70, 60, 24);
        TextField tf = new TextField("");
        tf.setBounds(84, 66, 360, 28);
        Button ok = new Button("确定");
        ok.setBounds(188, 140, 80, 30);
        Button cancel = new Button("取消");
        cancel.setBounds(274, 140, 80, 30);
        Button browse = new Button("浏览(B)...");
        browse.setBounds(360, 140, 84, 30);
        p.add(l);
        p.add(l2);
        p.add(tf);
        p.add(ok);
        p.add(cancel);
        p.add(browse);
        d.setContent(p);
        d.setDefaultButton(ok);
        d.setCancelButton(cancel);
        runWin = d;
        runField = tf;
        ok.onClick(() -> {
            TmDialogs.doRun();
        });
        cancel.onClick(() -> {
            TmDialogs.closeRun();
        });
        browse.onClick(() -> {
            String f = FileDialog.open(TmDialogs.runWin, "exe");
            if (f != null && f.length() > 0) {
                TmDialogs.runField.setText(f);
            }
        });
        d.onClose(() -> {
            TmDialogs.runWin = null;
            TmDialogs.runField = null;
        });
        d.centerOnOwner();
        d.show();
        tf.requestFocus();
    }

    static void closeRun() {
        if (runWin != null && runWin.disposed == 0) {
            Window w = runWin;
            runWin = null;
            runField = null;
            w.close();
        }
    }

    static void doRun() {
        if (runField == null) {
            return;
        }
        String cmd = AmParse.trim(runField.getText());
        if (cmd.length() == 0) {
            return;
        }
        int rc = AmNative.runTask(cmd);
        if (rc != 0) {
            Dialogs.error(runWin, "Windows 找不到“" + cmd + "”。请确认输入的名称是否正确，然后再试一次。(错误 "
                    + String.valueOf(rc) + ")");
            return;
        }
        closeRun();
    }
}
