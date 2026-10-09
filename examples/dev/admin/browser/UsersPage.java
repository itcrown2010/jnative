import jnative.browser.*;

/**
 * @author 张晓达
 */
class UsersPage {
    public static void main(String[] args) {
        Document document = Browser.document();
        Element root = document.getElementById("app");
        Element h = document.createElement("h1");
        h.setText("Users");
        if (root != null) {
            root.appendChild(h);
        }
        Browser.console().log("admin browser page");
    }
}
