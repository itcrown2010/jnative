/** Minimal repro: static final int constants were rejected by Parser.
 * @author 张晓达
 */
public class StaticFinalRepro {
    public static final int X = 1;
    public static void main(String[] args) {
        System.out.println(X);
    }
}
