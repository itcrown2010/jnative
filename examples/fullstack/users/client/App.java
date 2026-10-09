import jnative.browser.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Browser.console().log("fullstack client");
        Document document = Browser.document();
        Element list = document.getElementById("users");
        list.setText("Hello from JNative");
    }
}
