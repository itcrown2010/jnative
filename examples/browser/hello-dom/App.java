import jnative.browser.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Browser.console().log("Hello JNative WASM!");
        Document document = Browser.document();
        Element title = document.getElementById("title");
        title.setText("Hello from JNative");
    }
}
