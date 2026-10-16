/**
 * Entry point of the JNative Windows-style Calculator.
 *
 * @author 张晓达
 */
class WcApp {
    public static void main(String[] args) {
        Application.setAppName("Calculator");
        Application app = new Application();
        WcUi ui = new WcUi();
        ui.show();
        app.run();
    }
}
