import jnative.web.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        System.out.println("hashing...");
        String h = Password.hashBlocking("AlicePass1");
        System.out.println(h);
        int ok = Password.verifyBlocking("AlicePass1", h);
        System.out.println("verify=" + String.valueOf(ok));
        int bad = Password.verifyBlocking("wrong", h);
        System.out.println("bad=" + String.valueOf(bad));
    }
}
