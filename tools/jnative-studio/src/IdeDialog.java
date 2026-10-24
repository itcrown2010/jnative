import jnative.ui.*;
import jnative.graphics.*;

interface IdeStringHandler {
    void handle(String s);
}

interface IdeStringPairHandler {
    void handle(String a, String b);
}

/**
 * Small modal-style input dialogs (the stdlib prompt is only a confirm box).
 *
 * @author 张晓达
 */
public class IdeDialog {
    static Window last;

    static Label label(Panel p, String text, int x, int y, int w) {
        Label l = new Label(text);
        l.setForegroundRgba(Component.rgba(75, 85, 99, 255));
        p.add(l);
        l.setBounds(x, y, w, 20);
        return l;
    }

    /** Single text input. The handler gets the trimmed text on OK. */
    static void input(Window owner, String title, String prompt, String initial, IdeStringHandler h) {
        Window w = new Window(title, 440, 168);
        w.setDialogStyle(owner);
        Panel p = new Panel();
        label(p, prompt, 20, 18, 400);
        TextField tf = new TextField(initial);
        p.add(tf);
        tf.setBounds(20, 46, 400, 30);
        Button ok = new Button("确定");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(250, 108, 80, 32);
        cancel.setBounds(340, 108, 80, 32);
        ok.onClick(() -> {
            String t = tf.getText().trim();
            if (t.length() == 0) {
                return;
            }
            w.close();
            h.handle(t);
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        last = w;
        w.show();
        tf.requestFocus();
    }

    /** Template chosen in the last New Project dialog: 0 form app, 1 blank form, 2 console. */
    static int template = 0;

    /** New-project dialog: project name + parent folder. */
    static void newProject(Window owner, String defaultParent, IdeStringPairHandler h) {
        Window w = new Window("新建项目", 520, 290);
        w.setDialogStyle(owner);
        Panel p = new Panel();
        label(p, "项目名称", 20, 18, 200);
        TextField name = new TextField("MyApp");
        p.add(name);
        name.setBounds(20, 42, 480, 30);
        label(p, "保存位置", 20, 84, 200);
        TextField loc = new TextField(defaultParent);
        p.add(loc);
        loc.setBounds(20, 108, 390, 30);
        Button browse = new Button("浏览…");
        p.add(browse);
        browse.setBounds(420, 106, 80, 32);
        browse.onClick(() -> {
            String d = FileDialogEx.show(w, 2, "", "", "", 0);
            if (d != null && d.length() > 0) {
                loc.setText(d);
            }
        });
        label(p, "项目模板", 20, 150, 200);
        SegmentedControl tpl = new SegmentedControl("窗体应用\t空白窗体\t控制台程序");
        tpl.setSelectedIndex(template);
        p.add(tpl);
        tpl.setBounds(20, 174, 480, 30);
        Button ok = new Button("创建");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(330, 226, 80, 32);
        cancel.setBounds(420, 226, 80, 32);
        ok.onClick(() -> {
            template = tpl.getSelectedIndex();
            String n = name.getText().trim();
            String l = loc.getText().trim();
            if (n.length() == 0 || l.length() == 0) {
                return;
            }
            w.close();
            h.handle(l, n);
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        last = w;
        w.show();
        name.requestFocus();
    }

    /** Compiler / Java settings. */
    static void settings(Window owner, Runnable changed) {
        Window w = new Window("设置", 560, 244);
        w.setDialogStyle(owner);
        Panel p = new Panel();
        label(p, "JNative 编译器（jnative.jar 或 target/classes 目录）", 20, 18, 500);
        TextField comp = new TextField(IdeSettings.compiler);
        p.add(comp);
        comp.setBounds(20, 42, 410, 30);
        Button browse = new Button("浏览…");
        p.add(browse);
        browse.setBounds(440, 40, 100, 32);
        browse.onClick(() -> {
            String d = FileDialogEx.show(w, 0, "JNative 编译器 (*.jar)|*.jar", "", "", 0);
            if (d != null && d.length() > 0) {
                comp.setText(d);
            }
        });
        label(p, "Java 命令", 20, 88, 300);
        TextField java = new TextField(IdeSettings.javaCmd);
        p.add(java);
        java.setBounds(20, 112, 520, 30);
        Button ok = new Button("保存");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(360, 178, 80, 32);
        cancel.setBounds(450, 178, 80, 32);
        ok.onClick(() -> {
            IdeSettings.compiler = comp.getText().trim();
            IdeSettings.javaCmd = java.getText().trim().length() == 0 ? "java" : java.getText().trim();
            IdeSettings.save();
            w.close();
            if (changed != null) {
                changed.run();
            }
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        last = w;
        w.show();
    }
}
