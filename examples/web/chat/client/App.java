import jnative.browser.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Browser.console().log("JNative Chat WASM client");
        Document document = Browser.document();
        Element title = document.getElementById("title");
        if (title != null) {
            title.setText("Hello from JNative");
        }
    }
}
