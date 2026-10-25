import jnative.ui.*;

public class Main {
    public static void main(String[] args) {
        Application app = new Application();
        if (args.length > 0 && args[0].equals("--selftest")) {
            int failed = SelfTest.run();
            System.out.println(failed == 0 ? "ALL OK" : failed + " FAILED");
            System.exit(failed == 0 ? 0 : 1);
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
