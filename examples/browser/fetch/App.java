import jnative.browser.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Browser.console().log("fetch demo");
        Document document = Browser.document();
        Element out = document.getElementById("out");
        out.setText("Hello from JNative");
    }
}
