import jnative.ui.*;

public class Main {
    public static void main(String[] args) {
        Application app = new Application();
        if (args.length > 0 && args[0].equals("--selftest")) {
            SelfTest.start();
            app.run();
            return;
        }
        if (args.length > 1 && args[0].equals("--snapshot")) {
            SelfTest.snapshot(args[1], args.length > 2 ? args[2] : "");
            app.run();
            return;
        }
        MainForm form = new MainForm();
        form.show();
        if (args.length > 0 && args[0].length() > 0 && !args[0].startsWith("-")) {
            form.openPath(args[0]);
        }
        app.run();
    }
}
