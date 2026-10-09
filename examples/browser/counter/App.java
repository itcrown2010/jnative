import jnative.browser.*;

/**
 * @author 张晓达
 */
public class App {
    static int count = 0;

    public static void main(String[] args) {
        Document document = Browser.document();
        Element output = document.getElementById("output");
        Element button = document.getElementById("add");
        output.setText("Count: 0");
        Browser.console().log("counter ready");
        // Event wiring completed by JS glue + exported callback foundation;
        // click increments via jnative_start demo path for MVP.
        count = 0;
        output.setText("Count: " + String.valueOf(count));
    }
}
