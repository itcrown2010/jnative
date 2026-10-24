import jnative.ui.*;
import jnative.io.*;

/**
 * JNative Studio entry point.
 *
 * @author 张晓达
 */
public class StudioMain {
    static DsDoc sampleDoc() {
        DsDoc d = DsSample.login("LoginForm");
        d.clearHistory();
        return d;
    }

    static void runIde(String openDir, boolean demo) {
        Application app = new Application();
        Window w = new Window("JNative Studio", 1440, 900);
        w.setMinSize(960, 600);
        IdeApp ide = new IdeApp(w);
        w.setContent(ide);
        w.onClose(() -> {
            if (!ide.confirmExit()) {
                w.vetoClose();
            }
        });
        w.show();
        w.activate();
        if (openDir.length() > 0) {
            ide.openProject(openDir);
        }
        if (demo) {
            IdeDemo.start(ide);
        }
        app.run();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            runIde("", false);
            return;
        }
        if (args[0].equals("--welcome")) {
            runIde("", true);
            return;
        }
        if (args[0].equals("--newproject") && args.length > 2) {
            if (args.length > 3 && args[3].equals("blank")) {
                IdeDialog.template = 1;
            } else if (args.length > 3 && args[3].equals("console")) {
                IdeDialog.template = 2;
            }
            IdeProject p = IdeProject.create(args[1], args[2]);
            if (p != null && args.length > 3 && args[3].equals("everything")) {
                DsDoc ev = DsSample.everything();
                ev.className = "MainForm";
                ev.root.name = "MainForm";
                IdeProject.writeForm(p.dir + "/src", ev, false);
            }
            System.out.println(p == null ? "FAILED" : "created " + p.dir);
            return;
        }
        if (args[0].equals("--open") && args.length > 1) {
            runIde(args[1], false);
            return;
        }
        if (args[0].equals("--demo") && args.length > 1) {
            runIde(args[1], true);
            return;
        }
        if (args.length > 0 && args[0].equals("--designer")) {
            Application app = new Application();
            Window w = new Window("JNative Studio - Designer", 1440, 900);
            DsDesigner d = new DsDesigner(sampleDoc());
            w.setContent(d);
            w.show();
            w.activate();
            int[] step = new int[1];
            UiTimer tm = new UiTimer(3000, () -> {
                step[0] = step[0] + 1;
                if (step[0] == 1) {
                    d.selectNode(d.doc.findByName("btnLogin"));
                    d.props.scrollBy(120);
                } else if (step[0] == 2) {
                    d.surface.externalDrop("CheckBox", 200, 250);
                } else if (step[0] == 3) {
                    d.setDeviceIndex(3);
                    d.deviceBox.setSelectedIndex(3);
                } else if (step[0] == 4) {
                    d.togglePreview();
                }
            });
            app.run();
            return;
        }
        if (args.length > 1 && args[0].equals("--genproject")) {
            DsDoc d = DsSample.everything();
            String dir = args[1];
            File.writeString(dir + "/src/DemoFormBase.java", DsGen.generateBase(d));
            File.writeString(dir + "/src/DemoForm.java", DsGen.userStub(d));
            File.writeString(dir + "/src/Main.java", DsGen.mainStub(d));
            return;
        }
        if (args.length > 0 && args[0].equals("--selftest")) {
            if (IdeSelfTest.run() != 0) {
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("--gen")) {
            DsDoc d = DsSample.everything();
            System.out.println(d.toFileText());
            System.out.println(DsGen.generateBase(d));
            DsDoc r = DsDoc.fromFileText(d.toFileText(), "X");
            System.out.println(r.toFileText().equals(d.toFileText()) ? "ROUNDTRIP OK" : "ROUNDTRIP DIFF");
            return;
        }
    }
}
