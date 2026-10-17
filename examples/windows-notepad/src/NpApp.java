/**
 * Entry point of the JNative Notepad — a native (no JRE) re-implementation of the
 * Windows in-box Notepad built with the JNative AOT compiler.
 *
 * Usage: Notepad.exe [file]
 *
 * @author 张晓达
 */
class NpApp {
    public static void main(String[] args) {
        Application.setAppName("Notepad");
        Application.setDpiAware(true);
        Application app = new Application();
        NpWindow w = new NpWindow();
        w.show();
        if (args != null && args.length > 0 && args[0].length() > 0) {
            w.openFromArgument(args[0]);
        }
        app.run();
    }
}
